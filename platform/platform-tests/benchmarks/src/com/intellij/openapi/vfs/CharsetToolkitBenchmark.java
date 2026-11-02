// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Random;

import static com.intellij.openapi.vfs.CharsetToolkit.BINARY_THRESHOLD;
import static com.intellij.openapi.vfs.CharsetToolkit.isContinuationChar;
import static com.intellij.openapi.vfs.CharsetToolkit.isFiveBytesSequence;
import static com.intellij.openapi.vfs.CharsetToolkit.isFourBytesSequence;
import static com.intellij.openapi.vfs.CharsetToolkit.isSixBytesSequence;
import static com.intellij.openapi.vfs.CharsetToolkit.isThreeBytesSequence;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class CharsetToolkitBenchmark {
  @State(Scope.Benchmark)
  public static class DataContext {
    @Param({"100", "5000", "100000"})
    public int byteCount;

    @Param({"US_ASCII", "UTF8_2_BYTES", "UTF8_5_BYTES"})
    public InputKind inputKind;

    @Param("80")
    public int asciiPercentage;

    public byte[] bytes;
    private CharsetToolkit toolkit;

    @Setup
    public void setup() {
      this.bytes = inputKind.createBytes(byteCount, asciiPercentage);
      toolkit = new CharsetToolkit(bytes, UTF_8, /*enforce8Bit:*/false);
    }

    public enum InputKind {
      US_ASCII(1),
      UTF8_2_BYTES(2),
      UTF8_3_BYTES(3),
      UTF8_4_BYTES(4),
      UTF8_5_BYTES(5),
      UTF8_6_BYTES(6);

      private final int bytesPerCodePoint;

      InputKind(int bytesPerCodePoint) {
        this.bytesPerCodePoint = bytesPerCodePoint;
      }

      private byte[] createBytes(int byteCount, int asciiPercentage) {
        if (asciiPercentage < 0 || asciiPercentage > 100) {
          throw new IllegalArgumentException("ASCII percentage must be between 0 and 100: " + asciiPercentage);
        }

        var random = new Random(0);
        var bytes = new byte[byteCount];
        var multibytePercentage = bytesPerCodePoint == 1 ? 0 : 100 - asciiPercentage;
        var denominator = 100 + multibytePercentage * (bytesPerCodePoint - 1);
        var multibyteCount = (int)Math.round((double)byteCount * multibytePercentage / denominator);
        var asciiCount = byteCount - multibyteCount * bytesPerCodePoint;

        var offset = 0;
        while (asciiCount + multibyteCount > 0) {
          if (random.nextInt(asciiCount + multibyteCount) < asciiCount) {
            bytes[offset++] = (byte)random.nextInt(0x20, 0x7F);
            asciiCount--;
          }
          else {
            writeMultibyteCodePoint(bytes, offset, random);
            offset += bytesPerCodePoint;
            multibyteCount--;
          }
        }
        return bytes;
      }

      private void writeMultibyteCodePoint(byte[] bytes, int offset, Random random) {
        switch (this) {
          case US_ASCII -> throw new IllegalStateException("US-ASCII has no multibyte characters");
          case UTF8_2_BYTES -> writeCodePoint(bytes, offset, random.nextInt(0x80, 0x800), bytesPerCodePoint);
          case UTF8_3_BYTES -> {
            var codePoint = random.nextInt(0xF000) + 0x800;
            writeCodePoint(bytes, offset, codePoint < Character.MIN_SURROGATE ? codePoint : codePoint + 0x800, bytesPerCodePoint);
          }
          case UTF8_4_BYTES -> {
            int codePoint = random.nextInt(0x10000, Character.MAX_CODE_POINT + 1);
            writeCodePoint(bytes, offset, codePoint, bytesPerCodePoint);
          }
          case UTF8_5_BYTES -> {
            // CharsetToolkit accepts this obsolete form although modern UTF-8 does not
            bytes[offset] = (byte)random.nextInt(0xF8, 0xFC);
            for (var i = 1; i < bytesPerCodePoint; i++) {
              bytes[offset + i] = (byte)random.nextInt(0x80, 0xC0);
            }
          }
          case UTF8_6_BYTES -> {
            // CharsetToolkit accepts this obsolete form although modern UTF-8 does not
            bytes[offset] = (byte)random.nextInt(0xFC, 0xFE);
            for (var i = 1; i < bytesPerCodePoint; i++) {
              bytes[offset + i] = (byte)random.nextInt(0x80, 0xC0);
            }
          }
        }
      }

      private static void writeCodePoint(byte[] bytes, int offset, int codePoint, int byteCount) {
        for (var i = byteCount - 1; i > 0; i--) {
          bytes[offset + i] = (byte)(0x80 | (codePoint & 0x3F));
          codePoint >>>= 6;
        }
        var prefix = switch (byteCount) {
          case 2 -> 0xC0;
          case 3 -> 0xE0;
          case 4 -> 0xF0;
          default -> throw new IllegalArgumentException("Unsupported UTF-8 byte count: " + byteCount);
        };
        bytes[offset] = (byte)(prefix | codePoint);
      }
    }
  }


  @Benchmark
  public CharsetToolkit.GuessedEncoding baseline_iterateArray(DataContext context) {
    byte[] bytes = context.bytes;
    int count = context.byteCount;
    boolean nonAsciiBytesExists = false;
    for (int i = 0; i < count; i++) {
      byte b = bytes[i];
      if ((b & 0b1000_0000) != 0) {
        nonAsciiBytesExists = true;
      }
      else if ((b & 0b1111_1000) == 0 /*b < BINARY_THRESHOLD*/) {
        return CharsetToolkit.GuessedEncoding.BINARY;
      }
    }

    return nonAsciiBytesExists ?
           CharsetToolkit.GuessedEncoding.VALID_UTF8:
           CharsetToolkit.GuessedEncoding.SEVEN_BIT;
  }

  private static final VarHandle LONG_VIEW = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

  @Benchmark
  public CharsetToolkit.GuessedEncoding baseline_iterateArray64(DataContext context) {
    byte[] bytes = context.bytes;
    int count = context.byteCount;
    boolean nonAsciiBytesExists = false;
    int i = 0;
    for (; i + Long.BYTES <= count; i += Long.BYTES) {
      long l = (long)LONG_VIEW.get(bytes, i);
      if ((l & 0x8080_8080_8080_8080L) != 0) {
        nonAsciiBytesExists = true;
      }
      else if ( (l & 0b1111_1000_1111_1000_1111_1000_1111_1000_1111_1000_1111_1000_1111_1000_1111_1000L) == 0 ) {
        return CharsetToolkit.GuessedEncoding.BINARY;
      }
    }
    for (; i < count; i++) {
      byte b = bytes[i];
      if ((b & 0b1000_0000) != 0) {
        nonAsciiBytesExists = true;
      }
      else if ((b & 0b1111_1000) == 0 /*b < BINARY_THRESHOLD*/) {
        return CharsetToolkit.GuessedEncoding.BINARY;
      }
    }

    return nonAsciiBytesExists  ?
           CharsetToolkit.GuessedEncoding.VALID_UTF8:
           CharsetToolkit.GuessedEncoding.SEVEN_BIT;
  }


  @Benchmark
  public CharsetToolkit.GuessedEncoding guessFromContent(DataContext context) {
    return context.toolkit.guessFromContent(context.byteCount);
  }

  @Benchmark
  public CharsetToolkit.GuessedEncoding guessFromContentAlt(DataContext context) {
    return guessFromContentAlt(
      context.bytes,
      0, context.byteCount
    );
  }


  public @NotNull CharsetToolkit.GuessedEncoding guessFromContentAlt(byte[] buffer, int startOffset, int endOffset) {
    // if a byte has its most significant bit set, the file is in UTF-8 or in the default encoding
    // otherwise, the file is in US-ASCII
    boolean highOrderBit = false;

    // if the file is in UTF-8, high order bytes must have a certain value, in order to be valid
    // if it's not the case, we can assume the encoding is the default encoding of the system
    boolean validU8Char = true;

    int end = Math.min(buffer.length, endOffset);
    int i = startOffset;
    while (i < end) {
      byte b0 = buffer[i];
      if (b0 < 0) {
        // a high order bit was encountered, thus the encoding is not US-ASCII
        // it may be either an 8-bit encoding or UTF-8
        highOrderBit = true;

        // a two-bytes sequence was encountered
        if (CharsetToolkit.isTwoBytesSequence(b0)) {
          if (i + 1 >= end) break; // multibyte sequence is at the end, it's hard to say is it correct
          byte b1 = buffer[i + 1];
          // there must be one continuation byte of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          if (!isContinuationChar(b1)) {
            validU8Char = false;
          }
          else {
            i++;
          }
        }
        // a three-bytes sequence was encountered
        else if (isThreeBytesSequence(b0)) {
          if (i + 2 >= end) break; // multibyte sequence is at the end, it's hard to say is it correct
          // there must be two continuation bytes of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          byte b1 = buffer[i + 1];
          byte b2 = buffer[i + 2];
          if (!(isContinuationChar(b1) && isContinuationChar(b2))) {
            validU8Char = false;
          }
          else {
            i += 2;
          }
        }
        // a four-bytes sequence was encountered
        else if (isFourBytesSequence(b0)) {
          if (i + 3 >= end) break; // multibyte sequence is at the end, it's hard to say is it correct
          // there must be three continuation bytes of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          byte b1 = buffer[i + 1];
          byte b2 = buffer[i + 2];
          byte b3 = buffer[i + 3];
          if (!(isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3))) {
            validU8Char = false;
          }
          else {
            i += 3;
          }
        }
        // a five-bytes sequence was encountered
        else if (isFiveBytesSequence(b0)) {
          if (i + 4 >= end) break; // multibyte sequence is at the end, it's hard to say is it correct
          // there must be four continuation bytes of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          byte b1 = buffer[i + 1];
          byte b2 = buffer[i + 2];
          byte b3 = buffer[i + 3];
          byte b4 = buffer[i + 4];
          if (!(isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3) && isContinuationChar(b4))) {
            validU8Char = false;
          }
          else {
            i += 4;
          }
        }
        // a six-bytes sequence was encountered
        else if (isSixBytesSequence(b0)) {
          if (i + 5 >= end) break; // multibyte sequence is at the end, it's hard to say is it correct
          // there must be five continuation bytes of the form 10xxxxxx,
          // otherwise the following characters is not a valid UTF-8 construct
          byte b1 = buffer[i + 1];
          byte b2 = buffer[i + 2];
          byte b3 = buffer[i + 3];
          byte b4 = buffer[i + 4];
          byte b5 = buffer[i + 5];
          if (!(isContinuationChar(b1) &&
                isContinuationChar(b2) &&
                isContinuationChar(b3) &&
                isContinuationChar(b4) &&
                isContinuationChar(b5))) {
            validU8Char = false;
          }
          else {
            i += 5;
          }
        }
        else {
          validU8Char = false;
        }
      }
      else if (b0 < BINARY_THRESHOLD) {
        return CharsetToolkit.GuessedEncoding.BINARY;
      }

      i++;
    }

    if (!highOrderBit) {
      return CharsetToolkit.GuessedEncoding.SEVEN_BIT;
    }
    // finally, if it's not UTF-8 nor US-ASCII
    if (!validU8Char) {
      //we can't return early, as soon as we detect invalid char, since we still need to scan for BINARY
      return CharsetToolkit.GuessedEncoding.INVALID_UTF8;
    }

    // if no invalid UTF-8 were encountered, we can assume the encoding is UTF-8,
    // otherwise the file would not be human-readable
    return CharsetToolkit.GuessedEncoding.VALID_UTF8;
  }
}
