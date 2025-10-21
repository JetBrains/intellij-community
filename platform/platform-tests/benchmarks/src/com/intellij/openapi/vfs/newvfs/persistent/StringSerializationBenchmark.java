// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * What if write/read String by directly accessing (via Unsafe) it's internal .byte[] and .coder fields?
 * It should save us at least byte[] allocation & copy, and maybe a bit of encoding/decoding.
 *
 * Results promising: on my laptop restoreStringWithUnsafe() is ~20-40% (depending on workset size) faster than restoreStringUTF8()
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(4)
public class StringSerializationBenchmark {
  private static final long VALUE_OFFSET;
  private static final long CODER_OFFSET;
  //don't need to write/read those, since they'll be happily recalculated:
  private static final long HASH_OFFSET;
  private static final long HASH_IS_ZERO_OFFSET;

  private static final Unsafe UNSAFE;

  static {
    try {
      Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafeField.setAccessible(true);
      UNSAFE = (Unsafe)theUnsafeField.get(null);


      Field valueField = String.class.getDeclaredField("value");
      Field coderField = String.class.getDeclaredField("coder");
      Field hashField = String.class.getDeclaredField("hash");
      Field hashZeroField = String.class.getDeclaredField("hashIsZero");
      valueField.setAccessible(true);
      coderField.setAccessible(true);
      hashField.setAccessible(true);
      hashZeroField.setAccessible(true);


      VALUE_OFFSET = UNSAFE.objectFieldOffset(valueField);
      CODER_OFFSET = UNSAFE.objectFieldOffset(coderField);
      HASH_OFFSET = UNSAFE.objectFieldOffset(hashField);
      HASH_IS_ZERO_OFFSET = UNSAFE.objectFieldOffset(hashZeroField);
    }
    catch (Throwable e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @State(Scope.Benchmark)
  public static class UnsafeContext {
    @Param({"5000", "500000", "5000000"})
    public int STRINGS_COUNT = 5_000_000;//5M strings ~ huge project

    private ByteBuffer buffer;
    private int[] offsets;

    @Setup
    public void setup() throws Exception {
      String[] strings = generateStrings(STRINGS_COUNT);
      int bufferLength = 0;
      for (String string : strings) {
        byte[] bytes = string.getBytes(UTF_8);
        bufferLength += bytes.length + 4 + 1;
      }
      buffer = ByteBuffer.allocate(bufferLength);
      offsets = new int[strings.length];
      for (int i = 0; i < strings.length; i++) {
        offsets[i] = buffer.position();

        String string = strings[i];
        byte[] bytes = (byte[])UNSAFE.getObject(string, VALUE_OFFSET);
        byte coder = UNSAFE.getByte(string, CODER_OFFSET);

        buffer
          .put(coder)
          .putInt(bytes.length)
          .put(bytes);
      }
      buffer.clear();
    }
  }


  @State(Scope.Benchmark)
  public static class UTF8Context {
    @Param({"5000", "500000", "5000000"})
    public int STRINGS_COUNT = 5_000_000;

    private ByteBuffer buffer;
    private int[] offsets;

    @Setup
    public void setup() throws Exception {
      String[] strings = generateStrings(STRINGS_COUNT);
      int bufferLength = 0;
      for (String string : strings) {
        byte[] bytes = string.getBytes(UTF_8);
        bufferLength += bytes.length + 4;
      }
      buffer = ByteBuffer.allocate(bufferLength);
      offsets = new int[strings.length];
      for (int i = 0; i < strings.length; i++) {
        offsets[i] = buffer.position();

        String string = strings[i];
        byte[] bytes = string.getBytes(UTF_8);
        buffer
          .putInt(bytes.length)
          .put(bytes);
      }
      buffer.clear();
    }
  }

  @Benchmark
  public String restoreStringWithUnsafe(UnsafeContext ctx) throws Exception {
    int[] offsets = ctx.offsets;
    ByteBuffer buffer = ctx.buffer;

    int offset = randomOffsetOf(offsets);

    byte coder = buffer.get(offset);
    int length = buffer.getInt(offset + 1);
    byte[] bytes = new byte[length];
    buffer.get(offset + 4 + 1, bytes);

    String newInstance = (String)UNSAFE.allocateInstance(String.class);
    UNSAFE.putObject(newInstance, VALUE_OFFSET, bytes);
    UNSAFE.putByte(newInstance, CODER_OFFSET, coder);
    return newInstance;
  }

  @Benchmark
  public int restoreStringWithUnsafeAndCallHashCode(UnsafeContext ctx) throws Exception {
    String string = restoreStringWithUnsafe(ctx);
    return string.hashCode();
  }

  @Benchmark
  public String restoreStringUTF8(UTF8Context ctx) throws Exception {
    int[] offsets = ctx.offsets;
    ByteBuffer buffer = ctx.buffer;

    int offset = randomOffsetOf(offsets);

    int length = buffer.getInt(offset);
    byte[] bytes = new byte[length];
    buffer.get(offset + 4, bytes);

    return new String(bytes, UTF_8);
  }

  @Benchmark
  public int restoreStringUTF8AndCallHashCode(UTF8Context ctx) throws Exception {
    String string = restoreStringUTF8(ctx);
    return string.hashCode();
  }

  @Benchmark
  public int _baseline_randomOffset(UTF8Context ctx) {
    int[] offsets = ctx.offsets;
    ByteBuffer buffer = ctx.buffer;

    return randomOffsetOf(offsets);
  }

  private static String[] generateStrings(int stringsCount) {
    String[] strings = new String[stringsCount];
    for (int i = 0; i < stringsCount; i++) {
      strings[i] = "name.%022d".formatted(i);
    }
    return strings;
  }

  private static int randomOffsetOf(int[] offsets) {
    int index = ThreadLocalRandom.current().nextInt(offsets.length);
    return offsets[index];
  }


  public static void main(final String[] args) throws Exception {

    final Options opt = new OptionsBuilder()
      .jvmArgs(
        "-Djava.awt.headless=true",
        "--add-opens=java.base/java.lang=ALL-UNNAMED"

      )
      .threads(1)
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include("\\W" + StringSerializationBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
