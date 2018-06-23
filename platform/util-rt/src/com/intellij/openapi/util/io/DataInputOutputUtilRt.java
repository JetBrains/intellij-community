/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DataInputOutputUtilRt {
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

  public static int readINT(@NotNull ByteBuffer byteBuffer) {
    final int val = byteBuffer.get() & 0xFF;
    if (val < 192) {
      return val;
    }

    int res = val - 192;
    for (int sh = 6; ; sh += 7) {
      int next = byteBuffer.get() & 0xFF;
      res |= (next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  public static void writeINT(@NotNull DataOutput record, int val) throws IOException {
    if (0 > val || val >= 192) {
      record.writeByte(192 + (val & 0x3F));
      val >>>= 6;
      while (val >= 128) {
        record.writeByte((val & 0x7F) | 0x80);
        val >>>= 7;
      }
    }
    record.writeByte(val);
  }

  public static void writeINT(@NotNull ByteBuffer byteBuffer, int val) {
    if (0 > val || val >= 192) {
      byteBuffer.put( (byte)(192 + (val & 0x3F)));
      val >>>= 6;
      while (val >= 128) {
        byteBuffer.put((byte)((val & 0x7F) | 0x80));
        val >>>= 7;
      }
    }
    byteBuffer.put((byte)val);
  }

  /**
   * Writes the given collection to the output using the given procedure to write each element.
   * Should be coupled with {@link #readSeq}
   */
  public static <T> void writeSeq(@NotNull DataOutput out, @NotNull Collection<T> collection, @NotNull ThrowableConsumer<T, IOException> writeElement) throws IOException {
    writeINT(out, collection.size());
    for (T t : collection) {
      writeElement.consume(t);
    }
  }

  /**
   * Reads a collection using the given function to read each element.
   * Should be coupled with {@link #writeSeq}
   */
  @NotNull
  public static <T> List<T> readSeq(@NotNull DataInput in, @NotNull ThrowableComputable<T, IOException> readElement) throws IOException {
    int size = readINT(in);
    List<T> result = new ArrayList<T>(size);
    for (int i = 0; i < size; i++) {
      result.add(readElement.compute());
    }
    return result;
  }

}
