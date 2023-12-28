// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps.DurableMapFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.io.*;
import com.intellij.util.io.dev.enumerator.StringAsUTF8;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Compares performance of different {@link KeyValueStore} implementations:
 * mainly {@link PersistentMap} vs {@link com.intellij.util.io.dev.durablemaps.DurableMap}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class PersistentMapsBenchmark {

  @State(Scope.Benchmark)
  public static class KeyValueStoreContext {

    @Param({"100000", "1000000", "10000000"})
    protected int totalKeys = 1000000;

    @Param({"true", "false"})
    protected boolean newImplementation;


    private Path tempDir;

    protected KeyValueStore<String, String> map;

    protected String[] generatedKeys;


    @Setup
    public void setup() throws Exception {
      tempDir = FileUtil.createTempDirectory("DurableMapBenchmarks", "tst").toPath();
      map = createStore();

      generatedKeys = generateKeys(totalKeys);

      for (String key : generatedKeys) {
        map.put(key, key + "." + key);
      }
    }

    @TearDown
    public void tearDown() throws Exception {
      if (map != null) {
        map.close();
        if (map instanceof CleanableStorage) {
          ((CleanableStorage)map).closeAndClean();
        }
      }
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }

    private KeyValueStore<String, String> createStore() throws IOException {
      Path storagePath = tempDir.resolve("map");
      if (newImplementation) {
        return DurableMapFactory
          .withDefaults(StringAsUTF8.INSTANCE, StringAsUTF8.INSTANCE)
          .mapFactory(ExtendibleMapFactory.largeSize())
          .open(storagePath);
      }
      else {
        Files.deleteIfExists(storagePath);
        return new PersistentHashMap<>(
          storagePath,
          EnumeratorStringDescriptor.INSTANCE,
          EnumeratorStringDescriptor.INSTANCE,
          1 << 16
        );
      }
    }

    private static String[] generateKeys(int keysCount) {
      String[] keys = new String[keysCount];
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      for (int i = 0; i < keysCount; i++) {
        keys[i] = randomString(rnd, rnd.nextInt(5, 128));
      }
      return keys;
    }

    private static String randomString(final Random rnd,
                                       final int size) {
      final char[] chars = new char[size];
      for (int i = 0; i < chars.length; i++) {
        chars[i] = Character.forDigit(rnd.nextInt(0, Character.MAX_RADIX), Character.MAX_RADIX);
      }
      return new String(chars);
    }
  }

  @Benchmark
  public String _baseline(KeyValueStoreContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    KeyValueStore<String, String> map = context.map;
    String[] keys = context.generatedKeys;
    return keys[rnd.nextInt(keys.length)];
  }

  @Benchmark
  public String getExistingKey(KeyValueStoreContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    KeyValueStore<String, String> map = context.map;
    String[] keys = context.generatedKeys;

    int index = rnd.nextInt(keys.length);
    String key = keys[index];
    return map.get(key);
  }

  @Benchmark
  public String getNonExistingKey(KeyValueStoreContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    KeyValueStore<String, String> map = context.map;
    String[] keys = context.generatedKeys;

    int index = rnd.nextInt(keys.length);
    String key = keys[index] + "_non-existing-suffix";
    return map.get(key);
  }

  @Benchmark
  public void overwriteExistingKey(KeyValueStoreContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    KeyValueStore<String, String> map = context.map;
    String[] keys = context.generatedKeys;

    int index = rnd.nextInt(keys.length);
    String key = keys[index];
    map.put(key, key + index);
  }


  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      //.mode(Mode.SampleTime)
      //.forks(0)
      .include(PersistentMapsBenchmark.class.getSimpleName() + ".*overwriteExistingKey.*")
      //.threads(4)
      .build();

    new Runner(opt).run();
  }
}
