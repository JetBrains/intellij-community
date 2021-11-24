/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.integratedBinaryPacking.IntegratedBinaryPacking;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class DataUtil {
  /**
   * The maximum length of a variable size int.
   */
  public static final int VAR_INT_MAX_SIZE = 5;
  /**
   * The maximum length of a variable size long.
   */
  public static final int VAR_LONG_MAX_SIZE = 9;

  /**
   * The type for leaf page.
   */
  static final int PAGE_TYPE_LEAF = 0;

  /**
   * The marker size of a very large page.
   */
  static final int PAGE_LARGE = 2 * 1024 * 1024;

  public static void writeString(ByteBuf buf, String s) {
    assert s.length() <= 8_388_607;
    int lengthIndex = buf.writerIndex();
    // netty also calls utf8MaxBytes, but setting writerIndex without ensureWritable
    // can lead to error if capacity is not yet adjusted
    buf.ensureWritable(4 + ByteBufUtil.utf8MaxBytes(s.length()));
    buf.writerIndex(lengthIndex + 3);
    buf.writeCharSequence(s, StandardCharsets.UTF_8);
    buf.setMedium(lengthIndex, buf.writerIndex() - lengthIndex - 3);
  }

  /**
   * Read a string.
   *
   * @param buf the source buffer
   * @return the value
   */
  public static String readString(ByteBuf buf) {
    int length = buf.readUnsignedMedium();
    int readerIndex = buf.readerIndex();
    String result = buf.toString(readerIndex, length, StandardCharsets.UTF_8);
    buf.readerIndex(readerIndex + length);
    return result;
  }

  /**
   * Copy the elements of an array, with a gap.
   *
   * @param src      the source array
   * @param dst      the target array
   * @param oldSize  the size of the old array
   * @param gapIndex the index of the gap
   */
  @SuppressWarnings("SuspiciousSystemArraycopy")
  public static void copyWithGap(Object src, Object dst, int oldSize, int gapIndex) {
    if (gapIndex > 0) {
      System.arraycopy(src, 0, dst, 0, gapIndex);
    }
    if (gapIndex < oldSize) {
      System.arraycopy(src, gapIndex, dst, gapIndex + 1, oldSize - gapIndex);
    }
  }

  /**
   * Copy the elements of an array, and remove one element.
   *
   * @param src         the source array
   * @param dst         the target array
   * @param oldSize     the size of the old array
   * @param removeIndex the index of the entry to remove
   */
  @SuppressWarnings("SuspiciousSystemArraycopy")
  public static void copyExcept(Object src, Object dst, int oldSize, int removeIndex) {
    if (removeIndex > 0 && oldSize > 0) {
      System.arraycopy(src, 0, dst, 0, removeIndex);
    }
    if (removeIndex < oldSize) {
      System.arraycopy(src, removeIndex + 1, dst, removeIndex, oldSize - removeIndex - 1);
    }
  }

  static void readFully(FileChannel file, long position, int length, ByteBuf out, Path filePath) {
    try {
      do {
        int n = out.writeBytes(file, position, length);
        if (n < 0) {
          throw new MVStoreException(MVStoreException.ERROR_READING_FAILED,
                                     "End of file " + filePath + " (position=" + position +
                                     ", length " + getFileSizeOrErrorMessage(file) +
                                     ", read=" + out.writerIndex() +
                                     ", remaining=" + out.writableBytes() +
                                     ")");
        }
        position += n;
        length -= n;
      }
      while (length > 0);
    }
    catch (IOException e) {
      throw new MVStoreException(MVStoreException.ERROR_READING_FAILED,
                                 "Reading from file " +
                                 filePath +
                                 " failed at " +
                                 position +
                                 " (length " +
                                 getFileSizeOrErrorMessage(file) +
                                 "), " +
                                 "read " +
                                 out.writerIndex() +
                                 ", remaining " +
                                 out.writableBytes(), e);
    }
  }

  private static String getFileSizeOrErrorMessage(FileChannel file) {
    String size;
    try {
      size = Long.toString(file.size());
    }
    catch (IOException e2) {
      size = e2.getMessage();
    }
    return size;
  }

  /**
   * Write to a file channel.
   *
   * @param file     the file channel
   * @param position the absolute position within the file
   * @param in       the source buffer
   */
  static void writeFully(FileChannel file, long position, ByteBuf in) {
    try {
      int length = in.readableBytes();
      do {
        int n = in.readBytes(file, position, length);
        position += n;
        length -= n;
      }
      while (length > 0);
    }
    catch (IOException e) {
      throw new MVStoreException(MVStoreException.ERROR_WRITING_FAILED,
                                 "Writing to " + file + " failed; length " + in.readableBytes() + " at " + position, e);
    }
  }

  /**
   * Convert the length to a length code 0..31. 31 means more than 1 MB.
   *
   * @param len the length
   * @return the length code
   */
  private static int encodeLength(int len) {
    if (len <= 32) {
      return 0;
    }
    int code = Integer.numberOfLeadingZeros(len);
    int remaining = len << (code + 1);
    code += code;
    if ((remaining & (1 << 31)) != 0) {
      code--;
    }
    if ((remaining << 1) != 0) {
      code--;
    }
    code = Math.min(31, 52 - code);
    return code;
  }

  /**
   * Get the chunk id from the position.
   *
   * @param pos the position
   * @return the chunk id
   */
  static int getPageChunkId(long pos) {
    return (int)(pos >>> 38);
  }

  /**
   * Get the map id from the chunk's table of content element.
   *
   * @param tocElement packed table of content element
   * @return the map id
   */
  static int getPageMapId(long tocElement) {
    return (int)(tocElement >>> 38);
  }

  /**
   * Get the maximum length for the given page position.
   *
   * @param pos the position
   * @return the maximum length
   */
  public static int getPageMaxLength(long pos) {
    int code = (int)((pos >> 1) & 31);
    if (code == 31) {
      return PAGE_LARGE;
    }
    return (2 + (code & 1)) << ((code >> 1) + 4);
  }

  /**
   * Get the offset from the position.
   *
   * @param tocElement packed table of content element
   * @return the offset
   */
  public static int getPageOffset(long tocElement) {
    return (int)(tocElement >> 6);
  }

  /**
   * Get the page type from the info.
   *
   * @param info the info
   * @return the page type (PAGE_TYPE_NODE or PAGE_TYPE_LEAF)
   */
  static int getPageType(long info) {
    return ((int)info) & 1;
  }

  /**
   * Determines whether specified file position corresponds to a leaf page
   *
   * @param info the info
   * @return true if it is a leaf, false otherwise
   */
  static boolean isLeafPage(long info) {
    return getPageType(info) == PAGE_TYPE_LEAF;
  }

  /**
   * Find out if page was saved.
   *
   * @param pos the position
   * @return true if page has been saved
   */
  static boolean isPageSaved(long pos) {
    return (pos & ~1L) != 0;
  }

  /**
   * Find out if page was removed.
   *
   * @param pos the position
   * @return true if page has been removed (no longer accessible from the
   * current root of the tree)
   */
  static boolean isPageRemoved(long pos) {
    return pos == 1L;
  }

  /**
   * Get the position of this page. The following information is encoded in
   * the position: the chunk id, the page sequential number, the maximum length, and the type
   * (node or leaf).
   *
   * @param chunkId the chunk id
   * @param offset  the offset
   * @param length  the length
   * @param type    the page type (1 for node, 0 for leaf)
   * @return the position
   */
  public static long getPageInfo(int chunkId, int offset, int length, int type) {
    long pos = (long)chunkId << 38;
    pos |= (long)offset << 6;
    pos |= (long)encodeLength(length) << 1;
    pos |= type;
    return pos;
  }

  /**
   * Convert tocElement into pagePos by replacing mapId with chunkId.
   *
   * @param chunkId    the chunk id
   * @param tocElement the element
   * @return the page position
   */
  static long getPageInfo(int chunkId, long tocElement) {
    return (tocElement & 0x3FFFFFFFFFL) | ((long)chunkId << 38);
  }

  /**
   * Create table of content element. The following information is encoded in it:
   * the map id, the page offset, the maximum length, and the type
   * (node or leaf).
   *
   * @param mapId  the chunk id
   * @param offset the offset
   * @param length the length
   * @param type   the page type (1 for node, 0 for leaf)
   * @return the position
   */
  static long getTocElement(int mapId, int offset, int length, int type) {
    long pos = (long)mapId << 38;
    pos |= (long)offset << 6;
    pos |= (long)encodeLength(length) << 1;
    pos |= type;
    return pos;
  }

  /**
   * Calculate a check value for the given integer. A check value is mean to
   * verify the data is consistent with a high probability, but not meant to
   * protect against media failure or deliberate changes.
   *
   * @param x the value
   * @return the check value
   */
  static short getCheckValue(int x) {
    return (short)((x >> 16) ^ x);
  }

  static int getFletcher32(ByteBuf buf, int offset, int length) {
    int s1 = 0xffff, s2 = 0xffff;
    int i = offset, len = offset + (length & ~1);
    while (i < len) {
      // reduce after 360 words (each word is two bytes)
      for (int end = Math.min(i + 720, len); i < end; ) {
        int x = ((buf.getByte(i++) & 0xff) << 8) | (buf.getByte(i++) & 0xff);
        s2 += s1 += x;
      }
      s1 = (s1 & 0xffff) + (s1 >>> 16);
      s2 = (s2 & 0xffff) + (s2 >>> 16);
    }
    if ((length & 1) != 0) {
      // odd length: append 0
      int x = (buf.getByte(i) & 0xff) << 8;
      s2 += s1 += x;
    }
    s1 = (s1 & 0xffff) + (s1 >>> 16);
    s2 = (s2 & 0xffff) + (s2 >>> 16);
    return (s2 << 16) | s1;
  }

  public static byte[] readByteArray(ByteBuf buf) {
    int length = IntBitPacker.readVar(buf);
    int readerIndex = buf.readerIndex();
    byte[] data = ByteBufUtil.getBytes(buf, readerIndex, length);
    buf.readerIndex(readerIndex + length);
    return data;
  }

  public static void writeByteArray(ByteBuf buf, byte[] value) {
    IntBitPacker.writeVar(buf, value.length);
    buf.writeBytes(value);
  }

  /**
   * Round the value up to the next block size. The block size must be a power
   * of two. As an example, using the block size of 8, the following rounding
   * operations are done: 0 stays 0; values 1..8 results in 8, 9..16 results
   * in 16, and so on.
   *
   * @param x                 the value to be rounded
   * @param blockSizePowerOf2 the block size
   * @return the rounded value
   */
  static int roundUpInt(int x, int blockSizePowerOf2) {
    return (x + blockSizePowerOf2 - 1) & (-blockSizePowerOf2);
  }

  public static ByteBuffer getNioBuffer(ByteBuf buf, int index, int length) {
    int nioBufferCount = buf.nioBufferCount();
    assert nioBufferCount > 0;
    return nioBufferCount == 1 ? buf.internalNioBuffer(index, length)
                               : buf.nioBuffer(index, length);
  }

  public static void writeLongArray(long[] values, ByteBuf buf, int length) {
    if (length < 4) {
      for (int i = 0; i < length; i++) {
        buf.writeLongLE(values[i]);
      }
      return;
    }

    int lengthInBytes = length << 3;
    // use LITTLE_ENDIAN because both Apple silicon and Intel-based computers use the little-endian format for data
    ByteBuffer nioBuf = getNioBuffer(buf, buf.writerIndex(), lengthInBytes).order(ByteOrder.LITTLE_ENDIAN);
    nioBuf.asLongBuffer().put(values, 0, length);
    nioBuf.order(ByteOrder.BIG_ENDIAN);
    buf.writerIndex(buf.writerIndex() + lengthInBytes);
  }

  public static void writeIntArray(int[] values, ByteBuf buf, int length) {
    if (length < 4) {
      for (int i = 0; i < length; i++) {
        int value = values[i];
        buf.writeIntLE(value);
      }
      return;
    }

    int lengthInBytes = length << 2;
    // use LITTLE_ENDIAN because both Apple silicon and Intel-based computers use the little-endian format for data
    ByteBuffer nioBuf = getNioBuffer(buf, buf.writerIndex(), lengthInBytes).order(ByteOrder.LITTLE_ENDIAN);
    nioBuf.asIntBuffer().put(values, 0, length);
    nioBuf.order(ByteOrder.BIG_ENDIAN);
    buf.writerIndex(buf.writerIndex() + lengthInBytes);
  }

  public static void readLongArray(long[] values, ByteBuf buf, int length) {
    if (length < 4) {
      for (int i = 0; i < length; i++) {
        values[i] = buf.readLongLE();
      }
      return;
    }

    int lengthInBytes = length << 3;
    ByteBuffer nioBuf = getNioBuffer(buf, buf.readerIndex(), lengthInBytes).order(ByteOrder.LITTLE_ENDIAN);
    nioBuf.asLongBuffer().get(values, 0, length);
    nioBuf.order(ByteOrder.BIG_ENDIAN);
    buf.readerIndex(buf.readerIndex() + lengthInBytes);
  }

  public static void readIntArray(int[] values, ByteBuf buf, int length) {
    if (length < 4) {
      for (int i = 0; i < length; i++) {
        values[i] = buf.readIntLE();
      }
      return;
    }

    int lengthInBytes = length << 2;
    ByteBuffer nioBuf = getNioBuffer(buf, buf.readerIndex(), lengthInBytes).order(ByteOrder.LITTLE_ENDIAN);
    nioBuf.asIntBuffer().get(values, 0, length);
    nioBuf.order(ByteOrder.BIG_ENDIAN);
    buf.readerIndex(buf.readerIndex() + lengthInBytes);
  }

  public static void packInts(int[] values, ByteBuf buf) {
    int count = values.length;
    int variableEncodingLength = count % IntegratedBinaryPacking.INT_BLOCK_SIZE;
    int initValue;
    if (variableEncodingLength == 0) {
      initValue = 0;
    }
    else {
      IntBitPacker.compressVariable(values, 0, variableEncodingLength, buf);
      if (count == variableEncodingLength) {
        return;
      }

      initValue = values[variableEncodingLength - 1];
    }

    int[] compressed = new int[IntegratedBinaryPacking.estimateCompressedArrayLength(values, variableEncodingLength, count, initValue)];
    int compressedArrayLength = IntBitPacker.compressIntegrated(values, variableEncodingLength, count, compressed, initValue);
    IntBitPacker.writeVar(buf, compressedArrayLength);
    writeIntArray(compressed, buf, compressedArrayLength);
  }

  public static void unpackInts(int[] values, ByteBuf buf) {
    int count = values.length;
    int variableEncodingLength = count % IntegratedBinaryPacking.INT_BLOCK_SIZE;
    int initValue;
    if (variableEncodingLength == 0) {
      initValue = 0;
    }
    else {
      IntBitPacker.decompressVariable(buf, values, variableEncodingLength);
      initValue = values[variableEncodingLength - 1];
      if (variableEncodingLength == count) {
        return;
      }
    }

    int compressedArrayLength = IntBitPacker.readVar(buf);
    int[] compressed = new int[compressedArrayLength];
    readIntArray(compressed, buf, compressedArrayLength);
    IntBitPacker.decompressIntegrated(compressed, 0, values, variableEncodingLength, count, initValue);
  }
}
