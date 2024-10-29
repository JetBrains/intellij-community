// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.immutableKeyValueStore.benchmark;

import com.intellij.util.lang.Ikv;
import kotlin.Pair;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

// annotation processor in IDEA doesn't support kotlin, so, Java is used
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 4)
@Fork(2)
public class IkvBenchmark {
  @State(Scope.Benchmark)
  public static class IkvBenchmarkState {
    //@Param({"7", "10", "14", "18"})
    //public int leafSize;
    @Param({"32", "64", "100", "128", "256"})
    public int averageBucketSize;

    private Path dbFile;
    private Ikv.SizeAwareIkv ikv;
    private long key;

    @Setup
    public void setupDb() throws Exception {
      dbFile = Files.createTempDirectory("ikv-").resolve("db");
      List<Pair<Long, byte[]>> list = BenchmarkHelperKt.generateDb(dbFile, 5_000);
      ikv = (Ikv.SizeAwareIkv)Ikv.loadIkv(dbFile);
      key = list.get(3000).getFirst();
    }

    @TearDown
    public void removeDb() throws Exception {
      ikv.close();
      Files.deleteIfExists(dbFile);
      dbFile = null;
      ikv = null;
    }
  }

  //@State(Scope.Benchmark)
  //public static class GetState {
  //  public int[] keys = new int[5_000];
  //
  //  @Setup
  //  public void setup() {
  //    generateKeys(keys);
  //  }
  //
  //  private static void generateKeys(int[] keys) {
  //    Random random = new Random(42);
  //    for (int i = 0, n = keys.length; i < n; i++) {
  //      keys[i] = random.nextInt();
  //    }
  //  }
  //}
  //
  //@Benchmark
  //public ByteBuffer construct(GetState state) {
  //  new RecSplitGenerator<Integer>(UniversalHash.IntHash.INSTANCE, RecSplitSettings.DEFAULT_SETTINGS).generate(state.keys,);
  //}

  @Benchmark
  public ByteBuffer lookup(IkvBenchmarkState state) {
    return state.ikv.getValue(state.key);
  }
}