import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.mapred.AvroCollector;
import org.apache.avro.mapred.AvroJob;
import org.apache.avro.mapred.AvroMapper;
import org.apache.avro.mapred.AvroReducer;
import org.apache.avro.mapred.AvroUtf8InputFormat;
import org.apache.avro.mapred.Pair;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class AvroSpecificMaxTemperature extends Configured implements Tool {
  
  public static class MaxTemperatureMapper extends AvroMapper<Utf8, Pair<Integer, WeatherRecord>> {
    private NcdcRecordParser parser = new NcdcRecordParser();
    private WeatherRecord record = new WeatherRecord();
    @Override
    public void map(Utf8 line,
        AvroCollector<Pair<Integer, WeatherRecord>> collector, Reporter reporter)
        throws IOException {
      parser.parse(line.toString());
      if (parser.isValidTemperature()) {
        record.year = parser.getYearInt();
        record.temperature = parser.getAirTemperature();
        record.stationId = new Utf8(parser.getStationId());
        collector.collect(
            new Pair<Integer, WeatherRecord>(parser.getYearInt(), record));
      }
    }
  }
  
  public static class MaxTemperatureReducer extends
      AvroReducer<Integer, WeatherRecord, WeatherRecord> {

    @Override
    public void reduce(Integer key, Iterable<WeatherRecord> values,
        AvroCollector<WeatherRecord> collector, Reporter reporter) throws IOException {
      WeatherRecord max = null;
      for (WeatherRecord value : values) {
        if (max == null || value.temperature > max.temperature) {
          max = newWeatherRecord(value);
        }
      }
      collector.collect(max);
    }
    private WeatherRecord newWeatherRecord(WeatherRecord value) {
      WeatherRecord record = new WeatherRecord();
      record.year = value.year;
      record.temperature = value.temperature;
      record.stationId = value.stationId;
      return record;
    }
  }

  @Override
  public int run(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.printf("Usage: %s [generic options] <input> <output>\n",
          getClass().getSimpleName());
      ToolRunner.printGenericCommandUsage(System.err);
      return -1;
    }
    
    JobConf conf = new JobConf(getConf(), getClass());
    conf.setJobName("Max temperature");
    
    FileInputFormat.addInputPath(conf, new Path(args[0]));
    FileOutputFormat.setOutputPath(conf, new Path(args[1]));
    
    AvroJob.setInputSchema(conf, Schema.create(Schema.Type.STRING));
    AvroJob.setMapOutputSchema(conf,
        Pair.getPairSchema(Schema.create(Schema.Type.INT), WeatherRecord.SCHEMA$));
    AvroJob.setOutputSchema(conf, WeatherRecord.SCHEMA$);
    conf.setInputFormat(AvroUtf8InputFormat.class);

    AvroJob.setMapperClass(conf, MaxTemperatureMapper.class);
    AvroJob.setReducerClass(conf, MaxTemperatureReducer.class);

    JobClient.runJob(conf);
    return 0;
  }
  
  public static void main(String[] args) throws Exception {
    int exitCode = ToolRunner.run(new AvroSpecificMaxTemperature(), args);
    System.exit(exitCode);
  }
}
