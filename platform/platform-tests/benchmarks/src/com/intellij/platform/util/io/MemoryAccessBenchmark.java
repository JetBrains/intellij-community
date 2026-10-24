// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io;

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
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/// Measures a speed of read/write access to `byte[]`/[ByteBuffer]/[MemorySegment],
/// - random access/sequential access,
/// - via [Unsafe]/[VarHandle]/direct access,
/// - plain/valatile/CAS
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@SuppressWarnings("removal")
public class MemoryAccessBenchmark {
  private static final Unsafe UNSAFE = unsafe();

  private static final long BYTE_ARRAY_BASE = UNSAFE.arrayBaseOffset(byte[].class);
  private static final long INT_ARRAY_BASE = UNSAFE.arrayBaseOffset(int[].class);
  private static final long BUFFER_ADDRESS_OFFSET = bufferAddressOffset();

  private static final VarHandle ARRAY_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());
  private static final VarHandle BUFFER_HANDLE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());
  private static final VarHandle SEGMENT_HANDLE = ValueLayout.JAVA_INT.varHandle();

  private static final boolean IS_NATIVE_LITTLE_ENDIAN = (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN);

  private static Unsafe unsafe() {
    try {
      Field field = Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      return (Unsafe)field.get(null);
    }
    catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static long bufferAddressOffset() {
    try {
      return UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
    }
    catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @State(Scope.Thread)
  public abstract static class AccessState {
    @Param({"65536", "16777216"})
    public int sizeBytes;

    @Param({"sequential", "random"})
    public String accessPattern;

    private int index;
    private int randomBits = 0x12345678;
    private int value;
    private int indexMask;
    private boolean random;

    protected final void initialize() {
      indexMask = sizeBytes / Integer.BYTES - 1;
      random = switch (accessPattern) {
        case "sequential" -> false;
        case "random" -> true;
        default -> throw new IllegalArgumentException(accessPattern);
      };
      index = 0;
      randomBits = 0x12345678;
      value = 0;
    }

    public final int nextOffset() {
      if (random) {
        // Use an integer generator so random access covers the whole selected memory region.
        int bits = randomBits;
        bits ^= bits << 13;
        bits ^= bits >>> 17;
        bits ^= bits << 5;
        randomBits = bits;
        return (bits & indexMask) * Integer.BYTES;
      }
      index = (index + 1) & indexMask;
      return index * Integer.BYTES;
    }

    public final int nextValue() {
      return ++value;
    }
  }

  @State(Scope.Thread)
  public static class ArrayState extends AccessState {
    public byte[] bytes;

    @Setup(Level.Trial)
    public void setup() {
      initialize();
      bytes = new byte[sizeBytes];
    }
  }

  @State(Scope.Thread)
  public static class ByteBufferState extends AccessState {
    @Param({"heap", "direct"})
    public String kind;

    public ByteBuffer buffer;
    public Object unsafeBase;
    public long unsafeOffset;

    @Setup(Level.Trial)
    public void setup() {
      initialize();
      buffer = switch (kind) {
        case "heap" -> ByteBuffer.allocate(sizeBytes);
        case "direct" -> ByteBuffer.allocateDirect(sizeBytes);
        default -> throw new IllegalArgumentException(kind);
      };
      buffer.order(ByteOrder.nativeOrder());
      unsafeBase = buffer.hasArray() ? buffer.array() : null;
      unsafeOffset = unsafeBase == null
                     ? UNSAFE.getLong(buffer, BUFFER_ADDRESS_OFFSET)
                     : BYTE_ARRAY_BASE + buffer.arrayOffset();
    }
  }

  @State(Scope.Thread)
  public static class DirectByteBufferState extends AccessState {
    public ByteBuffer buffer;

    @Setup(Level.Trial)
    public void setup() {
      initialize();
      buffer = ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder());
    }
  }

  @State(Scope.Thread)
  public static class MemorySegmentState extends AccessState {
    @Param({"heap", "native"})
    public String kind;

    public MemorySegment segment;
    public Object unsafeBase;
    public long unsafeOffset;
    private Arena arena;

    @Setup(Level.Trial)
    public void setup() {
      initialize();
      switch (kind) {
        case "heap" -> {
          int[] ints = new int[sizeBytes / Integer.BYTES];
          segment = MemorySegment.ofArray(ints);
          unsafeBase = ints;
          unsafeOffset = INT_ARRAY_BASE;
        }
        case "native" -> {
          arena = Arena.ofConfined();
          segment = arena.allocate(sizeBytes, Integer.BYTES);
          unsafeBase = null;
          unsafeOffset = segment.address();
        }
        default -> throw new IllegalArgumentException(kind);
      }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      if (arena != null) {
        arena.close();
      }
    }
  }



  @Benchmark
  public int baseline_generateIndex(ArrayState state) {
    return state.nextOffset();
  }

  //==================== byte[]: ============================================================

  @Benchmark
  public int array_read_plain(ArrayState state) {
    byte[] bytes = state.bytes;
    int offset = state.nextOffset();
    if (IS_NATIVE_LITTLE_ENDIAN) {
      return (bytes[offset] & 0xff)
             | ((bytes[offset + 1] & 0xff) << 8)
             | ((bytes[offset + 2] & 0xff) << 16)
             | (bytes[offset + 3] << 24);
    }
    return (bytes[offset] << 24)
           | ((bytes[offset + 1] & 0xff) << 16)
           | ((bytes[offset + 2] & 0xff) << 8)
           | (bytes[offset + 3] & 0xff);
  }

  @Benchmark
  public void array_write_plain(ArrayState state) {
    byte[] bytes = state.bytes;
    int offset = state.nextOffset();
    int value = state.nextValue();
    if (IS_NATIVE_LITTLE_ENDIAN) {
      bytes[offset] = (byte)value;
      bytes[offset + 1] = (byte)(value >>> 8);
      bytes[offset + 2] = (byte)(value >>> 16);
      bytes[offset + 3] = (byte)(value >>> 24);
    }
    else {
      bytes[offset] = (byte)(value >>> 24);
      bytes[offset + 1] = (byte)(value >>> 16);
      bytes[offset + 2] = (byte)(value >>> 8);
      bytes[offset + 3] = (byte)value;
    }
  }

  @Benchmark
  public int array_read_plainViaUnsafe(ArrayState state) {
    return UNSAFE.getInt(state.bytes, BYTE_ARRAY_BASE + state.nextOffset());
  }

  @Benchmark
  public void array_write_plainViaUnsafe(ArrayState state) {
    UNSAFE.putInt(state.bytes, BYTE_ARRAY_BASE + state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public int array_read_volatileViaUnsafe(ArrayState state) {
    return UNSAFE.getIntVolatile(state.bytes, BYTE_ARRAY_BASE + state.nextOffset());
  }

  @Benchmark
  public void array_write_volatileViaUnsafe(ArrayState state) {
    UNSAFE.putIntVolatile(state.bytes, BYTE_ARRAY_BASE + state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public boolean array_write_casViaUnsafe(ArrayState state) {
    long offset = BYTE_ARRAY_BASE + state.nextOffset();
    int previous = UNSAFE.getIntVolatile(state.bytes, offset);
    return UNSAFE.compareAndSwapInt(state.bytes, offset, previous, state.nextValue());
  }

  @Benchmark
  public int array_read_plainViaVarHandle(ArrayState state) {
    return (int)ARRAY_HANDLE.get(state.bytes, state.nextOffset());
  }

  @Benchmark
  public void array_write_plainViaVarHandle(ArrayState state) {
    ARRAY_HANDLE.set(state.bytes, state.nextOffset(), state.nextValue());
  }

  //==================== ByteBuffer: ============================================================

  @Benchmark
  public int buffer_read_plain(ByteBufferState state) {
    return state.buffer.getInt(state.nextOffset());
  }

  @Benchmark
  public void buffer_write_plain(ByteBufferState state) {
    state.buffer.putInt(state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public int buffer_read_plainViaUnsafe(ByteBufferState state) {
    return UNSAFE.getInt(state.unsafeBase, state.unsafeOffset + state.nextOffset());
  }

  @Benchmark
  public void buffer_write_plainViaUnsafe(ByteBufferState state) {
    UNSAFE.putInt(state.unsafeBase, state.unsafeOffset + state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public int buffer_read_volatileViaUnsafe(ByteBufferState state) {
    return UNSAFE.getIntVolatile(state.unsafeBase, state.unsafeOffset + state.nextOffset());
  }

  @Benchmark
  public void buffer_write_volatileViaUnsafe(ByteBufferState state) {
    UNSAFE.putIntVolatile(state.unsafeBase, state.unsafeOffset + state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public boolean buffer_write_casViaUnsafe(ByteBufferState state) {
    long offset = state.unsafeOffset + state.nextOffset();
    int previous = UNSAFE.getIntVolatile(state.unsafeBase, offset);
    return UNSAFE.compareAndSwapInt(state.unsafeBase, offset, previous, state.nextValue());
  }

  @Benchmark
  public int buffer_read_plainViaVarHandle(ByteBufferState state) {
    return (int)BUFFER_HANDLE.get(state.buffer, state.nextOffset());
  }

  @Benchmark
  public void buffer_write_plainViaVarHandle(ByteBufferState state) {
    BUFFER_HANDLE.set(state.buffer, state.nextOffset(), state.nextValue());
  }

  //==================== direct ByteBuffer: ============================================================

  @Benchmark
  public int directBuffer_read_volatileViaVarHandle(DirectByteBufferState state) {
    return (int)BUFFER_HANDLE.getVolatile(state.buffer, state.nextOffset());
  }

  @Benchmark
  public void directBuffer_write_volatileViaVarHandle(DirectByteBufferState state) {
    BUFFER_HANDLE.setVolatile(state.buffer, state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public boolean directBuffer_write_casViaVarHandle(DirectByteBufferState state) {
    int offset = state.nextOffset();
    int previous = (int)BUFFER_HANDLE.getVolatile(state.buffer, offset);
    return BUFFER_HANDLE.compareAndSet(state.buffer, offset, previous, state.nextValue());
  }

  //==================== MemorySegment: ============================================================

  @Benchmark
  public int segment_read_plain(MemorySegmentState state) {
    return state.segment.get(ValueLayout.JAVA_INT, state.nextOffset());
  }

  @Benchmark
  public void segment_write_plain(MemorySegmentState state) {
    state.segment.set(ValueLayout.JAVA_INT, state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public int segment_read_plainViaUnsafe(MemorySegmentState state) {
    return UNSAFE.getInt(state.unsafeBase, state.unsafeOffset + state.nextOffset());
  }

  @Benchmark
  public void segment_write_plainViaUnsafe(MemorySegmentState state) {
    UNSAFE.putInt(state.unsafeBase, state.unsafeOffset + state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public int segment_read_volatileViaUnsafe(MemorySegmentState state) {
    return UNSAFE.getIntVolatile(state.unsafeBase, state.unsafeOffset + state.nextOffset());
  }

  @Benchmark
  public void segment_write_volatileViaUnsafe(MemorySegmentState state) {
    UNSAFE.putIntVolatile(state.unsafeBase, state.unsafeOffset + state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public boolean segment_write_casViaUnsafe(MemorySegmentState state) {
    long offset = state.unsafeOffset + state.nextOffset();
    int previous = UNSAFE.getIntVolatile(state.unsafeBase, offset);
    return UNSAFE.compareAndSwapInt(state.unsafeBase, offset, previous, state.nextValue());
  }

  @Benchmark
  public int segment_read_plainViaVarHandle(MemorySegmentState state) {
    return (int)SEGMENT_HANDLE.get(state.segment, (long)state.nextOffset());
  }

  @Benchmark
  public void segment_write_plainViaVarHandle(MemorySegmentState state) {
    SEGMENT_HANDLE.set(state.segment, (long)state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public int segment_read_volatileViaVarHandle(MemorySegmentState state) {
    return (int)SEGMENT_HANDLE.getVolatile(state.segment, (long)state.nextOffset());
  }

  @Benchmark
  public void segment_write_volatileViaVarHandle(MemorySegmentState state) {
    SEGMENT_HANDLE.setVolatile(state.segment, (long)state.nextOffset(), state.nextValue());
  }

  @Benchmark
  public boolean segment_write_casViaVarHandle(MemorySegmentState state) {
    long offset = state.nextOffset();
    int previous = (int)SEGMENT_HANDLE.getVolatile(state.segment, offset);
    return SEGMENT_HANDLE.compareAndSet(state.segment, offset, previous, state.nextValue());
  }
}
