// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.IntRef;
import com.intellij.util.io.PagedFileStorageLockFree;
import com.intellij.util.io.StorageLockContext;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Compares read/write speed from few chosen {@link PersistentFSRecordsStorage} implementations with
 * raw speed of memory access (via array, ByteBuffer). Tries to answer the question: how much overhead
 * {@link PersistentFSRecordsStorage} implementation code puts on the top of raw memory access -- i.e.
 * how much space for optimization is left.
 */
@State(Scope.Benchmark)
@SuppressWarnings("FieldMayBeStatic")
public class PersistentFSStoragesBenchmarks {
  //@Param
  public static final int RECORDS_COUNT = 1 << 22;
  //@Param
  public static final int RECORD_SIZE_IN_BYTES = PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES;
  public static final StorageLockContext CONTEXT = new StorageLockContext(true, true, true);

  @State(Scope.Thread)
  public static class IterationState {
    public int cursor = 0;

    @Setup
    public void resetCursor() {
      cursor = 0;
    }

    public int nextSequential() {
      cursor++;
      if (cursor >= RECORDS_COUNT) {
        cursor = 0;
      }
      return cursor;
    }

    public int nextRandom() {
      return ThreadLocalRandom.current().nextInt(RECORDS_COUNT);
    }
  }

  @State(Scope.Benchmark)
  public static class ArrayState extends IterationState {
    public final int recordSize = RECORD_SIZE_IN_BYTES;
    public final int recordSizeInInts = recordSize / Integer.BYTES;
    public final int[] array = new int[recordSizeInInts * RECORDS_COUNT];
  }

  @State(Scope.Benchmark)
  public static class AtomicArrayState {
    public final int recordSize = RECORD_SIZE_IN_BYTES;
    public final int recordSizeInInts = recordSize / Integer.BYTES;
    public final AtomicIntegerArray array = new AtomicIntegerArray(recordSizeInInts * RECORDS_COUNT);
  }

  @State(Scope.Benchmark)
  public static class ByteBufferState {
    public final int recordSize = RECORD_SIZE_IN_BYTES;
    public final int recordSizeInInts = recordSize / Integer.BYTES;
    public final ByteBuffer buffer = ByteBuffer.allocate(recordSize * RECORDS_COUNT)
      .order(ByteOrder.nativeOrder());
  }

  @State(Scope.Benchmark)
  public static class DirectByteBufferState {
    public final int recordSize = RECORD_SIZE_IN_BYTES;
    public final int recordSizeInInts = recordSize / Integer.BYTES;
    public final ByteBuffer buffer = ByteBuffer.allocateDirect(recordSize * RECORDS_COUNT)
      .order(ByteOrder.nativeOrder());
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
  public static class MappedByteBufferState extends FileState {
    public final int recordSize = RECORD_SIZE_IN_BYTES;
    public final int recordSizeInInts = recordSize / Integer.BYTES;

    public ByteBuffer buffer;

    @Override
    @Setup
    public void setup() throws IOException {
      super.setup();

      try (FileChannel channel = FileChannel.open(file, WRITE, READ, CREATE)) {
        buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, recordSize * RECORDS_COUNT)
          .order(ByteOrder.nativeOrder());
      }
    }
  }

  @State(Scope.Benchmark)
  public static class FSRecordsInMemoryState extends FileState {

    private PersistentInMemoryFSRecordsStorage storage;

    @Override
    @Setup
    public void setup() throws IOException {
      super.setup();
      storage = new PersistentInMemoryFSRecordsStorage(
        file,
        RECORDS_COUNT
      );
      for (int i = 0; i < RECORDS_COUNT; i++) {
        storage.allocateRecord();
      }
    }

    @Override
    @TearDown
    public void close() throws IOException {
      storage.close();
      super.close();
    }
  }

  @State(Scope.Benchmark)
  public static class FSRecordsOverLockFreeFPCState extends FileState {

    private PersistentFSRecordsOverLockFreePagedStorage storage;

    @Override
    @Setup
    public void setup() throws IOException {
      super.setup();

      final int pageSize = 1 << 20;
      storage = new PersistentFSRecordsOverLockFreePagedStorage(
        new PagedFileStorageLockFree(
          file,
          CONTEXT,
          pageSize,
          true
        )
      );
      for (int i = 0; i < RECORDS_COUNT; i++) {
        storage.allocateRecord();
      }
    }

    @Override
    @TearDown
    public void close() throws IOException {
      storage.close();
      super.close();
    }
  }

  @State(Scope.Benchmark)
  public static class FSRecordsOverMMappedFileState extends FileState {

    private PersistentFSRecordsLockFreeOverMMappedFile storage;

