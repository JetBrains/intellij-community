package org.jetbrains.completion.full.line.local;

import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipelineConfig;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(3)
@Measurement(iterations = 5)
@Warmup(iterations = 5)
public class CompletionModelBenchmark {
  @State(Scope.Benchmark)
  public static class GenerateCompletionBenchmarkState {

    @Param("10")
    public int filenameLen;

    @Param("5")
    public int maxLen;

    @Param({"0", "10"})
    public int prefixLen;

    @Param("999")
    public int contextTextLen;
    @Param({"0", "5", "50", "1000"})
    public int offsetContext;

    @Param({"false", "true"})
    public boolean useCache;

    private String filename;

    private final static Random random = new Random(42);
    private final static CompletionModelBenchmarkHelper helper = new CompletionModelBenchmarkHelper(random);

    public FullLineCompletionPipelineConfig config;
    public String context;

    public String prefix;

    public List<CompletionModel.CompletionResult> generate() {
      return helper.generate(context, prefix, config);
    }

    @Setup(Level.Iteration)
    public void generateFilename() {
      filename = helper.randomFilename(filenameLen);
    }

    @Setup(Level.Invocation)
    public void setupRun() {
      prefix = helper.randomPrefix(prefixLen);
      context = helper.continueContextRandomly(context, contextTextLen, offsetContext);
      config = helper.getConfig(maxLen, filename);
      if (!useCache) {
        helper.resetCache();
      }
    }

    @TearDown(Level.Invocation)
    public void teardown() {
      if (useCache) {
        if (offsetContext <= 10) {
          assert helper.getCacheHits() > 0;
        }
        else if (offsetContext > contextTextLen) {
          assert helper.getCacheHits() == 0;
        }
        helper.resetCacheHits();
      }
      else {
        assert helper.getCacheHits() == 0;
      }
    }
  }

  @Benchmark
  public void generateCompletionBenchmark(GenerateCompletionBenchmarkState generateState,
                                          Blackhole blackhole) {
    blackhole.consume(generateState.generate());
  }
}