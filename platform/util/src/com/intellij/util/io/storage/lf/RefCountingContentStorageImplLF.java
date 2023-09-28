// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage.lf;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.storage.CapacityAllocationPolicy;
import com.intellij.util.io.storage.RefCountingContentStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

@ApiStatus.Internal
public final class RefCountingContentStorageImplLF extends AbstractStorageLF implements RefCountingContentStorage {
  
  private final Map<Integer, Future<?>> pendingWriteRequests = new ConcurrentHashMap<>();
  private int pendingWriteRequestsSize;
  private final ExecutorService writeRequestExecutor;

  /**Basically, it means "never delete records" */
  private final boolean useContentHashes;

  private static final int MAX_PENDING_WRITE_SIZE = 20 * 1024 * 1024;

  private final IntObjectMap<RecordData> currentRecords = ContainerUtil.createConcurrentIntObjectMap();

  private static final class RecordData {
    private final int compressedSize;
    private final int compressedHash;

    private RecordData(int size, int hash) {
      compressedSize = size;
      compressedHash = hash;
    }
  }

  public RefCountingContentStorageImplLF(@NotNull Path path,
                                         @Nullable CapacityAllocationPolicy capacityAllocationPolicy,
                                         @NotNull ExecutorService writeRequestExecutor,
                                         boolean useContentHashes) throws IOException {
    super(path, capacityAllocationPolicy);

    this.writeRequestExecutor = writeRequestExecutor;
    this.useContentHashes = useContentHashes;
  }

  @Override
  protected void doDeleteRecord(int record) throws IOException {
    if (useContentHashes) {
      throw new UnsupportedEncodingException("Records can't be released completely with enabled content hashes support");
    }
    super.doDeleteRecord(record);
  }

  @Override
  public DataInputStream readStream(int record) throws IOException {
    BufferExposingByteArrayOutputStream stream = internalReadStream(record);
    return new DataInputStream(stream.toInputStream());
  }

  @Override
  protected byte[] readBytes(int record) throws IOException {
    return internalReadStream(record).toByteArray();
  }

  private BufferExposingByteArrayOutputStream internalReadStream(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    byte[] result = withReadLock(() -> super.readBytes(record));

    if (IndexDebugProperties.IS_UNIT_TEST_MODE) {
      doRecordSanityCheck(record, result);
    }

    //text files usually 3-4x compressible:
    int uncompressedSizeEstimation = Math.max(512, result.length * 3);
    try (InflaterInputStream in = new CustomInflaterInputStream(result)) {
      final BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream(uncompressedSizeEstimation);
      StreamUtil.copy(in, outputStream);
      return outputStream;
    }
  }

  private void doRecordSanityCheck(int record, byte[] result) {
    RecordData savedData = currentRecords.get(record);
    if (savedData == null) {
      return;
    }
    int currentHash = 0;
    if (savedData.compressedSize != result.length ||
        savedData.compressedHash != (currentHash = new ByteArraySequence(result).hashCode())) {
      String msg = "expected compressed len = " + savedData.compressedSize + ", but actual len = " + result.length + ", \n"
                   + " expected content hash = " + savedData.compressedHash + ", but actual hash = " + currentHash;
      throw new AssertionError(msg);
    }
  }

  private static final class CustomInflaterInputStream extends InflaterInputStream {
    CustomInflaterInputStream(byte[] compressedData) {
      super(new UnsyncByteArrayInputStream(compressedData), new Inflater(), 1);
      // force to directly use compressed data, this ensures less round trips with native extraction code and copy streams
      this.buf = compressedData;
      this.len = -1;
    }

    @Override
    protected void fill() throws IOException {
      if (len >= 0) throw new EOFException();
      len = buf.length;
      inf.setInput(buf, 0, len);
    }

    @Override
    public void close() throws IOException {
      super.close();
      inf.end(); // custom inflater need explicit dispose
    }
  }

