// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.zip;

import com.intellij.util.io.DirectByteBufferPool;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

@ApiStatus.Internal
public final class ImmutableZipEntry {
  private final int uncompressedSize;
  private final int compressedSize;
  private final int method;

  private final String name;

  private final int headerOffset;
  private final int nameLengthInBytes;
  // we cannot compute dataOffset in advance because there is incorrect ZIP files where extra data specified for entry in central directory,
  // but not in local file header
  private int dataOffset = -1;

  public ImmutableZipEntry(String name, int compressedSize, int uncompressedSize, int headerOffset, int nameLengthInBytes, int method) {
    this.name = name;
    this.headerOffset = headerOffset;
    this.nameLengthInBytes = nameLengthInBytes;
    this.compressedSize = compressedSize;
    this.uncompressedSize = uncompressedSize;
    this.method = method;
  }

  void setDataOffset(int dataOffset) {
    this.dataOffset = dataOffset;
  }

  public int getHeaderOffset() {
    return headerOffset;
  }

  public int getSize() {
    return uncompressedSize;
  }

  /**
   * Get the name of the entry.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the size of the compressed entry data.
   * In the case of a stored entry, the compressed size will be the same
   * as the uncompressed size of the entry.
   */
  public int getCompressedSize() {
    return compressedSize;
  }

  public int getUncompressedSize() {
    return uncompressedSize;
  }

  /**
   * Returns the compression method of the entry.
   */
  public int getMethod() {
    return method;
  }

  /**
   * Is this entry a directory?
   */
  public boolean isDirectory() {
    return uncompressedSize == -2;
  }

  public int hashCode() {
    return name.hashCode();
  }

  public byte[] getData(@NotNull ImmutableZipFile file) throws IOException {
    if (uncompressedSize < 0) {
      throw new IOException("no data");
    }

    if (file.fileSize < (dataOffset + compressedSize)) {
      throw new EOFException();
    }

    switch (getMethod()) {
      case ZipEntry.STORED: {
        ByteBuffer inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(file.mappedBuffer);
        byte[] result = new byte[uncompressedSize];
        inputBuffer.get(result);
        return result;
      }
      case ZipEntry.DEFLATED: {
        ByteBuffer inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(file.mappedBuffer);
        Inflater inflater = new Inflater(true);
        inflater.setInput(inputBuffer);
        int count = uncompressedSize;
        byte[] result = new byte[count];
        int offset = 0;
        try {
          while (count > 0) {
            int n = inflater.inflate(result, offset, count);
            if (n == 0) {
              throw new IllegalStateException("Inflater wants input, but input was already set");
            }

            offset += n;
            count -= n;
          }
          return result;
        }
        catch (DataFormatException e) {
          String s = e.getMessage();
          throw new ZipException(s == null ? "Invalid ZLIB data format" : s);
        }
      }

      default:
        throw new ZipException("Found unsupported compression method " + getMethod());
    }
  }

  @ApiStatus.Internal
  public InputStream getInputStream(@NotNull ImmutableZipFile file) throws IOException {
    return new DirectByteBufferBackedInputStream(getByteBuffer(file), getMethod() == ZipEntry.DEFLATED);
  }

  /**
   * Release returned buffer using {@link #releaseBuffer} after use.
   */
  @ApiStatus.Internal
  public ByteBuffer getByteBuffer(@NotNull ImmutableZipFile file) throws IOException {
    if (uncompressedSize < 0) {
      throw new IOException("no data");
    }

    if (file.fileSize < (dataOffset + compressedSize)) {
      throw new EOFException();
    }

    switch (getMethod()) {
      case ZipEntry.STORED: {
        return computeDataOffsetIfNeededAndReadInputBuffer(file.mappedBuffer);
      }
      case ZipEntry.DEFLATED: {
        ByteBuffer inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(file.mappedBuffer);
        Inflater inflater = new Inflater(true);
        inflater.setInput(inputBuffer);
        try {
          ByteBuffer result = DirectByteBufferPool.DEFAULT_POOL.allocate(uncompressedSize);
          while (result.hasRemaining()) {
            if (inflater.inflate(result) == 0) {
              throw new IllegalStateException("Inflater wants input, but input was already set");
            }
          }
          result.rewind();
          return result;
        }
        catch (DataFormatException e) {
          String s = e.getMessage();
          throw new ZipException(s == null ? "Invalid ZLIB data format" : s);
        }
      }

      default:
        throw new ZipException("Found unsupported compression method " + getMethod());
    }
  }

  public void releaseBuffer(ByteBuffer buffer) {
    if (method == ZipEntry.DEFLATED) {
      DirectByteBufferPool.DEFAULT_POOL.release(buffer);
    }
  }

  private @NotNull ByteBuffer computeDataOffsetIfNeededAndReadInputBuffer(ByteBuffer mappedBuffer) {
    if (dataOffset == -1) {
      int start = headerOffset + 28;
      // read actual extra field length
      int extraFieldLength = mappedBuffer.getShort(start) & 0xffff;
      if (extraFieldLength > 128) {
        // assert just to be sure that we don't read a lot of data in case of some error in zip file or our impl
        throw new UnsupportedOperationException(
          "extraFieldLength expected to be less than 128 bytes but " + extraFieldLength + " (name=" + name + ")");
      }

      dataOffset = start + 2 + nameLengthInBytes + extraFieldLength;
    }

    ByteBuffer inputBuffer = mappedBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    inputBuffer.position(dataOffset);
    inputBuffer.limit(dataOffset + compressedSize);
    return inputBuffer;
  }

  @Override
  public String toString() {
    return name;
  }
}

final class DirectByteBufferBackedInputStream extends InputStream {
  private ByteBuffer buffer;
  private final boolean isPooled;

  DirectByteBufferBackedInputStream(ByteBuffer buffer, boolean isPooled) {
    this.buffer = buffer;
    this.isPooled = isPooled;
  }

  @Override
  public int read() {
    return buffer.hasRemaining() ? buffer.get() & 0xff : -1;
  }

  @Override
  public int read(byte[] bytes, int offset, int length) {
    if (!buffer.hasRemaining()) {
      return -1;
    }

    int actualLength = Math.min(length, buffer.remaining());
    buffer.get(bytes, offset, actualLength);
    return actualLength;
  }

  @Override
  public byte[] readNBytes(int length) {
    byte[] result = new byte[Math.min(length, buffer.remaining())];
    buffer.get(result);
    return result;
  }

  @Override
  public int readNBytes(byte[] bytes, int offset, int length) {
    int actualLength = Math.min(length, buffer.remaining());
    buffer.get(bytes, offset, actualLength);
    return actualLength;
  }

  @Override
  public int available() {
    return buffer.remaining();
  }

  @Override
  public byte @NotNull [] readAllBytes() {
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);
    return result;
  }

  @Override
  public long skip(long length) {
    int actualLength = Math.min((int)length, buffer.remaining());
    buffer.position(buffer.position() + actualLength);
    return actualLength;
  }

  @Override
  public void close() {
    ByteBuffer buffer = this.buffer;
    if (buffer == null) {
      // already closed
      return;
    }

    this.buffer = null;
    if (isPooled) {
      DirectByteBufferPool.DEFAULT_POOL.release(buffer);
    }
  }
}
