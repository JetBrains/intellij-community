// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.newvfs.persistent.dev.OptimizedCaseInsensitiveStringHashing;
import it.unimi.dsi.fastutil.Hash;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Compares different methods of calculating case-insensitive hashCode for a String
 * There are some low-level optimizations could be applied for Latin1(ASCII) strings
 * <p>
 * On my machine, Unsafe/VarHande-based approach saves 25% on case-insensitive hashCode
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(4)
public class StringHashBenchmark {
  private static final long VALUE_OFFSET;
  private static final long CODER_OFFSET;

  private static final Unsafe UNSAFE;

  static {
    try {
      Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafeField.setAccessible(true);
      UNSAFE = (Unsafe)theUnsafeField.get(null);

      Field valueField = String.class.getDeclaredField("value");
      Field coderField = String.class.getDeclaredField("coder");
      valueField.setAccessible(true);
      coderField.setAccessible(true);


      VALUE_OFFSET = UNSAFE.objectFieldOffset(valueField);
      CODER_OFFSET = UNSAFE.objectFieldOffset(coderField);
    }
    catch (Throwable e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @State(Scope.Benchmark)
  public static class DataContext {
    //@Param({"5000"})
    public int STRINGS_COUNT = 5000;

    @Param({"32", "128"})
    public int STRING_LENGTH = 32;

    @Param({"ascii", "unicode"})
    public String stringContent = "ascii";

    private String[] strings;

    @Setup
    public void setup() throws Exception {
      strings = switch (stringContent) {
        case "ascii" -> generateASCIIStrings(STRINGS_COUNT, STRING_LENGTH);
        case "unicode" -> generateUnicodeStrings(STRINGS_COUNT, STRING_LENGTH);
        default -> throw new IllegalStateException("[" + stringContent + "] is unknown");
      };
    }
  }

  @Benchmark
  public int caseInsensitiveHashCode(DataContext ctx) throws Exception {
    String[] strings = ctx.strings;
    int index = ThreadLocalRandom.current().nextInt(0, strings.length);
    String string = strings[index];
    return StringUtilRt.stringHashCodeInsensitive(string);
  }

  @Benchmark
  public int caseInsensitiveHashCode_Unsafe(DataContext ctx) throws Exception {
    String[] strings = ctx.strings;
    int index = ThreadLocalRandom.current().nextInt(0, strings.length);
    String string = strings[index];
    return caseInsensitiveHashCode(string);
  }


  private static final Hash.Strategy<String> CASE_INSENSITIVE_STRING_HASHING_STRATEGY = OptimizedCaseInsensitiveStringHashing.instance();

  @Benchmark
  public int caseInsensitiveHashCode_VarHandle(DataContext ctx) throws Exception {
    String[] strings = ctx.strings;
    int index = ThreadLocalRandom.current().nextInt(0, strings.length);
    String string = strings[index];
    return CASE_INSENSITIVE_STRING_HASHING_STRATEGY.hashCode(string);
  }

  //================================ baselines: ===============================================================================

  @Benchmark
  public String _baseline_randomString(DataContext ctx) {
    String[] strings = ctx.strings;
    int index = ThreadLocalRandom.current().nextInt(0, strings.length);
    return strings[index];
  }

  @Benchmark
  public int caseSensitiveHashCode(DataContext ctx) throws Exception {
    String[] strings = ctx.strings;
    int index = ThreadLocalRandom.current().nextInt(0, strings.length);
    String string = strings[index];
    return string.hashCode();
  }

  //================================ infrastructure: ==========================================================================

  private static int caseInsensitiveHashCode(String str) {
    byte coder = UNSAFE.getByte(str, CODER_OFFSET);
    if (coder == 0) {//==Latin1 (ASCII)
      byte[] bytes = (byte[])UNSAFE.getObject(str, VALUE_OFFSET);
      int hash = 0;
      int length = str.length();
      for (int i = 0; i < length; i++) {
        byte ch = bytes[i];
        //in ASCII lower and upper case letters differ by a single bit:
        hash = 31 * hash + (ch & 0b1101_1111);
      }
      return hash;
    }
    else {

      return StringUtilRt.stringHashCodeInsensitive(str);
    }
  }


  private static String[] generateASCIIStrings(int stringsCount, int stringLength) {
    String[] strings = new String[stringsCount];
    for (int i = 0; i < stringsCount; i++) {
      strings[i] = generateRandomASCII(stringLength);
    }
    return strings;
  }

  private static String[] generateUnicodeStrings(int stringsCount, int stringLength) {
    String[] strings = new String[stringsCount];
    for (int i = 0; i < stringsCount; i++) {
      strings[i] = generateRandomUnicode(stringLength);
    }
    return strings;
  }

  private static String generateRandomASCII(int size) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    char[] chars = new char[size];
    for (int i = 0; i < size; i++) {
      chars[i] = (char)rnd.nextInt(0, 128);
    }
    String str = new String(chars);

    byte coder = UNSAFE.getByte(str, CODER_OFFSET);
    if (coder != 0) {
      throw new IllegalStateException("[" + str + "]: is not ASCII");
    }

    return str;
  }

  private static String generateRandomUnicode(int size) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    char[] chars = new char[size];
    for (int i = 0; i < size; i++) {
      chars[i] = (char)rnd.nextInt(0, Character.MAX_VALUE + 1);
    }
    return new String(chars);
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
      .include("\\W" + StringHashBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
