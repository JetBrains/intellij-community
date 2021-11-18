// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.mvstore.DataUtil;
import org.jetbrains.mvstore.type.DataType;

import java.util.Arrays;

public final class ImageValue {
  final byte[] data;
  final float width;
  final float height;
  final int actualWidth;
  final int actualHeight;

  ImageValue(byte[] data, float width, float height, int actualWidth, int actualHeight) {
    this.data = data;
    this.width = width;
    this.height = height;
    this.actualWidth = actualWidth;
    this.actualHeight = actualHeight;
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
    return Float.compare(value.width, width) == 0 &&
           Float.compare(value.height, height) == 0 &&
           actualWidth == value.actualWidth &&
           actualHeight == value.actualHeight && Arrays.equals(data, value.data);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(data);
    result = 31 * result + (width != 0.0f ? Float.floatToIntBits(width) : 0);
    result = 31 * result + (height != 0.0f ? Float.floatToIntBits(height) : 0);
    result = 31 * result + actualWidth;
    result = 31 * result + actualHeight;
    return result;
  }

  public static final class ImageValueSerializer implements DataType<ImageValue> {
    @Override
    public ImageValue[] createStorage(int size) {
      return new ImageValue[size];
    }

    @Override
    public int getMemory(ImageValue obj) {
      return Float.BYTES * 2 +
             DataUtil.VAR_INT_MAX_SIZE * 2 +
             obj.data.length + 1;
    }

    @Override
    public int getFixedMemory() {
      return -1;
    }

    @Override
    public void write(ByteBuf buf, ImageValue obj) {
      if (obj.width == obj.actualWidth && obj.height == obj.actualHeight) {
        if (obj.height == obj.width) {
          if (obj.actualWidth < 254) {
            buf.writeByte(obj.actualWidth);
          }
          else {
            buf.writeByte(255);
            IntBitPacker.writeVar(buf, obj.actualWidth);
          }
        }
        else {
          buf.writeByte(254);
          IntBitPacker.writeVar(buf, obj.actualWidth);
          IntBitPacker.writeVar(buf, obj.actualHeight);
        }
      }
      else {
        buf.writeByte(0);
        buf.writeFloat(obj.width);
        buf.writeFloat(obj.height);
        IntBitPacker.writeVar(buf, obj.actualWidth);
        IntBitPacker.writeVar(buf, obj.actualHeight);
      }
      buf.writeBytes(obj.data);
    }

    @Override
    public ImageValue read(ByteBuf buf) {
      float width;
      float height;
      int actualWidth;
      int actualHeight;

      short format = buf.readUnsignedByte();
      if (format == 255) {
        actualWidth = IntBitPacker.readVar(buf);
        //noinspection SuspiciousNameCombination
        actualHeight = actualWidth;

        width = actualWidth;
        //noinspection SuspiciousNameCombination
        height = actualWidth;
      }
      else if (format == 254) {
        actualWidth = IntBitPacker.readVar(buf);
        actualHeight = IntBitPacker.readVar(buf);

        width = actualWidth;
        height = actualHeight;
      }
      else if (format != 0) {
        actualWidth = format;
        actualHeight = format;
        width = format;
        height = format;
      }
      else {
        width = buf.readFloat();
        height = buf.readFloat();
        actualWidth = IntBitPacker.readVar(buf);
        actualHeight = IntBitPacker.readVar(buf);
      }

      int length = actualWidth * actualHeight * 4;
      byte[] data = ByteBufUtil.getBytes(buf, buf.readerIndex(), length);
      buf.readerIndex(buf.readerIndex() + length);
      return new ImageValue(data, width, height, actualWidth, actualHeight);
    }
  }
}
