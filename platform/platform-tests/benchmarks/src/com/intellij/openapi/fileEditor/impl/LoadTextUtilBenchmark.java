// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.util.text.CharArrayUtil;
import joptsimple.internal.Strings;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.CharBuffer;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark different optimizations in {@link LoadTextUtil#convertLineSeparatorsToSlashN(byte[], int, int)} method, namely:
 * - Operate with char[] if CharBuffer is array-buffer
 * - Skip modifying element if no actual update happens
 *
 * Results:
 * - char[]-processing is indeed noticeably faster (-15..-30%) than CharBuffer, especially for longer contents.
 * - Skipping dummy modifications has a barely noticeable positive effect, almost on the level of noise.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 4, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class LoadTextUtilBenchmark {

  @State(Scope.Benchmark)
  public static class InputData {
    @Param({"1024", "4096", "65536"})
    public int contentLength;

    /** Not separators themselves because values will be printed in reports */
    @Param({"CR", "LF", "CRLF"})
    public String separatorKind;

    public char[] multiLineContentWithGivenSeparator;

    @Setup
    public void setup() {
      multiLineContentWithGivenSeparator = generateMultilineText(contentLength, separatorKind);
    }

    private static char[] generateMultilineText(int contentLength, String separatorKind) {
      StringBuilder builder = new StringBuilder(contentLength + contentLength / 16);
      for (int lineNo = 0; builder.length() < contentLength; lineNo++) {
        builder.append(generateLine(lineNo));

        switch (separatorKind) {
          case "CR" -> builder.append('\r');
          case "LF" -> builder.append('\n');
          case "CRLF" -> builder.append("\r\n");
          default -> throw new IllegalStateException("Unexpected separator kind: " + separatorKind);
        }
      }
      return builder.substring(0, contentLength).toCharArray();
    }

    private static @NotNull CharSequence generateLine(int lineIndex) {
      String[] words = {
        Strings.repeat((char)('a' + lineIndex % 26), 3),
        Strings.repeat((char)('A' + lineIndex % 26), 6),
        Strings.repeat((char)('a' + lineIndex % 26), 13)
      };
      StringBuilder lineBuilder = new StringBuilder();
      int wordsCount = 3 + (lineIndex % 5);
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      for (int i = 0; i < wordsCount; i++) {
        lineBuilder.append(words[rnd.nextInt(words.length)]);
        if (i < wordsCount - 1) {
          lineBuilder.append(' ');
        }
      }
      return lineBuilder;
    }
  }

  @State(Scope.Thread)
  public static class ArrayBackedBuffer {
    public CharBuffer charBuffer;

    @Setup(Level.Invocation)
    public void setup(InputData inputData) {
      charBuffer = CharBuffer.wrap(inputData.multiLineContentWithGivenSeparator.clone());
      char[] array = CharArrayUtil.fromSequenceWithoutCopying(charBuffer);
      if (array == null) {
        throw new IllegalStateException(charBuffer + " MUST be array-backed");
      }
    }
  }

  @State(Scope.Thread)
  public static class NonArrayBackedBuffer {
    public CharBuffer charBuffer;


    @Setup(Level.Invocation)
    public void setup(InputData inputData) {
      int offset = 10;
      charBuffer = CharBuffer.allocate(inputData.multiLineContentWithGivenSeparator.length + offset)
        .position(offset)
        .put(inputData.multiLineContentWithGivenSeparator)
        .position(offset)
        .limit(inputData.multiLineContentWithGivenSeparator.length);

      char[] array = CharArrayUtil.fromSequenceWithoutCopying(charBuffer);
      if (array != null) {
        throw new IllegalStateException(charBuffer + " must NOT be array-backed");
      }
    }
  }

  @Benchmark
  public int convertArrayBacked(ArrayBackedBuffer buffer) {
    return LoadTextUtil.convertLineSeparatorsToSlashN(buffer.charBuffer).text.length();
  }

  @Benchmark
  public int convertNonArrayBacked(NonArrayBackedBuffer buffer) {
    return LoadTextUtil.convertLineSeparatorsToSlashN(buffer.charBuffer).text.length();
  }

  public static void main(String[] args) throws RunnerException {
    Options options = new OptionsBuilder()
      .forks(1)
      .threads(1)
      .include("\\W" + LoadTextUtilBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(options).run();
  }
}
