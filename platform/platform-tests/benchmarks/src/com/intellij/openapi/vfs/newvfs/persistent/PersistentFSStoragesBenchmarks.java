// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.IntRef;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openjdk.jmh.runner.options.TimeValue.seconds;

/**
 * Compares read/write speed from few chosen {@link PersistentFSRecordsStorage} implementations with
 * raw speed of memory access (via array, ByteBuffer). Tries to answer the question: how much overhead
 * {@link PersistentFSRecordsStorage} implementation code puts on the top of raw memory access -- i.e.
 * how much space for optimization is left.
 */
@State(Scope.Benchmark)
public class PersistentFSStoragesBenchmarks {
  //@Param
  public static final int RECORDS_COUNT = 1 << 22;

  private static final Unsafe UNSAFE;
  private static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

  static {
    try {
      Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafeField.setAccessible(true);
      UNSAFE = (Unsafe)theUnsafeField.get(null);

      Field addressField = Buffer.class.getDeclaredField("address");

      BYTE_BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(addressField);
    }
    catch (Throwable e) {
      throw new ExceptionInInitializerError(e);
    }
  }


  @State(Scope.Thread)
  public static class RecordIterationState {
    public int cursor = 0;

    @Setup
    public void resetCursor() {
      cursor = 0;
    }

    public int nextSequential() {
      cursor++;
      if (cursor >= RECORDS_COUNT) {
        cursor = 1;
      }
      return cursor;
    }

    public int nextRandomRecordIndex() {
      return ThreadLocalRandom.current().nextInt(1, RECORDS_COUNT);
    }
  }

  @State(Scope.Benchmark)
  public static class RecordsState {
    @Param({"24", "40", "56"})
    public int recordSize;
  }

  @State(Scope.Benchmark)
  public static class ArrayState extends RecordsState {
    public int recordSizeInInts;
    public int[] array;

    @Setup
    public void setup() {
      recordSizeInInts = recordSize / Integer.BYTES;
      array = new int[recordSizeInInts * RECORDS_COUNT];
    }
  }

  @State(Scope.Benchmark)
  public static class AtomicArrayState extends RecordsState {
    public int recordSizeInInts;
    public AtomicIntegerArray array;

    @Setup
    public void setup() {
      recordSizeInInts = recordSize / Integer.BYTES;
      array = new AtomicIntegerArray(recordSizeInInts * RECORDS_COUNT);
    }
  }

  @State(Scope.Benchmark)
  public static class ByteBufferState extends RecordsState {
    public int recordSizeInInts;
    public ByteBuffer buffer;
    public long bufferAddress;//for Unsafe-based access

    @Setup
    public void setup() {
      recordSizeInInts = recordSize / Integer.BYTES;
      buffer = ByteBuffer.allocate(recordSize * RECORDS_COUNT)
        .order(nativeOrder());
      bufferAddress = UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }
  }

  @State(Scope.Benchmark)
  public static class DirectByteBufferState extends RecordsState {
    public int recordSizeInInts;
    public ByteBuffer buffer;
    public long bufferAddress;//for Unsafe-based access

    @Setup
    public void setup() {
      recordSizeInInts = recordSize / Integer.BYTES;
      buffer = ByteBuffer.allocateDirect(recordSize * RECORDS_COUNT)
        .order(nativeOrder());
      bufferAddress = UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }
  }

  @State(Scope.Benchmark)
  public static class FileState {
    public Path file;

    @Setup
    public void setup() throws IOException {
      file = Files.createTempFile("benchmark-", ".tmp");
    }

    @TearDown
    public void close() throws IOException {
      Files.deleteIfExists(file);
    }
  }

  @State(Scope.Benchmark)
  public static class MappedByteBufferState extends RecordsState {
    public ByteBuffer buffer;
    public int recordSizeInInts;

    @Setup
    public void setup(FileState fileState) throws IOException {
      recordSizeInInts = recordSize / Integer.BYTES;
      try (FileChannel channel = FileChannel.open(fileState.file, WRITE, READ, CREATE)) {
        buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, recordSize * RECORDS_COUNT)
          .order(nativeOrder());
      }
    }