    @Override
    @Setup
    public void setup() throws IOException {
      super.setup();
      storage = new PersistentFSRecordsLockFreeOverMMappedFile(
        file,
        PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE
      );
      for (int i = 0; i < RECORDS_COUNT; i++) {
        storage.allocateRecord();
      }
    }

    @Override
    @TearDown
    public void close() throws IOException {
      storage.close();
      super.close();
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
    public int array_SequentialPlainRead(final IterationState it,
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
    public int array_RandomPlainRead(final IterationState it,
                                     final ArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int[] array = state.array;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += array[offset + recordField];
      }
      return consumer;
    }

    @Benchmark
    public int array_RandomVolatileRead(final IterationState it,
                                        final AtomicArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final AtomicIntegerArray array = state.array;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += array.get(offset + recordField);
      }
      return consumer;
    }

    @Benchmark
    public int byteBuffer_SequentialPlainRead(final IterationState it,
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
    public int byteBuffer_RandomPlainRead(final IterationState it,
                                          final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += buffer.getInt(offset + recordField);
      }
      return consumer;
    }

    @Benchmark
    public int byteBufferDirect_RandomPlainRead(final IterationState it,
                                                final DirectByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      int consumer = 1;
      final int offset = recordSizeInInts * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += buffer.getInt(offset + recordField);
      }
      return consumer;
    }

    private static final VarHandle BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE =
      MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

