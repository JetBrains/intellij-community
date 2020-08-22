// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public final class DataInputOutputUtilRt {
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
      byteBuffer.put((byte)(192 + (val & 0x3F)));
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
  public static <T> void writeSeq(@NotNull DataOutput out,
                                  @NotNull Collection<? extends T> collection,
                                  @SuppressWarnings("BoundedWildcard")
                                  @NotNull ThrowableConsumer<T, IOException> writeElement) throws IOException {
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
  public static <T> List<T> readSeq(@NotNull DataInput in,
                                    @SuppressWarnings("BoundedWildcard")
                                    @NotNull ThrowableComputable<? extends T, IOException> readElement) throws IOException {
    int size = readINT(in);
    List<T> result = new ArrayList<T>(size);
    for (int i = 0; i < size; i++) {
      result.add(readElement.compute());
    }
    return result;
  }

  /**
   * Writes the given map to the output using the given procedure to write each key and value.
   * Should be coupled with {@link #readMap}
   */
  public static <K, V> void writeMap(@NotNull DataOutput out,
                                     @NotNull Map<? extends K, ? extends V> map,
                                     @NotNull ThrowableConsumer<K, ? extends IOException> writeKey,
                                     @NotNull ThrowableConsumer<V, ? extends IOException> writeValue) throws IOException {
    writeINT(out, map.size());
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      writeKey.consume(e.getKey());
      writeValue.consume(e.getValue());
    }
  }

  /**
   * Reads a map using the given function to read each element.
   * Should be coupled with {@link #writeMap}
   */
  @NotNull
  public static <K, V> Map<K, V> readMap(@NotNull DataInput in,
                                         @NotNull ThrowableComputable<? extends K, ? extends IOException> readKey,
                                         @NotNull ThrowableComputable<? extends V, ? extends IOException> readValue) throws IOException {
    int size = readINT(in);
    Map<K, V> result = new HashMap<K, V>();
    for (int i = 0; i < size; i++) {
      result.put(readKey.compute(), readValue.compute());
    }
    return result;
  }
}