  private void waitForPendingWriteForRecord(int record) throws InterruptedIOException {
    Future<?> future = pendingWriteRequests.get(record);
    if (future != null) {
      try {
        future.get();
      }
      catch (InterruptedException ie) {
        //RC: need to throw something recognizable as Interrupted, but InterruptedException is checked,
        //    and not a part of IStorage API, hence we can't just rethrow it. InterruptedIOException
        //    seems like a good candidate for a wrapper: it carries very similar meaning (i.e. it is
        //    clear why we catch it somewhere up the stack), but it is also an IOException, which _is_
        //    a part of IStorage API
        final InterruptedIOException wrapperException = new InterruptedIOException();
        wrapperException.addSuppressed(ie);
        throw wrapperException;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  protected void appendBytes(int record, ByteArraySequence bytes) {
    throw new IncorrectOperationException("Appending is not supported");
  }

  @Override
  public void writeBytes(final int record, final @NotNull ByteArraySequence bytes, final boolean fixedSize) throws IOException {
    waitForPendingWriteForRecord(record);

    withWriteLock(() -> {
      pendingWriteRequestsSize += bytes.getLength();
      if (pendingWriteRequestsSize > MAX_PENDING_WRITE_SIZE) {
        zipAndWrite(bytes, record, fixedSize);
      }
      else {
        pendingWriteRequests.put(record, writeRequestExecutor.submit(() -> {
          zipAndWrite(bytes, record, fixedSize);
          return null;
        }));
      }
    });
  }

  private void zipAndWrite(ByteArraySequence bytes, int record, boolean fixedSize) throws IOException {
    BufferExposingByteArrayOutputStream s = new BufferExposingByteArrayOutputStream();
    try (DeflaterOutputStream out = new DeflaterOutputStream(s)) {
      out.write(bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
    }
    ByteArraySequence compressedBytes = s.toByteArraySequence();

    withWriteLock(() -> {
      super.writeBytes(record, compressedBytes, fixedSize);
      if (IndexDebugProperties.IS_UNIT_TEST_MODE) {
        currentRecords.put(record, new RecordData(compressedBytes.getLength(), compressedBytes.hashCode()));
      }
      pendingWriteRequestsSize -= bytes.getLength();
      pendingWriteRequests.remove(record);
    });
  }

  @Override
  protected RefCountingRecordsTableLF createRecordsTable(@NotNull StorageLockContext storageLockContext, @NotNull Path recordsFile)
    throws IOException {
    return new RefCountingRecordsTableLF(recordsFile, storageLockContext);
  }

  @Override
  public int acquireNewRecord() throws IOException {
    return withWriteLock(() -> {
      int record = recordsTable.createNewRecord();
      ((RefCountingRecordsTableLF)recordsTable).incRefCount(record);
      return record;
    });
  }

  @Override
  public int getRecordsCount() throws IOException {
    return recordsTable.getRecordsCount();
  }

  @Override
  public void acquireRecord(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    withWriteLock(() -> {
      ((RefCountingRecordsTableLF)recordsTable).incRefCount(record);
    });
  }

  @Override
  public void releaseRecord(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    withWriteLock(() -> {
      if (((RefCountingRecordsTableLF)recordsTable).decRefCount(record) && !useContentHashes) {
        doDeleteRecord(record);
      }
    });
  }

  @Override
  public int getRefCount(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    return withReadLock(() -> {
      return ((RefCountingRecordsTableLF)recordsTable).getRefCount(record);
    });
  }

  @Override
  public void force() throws IOException {
    flushPendingWrites();
    super.force();
  }

  @Override
  public boolean isDirty() {
    return !pendingWriteRequests.isEmpty() || super.isDirty();
  }

  @Override
  public void dispose() {
    flushPendingWrites();
    super.dispose();
  }

  @Override
  public void checkSanity(int record) throws IOException {
    flushPendingWrites();
    super.checkSanity(record);
  }

  private void flushPendingWrites() {
    for (Map.Entry<Integer, Future<?>> entry : pendingWriteRequests.entrySet()) {
      try {
        entry.getValue().get();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
