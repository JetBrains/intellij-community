/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author max
 */
@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public class DataInputOutputUtil extends DataInputOutputUtilRt {
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

  /**
   * Writes the given (possibly null) element to the output using the given procedure to write the element if it's not null.
   * Should be coupled with {@link #readNullable}
   */
  public static <T> void writeNullable(@NotNull DataOutput out, @Nullable T value, @NotNull ThrowableConsumer<T, IOException> writeValue)
    throws IOException {
    out.writeBoolean(value != null);
    if (value != null) writeValue.consume(value);
  }

  /**
   * Reads an element from the stream, using the given function to read it when a not-null element is expected, or returns null otherwise.
   * Should be coupled with {@link #writeNullable}
   */
  @Nullable
  public static <T> T readNullable(@NotNull DataInput in, @NotNull ThrowableComputable<T, IOException> readValue) throws IOException {
    return in.readBoolean() ? readValue.compute() : null;
  }
}