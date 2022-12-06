package org.jetbrains.completion.full.line.local.generation;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(3)
public class UtilsBenchmark {
  @State(Scope.Benchmark)
  public static class Topk1dState {
    public double[] data;
    @Param("6")
    public int size;

    @Param("16384")
    public int dataSize;


    @Setup
    public void setup() {
      Random random = new Random(42);
      data = new double[dataSize];
      for (int i = 0; i < data.length; i++) {
        data[i] = random.nextDouble();
      }
    }
  }

  @Benchmark
  public void benchmarkTopk1d(Topk1dState topk1dState, Blackhole blackhole) {
    blackhole.consume(
      UtilsKt.topk1d(topk1dState.data, topk1dState.size)
    );
  }
}
