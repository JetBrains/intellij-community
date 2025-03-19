// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.util.io.storages.enumerator.DurableEnumeratorFactory;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.io.*;
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

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Compares performance of PersistentEnumerator vs DurableEnumerator implementations
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class EnumeratorsBenchmark {

  @State(Scope.Benchmark)
  public static class DurableDataEnumeratorContext {

    @Param({"100000", "1000000", "10000000"})
    protected int totalKeys = 1000000;

    @Param({"true", "false"})
    protected boolean newImplementation;


    private Path tempDir;

    protected DurableDataEnumerator<String> enumerator;

    protected String[] generatedKeys;
    protected int[] enumeratedIds;


    @Setup
    public void setup() throws Exception {
      tempDir = FileUtil.createTempDirectory("DurableEnumerator", "tst").toPath();
      enumerator = createEnumerator();

      generatedKeys = generateKey(totalKeys);
      enumeratedIds = new int[generatedKeys.length];

      for (int i = 0; i < generatedKeys.length; i++) {
        String key = generatedKeys[i];
        enumeratedIds[i] = enumerator.enumerate(key);
      }
    }

    @TearDown
    public void tearDown() throws Exception {
      if (enumerator != null) {
        enumerator.close();
        if (enumerator instanceof CleanableStorage) {
          ((CleanableStorage)enumerator).closeAndClean();
        }
      }
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }

    private DurableDataEnumerator<String> createEnumerator() throws IOException {
      Path file = tempDir.resolve("enumerator");
      if (newImplementation) {
        return DurableEnumeratorFactory.defaultWithDurableMap(stringAsUTF8())
          .mapFactory(ExtendibleMapFactory.largeSize())
          .open(file);
      }
      else {
        Files.deleteIfExists(file);//PersistentEnumerator is very confused if empty file is already exist
        StorageLockContext lockContext = new StorageLockContext(true, true, true);
        return new PersistentEnumerator<>(file, EnumeratorStringDescriptor.INSTANCE, 1 << 16, lockContext);
      }
    }

    private static String[] generateKey(int keysCount) {
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
  public String _baseline(DurableDataEnumeratorContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    DurableDataEnumerator<String> enumerator = context.enumerator;
    String[] keys = context.generatedKeys;
    int[] ids = context.enumeratedIds;

    return keys[rnd.nextInt(ids.length)];
  }

  @Benchmark
  public int enumerateExistingKey(DurableDataEnumeratorContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    DurableDataEnumerator<String> enumerator = context.enumerator;
    String[] keys = context.generatedKeys;

    int index = rnd.nextInt(keys.length);
    String key = keys[index];
    return enumerator.enumerate(key);
  }

  @Benchmark
  public int enumerateNonExistingKey(DurableDataEnumeratorContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    DurableDataEnumerator<String> enumerator = context.enumerator;
    String[] keys = context.generatedKeys;

    int index = rnd.nextInt(keys.length);
    String key = keys[index] + "_non-existing-suffix";
    return enumerator.enumerate(key);
  }

  @Benchmark
  public int tryEnumerateExistingKey(DurableDataEnumeratorContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    DurableDataEnumerator<String> enumerator = context.enumerator;
    String[] keys = context.generatedKeys;

    int index = rnd.nextInt(keys.length);
    String key = keys[index];
    return enumerator.tryEnumerate(key);
  }

  @Benchmark
  public int tryEnumerateNonExistingKey(DurableDataEnumeratorContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    DurableDataEnumerator<String> enumerator = context.enumerator;
    String[] keys = context.generatedKeys;

    int index = rnd.nextInt(keys.length);
    String key = keys[index] + "_another-non-existing-suffix";
    return enumerator.tryEnumerate(key);
  }

  @Benchmark
  public String valueOfExistingId(DurableDataEnumeratorContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    DurableDataEnumerator<String> enumerator = context.enumerator;
    int[] ids = context.enumeratedIds;

    int index = rnd.nextInt(ids.length);
    int id = ids[index];
    return enumerator.valueOf(id);
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      //.mode(Mode.SampleTime)
      //.forks(0)
      .include(EnumeratorsBenchmark.class.getSimpleName() + ".*")
      //.include(EnumeratorsBenchmark.class.getSimpleName() + ".*enumerateNon.*")
      //.threads(4)
      .build();

    new Runner(opt).run();
  }
}
