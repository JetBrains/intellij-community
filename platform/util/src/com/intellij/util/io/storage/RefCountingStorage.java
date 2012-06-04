/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.UnsyncByteArrayInputStream;

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
  private final Map<Integer, Future<?>> myPendingZipRequests = new ConcurrentHashMap<Integer, Future<?>>();
  private int myPendingZipRequestsSize;
  private final ThreadPoolExecutor myPendingZipRequestsExecutor = new ThreadPoolExecutor(1, 1, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, "Ref Counter Storage Zipper");
    }
  });

  private final boolean myDoNotZipCaches = Boolean.valueOf(System.getProperty("idea.doNotZipCaches")).booleanValue();
  private static final int MAX_PENDING_ZIP_SIZE = 20 * 1024 * 1024;

  private final Map<Integer, Callable> myPendingWriteRequests = new ConcurrentHashMap<Integer, Callable>();
  private int myPendingWriteRequestsSize;
  private final LowMemoryWatcher myPendingWritesFlusher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      flushPendingWrites(); // only pending writes
    }
  });

  private static final int MAX_PENDING_WRITE_SIZE = 5 * 1024 * 1024;

  public RefCountingStorage(String path) throws IOException {
    super(path);
  }

  public DataInputStream readStream(int record) throws IOException {
    if (myDoNotZipCaches) return super.readStream(record);
    BufferExposingByteArrayOutputStream stream = internalReadStream(record);
    return new DataInputStream(new UnsyncByteArrayInputStream(stream.getInternalBuffer(), 0, stream.size()));
  }

  @Override
  protected byte[] readBytes(int record) throws IOException {
    if (myDoNotZipCaches) return super.readBytes(record);
    return internalReadStream(record).toByteArray();
  }

  private BufferExposingByteArrayOutputStream internalReadStream(int record) throws IOException {
    waitForPendingWriteForRecord(record);

    synchronized (myLock) {

      byte[] result = super.readBytes(record);
      InflaterInputStream in = new CustomInflaterInputStream(result);
      try {
        final BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
        StreamUtil.copyStreamContent(in, outputStream);
        return outputStream;
      }
      finally {
        in.close();
      }
    }
  }

  private static class CustomInflaterInputStream extends InflaterInputStream {
    public CustomInflaterInputStream(byte[] compressedData) {
      super(new UnsyncByteArrayInputStream(compressedData), new Inflater(), 1);
      // force to directly use compressed data, this ensures less round trips with native extraction code and copy streams
      this.buf = compressedData;
    }

    @Override
    protected void fill() throws IOException {
      if (len > 0) throw new EOFException();
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
    Future<?> future = myPendingZipRequests.get(record);
    if (future != null) {
      try {
        future.get();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    Callable action = myPendingWriteRequests.get(record);
    if (action != null) {
      try {
        action.call();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  protected void appendBytes(int record, ByteSequence bytes) throws IOException {
    throw new IncorrectOperationException("Appending is not supported");
  }

  @Override
  public void writeBytes(final int record, final ByteSequence bytes, final boolean fixedSize) throws IOException {

    if (myDoNotZipCaches) {
      super.writeBytes(record, bytes, fixedSize);
      return;
    }

    waitForPendingWriteForRecord(record);

    synchronized (myLock) {
      myPendingZipRequestsSize += bytes.getLength();
      if (myPendingZipRequestsSize > MAX_PENDING_ZIP_SIZE) {
        scheduleZippedContentToWrite(zip(bytes, record), record, fixedSize);
      } else {
        myPendingZipRequests.put(record, myPendingZipRequestsExecutor.submit(new Callable<Object>() {
          @Override
          public Object call() throws IOException {
            scheduleZippedContentToWrite(zip(bytes, record), record, fixedSize);
            return null;
          }
        }));
      }

      if (myPendingWriteRequestsSize > MAX_PENDING_WRITE_SIZE) {
        flushPendingWrites();
      }
    }
  }

  private void scheduleZippedContentToWrite(final BufferExposingByteArrayOutputStream outputStream, final int record, final boolean fixedSize) {
    synchronized (myLock) {
      myPendingWriteRequestsSize += outputStream.size();
      myPendingWriteRequests.put(record, new Callable<Object>() {
        @Override
        public Void call() throws Exception {
          write(outputStream, record, fixedSize);
          return null;
        }
      });
    }
  }


  private void write(BufferExposingByteArrayOutputStream zippedBytes, int record, boolean fixedSize) throws IOException {
    synchronized (myLock) {
      super.writeBytes(record, new ByteSequence(zippedBytes.getInternalBuffer(), 0, zippedBytes.size()), fixedSize);
      myPendingWriteRequestsSize -= zippedBytes.size();
      myPendingWriteRequests.remove(record);
    }
  }

  private BufferExposingByteArrayOutputStream zip(ByteSequence bytes, int record) throws IOException {
    BufferExposingByteArrayOutputStream s = new BufferExposingByteArrayOutputStream();
    DeflaterOutputStream out = new DeflaterOutputStream(s);
    try {
      out.write(bytes.getBytes(), bytes.getOffset(), bytes.getLength());
    }
    finally {
      out.close();
    }
    synchronized (myLock) {
      myPendingZipRequestsSize -= bytes.getLength();
      myPendingZipRequests.remove(record);
    }
    return s;
  }

  @Override
  protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
    return new RefCountingRecordsTable(recordsFile, pool);
  }

  public int acquireNewRecord() throws IOException {
    synchronized (myLock) {
      int record = myRecordsTable.createNewRecord();
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
      return record;
    }
  }

  public void acquireRecord(int record) {
    waitForPendingWriteForRecord(record);
    synchronized (myLock) {
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
    }
  }

  public void releaseRecord(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    synchronized (myLock) {
      if (((RefCountingRecordsTable)myRecordsTable).decRefCount(record)) {
        doDeleteRecord(record);
      }
    }
  }

  public int getRefCount(int record) {
    waitForPendingWriteForRecord(record);
    synchronized (myLock) {
      return ((RefCountingRecordsTable)myRecordsTable).getRefCount(record);
    }
  }

  @Override
  public void force() {
    flushAllPendingWrites();
    super.force();
  }

  @Override
  public boolean isDirty() {
    return myPendingZipRequests.size() > 0 || myPendingWriteRequests.size() > 0 || super.isDirty();
  }

  @Override
  public boolean flushSome() {
    flushAllPendingWrites();
    return super.flushSome();
  }

  @Override
  public void dispose() {
    flushAllPendingWrites();
    super.dispose();
  }

  @Override
  public void checkSanity(int record) {
    flushAllPendingWrites();
    super.checkSanity(record);
  }

  private void flushPendingWrites() {
    for(Map.Entry<Integer, Callable> entry: myPendingWriteRequests.entrySet()) {
      try {
        entry.getValue().call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void flushAllPendingWrites() {
    for(Map.Entry<Integer, Future<?>> entry: myPendingZipRequests.entrySet()) {
      try {
        entry.getValue().get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    flushPendingWrites();
  }
}
