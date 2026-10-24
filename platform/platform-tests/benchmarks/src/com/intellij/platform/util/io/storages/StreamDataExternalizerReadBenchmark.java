// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.ValueContainerExternalizer;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.ValueContainerInputRemapping;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/// Compares byte array copies with direct stream reads from a [MemorySegment]
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class StreamDataExternalizerReadBenchmark {
  private interface Reader {
    Object read(MemorySegment input) throws IOException;
  }

  private static final class ReadViaByteArrayCopy<T> implements Reader {
    private final DataExternalizer<T> externalizer;

    private ReadViaByteArrayCopy(DataExternalizer<T> externalizer) {
      this.externalizer = externalizer;
    }

    @Override
    public T read(MemorySegment input) throws IOException {
      if (input.byteSize() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("The record is too large: " + input.byteSize());
      }
      byte[] content = input.toArray(ValueLayout.JAVA_BYTE);
      return externalizer.read(new DataInputStream(new UnsyncByteArrayInputStream(content)));
    }
  }

  private static final class ReadViaMemorySegment<T> implements Reader {
    private final DataExternalizer<T> externalizer;

    private ReadViaMemorySegment(DataExternalizer<T> externalizer) {
      this.externalizer = externalizer;
    }

    @Override
    public T read(MemorySegment input) throws IOException {
      if (input.byteSize() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("The record is too large: " + input.byteSize());
      }
      return externalizer.read(new DataInputStream(new InputStreamOverMemorySegment(input)));
    }
  }

  private static final class InputStreamOverMemorySegment extends InputStream {
    private final MemorySegment segment;
    private final int limit;
    private int position;
    private int mark;

    private InputStreamOverMemorySegment(MemorySegment segment) {
      this.segment = segment;
      limit = (int)segment.byteSize();
    }

    @Override
    public int read() {
      return position < limit ? segment.get(ValueLayout.JAVA_BYTE, position++) & 0xff : -1;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) {
      Objects.checkFromIndexSize(offset, length, bytes.length);
      if (length == 0) return 0;
      if (position == limit) return -1;
      int count = Math.min(length, limit - position);
      MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, position, bytes, offset, count);
      position += count;
      return count;
    }

    @Override
    public long skip(long count) {
      int skipped = Math.clamp(count, 0, limit - position);
      position += skipped;
      return skipped;
    }

    @Override
    public int available() {
      return limit - position;
    }

    @Override
    public boolean markSupported() {
      return true;
    }

    @Override
    public void mark(int readLimit) {
      mark = position;
    }

    @Override
    public void reset() {
      position = mark;
    }
  }

  private abstract static class RecordState {
    private Arena arena;
    protected MemorySegment record;
    protected Reader viaByteArrayReader;
    protected Reader viaMemorySegmentReader;

    protected final <T> void setup(DataExternalizer<T> externalizer, T value, String segmentKind) throws IOException {
      var bytes = new UnsyncByteArrayOutputStream();
      try (var output = new DataOutputStream(bytes)) {
        externalizer.save(output, value);
      }
      byte[] serialized = bytes.toByteArray();
      if (segmentKind.equals("native")) {
        arena = Arena.ofShared();
        record = arena.allocate(serialized.length);
        MemorySegment.copy(serialized, 0, record, ValueLayout.JAVA_BYTE, 0, serialized.length);
      }
      else {
        record = MemorySegment.ofArray(serialized);
      }

      viaByteArrayReader = new ReadViaByteArrayCopy<>(externalizer);
      viaMemorySegmentReader = new ReadViaMemorySegment<>(externalizer);
    }

    @TearDown(Level.Trial)
    public void close() {
      if (arena != null) arena.close();
    }
  }

  @State(Scope.Thread)
  public static class ValueContainerState extends RecordState {
    @Param("native")
    public String segmentKind;

    @Param({"1", "32", "128"})
    public int valuesCount;

    @Setup(Level.Trial)
    public void setup() throws IOException {
      var container = ValueContainerImpl.<String>createNewValueContainer();
      for (int inputId = 1; inputId <= valuesCount; inputId++) {
        int fileId = inputId * 100_000;
        container.addValue(fileId, "" + fileId);
      }
      DataExternalizer<UpdatableValueContainer<String>> externalizer = new ValueContainerExternalizer<>(
        EnumeratorStringDescriptor.INSTANCE,
        ValueContainerInputRemapping.IDENTITY
      );

      setup(externalizer, container, segmentKind);

      //sanity check:
      UpdatableValueContainer<?> container1 = (UpdatableValueContainer<?>)viaByteArrayReader.read(record);
      UpdatableValueContainer<?> container2 = (UpdatableValueContainer<?>)viaMemorySegmentReader.read(record);
      if (container1.size() != container.size() || container2.size() != container.size()) {
        throw new IllegalStateException("The adapters read different value counts");
      }
    }
  }

  @State(Scope.Thread)
  public static class ByteArrayState extends RecordState {
    @Param("native")
    public String segmentKind;

    @Param({"16", "256", "4096", "65536"})
    public int arraySize;

    @Setup(Level.Trial)
    public void setup() throws IOException {
      byte[] value = new byte[arraySize];
      for (int i = 0; i < value.length; i++) {
        value[i] = (byte)i;
      }

      DataExternalizer<byte[]> byteArrayExternalizer = new DataExternalizer<>() {
        @Override
        public void save(@NotNull DataOutput out, byte[] bytes) throws IOException {
          out.write(bytes);
        }

        @Override
        public byte[] read(@NotNull DataInput in) throws IOException {
          byte[] bytes = new byte[arraySize];
          in.readFully(bytes);
          return bytes;
        }
      };
      setup(byteArrayExternalizer, value, segmentKind);

      //sanity check:
      byte[] bytes1 = (byte[])viaByteArrayReader.read(record);
      byte[] bytes2 = (byte[])viaMemorySegmentReader.read(record);
      if (!Arrays.equals(value, bytes1) || !Arrays.equals(value, bytes2)) {
        throw new IllegalStateException("The adapters read different byte arrays");
      }
    }
  }

  @Benchmark
  public Object valueContainerWithCopy(ValueContainerState state) throws IOException {
    return state.viaByteArrayReader.read(state.record);
  }

  @Benchmark
  public Object valueContainerDirect(ValueContainerState state) throws IOException {
    return state.viaMemorySegmentReader.read(state.record);
  }

  @Benchmark
  public Object byteArrayWithCopy(ByteArrayState state) throws IOException {
    return state.viaByteArrayReader.read(state.record);
  }

  @Benchmark
  public Object byteArrayDirect(ByteArrayState state) throws IOException {
    return state.viaMemorySegmentReader.read(state.record);
  }
}
