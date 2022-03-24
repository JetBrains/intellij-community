// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

@ApiStatus.Internal
public final class ImmutableZipEntry {
  final static byte STORED = 0;
  final static byte DEFLATED = 8;

  final int uncompressedSize;
  final int compressedSize;
  private final byte method;

  final String name;

  // headerOffset and nameLengthInBytes
  private final long offsets;
  // we cannot compute dataOffset in advance because there is incorrect ZIP files where extra data specified for entry in central directory,
  // but not in local file header
  private int dataOffset = -1;

  ImmutableZipEntry(String name, int compressedSize, int uncompressedSize, int headerOffset, int nameLengthInBytes, byte method) {
    this.name = name;
    this.offsets = (((long)headerOffset) << 32) | (nameLengthInBytes & 0xffffffffL);
    this.compressedSize = compressedSize;
    this.uncompressedSize = uncompressedSize;
    this.method = method;
  }

  ImmutableZipEntry(String name, int compressedSize, int uncompressedSize, byte method) {
    this.name = name;
    this.offsets = 0;
    this.compressedSize = compressedSize;
    this.uncompressedSize = uncompressedSize;
    this.method = method;
  }

  public boolean isCompressed() {
    return method != STORED;
  }

  void setDataOffset(int dataOffset) {
    this.dataOffset = dataOffset;
  }

  /**
   * Get the name of the entry.
   */
  public String getName() {
    return name;
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

  public byte[] getData(@NotNull HashMapZipFile file) throws IOException {
    if (uncompressedSize < 0) {
      throw new IOException("no data");
    }

    if (file.fileSize < (dataOffset + compressedSize)) {
      throw new EOFException();
    }

    switch (method) {
      case STORED: {
        ByteBuffer inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(file.mappedBuffer);
        byte[] result = new byte[uncompressedSize];
        inputBuffer.get(result);
        return result;
      }
      case DEFLATED: {
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
        finally {
          inflater.end();
        }
      }

      default:
        throw new ZipException("Found unsupported compression method " + method);
    }
  }

  @ApiStatus.Internal
  public InputStream getInputStream(@NotNull HashMapZipFile file) throws IOException {
    return new DirectByteBufferBackedInputStream(getByteBuffer(file), method == DEFLATED);
  }

  /**
   * Release returned buffer using {@link #releaseBuffer} after use.
   */
  @ApiStatus.Internal
  public ByteBuffer getByteBuffer(@NotNull HashMapZipFile file) throws IOException {
    if (uncompressedSize < 0) {
      throw new IOException("no data");
    }

    if (file.fileSize < (dataOffset + compressedSize)) {
      throw new EOFException();
    }

    switch (method) {
      case STORED: {
        return computeDataOffsetIfNeededAndReadInputBuffer(file.mappedBuffer);
      }
      case DEFLATED: {
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
        finally {
          inflater.end();
        }
      }

      default:
        throw new ZipException("Found unsupported compression method " + method);
    }
  }

  private @NotNull ByteBuffer computeDataOffsetIfNeededAndReadInputBuffer(ByteBuffer mappedBuffer) {
    int dataOffset = this.dataOffset;
    if (dataOffset == -1) {
      dataOffset = computeDataOffset(mappedBuffer);
    }

    ByteBuffer inputBuffer = mappedBuffer.asReadOnlyBuffer();
    inputBuffer.position(dataOffset);
    inputBuffer.limit(dataOffset + compressedSize);
    return inputBuffer;
  }

  private int computeDataOffset(ByteBuffer mappedBuffer) {
    int headerOffset = (int)(offsets >> 32);
    int start = headerOffset + 28;
    // read actual extra field length
    int extraFieldLength = mappedBuffer.getShort(start) & 0xffff;
    if (extraFieldLength > 128) {
      // assert just to be sure that we don't read a lot of data in case of some error in zip file or our impl
      throw new UnsupportedOperationException(
        "extraFieldLength expected to be less than 128 bytes but " + extraFieldLength + " (name=" + name + ")");
    }

    int nameLengthInBytes = (int)offsets;
    int result = start + 2 + nameLengthInBytes + extraFieldLength;
    dataOffset = result;
    return result;
  }

  @Override
  public String toString() {
    return name;
  }
}