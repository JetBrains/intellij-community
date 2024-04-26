// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.UnsyncByteArrayInputStream;
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
public final class RefCountingContentStorageImpl extends AbstractStorage implements RefCountingContentStorage {
  private final Map<Integer, Future<?>> myPendingWriteRequests = new ConcurrentHashMap<>();
  private int myPendingWriteRequestsSize;
  private final ExecutorService myWriteRequestExecutor;

  /**Basically, it means "never delete records" */
  private final boolean myUseContentHashes;

  private static final int MAX_PENDING_WRITE_SIZE = 20 * 1024 * 1024;

  private final IntObjectMap<RecordData> myCurrentRecords = ContainerUtil.createConcurrentIntObjectMap();

  private static final class RecordData {
    private final int compressedSize;
    private final int compressedHash;

    private RecordData(int size, int hash) {
      compressedSize = size;
      compressedHash = hash;
    }
  }

  public RefCountingContentStorageImpl(@NotNull Path path,
                                       @Nullable CapacityAllocationPolicy capacityAllocationPolicy,
                                       @NotNull ExecutorService writeRequestExecutor,
                                       boolean useContentHashes) throws IOException {
    super(path, capacityAllocationPolicy);

    myWriteRequestExecutor = writeRequestExecutor;
    myUseContentHashes = useContentHashes;
  }

  @Override
  protected void doDeleteRecord(int record) throws IOException {
    if (myUseContentHashes) {
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

    try (InflaterInputStream in = new CustomInflaterInputStream(result)) {
      final BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
      StreamUtil.copy(in, outputStream);
      return outputStream;
    }
  }

  private void doRecordSanityCheck(int record, byte[] result) {
    RecordData savedData = myCurrentRecords.get(record);
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
    Future<?> future = myPendingWriteRequests.get(record);
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
      myPendingWriteRequestsSize += bytes.getLength();
      if (myPendingWriteRequestsSize > MAX_PENDING_WRITE_SIZE) {
        zipAndWrite(bytes, record, fixedSize);
      }
      else {
        myPendingWriteRequests.put(record, myWriteRequestExecutor.submit(() -> {
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
        myCurrentRecords.put(record, new RecordData(compressedBytes.getLength(), compressedBytes.hashCode()));
      }
      myPendingWriteRequestsSize -= bytes.getLength();
      myPendingWriteRequests.remove(record);
    });
  }

  @Override
  protected RefCountingRecordsTable createRecordsTable(@NotNull StorageLockContext storageLockContext, @NotNull Path recordsFile)
    throws IOException {
    return new RefCountingRecordsTable(recordsFile, storageLockContext);
  }

  @Override
  public int acquireNewRecord() throws IOException {
    return withWriteLock(() -> {
      int record = myRecordsTable.createNewRecord();
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
      return record;
    });
  }

  @Override
  public int getRecordsCount() throws IOException {
    return myRecordsTable.getRecordsCount();
  }

  @Override
  public void acquireRecord(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    withWriteLock(() -> {
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
    });
  }

  @Override
  public void releaseRecord(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    withWriteLock(() -> {
      if (((RefCountingRecordsTable)myRecordsTable).decRefCount(record) && !myUseContentHashes) {
        doDeleteRecord(record);
      }
    });
  }

  @Override
  public int getRefCount(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    return withReadLock(() -> {
      return ((RefCountingRecordsTable)myRecordsTable).getRefCount(record);
    });
  }

  @Override
  public void force() throws IOException {
    flushPendingWrites();
    super.force();
  }

  @Override
  public boolean isDirty() {
    return !myPendingWriteRequests.isEmpty() || super.isDirty();
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
    for (Map.Entry<Integer, Future<?>> entry : myPendingWriteRequests.entrySet()) {
      try {
        entry.getValue().get();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
