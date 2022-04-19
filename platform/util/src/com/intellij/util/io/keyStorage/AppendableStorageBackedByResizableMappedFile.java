// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.keyStorage;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.*;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

public class AppendableStorageBackedByResizableMappedFile<Data> extends ResizeableMappedFile implements AppendableObjectStorage<Data> {
  private static final ThreadLocal<MyDataIS> ourReadStream = ThreadLocal.withInitial(() -> new MyDataIS());
  private volatile byte[] myAppendBuffer;
  private volatile int myFileLength;
  private volatile int myBufferPosition;
  private static final int ourAppendBufferLength = 4096;
  @NotNull
  private final DataExternalizer<Data> myDataDescriptor;

  public AppendableStorageBackedByResizableMappedFile(final Path file,
                                                      int initialSize,
                                                      @Nullable StorageLockContext lockContext,
                                                      int pageSize,
                                                      boolean valuesAreBufferAligned,
                                                      @NotNull DataExternalizer<Data> dataDescriptor) throws IOException {
    super(file, initialSize, lockContext, pageSize, valuesAreBufferAligned);
    myDataDescriptor = dataDescriptor;
    myFileLength = (int)length();
  }

  @Override
  public void clear() throws IOException {
    super.clear();
    myFileLength = 0;
  }

  private void flushKeyStoreBuffer() throws IOException {
    if (myBufferPosition > 0) {
      put(myFileLength, myAppendBuffer, 0, myBufferPosition);
      myFileLength += myBufferPosition;
      myBufferPosition = 0;
    }
  }

  @Override
  public void force() throws IOException {
    flushKeyStoreBuffer();
    super.force();
  }

  @Override
  public void close() throws IOException {
    try {
      List<Exception> exceptions = new SmartList<>();
      ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> flushKeyStoreBuffer()));
      ContainerUtil.addIfNotNull(exceptions, ExceptionUtil.runAndCatch(() -> super.close()));
      if (!exceptions.isEmpty()) {
        throw new IOException(new CompoundRuntimeException(exceptions));
      }
    }
    finally {
      ourReadStream.remove();
    }
  }

  @Override
  public Data read(final int addr) throws IOException {
    if (myFileLength <= addr) {
      // addr points to un-existed data
      if (myAppendBuffer == null) {
        throw new NoDataException("requested address points to un-existed data");
      }

      // addr points to un-existed data
      int bufferOffset = addr - myFileLength;
      if (bufferOffset > myBufferPosition) {
        throw new NoDataException("requested address points to un-existed data");
      }

      return myDataDescriptor.read(new DataInputStream(new UnsyncByteArrayInputStream(myAppendBuffer, bufferOffset, myBufferPosition)));
    }
    // we do not need to flushKeyBuffer since we store complete records
    MyDataIS rs = ourReadStream.get();

    rs.setup(this, addr, myFileLength);
    return myDataDescriptor.read(rs);
  }

  @Override
  public boolean processAll(@NotNull StorageObjectProcessor<? super Data> processor) throws IOException {
    assert !isDirty();
    if (myFileLength == 0) return true;
    IOCancellationCallbackHolder.checkCancelled();
    return readInputStream(is -> {
      // calculation may restart few times, so it's expected that processor processes duplicated
      LimitedInputStream lis = new LimitedInputStream(new BufferedInputStream(is), myFileLength) {
        @Override
        public int available() {
          return remainingLimit();
        }
      };
      DataInputStream keyStream = new DataInputStream(lis);
      try {
        while (true) {
          int offset = lis.getBytesRead();
          Data key = myDataDescriptor.read(keyStream);
          if (!processor.process(offset, key)) return false;
        }
      }
      catch (EOFException e) {
        // Done
      }

      return true;
    });
  }

  @Override
  public int getCurrentLength() {
    return myBufferPosition + myFileLength;
  }

  @Override
  public int append(Data value) throws IOException {
    final BufferExposingByteArrayOutputStream bos = new BufferExposingByteArrayOutputStream();
    DataOutput out = new com.intellij.util.io.DataOutputStream(bos);
    myDataDescriptor.save(out, value);
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

  @Override
  public boolean checkBytesAreTheSame(final int addr, Data value) throws IOException {
    final boolean[] sameValue = new boolean[1];
    OutputStream comparer = buildOldComparerStream(addr, sameValue);
    DataOutput out = new DataOutputStream(comparer);
    myDataDescriptor.save(out, value);
    comparer.close();
    return sameValue[0];
  }

  @NotNull
  private OutputStream buildOldComparerStream(final int addr, final boolean[] sameValue) throws IOException {
    OutputStream comparer;
    final PagedFileStorage storage = getPagedFileStorage();

    if (myFileLength <= addr) {
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
      comparer = new OutputStream() {
        int base = addr;
        int address = storage.getOffsetInPage(addr);
        boolean same = true;
        DirectBufferWrapper buffer = storage.getByteBuffer(addr, false);
        final int myPageSize = storage.getPageSize();

        @Override
        public void write(int b) throws IOException {
          if (same) {
            if (myPageSize == address && address < myFileLength) {    // reached end of current byte buffer
              base += address;
              buffer = storage.getByteBuffer(base, false);
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
    return comparer;
  }

  private static final class MyDataIS extends DataInputStream {
    private MyDataIS() {
      super(new MyBufferedIS());
    }

    void setup(ResizeableMappedFile is, long pos, long limit) {
      ((MyBufferedIS)in).setup(is, pos, limit);
    }
  }

  private static class MyBufferedIS extends BufferedInputStream {
    MyBufferedIS() {
      super(TOMBSTONE, 512);
    }

    void setup(ResizeableMappedFile in, long pos, long limit) {
      this.pos = 0;
      this.count = 0;
      this.in = new MappedFileInputStream(in, pos, limit);
    }
  }

  private static final InputStream TOMBSTONE = new InputStream() {
    @Override
    public int read() {
      throw new IllegalStateException("should not happen");
    }
  };
}