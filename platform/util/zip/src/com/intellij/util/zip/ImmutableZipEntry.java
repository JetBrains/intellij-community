// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.zip;

import com.intellij.util.io.DirectByteBufferPool;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

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

  ImmutableZipEntry(String name, int compressedSize, int uncompressedSize, int headerOffset, int nameLengthInBytes, int method) {
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
        ByteBuffer inputBuffer = null;
        try {
          inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(file.fileChannel, file.fileSize);
          byte[] result = new byte[uncompressedSize];
          inputBuffer.get(result);
          return result;
        }
        finally {
          if (inputBuffer != null) {
            DirectByteBufferPool.DEFAULT_POOL.release(inputBuffer);
          }
        }
      }
      case ZipEntry.DEFLATED: {
        ByteBuffer inputBuffer = null;
        try {
          inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(file.fileChannel, file.fileSize);

          Inflater inflater = new Inflater(true);
          inflater.setInput(inputBuffer);
          int count = uncompressedSize;
          byte[] result = new byte[count];
          int offset = 0;
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
        finally {
          if (inputBuffer != null) {
            DirectByteBufferPool.DEFAULT_POOL.release(inputBuffer);
          }
        }
      }

      default:
        throw new ZipException("Found unsupported compression method " + getMethod());
    }
  }

  /**
   * Returned buffer should be released using {@link DirectByteBufferPool#release(ByteBuffer)}
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
        return computeDataOffsetIfNeededAndReadInputBuffer(file.fileChannel, file.fileSize);
      }
      case ZipEntry.DEFLATED: {
        ByteBuffer inputBuffer = null;
        try {
          inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(file.fileChannel, file.fileSize);

          Inflater inflater = new Inflater(true);
          inflater.setInput(inputBuffer);
          ByteBuffer result;
          result = DirectByteBufferPool.DEFAULT_POOL.allocate(uncompressedSize);
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
        finally {
          if (inputBuffer != null) {
            DirectByteBufferPool.DEFAULT_POOL.release(inputBuffer);
          }
        }
      }

      default:
        throw new ZipException("Found unsupported compression method " + getMethod());
    }
  }

  private @NotNull ByteBuffer computeDataOffsetIfNeededAndReadInputBuffer(FileChannel channel, int fileSize) throws IOException {
    ByteBuffer inputBuffer;
    if (dataOffset == -1) {
      int additionalBytesToRead = 2 /* extra field length */ + nameLengthInBytes +
                                  64 /* assume that extra data will be not more than 64 bytes */;
      inputBuffer = DirectByteBufferPool.DEFAULT_POOL.allocate(compressedSize + additionalBytesToRead);
      inputBuffer.order(ByteOrder.LITTLE_ENDIAN);

      int start = headerOffset + 28;
      doReadInputBuffer(inputBuffer, channel, start, Math.min(start + additionalBytesToRead + compressedSize, fileSize));

      // read actual extra field length
      int extraFieldLength = inputBuffer.getShort(0) & 0xffff;
      if (extraFieldLength > 64) {
        // we can re-read, but for now let's check is it needed or not to implement
        throw new UnsupportedOperationException(
          "extraFieldLength expected to be less than 32 bytes but " + extraFieldLength + " (name=" + name + ")");
      }

      inputBuffer.position(2 + nameLengthInBytes + extraFieldLength);
      inputBuffer.limit(inputBuffer.position() + compressedSize);
      dataOffset = headerOffset + 30 + nameLengthInBytes + extraFieldLength;
      assert inputBuffer.remaining() == compressedSize;
    }
    else {
      inputBuffer = DirectByteBufferPool.DEFAULT_POOL.allocate(compressedSize);
      doReadInputBuffer(inputBuffer, channel, dataOffset, dataOffset + compressedSize);
      inputBuffer.rewind();
    }
    return inputBuffer;
  }

  private static void doReadInputBuffer(ByteBuffer inputBuffer, FileChannel channel, int start, int end) throws IOException {
    int offset = start;
    while (offset < end) {
      int n = channel.read(inputBuffer, offset);
      if (n <= 0) {
        break;
      }

      offset += n;
    }
  }

  @Override
  public String toString() {
    return name;
  }
}
