// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class RefCountingStorage extends AbstractStorage {
  private final Map<Integer, Future<?>> myPendingWriteRequests = new ConcurrentHashMap<>();
  private int myPendingWriteRequestsSize;
  private final ExecutorService myPendingWriteRequestsExecutor = createExecutor();

  protected @NotNull ExecutorService createExecutor() {
    return new ThreadPoolExecutor(1, 1, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<>(), ConcurrencyUtil
      .newNamedThreadFactory("RefCountingStorage write content helper"));
  }

  private final boolean myDoNotZipCaches;
  private static final int MAX_PENDING_WRITE_SIZE = 20 * 1024 * 1024;

  public RefCountingStorage(String path) throws IOException {
    this(path, CapacityAllocationPolicy.DEFAULT);
  }

  public RefCountingStorage(String path, CapacityAllocationPolicy capacityAllocationPolicy) throws IOException {
    this(path, capacityAllocationPolicy, Boolean.valueOf(System.getProperty("idea.doNotZipCaches")).booleanValue());
  }

  public RefCountingStorage(String path, CapacityAllocationPolicy capacityAllocationPolicy, boolean doNotZipCaches) throws IOException {
    super(path, capacityAllocationPolicy, true);
    myDoNotZipCaches = doNotZipCaches;
  }

  @Override
  public DataInputStream readStream(int record) throws IOException {
    if (myDoNotZipCaches) return super.readStream(record);
    BufferExposingByteArrayOutputStream stream = internalReadStream(record);
    return new DataInputStream(stream.toInputStream());
  }

  @Override
  protected byte[] readBytes(int record) throws IOException {
    if (myDoNotZipCaches) return super.readBytes(record);
    return internalReadStream(record).toByteArray();
  }

  private BufferExposingByteArrayOutputStream internalReadStream(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    byte[] result = withReadLock(() -> super.readBytes(record));

    try (InflaterInputStream in = new CustomInflaterInputStream(result)) {
      final BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
      StreamUtil.copyStreamContent(in, outputStream);
      return outputStream;
    }
  }

  private static class CustomInflaterInputStream extends InflaterInputStream {
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

  private void waitForPendingWriteForRecord(int record) {
    Future<?> future = myPendingWriteRequests.get(record);
    if (future != null) {
      try {
        future.get();
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
  public void writeBytes(final int record, final ByteArraySequence bytes, final boolean fixedSize) throws IOException {

    if (myDoNotZipCaches) {
      super.writeBytes(record, bytes, fixedSize);
      return;
    }

    waitForPendingWriteForRecord(record);

    withWriteLock(() -> {
      myPendingWriteRequestsSize += bytes.getLength();
      if (myPendingWriteRequestsSize > MAX_PENDING_WRITE_SIZE) {
        zipAndWrite(bytes, record, fixedSize);
      } else {
        myPendingWriteRequests.put(record, myPendingWriteRequestsExecutor.submit(() -> {
          zipAndWrite(bytes, record, fixedSize);
          return null;
        }));
      }
    });
  }

  private void zipAndWrite(ByteArraySequence bytes, int record, boolean fixedSize) throws IOException {
    BufferExposingByteArrayOutputStream s = new BufferExposingByteArrayOutputStream();
    try (DeflaterOutputStream out = new DeflaterOutputStream(s)) {
      out.write(bytes.getBytes(), bytes.getOffset(), bytes.getLength());
    }

    withWriteLock(() -> {
      doWrite(record, fixedSize, s);
      myPendingWriteRequestsSize -= bytes.getLength();
      myPendingWriteRequests.remove(record);
    });
  }

  private void doWrite(int record, boolean fixedSize, BufferExposingByteArrayOutputStream s) throws IOException {
    super.writeBytes(record, s.toByteArraySequence(), fixedSize);
  }

  @Override
  protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
    return new RefCountingRecordsTable(recordsFile, pool);
  }

  public int acquireNewRecord() throws IOException {
    return withWriteLock(() -> {
      int record = myRecordsTable.createNewRecord();
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
      return record;
    });
  }

  public void acquireRecord(int record) {
    waitForPendingWriteForRecord(record);
    withWriteLock(() -> {
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
    });
  }

  public void releaseRecord(int record) throws IOException {
    releaseRecord(record, true);
  }

  public void releaseRecord(int record, boolean completely) throws IOException {
    waitForPendingWriteForRecord(record);
    withWriteLock(() -> {
      if (((RefCountingRecordsTable)myRecordsTable).decRefCount(record) && completely) {
        doDeleteRecord(record);
      }
    });
  }

  public int getRefCount(int record) {
    waitForPendingWriteForRecord(record);
    return withReadLock(() -> {
      return ((RefCountingRecordsTable)myRecordsTable).getRefCount(record);
    });
  }

  @Override
  public void force() {
    flushPendingWrites();
    super.force();
  }

  @Override
  public boolean isDirty() {
    return !myPendingWriteRequests.isEmpty() || super.isDirty();
  }

  @Override
  public boolean flushSome() {
    flushPendingWrites();
    return super.flushSome();
  }

  @Override
  public void dispose() {
    flushPendingWrites();
    super.dispose();
  }

  @Override
  public void checkSanity(int record) {
    flushPendingWrites();
    super.checkSanity(record);
  }

  private void flushPendingWrites() {
    for(Map.Entry<Integer, Future<?>> entry:myPendingWriteRequests.entrySet()) {
      try {
        entry.getValue().get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
