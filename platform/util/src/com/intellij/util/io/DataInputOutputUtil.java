// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.ThrowableConsumer;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

public final class DataInputOutputUtil {
  public static final long timeBase = 33L * 365L * 24L * 3600L * 1000L;

  private DataInputOutputUtil() { }

  public static int readINT(@NotNull DataInput record) throws IOException {
    return DataInputOutputUtilRt.readINT(record);
  }

  public static int readINT(@NotNull ByteBuffer byteBuffer) {
    return DataInputOutputUtilRt.readINT(byteBuffer);
  }

  public static void writeINT(@NotNull DataOutput record, int val) throws IOException {
    DataInputOutputUtilRt.writeINT(record, val);
  }

  public static void writeINT(@NotNull ByteBuffer byteBuffer, int val) {
    DataInputOutputUtilRt.writeINT(byteBuffer, val);
  }

  public static long readLONG(@NotNull DataInput record) throws IOException {
    final int val = record.readUnsignedByte();
    if (val < 192) {
      return val;
    }

    long res = val - 192;
    for (int sh = 6; ; sh += 7) {
      int next = record.readUnsignedByte();
      res |= (long)(next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  public static long readLONG(@NotNull ByteBuffer record) throws IOException {
    final int val = record.get() & 0xFF;
    if (val < 192) {
      return val;
    }

    long res = val - 192;
    for (int sh = 6; ; sh += 7) {
      int next = record.get() & 0xFF;
      res |= (long)(next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  public static void writeLONG(@NotNull DataOutput record, long val) throws IOException {
    if (0 > val || val >= 192) {
      record.writeByte(192 + (int)(val & 0x3F));
      val >>>= 6;
      while (val >= 128) {
        record.writeByte((int)(val & 0x7F) | 0x80);
        val >>>= 7;
      }
    }
    record.writeByte((int)val);
  }

  public static int readSINT(@NotNull DataInput record) throws IOException {
    return readINT(record) - 64;
  }

  public static void writeSINT(@NotNull DataOutput record, int val) throws IOException {
    writeINT(record, val + 64);
  }

  public static void writeTIME(@NotNull DataOutput record, long timestamp) throws IOException {
    long relStamp = timestamp - timeBase;
    if (relStamp < 0 || relStamp >= 0xFF00000000L) {
      record.writeByte(255);
      record.writeLong(timestamp);
    }
    else {
      record.writeByte((int)(relStamp >> 32));
      record.writeByte((int)(relStamp >> 24));
      record.writeByte((int)(relStamp >> 16));
      record.writeByte((int)(relStamp >> 8));
      record.writeByte((int)(relStamp));
    }
  }

  public static void writeTIME(@NotNull ByteBuffer buffer, long timestamp) {
    long relStamp = timestamp - timeBase;
    if (relStamp < 0 || relStamp >= 0xFF00000000L) {
      buffer.put((byte)255);
      buffer.putLong(timestamp);
    }
    else {
      buffer.put((byte)(relStamp >> 32));
      buffer.put((byte)(relStamp >> 24));
      buffer.put((byte)(relStamp >> 16));
      buffer.put((byte)(relStamp >> 8));
      buffer.put((byte)(relStamp));
    }
  }

  public static long readTIME(@NotNull DataInput record) throws IOException {
    final int first = record.readUnsignedByte();
    if (first == 255) {
      return record.readLong();
    }
    else {
      final int second = record.readUnsignedByte();

      final int third = record.readUnsignedByte() << 16;
      final int fourth = record.readUnsignedByte() << 8;
      final int fifth = record.readUnsignedByte();
      return ((((long)((first << 8) | second)) << 24) | (third | fourth | fifth)) + timeBase;
    }
  }

  public static long readTIME(@NotNull ByteBuffer buffer) {
    final int first = Byte.toUnsignedInt(buffer.get());
    if (first == 0xFF) {
      return buffer.getLong();
    }
    else {
      final int second = Byte.toUnsignedInt(buffer.get());

      final int third = Byte.toUnsignedInt(buffer.get()) << 16;
      final int fourth = Byte.toUnsignedInt(buffer.get()) << 8;
      final int fifth = Byte.toUnsignedInt(buffer.get());
      return ((((long)((first << 8) | second)) << 24) | (third | fourth | fifth)) + timeBase;
    }
  }

  /**
   * Writes the given (possibly null) element to the output using the given procedure to write the element if it's not null.
   * Should be coupled with {@link #readNullable}
   */
  public static <T> void writeNullable(@NotNull DataOutput out,
                                       @Nullable T value,
                                       @NotNull ThrowableConsumer<? super T, ? extends IOException> writeValue)
    throws IOException {
    out.writeBoolean(value != null);
    if (value != null) writeValue.consume(value);
  }

  /**
   * Reads an element from the stream, using the given function to read it when a not-null element is expected, or returns null otherwise.
   * Should be coupled with {@link #writeNullable}
   */
  public static @Nullable <T> T readNullable(@NotNull DataInput in,
                                             @NotNull ThrowableComputable<? extends T, ? extends IOException> readValue)
    throws IOException {
    return in.readBoolean() ? readValue.compute() : null;
  }

  public static @NotNull <T> List<T> readSeq(@NotNull DataInput in,
                                             @NotNull ThrowableComputable<? extends T, IOException> readElement) throws IOException {
    return DataInputOutputUtilRt.readSeq(in, readElement);
  }

  public static <T> void writeSeq(@NotNull DataOutput out,
                                  @NotNull Collection<? extends T> collection,
                                  @NotNull ThrowableConsumer<T, IOException> writeElement) throws IOException {
    DataInputOutputUtilRt.writeSeq(out, collection, writeElement);
  }

  /**
   * Method writes an array of integers in diff-compressed form, i.e. each element is written as difference from previous one.
   * Size of array (=length) is written first.
   * Values are written as varint (see {@link #writeLONG(DataOutput, long)} and {@link #writeINT(DataOutput, int)} methods),
   * which saves space by compressing smaller values. If values in the array are +/- sorted, the diffs are typically quite
   * small, and savings are quite significant.
   *
   * BEWARE: the array is NOT sorted inside the method -- if you want an effective compression, sort the array before calling this method.
   */
  @ApiStatus.Internal
  public static void writeDiffCompressed(@NotNull DataOutput out,
                                         int[] arrayPreferablySorted,
                                         int length) throws IOException {
    writeINT(out, length);
    int previousItem = 0;
    for (int i = 0; i < length; ++i) {
      int currentItem = arrayPreferablySorted[i];
      writeLONG(out, (long)currentItem - previousItem);
      previousItem = currentItem;
    }
  }

  /** @see #writeDiffCompressed(DataOutput, int[], int) */
  @ApiStatus.Internal
  public static void writeDiffCompressed(@NotNull DataOutput out,
                                         @NotNull IntList dataPreferablySorted) throws IOException {
    int length = dataPreferablySorted.size();
    writeINT(out, length);
    int previousItem = 0;
    for (int i = 0; i < length; ++i) {
      int currentItem = dataPreferablySorted.getInt(i);
      writeLONG(out, (long)currentItem - previousItem);
      previousItem = currentItem;
    }
  }
}