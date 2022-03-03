// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg;

import io.netty.buffer.ByteBuf;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.mvstore.DataUtil;
import org.jetbrains.mvstore.type.DataType;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class ImageValue {
  final int[] data;
  final int w;
  final int h;

  ImageValue(int[] data, int w, int h) {
    this.data = data;
    this.w = w;
    this.h = h;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ImageValue value = (ImageValue)o;
    return w == value.w && h == value.h && Arrays.equals(data, value.data);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(data);
    result = 31 * result + w;
    result = 31 * result + h;
    return result;
  }

  public static final class ImageValueSerializer implements DataType<ImageValue> {
    @Override
    public ImageValue[] createStorage(int size) {
      return new ImageValue[size];
    }

    @Override
    public int getMemory(ImageValue obj) {
      return DataUtil.VAR_INT_MAX_SIZE * 2 + (obj.data.length * Integer.BYTES) + 1;
    }

    @Override
    public int getFixedMemory() {
      return -1;
    }

    @Override
    public void write(ByteBuf buf, ImageValue obj) {
      if (obj.w == obj.h) {
        if (obj.w < 254) {
          buf.writeByte(obj.w);
        }
        else {
          buf.writeByte(255);
          IntBitPacker.writeVar(buf, obj.w);
        }
      }
      else {
        buf.writeByte(254);
        IntBitPacker.writeVar(buf, obj.w);
        IntBitPacker.writeVar(buf, obj.h);
      }
      for (int i : obj.data) {
        buf.writeInt(i);
      }
    }

    @Override
    public ImageValue read(ByteBuf buf) {
      int actualWidth;
      int actualHeight;

      short format = buf.readUnsignedByte();
      if (format < 254) {
        actualWidth = format;
        actualHeight = format;
      }
      else if (format == 255) {
        actualWidth = IntBitPacker.readVar(buf);
        //noinspection SuspiciousNameCombination
        actualHeight = actualWidth;
      }
      else {
        actualWidth = IntBitPacker.readVar(buf);
        actualHeight = IntBitPacker.readVar(buf);
      }

      int length = actualWidth * actualHeight;
      int[] data = new int[length];
      int lengthInBytes = length << 2;
      // must be big endian order - do not use little endian here (ARGB is expected)
      ByteBuffer nioBuf = DataUtil.getNioBuffer(buf, buf.readerIndex(), lengthInBytes);
      nioBuf.asIntBuffer().get(data, 0, length);
      buf.readerIndex(buf.readerIndex() + lengthInBytes);
      return new ImageValue(data, actualWidth, actualHeight);
    }
  }
}
