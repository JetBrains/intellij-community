/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Created by Maxim.Mossienko on 1/9/14.
 */
public class AppendableStorageBackedByResizableMappedFile extends ResizeableMappedFile {
  private final MyDataIS myReadStream;
  private byte[] myAppendBuffer;
  private volatile int myFileLength;
  private volatile int myBufferPosition;
  private static final int ourAppendBufferLength = 4096;

  public AppendableStorageBackedByResizableMappedFile(File file,
                                                      int initialSize,
                                                      @Nullable PagedFileStorage.StorageLockContext lockContext,
                                                      int pageSize,
                                                      boolean valuesAreBufferAligned) throws IOException {
    super(file, initialSize, lockContext, pageSize, valuesAreBufferAligned);
    myReadStream = new MyDataIS(this);
    myFileLength = (int)length();
  }

  private void flushKeyStoreBuffer() {
    if (myBufferPosition > 0) {
      put(myFileLength, myAppendBuffer, 0, myBufferPosition);
      myFileLength += myBufferPosition;
      myBufferPosition = 0;
    }
  }

  @Override
  public void force() {
    flushKeyStoreBuffer();
    super.force();
  }

  @Override
  public void close() {
    flushKeyStoreBuffer();
    super.close();
  }

  public <Data> Data read(int addr, KeyDescriptor<Data> descriptor) throws IOException {
    if (myFileLength <= addr) {
      return descriptor.read(new DataInputStream(new UnsyncByteArrayInputStream(myAppendBuffer, addr - myFileLength, myBufferPosition)));
    }
    // we do not need to flushKeyBuffer since we store complete records
    myReadStream.setup(addr, myFileLength);
    return descriptor.read(myReadStream);
  }

  public <Data> boolean processAll(Processor<Data> processor, KeyDescriptor<Data> descriptor) throws IOException {
    force();

    DataInputStream keysStream = new DataInputStream(new BufferedInputStream(new LimitedInputStream(new FileInputStream(getPagedFileStorage().getFile()),
                                                                                                    myFileLength) {
      @Override
      public int available() throws IOException {
        return remainingLimit();
      }
    }, 32768));
    try {
      try {
        while (true) {
          Data key = descriptor.read(keysStream);
          if (!processor.process(key)) return false;
        }
      }
      catch (EOFException e) {
        // Done
      }
      return true;
    }
    finally {
      keysStream.close();
    }
  }

  public int getCurrentLength() {
    return myBufferPosition + myFileLength;
  }

  public <Data> int append(Data value, KeyDescriptor<Data> descriptor) throws IOException {
    final BufferExposingByteArrayOutputStream bos = new BufferExposingByteArrayOutputStream();
    DataOutput out = new DataOutputStream(bos);
    descriptor.save(out, value);
    final int size = bos.size();
    final byte[] buffer = bos.getInternalBuffer();

    int currentLength = getCurrentLength();
    if (size > ourAppendBufferLength) {
      flushKeyStoreBuffer();
      put(currentLength, buffer, 0, size);
      myFileLength += size;
    }
    else {
      if (size > ourAppendBufferLength - myBufferPosition) {
        flushKeyStoreBuffer();
      }
      // myAppendBuffer will contain complete records
      if (myAppendBuffer == null) {
        myAppendBuffer = new byte[ourAppendBufferLength];
      }
      System.arraycopy(buffer, 0, myAppendBuffer, myBufferPosition, size);
      myBufferPosition += size;
    }
    return currentLength;
  }

  public <Data> boolean checkBytesAreTheSame(final int addr, Data value, KeyDescriptor<Data> descriptor) throws IOException {
    final boolean[] sameValue = new boolean[1];
    OutputStream comparer;

    final PagedFileStorage storage = getPagedFileStorage();

    if (myFileLength <= addr) {
      //noinspection IOResourceOpenedButNotSafelyClosed
      comparer = new OutputStream() {
        int address = addr - myFileLength;
        boolean same = true;
        @Override
        public void write(int b) {
          if (same) {
            same = address < myBufferPosition && myAppendBuffer[address++] == (byte)b;
          }
        }
        @Override
        public void close() {
          sameValue[0]  = same;
        }
      };
    }
    else {
      //noinspection IOResourceOpenedButNotSafelyClosed
      comparer = new OutputStream() {
        int base = addr;
        int address = storage.getOffsetInPage(addr);
        boolean same = true;
        ByteBuffer buffer = storage.getByteBuffer(addr, false).getCachedBuffer();
        final int myPageSize = storage.myPageSize;

        @Override
        public void write(int b) {
          if (same) {
            if (myPageSize == address && address < myFileLength) {    // reached end of current byte buffer
              base += address;
              buffer = storage.getByteBuffer(base, false).getCachedBuffer();
              address = 0;
            }
            same = address < myFileLength && buffer.get(address++) == (byte)b;
          }
        }

        @Override
        public void close() {
          sameValue[0]  = same;
        }
      };

    }

    DataOutput out = new DataOutputStream(comparer);
    descriptor.save(out, value);
    comparer.close();

    return sameValue[0];
  }

  private static class MyDataIS extends DataInputStream {
    private MyDataIS(ResizeableMappedFile raf) {
      super(new MyBufferedIS(new MappedFileInputStream(raf, 0, 0)));
    }

    public void setup(long pos, long limit) {
      ((MyBufferedIS)in).setup(pos, limit);
    }
  }

  private static class MyBufferedIS extends BufferedInputStream {
    public MyBufferedIS(final InputStream in) {
      super(in, 512);
    }

    public void setup(long pos, long limit) {
      this.pos = 0;
      count = 0;
      ((MappedFileInputStream)in).setup(pos, limit);
    }
  }
}
