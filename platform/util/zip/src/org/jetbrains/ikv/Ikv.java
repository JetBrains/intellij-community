// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ikv;

import com.intellij.util.lang.ByteBufferCleaner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

/*
byte array - values
key metadata - keys (sorted by index (probe provides unique index because we check collisions))
long array - value offset and size pairs
int - key data size
int - entry count

Value offsets are not sequential because MPH sorts the array but values are written in a user order.
So, besides offset, we have to store size.
MPH does not sort values to ensure that we will map the file to memory efficiently
 (assume that the user's order groups related values together).

Little endian is used because both Intel and M1 CPU use little endian (saves a little for writing and reading integers).
*/
@SuppressWarnings("DuplicatedCode")
@ApiStatus.Internal
public abstract class Ikv implements AutoCloseable {
  private ByteBuffer mappedBuffer;

  private Ikv(ByteBuffer mappedBuffer) {
    this.mappedBuffer = mappedBuffer;
  }

  public final ByteBuffer getMappedBufferAt(int position) {
    return mappedBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN).position(position);
  }

  public static final class SizeAwareIkv extends Ikv {
    private final StrippedLongToLongMap index;

    private SizeAwareIkv(long[] metadata, ByteBuffer mappedBuffer) {
      super(mappedBuffer);

      index = new StrippedLongToLongMap(metadata);
    }

    public ByteBuffer getValue(long key) {
      long pair = index.get(key);
      return pair == -1 ? null : getByteBufferByValue(pair);
    }

    public long getOffsetAndSize(long key) {
      return index.get(key);
    }

    public int getSizeByValue(long pair) {
      return (int)pair;
    }

    public ByteBuffer getByteBufferByValue(long pair) {
      int start = (int)(pair >> 32);
      return getMappedBufferAt(start).limit(start + (int)pair);
    }

    public byte[] getByteArray(long key) {
      long pair = index.get(key);
      if (pair == -1) {
        return null;
      }
      return getByteArrayByValue(pair);
    }

    public byte[] getByteArrayByValue(long pair) {
      int start = (int)(pair >> 32);
      byte[] result = new byte[(int)pair];
      getMappedBufferAt(start).get(result, 0, result.length);
      return result;
    }

    public int getEntryCount() {
      return index.size();
    }
  }

  public static final class SizeUnawareIkv extends Ikv {
    private final StrippedIntToIntMap index;

    private SizeUnawareIkv(int[] metadata, ByteBuffer mappedBuffer) {
      super(mappedBuffer);

      this.index = new StrippedIntToIntMap(metadata);
    }

    // if size is known by the reader
    public ByteBuffer getUnboundedValue(int key) {
      int offset = index.get(key);
      return offset == -1 ? null : getMappedBufferAt(offset).order(ByteOrder.LITTLE_ENDIAN);
    }
  }

  public static @NotNull SizeUnawareIkv loadSizeUnawareIkv(@NotNull Path file) throws IOException {
    return (SizeUnawareIkv)loadIkv(file);
  }

  public static @NotNull SizeAwareIkv loadSizeAwareIkv(@NotNull Path file) throws IOException {
    return (SizeAwareIkv)loadIkv(file);
  }

  public static @NotNull Ikv loadIkv(@NotNull Path file) throws IOException {
    ByteBuffer mappedBuffer;
    int fileSize;
    try (FileChannel fileChannel = FileChannel.open(file, EnumSet.of(StandardOpenOption.READ))) {
      fileSize = (int)fileChannel.size();
      try {
        mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
      }
      catch (UnsupportedOperationException e) {
        // in memory fs
        mappedBuffer = ByteBuffer.allocate(fileSize);
        while (mappedBuffer.hasRemaining()) {
          fileChannel.read(mappedBuffer);
        }
        mappedBuffer.rewind();
      }
    }
    mappedBuffer.order(ByteOrder.LITTLE_ENDIAN);
    mappedBuffer.position(fileSize);
    return loadIkv(mappedBuffer, fileSize);
  }

  public static @NotNull Ikv loadIkv(ByteBuffer mappedBuffer, int indexEndPosition) {
    final int position = indexEndPosition - Byte.BYTES - Integer.BYTES;
    int entryCount = mappedBuffer.getInt(position);
    boolean withSize = mappedBuffer.get(position + Integer.BYTES) == 1;

    int keyDataSize = entryCount * (withSize ? Long.BYTES : Integer.BYTES);
    int offsetAndSizePairsDataSize = entryCount * (withSize ? Long.BYTES : Integer.BYTES);

    mappedBuffer.position(position - (keyDataSize + offsetAndSizePairsDataSize));
    if (withSize) {
      long[] metadata = new long[entryCount * 2];
      mappedBuffer.asLongBuffer().get(metadata);
      return new SizeAwareIkv(metadata, mappedBuffer);
    }
    else {
      int[] metadata = new int[entryCount * 2];
      mappedBuffer.asIntBuffer().get(metadata);
      return new SizeUnawareIkv(metadata, mappedBuffer);
    }
  }

  @Override
  public void close() throws Exception {
    ByteBuffer buffer = mappedBuffer;
    if (buffer != null) {
      mappedBuffer = null;
      ByteBufferCleaner.unmapBuffer(buffer);
    }
  }
}