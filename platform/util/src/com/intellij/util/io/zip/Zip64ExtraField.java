// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.zip;

import org.jetbrains.annotations.NotNull;

import java.util.zip.ZipException;

import static com.intellij.util.io.zip.JBZipFile.DWORD;

public class Zip64ExtraField implements JBZipExtraField {
  static final ZipShort HEADER_ID = new ZipShort(0x0001);

  private static final byte[] EMPTY = new byte[0];

  private ZipUInt64 mySize;
  private ZipUInt64 myCompressedSize;
  private ZipUInt64 myHeaderOffset;

  Zip64ExtraField() { }

  public Zip64ExtraField(final ZipUInt64 size,
                         final ZipUInt64 compressedSize,
                         final ZipUInt64 headerOffset) {
    this.mySize = size;
    this.myCompressedSize = compressedSize;
    this.myHeaderOffset = headerOffset;
  }

  @Override
  public @NotNull ZipShort getHeaderId() {
    return HEADER_ID;
  }

  @Override
  public @NotNull ZipShort getLocalFileDataLength() {
    return new ZipShort(mySize != null ? 2 * DWORD : 0);
  }

  @Override
  public @NotNull ZipShort getCentralDirectoryLength() {
    return new ZipShort((mySize != null ? DWORD : 0)
                        + (myCompressedSize != null ? DWORD : 0)
                        + (myHeaderOffset != null ? DWORD : 0));
  }

  @Override
  public byte @NotNull [] getLocalFileDataData() {
    if (mySize != null || myCompressedSize != null) {
      if (mySize == null || myCompressedSize == null) {
        throw new IllegalArgumentException("Must contain both size values in the local file header");
      }
      final byte[] data = new byte[2 * DWORD];
      addSizes(data);
      return data;
    }
    return EMPTY;
  }

  @Override
  public byte @NotNull [] getCentralDirectoryData() {
    final byte[] data = new byte[getCentralDirectoryLength().getValue()];
    int off = addSizes(data);
    if (myHeaderOffset != null) {
      System.arraycopy(myHeaderOffset.getBytes(), 0, data, off, DWORD);
    }
    return data;
  }

  @Override
  public void parseFromLocalFileData(final byte @NotNull [] buffer, int offset, final int length)
    throws ZipException {
    if (length == 0) {
      return;
    }
    if (length < 2 * DWORD) {
      throw new ZipException("Must contain both size values in the local file header.");
    }
    mySize = new ZipUInt64(buffer, offset);
    offset += DWORD;
    myCompressedSize = new ZipUInt64(buffer, offset);
    offset += DWORD;
    int remaining = length - 2 * DWORD;
    if (remaining >= DWORD) {
      myHeaderOffset = new ZipUInt64(buffer, offset);
    }
  }

  @Override
  public void parseFromCentralDirectoryData(final byte @NotNull [] buffer, int offset,
                                            final int length)
    throws ZipException {
    mySize = new ZipUInt64(buffer, offset);
    offset += DWORD;
    myCompressedSize = new ZipUInt64(buffer, offset);
    offset += DWORD;
    myHeaderOffset = new ZipUInt64(buffer, offset);
  }
  /**
   * The uncompressed size stored in this extra field.
   * @return The uncompressed size stored in this extra field.
   */
  public ZipUInt64 getSize() {
    return mySize;
  }

  /**
   * The compressed size stored in this extra field.
   * @return The compressed size stored in this extra field.
   */
  public ZipUInt64 getCompressedSize() {
    return myCompressedSize;
  }

  /**
   * The relative header offset stored in this extra field.
   * @return The relative header offset stored in this extra field.
   */
  public ZipUInt64 getHeaderOffset() {
    return myHeaderOffset;
  }

  private int addSizes(final byte[] data) {
    int off = 0;
    if (mySize != null) {
      System.arraycopy(mySize.getBytes(), 0, data, 0, DWORD);
      off += DWORD;
    }
    if (myCompressedSize != null) {
      System.arraycopy(myCompressedSize.getBytes(), 0, data, off, DWORD);
      off += DWORD;
    }
    return off;
  }
}