    @Benchmark
    public int byteBuffer_RandomVolatileRead_ViaVarHandle(final IterationState it,
                                                          final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int recordSizeBytes = state.recordSize;
      final ByteBuffer buffer = state.buffer;

      int consumer = 1;
      final int offset = recordSizeBytes * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += (int)BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.getVolatile(buffer, offset + recordField * Integer.BYTES);
      }
      return consumer;
    }

    @Benchmark
    public int byteBufferMemoryMapped_RandomVolatileRead_ViaVarHandle(final IterationState it,
                                                                      final MappedByteBufferState state) throws IOException {
      final int recordSizeInBytes = state.recordSize;
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer mmappedBuffer = state.buffer;

      int consumer = 1;
      final int offset = recordSizeInBytes * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        consumer += (int)BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.getVolatile(mmappedBuffer, offset + recordField * Integer.BYTES);
      }
      return consumer;
    }

    //RC: now compare baseline with actual FSRecordsStorage implementations:

    @Benchmark
    public int FSRecords_InMemory_RandomReadByField(final IterationState it,
                                                    final FSRecordsInMemoryState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      return fsRecords_randomReadByField(it, storage);
    }

    @Benchmark
    public int FSRecords_MemoryMapped_RandomReadByField(final IterationState it,
                                                        final FSRecordsOverMMappedFileState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      return fsRecords_randomReadByField(it, storage);
    }

    @Benchmark
    public int FSRecords_OverNewFPC_RandomReadByField(final IterationState it,
                                                      final FSRecordsOverLockFreeFPCState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      return fsRecords_randomReadByField(it, storage);
    }

    @Benchmark
    public int FSRecords_MemoryMapped_RandomReadByRecord(final IterationState it,
                                                         final FSRecordsOverMMappedFileState state) throws IOException {
      final IPersistentFSRecordsStorage storage = state.storage;

      return fsRecords_RandomReadByRecord(it, storage);
    }

    @Benchmark
    public int FSRecords_OverNewFPC_RandomReadByRecord(final IterationState it,
                                                       final FSRecordsOverLockFreeFPCState state) throws IOException {
      final IPersistentFSRecordsStorage storage = state.storage;

      return fsRecords_RandomReadByRecord(it, storage);
    }

    private static int fsRecords_randomReadByField(final IterationState it,
                                                   final PersistentFSRecordsStorage storage) throws IOException {
      final int recordId = it.nextRandom();
      int consumer = 0;
      consumer += storage.getNameId(recordId);
      consumer += storage.getParent(recordId);
      consumer += storage.getContentRecordId(recordId);
      consumer += storage.getAttributeRecordId(recordId);
      consumer += storage.getLength(recordId);
      consumer += storage.getTimestamp(recordId);
      consumer += storage.getFlags(recordId);
      consumer += storage.getModCount(recordId);
      return consumer;
    }

    private static int fsRecords_RandomReadByRecord(final IterationState it,
                                                    final IPersistentFSRecordsStorage storage) throws IOException {
      final IntRef consumer = new IntRef(0);
      final int recordId = it.nextRandom();
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
    public void array_SequentialPlainWrite(final IterationState it,
                                           final ArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int[] array = state.array;
      final int offset = recordSizeInInts * it.nextSequential();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        array[offset + recordField] = recordField;
      }
    }

    @Benchmark
    public void array_RandomPlainWrite(final IterationState it,
                                       final ArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int[] array = state.array;
      final int offset = recordSizeInInts * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        array[offset + recordField] = recordField;
      }
    }

    @Benchmark
    public void array_RandomVolatileWrite(final IterationState it,
                                          final AtomicArrayState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final AtomicIntegerArray array = state.array;
      final int offset = recordSizeInInts * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        array.set(offset + recordField, recordField);
      }
    }

    @Benchmark
    public void byteBuffer_SequentialPlainWrite(final IterationState it,
                                                final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      final int offset = recordSizeInInts * it.nextSequential();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        buffer.putInt(offset + recordField, recordField);
      }
    }

    @Benchmark
    public void byteBuffer_RandomPlainWrite(final IterationState it,
                                            final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      final int offset = recordSizeInInts * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        buffer.putInt(offset + recordField, recordField);
      }
    }

    @Benchmark
    public void byteBufferDirect_RandomPlainWrite(final IterationState it,
                                                  final DirectByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer buffer = state.buffer;
      final int offset = recordSizeInInts * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        buffer.putInt(offset + recordField, recordField);
      }
    }

    private static final VarHandle BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE =
      MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

    @Benchmark
    public void byteBuffer_RandomVolatileWrite_ViaVarHandle(final IterationState it,
                                                            final ByteBufferState state) {
      final int recordSizeInInts = state.recordSizeInInts;
      final int recordSizeInBytes = state.recordSize;
      final ByteBuffer buffer = state.buffer;
      final int offset = recordSizeInBytes * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.setVolatile(buffer, offset + recordField * Integer.BYTES, recordField);
      }
    }

    @Benchmark
    public void byteBufferMemoryMapped_RandomVolatileWrite_ViaVarHandle(final IterationState it,
                                                                        final MappedByteBufferState state) throws IOException {
      final int recordSizeInInts = state.recordSizeInInts;
      final ByteBuffer mmappedBuffer = state.buffer;

      final int offset = RECORD_SIZE_IN_BYTES * it.nextRandom();
      for (int recordField = 0; recordField < recordSizeInInts; recordField++) {
        BYTE_BUFFER_AS_INT_ARRAY_VAR_HANDLE.setVolatile(mmappedBuffer, offset + recordField * Integer.BYTES, recordField);
      }
    }

    //RC: now compare baseline with actual FSRecordsStorage implementations:

    @Benchmark
    public void FSRecords_InMemory_RandomWriteByField(final IterationState it,
                                                      final FSRecordsInMemoryState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      fsRecords_randomWriteByField(it, storage);
    }

    @Benchmark
    public void FSRecords_MemoryMapped_RandomWriteByField(final IterationState it,
                                                          final FSRecordsOverMMappedFileState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      fsRecords_randomWriteByField(it, storage);
    }

    @Benchmark
    public void FSRecords_OverNewFPC_RandomWriteByField(final IterationState it,
                                                        final FSRecordsOverLockFreeFPCState state) throws IOException {
      final PersistentFSRecordsStorage storage = state.storage;

      fsRecords_randomWriteByField(it, storage);
    }

    @Benchmark
    public void FSRecords_MemoryMapped_RandomWriteByRecord(final IterationState it,
                                                           final FSRecordsOverMMappedFileState state) throws IOException {
      final IPersistentFSRecordsStorage storage = state.storage;

      fsRecords_randomWriteByRecord(it, storage);
    }

    @Benchmark
    public void FSRecords_OverNewFPC_RandomWriteByRecord(final IterationState it,
                                                         final FSRecordsOverLockFreeFPCState state) throws IOException {
      final IPersistentFSRecordsStorage storage = state.storage;

      fsRecords_randomWriteByRecord(it, storage);
    }

    private static void fsRecords_randomWriteByField(final IterationState it,
                                                     final PersistentFSRecordsStorage storage) throws IOException {
      final int recordId = it.nextRandom();
      storage.setNameId(recordId, 1);
      storage.setParent(recordId, 2);
      storage.setContentRecordId(recordId, 3);
      storage.setAttributeRecordId(recordId, 4);
      storage.setLength(recordId, 5);
      storage.setTimestamp(recordId, 6);
      storage.setFlags(recordId, 7);
    }

    private static void fsRecords_randomWriteByRecord(final IterationState it,
                                                      final IPersistentFSRecordsStorage storage) throws IOException {
      final int recordId = it.nextRandom();
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
      .mode(Mode.SampleTime)
      .include(PersistentFSStoragesBenchmarks.class.getSimpleName() + ".*AccessTest.*Volatile.*")
      .threads(7)
      .build();

    new Runner(opt).run();
  }
}
