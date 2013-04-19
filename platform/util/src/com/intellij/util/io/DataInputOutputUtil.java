/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author max
 */
public class DataInputOutputUtil {
  public static final long timeBase = 33L * 365L * 24L * 3600L * 1000L;

  private DataInputOutputUtil() {}

  @SuppressWarnings("UnusedDeclaration")
  /** @deprecated obsolete (use {@link IOUtil} methods) (to remove in IDEA 13) */
  public static void skipUTF(@NotNull DataInput record) throws IOException {
    record.skipBytes(record.readUnsignedShort());
  }

  public static StringRef readNAME(@NotNull DataInput record, @NotNull AbstractStringEnumerator nameStore) throws IOException {
    return StringRef.fromStream(record, nameStore);
  }

  public static void writeNAME(@NotNull DataOutput record, @Nullable String name, @NotNull AbstractStringEnumerator nameStore) throws IOException {
    final int nameId = name != null ? nameStore.enumerate(name) : 0;
    writeINT(record, nameId);
  }

  @SuppressWarnings("UnusedDeclaration")
  /** @deprecated use {@linkplain #readNAME(java.io.DataInput, AbstractStringEnumerator)} (to remove in IDEA 13) */
  public static void skipNAME(@NotNull DataInput record) throws IOException {
    readINT(record);
  }

  /** @deprecated use {@linkplain #readINT(java.io.DataInput)} (to remove in IDEA 13) */
  @SuppressWarnings("UnusedDeclaration")
  public static void skipINT(@NotNull DataInput record) throws IOException {
    readINT(record);
  }

  public static int readINT(@NotNull DataInput record) throws IOException {
    final int val = record.readUnsignedByte();
    if (val < 192) {
      return val;
    }

    int res = val - 192;
    for (int sh = 6; ; sh += 7) {
      int next = record.readUnsignedByte();
      res |= (next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  public static void writeINT(@NotNull DataOutput record, int val) throws IOException {
    if (0 <= val && val < 192) {
      record.writeByte(val);
    }
    else {
      record.writeByte(192 + (val & 0x3F));
      val >>>= 6;
      while (val >= 128) {
        record.writeByte((val & 0x7F) | 0x80);
        val >>>= 7;
      }
      record.writeByte(val);
    }
  }

  /** @deprecated use {@linkplain #readSINT(java.io.DataInput)} (to remove in IDEA 13) */
  @SuppressWarnings("UnusedDeclaration")
  public static void skipSINT(@NotNull DataInput record) throws IOException {
    readSINT(record);
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
}