    @TearDown
    public void close() {
      buffer = null;//help GC unmap it faster
    }
  }

  @State(Scope.Benchmark)
  public static class FSRecordsInMemoryState extends RecordsState {

    private PersistentFSRecordsOverInMemoryStorage storage;

    @Setup
    public void setup(FileState fileState) throws IOException {
      storage = new PersistentFSRecordsOverInMemoryStorage(
        fileState.file,
        RECORDS_COUNT
      );
      for (int i = 0; i < RECORDS_COUNT; i++) {
        storage.allocateRecord();
      }
    }

    @TearDown
    public void close() throws IOException {
      storage.close();
    }
  }

  @State(Scope.Benchmark)
  public static class FSRecordsOverMMappedFileState {

    private PersistentFSRecordsLockFreeOverMMappedFile storage;

    @Setup
    public void setup(FileState fileState) throws IOException {
      storage = MMappedFileStorageFactory.withDefaults()
        .pageSize(DEFAULT_MAPPED_CHUNK_SIZE)
        .wrapStorageSafely(fileState.file, PersistentFSRecordsLockFreeOverMMappedFile::new);
      for (int i = 0; i < RECORDS_COUNT; i++) {
        storage.allocateRecord();
      }
    }

    @TearDown
    public void close() throws IOException {
      storage.close();
    }
  }


  @State(Scope.Benchmark)
  @BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
  @Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
  @Fork(1)
  public static class ReadAccessTest {

    //RC: first establish baseline(s): sequential array/bytebuffer read, random read, random volatile read
    @Benchmark
    public int array_SequentialPlainRead(final RecordIterationState it,
                                         final ArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int[] array = state.array;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextSequential();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += array[offset + recordField];
      }
      return consumer;
    }

    @Benchmark
    public int array_RandomPlainRead(final RecordIterationState it,
                                     final ArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int[] array = state.array;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += array[offset + recordField];
      }
      return consumer;
    }

    @Benchmark
    public int array_RandomVolatileRead(final RecordIterationState it,
                                        final AtomicArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final AtomicIntegerArray array = state.array;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += array.get(offset + recordField);
      }
      return consumer;
    }

    @Benchmark
    public int byteBuffer_SequentialPlainRead(final RecordIterationState it,
                                              final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextSequential();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += buffer.getInt(offset + recordField);
      }
      return consumer;
    }

    @Benchmark
    public int byteBuffer_RandomPlainRead(final RecordIterationState it,
                                          final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += buffer.getInt(offset + recordField);
      }
      return consumer;
    }

    @Benchmark
    public int byteBufferDirect_RandomPlainRead(final RecordIterationState it,
                                                final DirectByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += buffer.getInt(offset + recordField);
      }
      return consumer;
    }

    private static final VarHandle BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE =
      byteBufferViewVarHandle(int[].class, nativeOrder());

    @Benchmark
    public int byteBuffer_RandomVolatileRead_ViaVarHandle(final RecordIterationState it,
                                                          final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int recordSizeBytes = state.recordSize;
      final ByteBuffer buffer = state.buffer;

      int consumer = 1;
      final int offset = recordSizeBytes * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += (int)BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.getVolatile(buffer, offset + recordField * Integer.BYTES);
      }
      return consumer;
    }

    @Benchmark
    public int byteBuffer_RandomVolatileRead_ViaUnsafe(final RecordIterationState it,
                                                       final ByteBufferState state) {
      int recordSizeInInts = state.recordSizeInInts;
      int recordSizeBytes = state.recordSize;
      //ByteBuffer buffer = state.buffer;
      //long bufferAddress = UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
      long bufferAddress = state.bufferAddress;

      int consumer = 1;
      int offset = recordSizeBytes * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += UNSAFE.getIntVolatile(null, bufferAddress + offset + (long)recordField * Integer.BYTES);
      }
      return consumer;
    }

    @Benchmark
    public int byteBuffer_SingleInt32RandomVolatileRead_ViaVarHandle(RecordIterationState it,
                                                                     ByteBufferState state) {
      int recordSizeBytes = state.recordSize;
      ByteBuffer buffer = state.buffer;

      int offset = recordSizeBytes * it.nextRandomRecordIndex();
      return (int)BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.getVolatile(buffer, offset);
    }

    @Benchmark
    public int byteBuffer_SingleInt32RandomVolatileRead_ViaUnsafe(RecordIterationState it,
                                                                  ByteBufferState state) {
      //int recordSizeInInts = state.recordSizeInInts;
      int recordSizeBytes = state.recordSize;
      ByteBuffer buffer = state.buffer;
      //long bufferAddress = UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
      long bufferAddress = state.bufferAddress;

      int offset = recordSizeBytes * it.nextRandomRecordIndex();
      int recordField = 0;
      //noinspection ConstantValue
      return UNSAFE.getIntVolatile(buffer.array(), bufferAddress + offset + recordField * Integer.BYTES);
    }

    @Benchmark
    public int byteBufferDirect_SingleInt32RandomVolatileRead_ViaUnsafe(final RecordIterationState it,
                                                                        final DirectByteBufferState state) {
      //int recordSizeInInts = state.recordSizeInInts;
      int recordSizeBytes = state.recordSize;
      //ByteBuffer buffer = state.buffer;
      long bufferAddress = state.bufferAddress;//UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);

      int offset = recordSizeBytes * it.nextRandomRecordIndex();
      return UNSAFE.getIntVolatile(null, bufferAddress + offset);
    }

    @Benchmark
    public int byteBufferDirect_SingleInt32RandomVolatileRead_ViaVarHandle(final RecordIterationState it,
                                                                           final DirectByteBufferState state) throws IOException {
      final int recordSizeInBytes = state.recordSize;
      //final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;

      final int offset = recordSizeInBytes * it.nextRandomRecordIndex();
      return (int)BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.getVolatile(buffer, offset);
    }

    @Benchmark
    public int byteBufferDirect_RandomVolatileRead_ViaUnsafe(final RecordIterationState it,
                                                             final DirectByteBufferState state) {
      int recordSizeInInts = state.recordSizeInInts;
      int recordSizeBytes = state.recordSize;
      //ByteBuffer buffer = state.buffer;
      long bufferAddress = state.bufferAddress;//UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);

      int consumer = 1;
      int offset = recordSizeBytes * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += UNSAFE.getIntVolatile(null, bufferAddress + offset + (long)recordField * Integer.BYTES);
      }
      return consumer;
    }


    @Benchmark
    public int byteBufferMemoryMapped_RandomVolatileRead_ViaVarHandle(final RecordIterationState it,
                                                                      final MappedByteBufferState state) throws IOException {
      final int recordSizeInBytes = state.recordSize;
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer mmappedBuffer = state.buffer;

      int consumer = 1;
      final int offset = recordSizeInBytes * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += (int)BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.getVolatile(mmappedBuffer, offset + recordField * Integer.BYTES);
      }
      return consumer;
    }

    //RC: now compare baseline with actual FSRecordsStorage implementations:

    @Benchmark
    public int FSRecords_InMemory_RandomReadByField(final RecordIterationState it,
                                                    final FSRecordsInMemoryState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      return fsRecords_randomReadByField(it, storage);
    }

    @Benchmark
    public int FSRecords_MemoryMapped_RandomReadByField(final RecordIterationState it,
                                                        final FSRecordsOverMMappedFileState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      return fsRecords_randomReadByField(it, storage);
    }

    @Benchmark
    public int FSRecords_MemoryMapped_RandomReadByRecord(final RecordIterationState it,
                                                         final FSRecordsOverMMappedFileState state) throws IOException {
      final IPersistentFSRecordsStorage storage = state.storage;

      return fsRecords_RandomReadByRecord(it, storage);
    }

    private static int fsRecords_randomReadByField(final RecordIterationState it,
                                                   final PersistentFSRecordsStorage storage) throws IOException {
      final int recordId = it.nextRandomRecordIndex();
      int consumer = 0;
      consumer += storage.getNameId(recordId);
      consumer += storage.getParent(recordId);
      consumer += storage.getContentRecordId(recordId);
      consumer += storage.getAttributeRecordId(recordId);
      consumer += (int)storage.getLength(recordId);
      consumer += (int)storage.getTimestamp(recordId);
      consumer += storage.getFlags(recordId);
      consumer += storage.getModCount(recordId);
      return consumer;
    }

    private static int fsRecords_RandomReadByRecord(final RecordIterationState it,
                                                    final IPersistentFSRecordsStorage storage) throws IOException {
      final IntRef consumer = new IntRef(0);
      final int recordId = it.nextRandomRecordIndex();
      storage.readRecord(recordId, record -> {
        final long result = record.getNameId()
                            + record.getParent()
                            + record.getContentRecordId()
                            + record.getAttributeRecordId()
                            + record.getLength()
                            + record.getTimestamp()
                            + record.getFlags()
                            + record.getModCount();
        consumer.set((int)result);
        return null;
      });
      return consumer.get();
    }
  }

  @State(Scope.Benchmark)
  @BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
  @Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
  @Fork(1)
  public static class WriteAccessTest {

    //RC: first establish baseline(s): sequential array/bytebuffer read, random read, random volatile read
    @Benchmark
    public void array_SequentialPlainWrite(final RecordIterationState it,
                                           final ArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int[] array = state.array;
      final int offset = recordSizeInInts * it.nextSequential();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        array[offset + recordField] = recordField;
      }
    }

    @Benchmark
    public void array_RandomPlainWrite(final RecordIterationState it,
                                       final ArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int[] array = state.array;
      final int offset = recordSizeInInts * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        array[offset + recordField] = recordField;
      }
    }

    @Benchmark
    public void array_RandomVolatileWrite(final RecordIterationState it,
                                          final AtomicArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final AtomicIntegerArray array = state.array;
      final int offset = recordSizeInInts * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        array.set(offset + recordField, recordField);
      }
    }

    @Benchmark
    public void byteBuffer_SequentialPlainWrite(final RecordIterationState it,
                                                final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      final int offset = recordSizeInInts * it.nextSequential();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        buffer.putInt(offset + recordField, recordField);
      }
    }

    @Benchmark
    public void byteBuffer_RandomPlainWrite(final RecordIterationState it,
                                            final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      final int offset = recordSizeInInts * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        buffer.putInt(offset + recordField, recordField);
      }
    }

    @Benchmark
    public void byteBufferDirect_RandomPlainWrite(final RecordIterationState it,
                                                  final DirectByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      final int offset = recordSizeInInts * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        buffer.putInt(offset + recordField, recordField);
      }
    }

    private static final VarHandle BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE = byteBufferViewVarHandle(int[].class, nativeOrder())
      .withInvokeExactBehavior();

    @Benchmark
    public void byteBuffer_RandomVolatileWrite_ViaVarHandle(final RecordIterationState it,
                                                            final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int recordSizeInBytes = state.recordSize;
      final ByteBuffer buffer = state.buffer;
      final int offset = recordSizeInBytes * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        int value = recordField;
        BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.setVolatile(buffer, offset + recordField * Integer.BYTES, value);
      }
    }

    @Benchmark
    public void byteBufferMemoryMapped_RandomCASWrite_ViaVarHandle(final RecordIterationState it,
                                                                   final MappedByteBufferState state) throws IOException {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer mmappedBuffer = state.buffer;

      //RC: Somehow on m1 CAS is 2x faster than volatile!
      final int offset = state.recordSize * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        int oldValue = (int)BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.getVolatile(mmappedBuffer, offset + recordField * Integer.BYTES);
        int newValue = recordField;
        BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.compareAndSet(mmappedBuffer, offset + recordField * Integer.BYTES, oldValue, newValue);
      }
    }

    @Benchmark
    public void byteBufferMemoryMapped_RandomVolatileWrite_ViaVarHandle(final RecordIterationState it,
                                                                        final MappedByteBufferState state) throws IOException {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer mmappedBuffer = state.buffer;

      final int offset = state.recordSize * it.nextRandomRecordIndex();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.setVolatile(mmappedBuffer, offset + recordField * Integer.BYTES, recordField);
      }
    }

    //RC: now compare baseline with actual FSRecordsStorage implementations:

    @Benchmark
    public void FSRecords_InMemory_RandomWriteByField(final RecordIterationState it,
                                                      final FSRecordsInMemoryState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      fsRecords_randomWriteByField(it, storage);
    }

    @Benchmark
    public void FSRecords_MemoryMapped_RandomWriteByField(final RecordIterationState it,
                                                          final FSRecordsOverMMappedFileState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      fsRecords_randomWriteByField(it, storage);
    }

    @Benchmark
    public void FSRecords_MemoryMapped_RandomWriteByRecord(final RecordIterationState it,
                                                           final FSRecordsOverMMappedFileState state) throws IOException {
      final IPersistentFSRecordsStorage storage = state.storage;

      fsRecords_randomWriteByRecord(it, storage);
    }

    private static void fsRecords_randomWriteByField(final RecordIterationState it,
                                                     final PersistentFSRecordsStorage storage) throws IOException {
      final int recordId = it.nextRandomRecordIndex();
      storage.updateNameId(recordId, 1);
      storage.setParent(recordId, 2);
      storage.setContentRecordId(recordId, 3);
      storage.setAttributeRecordId(recordId, 4);
      storage.setLength(recordId, 5);
      storage.setTimestamp(recordId, 6);
      storage.setFlags(recordId, 7);
    }

    private static void fsRecords_randomWriteByRecord(final RecordIterationState it,
                                                      final IPersistentFSRecordsStorage storage) throws IOException {
      final int recordId = it.nextRandomRecordIndex();
      storage.updateRecord(recordId, record -> {
        record.setNameId(1);
        record.setParent(2);
        record.setContentRecordId(3);
        record.setAttributeRecordId(4);
        record.setLength(5);
        record.setTimestamp(6);
        record.setFlags(7);
        return true;
      });
    }
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs(//to enable 'new' API:
               "-Dvfs.lock-free-impl.enable=true",
               "-Dvfs.lock-free-impl.fraction-direct-memory-to-utilize=0.5"
      )
      //.include(PersistentFSStoragesBenchmarks.class.getSimpleName() + ".*AccessTest.*Volatile.*")
      .include(PersistentFSStoragesBenchmarks.class.getSimpleName() + ".*ReadAccessTest.*Buffer.*SingleInt32.*")
      .threads(4)
      .forks(1)
      .warmupIterations(2).warmupTime(seconds(1))
      .measurementIterations(3).measurementTime(seconds(2))
      //.mode(Mode.SampleTime)
      .mode(Mode.AverageTime)
      .build();

    new Runner(opt).run();
  }
}
