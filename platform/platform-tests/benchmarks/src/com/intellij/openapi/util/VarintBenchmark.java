// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark variable-length integer serialization
 * <p>
 * ...On M2 serialization to byte[] is consistently 50% faster than to DataOutputStream(ByteArrayOutputStream)
 */
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class VarintBenchmark {

  @State(Scope.Benchmark)
  public static class DataContext {

    @Param({"8", "1024", "16384"})
    private int valuesCount;

    @Param({"192", "4096", "1048576", "2147483647"})
    private int maxValue;

    public int[] values;

    public byte[] buffer;
    private UnsyncByteArrayOutputStream outputStream;

    @Setup
    public void setup() throws Exception {
      //positive integers
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      values = IntStream.generate(() -> rnd.nextInt(0, maxValue))
        .limit(valuesCount)
        .toArray();

      int maxVarintSize = (Integer.BYTES + 1);//at worst, varint takes 5 bytes
      buffer = new byte[valuesCount * maxVarintSize];
      outputStream = new UnsyncByteArrayOutputStream(buffer);
    }

    @TearDown
    public void tearDown() throws Exception {
    }
  }

  //@Benchmark (no difference with j.u.DataOutputStream)
  public void writeVarints_toUnsyncDataOutputOverByteArrayStream(DataContext context) throws IOException {
    int[] values = context.values;
    UnsyncByteArrayOutputStream stream = context.outputStream;
    stream.reset();

    //use un-synchronized version of DOStream:
    DataOutput dataStream = new com.intellij.util.io.DataOutputStream(stream);
    for (int value : values) {
      DataInputOutputUtil.writeINT(dataStream, value);
    }
  }

  @Benchmark
  public void writeVarints_toDataOutputOverByteArrayStream(DataContext context) throws IOException {
    int[] values = context.values;
    UnsyncByteArrayOutputStream stream = context.outputStream;
    stream.reset();

    //use a standard java.io version of DOStream:
    DataOutput dataStream = new java.io.DataOutputStream(stream);
    for (int value : values) {
      DataInputOutputUtil.writeINT(dataStream, value);
    }
  }

  //@Benchmark (no difference with writeVarints_toDataOutputOverByteArrayStream)
  public void writeVarints_toDataOutputOverByteArrayStream_optimized(DataContext context) throws IOException {
    int[] values = context.values;
    UnsyncByteArrayOutputStream stream = context.outputStream;
    stream.reset();

    //use a standard java.io version of DOStream:
    DataOutput dataStream = new java.io.DataOutputStream(stream);
    for (int value : values) {
      writeINT(dataStream, value);
    }
  }


  @Benchmark
  public void writeVarints_toOutputStream(DataContext context) throws IOException {
    int[] values = context.values;
    UnsyncByteArrayOutputStream stream = context.outputStream;
    stream.reset();

    for (int value : values) {
      writeINT(stream, value);
    }
  }

  @Benchmark
  public void writeVarints_toByteArray(DataContext context) throws IOException {
    int[] values = context.values;
    writeINTS(values, context.buffer);
  }

  private static void writeINT(@NotNull DataOutput record, int val) throws IOException {
    //code below is exactly DataInputOutputUtil.writeINT(), just uses record.write() instead record.writeByte()
    if (0 > val || val >= 192) {
      record.write(192 + (val & 0x3F));
      val >>>= 6;
      while (val >= 128) {
        record.write((val & 0x7F) | 0x80);
        val >>>= 7;
      }
    }
    record.write(val);
  }

  private static void writeINT(@NotNull OutputStream stream, int val) throws IOException {
    //code below is exactly DataInputOutputUtil.writeINT(), just uses stream.write() instead dataOutput.writeByte()
    if (0 > val || val >= 192) {
      stream.write(192 + (val & 0x3F));
      val >>>= 6;
      while (val >= 128) {
        stream.write((val & 0x7F) | 0x80);
        val >>>= 7;
      }
    }
    stream.write(val);
  }

  private static void writeINTS(int[] values,
                                byte[] target) {
    int currentIndex = 0;
    for (int val : values) {
      //code below is exactly DataInputOutputUtil.writeINT(), just numbers converted to binary
      if (val < 0 || 0b1100_0000 <= val) {
        target[currentIndex] = (byte)(0b1100_0000 + (val & 0b0011_1111));
        currentIndex++;
        val >>>= 6;
        while (val >= 0b1000_0000) {
          target[currentIndex] = (byte)((val & 0b0111_1111) | 0b1000_0000);
          currentIndex++;
          val >>>= 7;
        }
      }
      target[currentIndex] = (byte)(val);
      currentIndex++;
    }
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      //.mode(Mode.SampleTime)
      .include(VarintBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
