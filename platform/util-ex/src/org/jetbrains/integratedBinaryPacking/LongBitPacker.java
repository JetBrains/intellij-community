// Copyright Daniel Lemire, http://lemire.me/en/ Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.integratedBinaryPacking;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;

public final class LongBitPacker {
  public static int compressIntegrated(final long[] in, int startIndex, final int endIndex,
      final long[] out, long initValue) {
    int tmpOutPos = 0;
    for (; startIndex + 448 < endIndex; startIndex += 512) {
      final long mBits1 = maxDiffBits(initValue, in, startIndex);
      final long initOffset2 = in[startIndex + 63];
      final long mBits2 = maxDiffBits(initOffset2, in, startIndex + 64);
      final long initOffset3 = in[startIndex + 127];
      final long mBits3 = maxDiffBits(initOffset3, in, startIndex + 128);
      final long initOffset4 = in[startIndex + 191];
      final long mBits4 = maxDiffBits(initOffset4, in, startIndex + 192);
      final long initOffset5 = in[startIndex + 255];
      final long mBits5 = maxDiffBits(initOffset5, in, startIndex + 256);
      final long initOffset6 = in[startIndex + 319];
      final long mBits6 = maxDiffBits(initOffset6, in, startIndex + 320);
      final long initOffset7 = in[startIndex + 383];
      final long mBits7 = maxDiffBits(initOffset7, in, startIndex + 384);
      final long initOffset8 = in[startIndex + 447];
      final long mBits8 = maxDiffBits(initOffset8, in, startIndex + 448);
      out[tmpOutPos++] = mBits1 << 56 | mBits2 << 48 | mBits3 << 40 | mBits4 << 32 | mBits5 << 24 | mBits6 << 16 | mBits7 << 8 | mBits8;
      pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
      tmpOutPos += mBits1;
      pack(initOffset2, in, startIndex + 64, out, tmpOutPos, mBits2);
      tmpOutPos += mBits2;
      pack(initOffset3, in, startIndex + 128, out, tmpOutPos, mBits3);
      tmpOutPos += mBits3;
      pack(initOffset4, in, startIndex + 192, out, tmpOutPos, mBits4);
      tmpOutPos += mBits4;
      pack(initOffset5, in, startIndex + 256, out, tmpOutPos, mBits5);
      tmpOutPos += mBits5;
      pack(initOffset6, in, startIndex + 320, out, tmpOutPos, mBits6);
      tmpOutPos += mBits6;
      pack(initOffset7, in, startIndex + 384, out, tmpOutPos, mBits7);
      tmpOutPos += mBits7;
      pack(initOffset8, in, startIndex + 448, out, tmpOutPos, mBits8);
      tmpOutPos += mBits8;
      initValue = in[startIndex + 511];
    }
    switch (endIndex - startIndex) {
      case 64: {
        final long mBits1 = maxDiffBits(initValue, in, startIndex);
        out[tmpOutPos++] = mBits1;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        return tmpOutPos + (int)mBits1;
      }
      case 128: {
        final long mBits1 = maxDiffBits(initValue, in, startIndex);
        final long initOffset2 = in[startIndex + 63];
        final long mBits2 = maxDiffBits(initOffset2, in, startIndex + 64);
        out[tmpOutPos++] = mBits1 << 8 | mBits2;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        tmpOutPos += mBits1;
        pack(initOffset2, in, startIndex + 64, out, tmpOutPos, mBits2);
        return tmpOutPos + (int)mBits2;
      }
      case 192: {
        final long mBits1 = maxDiffBits(initValue, in, startIndex);
        final long initOffset2 = in[startIndex + 63];
        final long mBits2 = maxDiffBits(initOffset2, in, startIndex + 64);
        final long initOffset3 = in[startIndex + 127];
        final long mBits3 = maxDiffBits(initOffset3, in, startIndex + 128);
        out[tmpOutPos++] = mBits1 << 16 | mBits2 << 8 | mBits3;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        tmpOutPos += mBits1;
        pack(initOffset2, in, startIndex + 64, out, tmpOutPos, mBits2);
        tmpOutPos += mBits2;
        pack(initOffset3, in, startIndex + 128, out, tmpOutPos, mBits3);
        return tmpOutPos + (int)mBits3;
      }
      case 256: {
        final long mBits1 = maxDiffBits(initValue, in, startIndex);
        final long initOffset2 = in[startIndex + 63];
        final long mBits2 = maxDiffBits(initOffset2, in, startIndex + 64);
        final long initOffset3 = in[startIndex + 127];
        final long mBits3 = maxDiffBits(initOffset3, in, startIndex + 128);
        final long initOffset4 = in[startIndex + 191];
        final long mBits4 = maxDiffBits(initOffset4, in, startIndex + 192);
        out[tmpOutPos++] = mBits1 << 24 | mBits2 << 16 | mBits3 << 8 | mBits4;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        tmpOutPos += mBits1;
        pack(initOffset2, in, startIndex + 64, out, tmpOutPos, mBits2);
        tmpOutPos += mBits2;
        pack(initOffset3, in, startIndex + 128, out, tmpOutPos, mBits3);
        tmpOutPos += mBits3;
        pack(initOffset4, in, startIndex + 192, out, tmpOutPos, mBits4);
        return tmpOutPos + (int)mBits4;
      }
      case 320: {
        final long mBits1 = maxDiffBits(initValue, in, startIndex);
        final long initOffset2 = in[startIndex + 63];
        final long mBits2 = maxDiffBits(initOffset2, in, startIndex + 64);
        final long initOffset3 = in[startIndex + 127];
        final long mBits3 = maxDiffBits(initOffset3, in, startIndex + 128);
        final long initOffset4 = in[startIndex + 191];
        final long mBits4 = maxDiffBits(initOffset4, in, startIndex + 192);
        final long initOffset5 = in[startIndex + 255];
        final long mBits5 = maxDiffBits(initOffset5, in, startIndex + 256);
        out[tmpOutPos++] = mBits1 << 32 | mBits2 << 24 | mBits3 << 16 | mBits4 << 8 | mBits5;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        tmpOutPos += mBits1;
        pack(initOffset2, in, startIndex + 64, out, tmpOutPos, mBits2);
        tmpOutPos += mBits2;
        pack(initOffset3, in, startIndex + 128, out, tmpOutPos, mBits3);
        tmpOutPos += mBits3;
        pack(initOffset4, in, startIndex + 192, out, tmpOutPos, mBits4);
        tmpOutPos += mBits4;
        pack(initOffset5, in, startIndex + 256, out, tmpOutPos, mBits5);
        return tmpOutPos + (int)mBits5;
      }
      case 384: {
        final long mBits1 = maxDiffBits(initValue, in, startIndex);
        final long initOffset2 = in[startIndex + 63];
        final long mBits2 = maxDiffBits(initOffset2, in, startIndex + 64);
        final long initOffset3 = in[startIndex + 127];
        final long mBits3 = maxDiffBits(initOffset3, in, startIndex + 128);
        final long initOffset4 = in[startIndex + 191];
        final long mBits4 = maxDiffBits(initOffset4, in, startIndex + 192);
        final long initOffset5 = in[startIndex + 255];
        final long mBits5 = maxDiffBits(initOffset5, in, startIndex + 256);
        final long initOffset6 = in[startIndex + 319];
        final long mBits6 = maxDiffBits(initOffset6, in, startIndex + 320);
        out[tmpOutPos++] = mBits1 << 40 | mBits2 << 32 | mBits3 << 24 | mBits4 << 16 | mBits5 << 8 | mBits6;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        tmpOutPos += mBits1;
        pack(initOffset2, in, startIndex + 64, out, tmpOutPos, mBits2);
        tmpOutPos += mBits2;
        pack(initOffset3, in, startIndex + 128, out, tmpOutPos, mBits3);
        tmpOutPos += mBits3;
        pack(initOffset4, in, startIndex + 192, out, tmpOutPos, mBits4);
        tmpOutPos += mBits4;
        pack(initOffset5, in, startIndex + 256, out, tmpOutPos, mBits5);
        tmpOutPos += mBits5;
        pack(initOffset6, in, startIndex + 320, out, tmpOutPos, mBits6);
        return tmpOutPos + (int)mBits6;
      }
      case 448: {
        final long mBits1 = maxDiffBits(initValue, in, startIndex);
        final long initOffset2 = in[startIndex + 63];
        final long mBits2 = maxDiffBits(initOffset2, in, startIndex + 64);
        final long initOffset3 = in[startIndex + 127];
        final long mBits3 = maxDiffBits(initOffset3, in, startIndex + 128);
        final long initOffset4 = in[startIndex + 191];
        final long mBits4 = maxDiffBits(initOffset4, in, startIndex + 192);
        final long initOffset5 = in[startIndex + 255];
        final long mBits5 = maxDiffBits(initOffset5, in, startIndex + 256);
        final long initOffset6 = in[startIndex + 319];
        final long mBits6 = maxDiffBits(initOffset6, in, startIndex + 320);
        final long initOffset7 = in[startIndex + 383];
        final long mBits7 = maxDiffBits(initOffset7, in, startIndex + 384);
        out[tmpOutPos++] = mBits1 << 48 | mBits2 << 40 | mBits3 << 32 | mBits4 << 24 | mBits5 << 16 | mBits6 << 8 | mBits7;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        tmpOutPos += mBits1;
        pack(initOffset2, in, startIndex + 64, out, tmpOutPos, mBits2);
        tmpOutPos += mBits2;
        pack(initOffset3, in, startIndex + 128, out, tmpOutPos, mBits3);
        tmpOutPos += mBits3;
        pack(initOffset4, in, startIndex + 192, out, tmpOutPos, mBits4);
        tmpOutPos += mBits4;
        pack(initOffset5, in, startIndex + 256, out, tmpOutPos, mBits5);
        tmpOutPos += mBits5;
        pack(initOffset6, in, startIndex + 320, out, tmpOutPos, mBits6);
        tmpOutPos += mBits6;
        pack(initOffset7, in, startIndex + 384, out, tmpOutPos, mBits7);
        return tmpOutPos + (int)mBits7;
      }
      case 0: {
        return tmpOutPos;
      }
      default: {
        throw new IllegalStateException();
      }
    }
  }

  public static void decompressIntegrated(final long[] in, int startIndex, final long[] out,
      final int outPosition, final int outEndIndex, long initValue) {
    assert outEndIndex != 0;
    int index = startIndex;
    int s = outPosition;
    for (; s + 511 < outEndIndex; s += 512) {
      final long mBits1 = in[index] >>> 56;
      final long mBits2 = in[index] >>> 48 & 0xff;
      final long mBits3 = in[index] >>> 40 & 0xff;
      final long mBits4 = in[index] >>> 32 & 0xff;
      final long mBits5 = in[index] >>> 24 & 0xff;
      final long mBits6 = in[index] >>> 16 & 0xff;
      final long mBits7 = in[index] >>> 8 & 0xff;
      final long mBits8 = in[index] & 0xff;
      index++;
      unpack(initValue, in, index, out, s, mBits1);
      index += mBits1;
      initValue = out[s + 63];
      unpack(initValue, in, index, out, s + 64, mBits2);
      index += mBits2;
      initValue = out[s + 127];
      unpack(initValue, in, index, out, s + 128, mBits3);
      index += mBits3;
      initValue = out[s + 191];
      unpack(initValue, in, index, out, s + 192, mBits4);
      index += mBits4;
      initValue = out[s + 255];
      unpack(initValue, in, index, out, s + 256, mBits5);
      index += mBits5;
      initValue = out[s + 319];
      unpack(initValue, in, index, out, s + 320, mBits6);
      index += mBits6;
      initValue = out[s + 383];
      unpack(initValue, in, index, out, s + 384, mBits7);
      index += mBits7;
      initValue = out[s + 447];
      unpack(initValue, in, index, out, s + 448, mBits8);
      index += mBits8;
      initValue = out[s + 511];
    }
    switch (outEndIndex - s) {
      case 64: {
        final long mBits1 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
      }
      break;
      case 128: {
        final long mBits1 = in[index] >>> 8;
        final long mBits2 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
        index += mBits1;
        initValue = out[s + 63];
        unpack(initValue, in, index, out, s + 64, mBits2);
      }
      break;
      case 192: {
        final long mBits1 = in[index] >>> 16;
        final long mBits2 = in[index] >>> 8 & 0xff;
        final long mBits3 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
        index += mBits1;
        initValue = out[s + 63];
        unpack(initValue, in, index, out, s + 64, mBits2);
        index += mBits2;
        initValue = out[s + 127];
        unpack(initValue, in, index, out, s + 128, mBits3);
      }
      break;
      case 256: {
        final long mBits1 = in[index] >>> 24;
        final long mBits2 = in[index] >>> 16 & 0xff;
        final long mBits3 = in[index] >>> 8 & 0xff;
        final long mBits4 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
        index += mBits1;
        initValue = out[s + 63];
        unpack(initValue, in, index, out, s + 64, mBits2);
        index += mBits2;
        initValue = out[s + 127];
        unpack(initValue, in, index, out, s + 128, mBits3);
        index += mBits3;
        initValue = out[s + 191];
        unpack(initValue, in, index, out, s + 192, mBits4);
      }
      break;
      case 320: {
        final long mBits1 = in[index] >>> 32;
        final long mBits2 = in[index] >>> 24 & 0xff;
        final long mBits3 = in[index] >>> 16 & 0xff;
        final long mBits4 = in[index] >>> 8 & 0xff;
        final long mBits5 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
        index += mBits1;
        initValue = out[s + 63];
        unpack(initValue, in, index, out, s + 64, mBits2);
        index += mBits2;
        initValue = out[s + 127];
        unpack(initValue, in, index, out, s + 128, mBits3);
        index += mBits3;
        initValue = out[s + 191];
        unpack(initValue, in, index, out, s + 192, mBits4);
        index += mBits4;
        initValue = out[s + 255];
        unpack(initValue, in, index, out, s + 256, mBits5);
      }
      break;
      case 384: {
        final long mBits1 = in[index] >>> 40;
        final long mBits2 = in[index] >>> 32 & 0xff;
        final long mBits3 = in[index] >>> 24 & 0xff;
        final long mBits4 = in[index] >>> 16 & 0xff;
        final long mBits5 = in[index] >>> 8 & 0xff;
        final long mBits6 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
        index += mBits1;
        initValue = out[s + 63];
        unpack(initValue, in, index, out, s + 64, mBits2);
        index += mBits2;
        initValue = out[s + 127];
        unpack(initValue, in, index, out, s + 128, mBits3);
        index += mBits3;
        initValue = out[s + 191];
        unpack(initValue, in, index, out, s + 192, mBits4);
        index += mBits4;
        initValue = out[s + 255];
        unpack(initValue, in, index, out, s + 256, mBits5);
        index += mBits5;
        initValue = out[s + 319];
        unpack(initValue, in, index, out, s + 320, mBits6);
      }
      break;
      case 448: {
        final long mBits1 = in[index] >>> 48;
        final long mBits2 = in[index] >>> 40 & 0xff;
        final long mBits3 = in[index] >>> 32 & 0xff;
        final long mBits4 = in[index] >>> 24 & 0xff;
        final long mBits5 = in[index] >>> 16 & 0xff;
        final long mBits6 = in[index] >>> 8 & 0xff;
        final long mBits7 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
        index += mBits1;
        initValue = out[s + 63];
        unpack(initValue, in, index, out, s + 64, mBits2);
        index += mBits2;
        initValue = out[s + 127];
        unpack(initValue, in, index, out, s + 128, mBits3);
        index += mBits3;
        initValue = out[s + 191];
        unpack(initValue, in, index, out, s + 192, mBits4);
        index += mBits4;
        initValue = out[s + 255];
        unpack(initValue, in, index, out, s + 256, mBits5);
        index += mBits5;
        initValue = out[s + 319];
        unpack(initValue, in, index, out, s + 320, mBits6);
        index += mBits6;
        initValue = out[s + 383];
        unpack(initValue, in, index, out, s + 384, mBits7);
      }
      break;
    }
  }

  public static void writeVar(final ByteBuf buf, final long value) {
    if (value >>> 7 == 0) {
      buf.writeByte((byte)value);
    } else if (value >>> 14 == 0) {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7));
    } else if (value >>> 21 == 0) {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7 | 128));
      buf.writeByte((byte)(value >>> 14));
    } else if (value >>> 28 == 0) {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7 | 128));
      buf.writeByte((byte)(value >>> 14 | 128));
      buf.writeByte((byte)(value >>> 21));
    } else if (value >>> 35 == 0) {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7 | 128));
      buf.writeByte((byte)(value >>> 14 | 128));
      buf.writeByte((byte)(value >>> 21 | 128));
      buf.writeByte((byte)(value >>> 28));
    } else if (value >>> 42 == 0) {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7 | 128));
      buf.writeByte((byte)(value >>> 14 | 128));
      buf.writeByte((byte)(value >>> 21 | 128));
      buf.writeByte((byte)(value >>> 28 | 128));
      buf.writeByte((byte)(value >>> 35));
    } else if (value >>> 49 == 0) {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7 | 128));
      buf.writeByte((byte)(value >>> 14 | 128));
      buf.writeByte((byte)(value >>> 21 | 128));
      buf.writeByte((byte)(value >>> 28 | 128));
      buf.writeByte((byte)(value >>> 35 | 128));
      buf.writeByte((byte)(value >>> 42));
    } else if (value >>> 56 == 0) {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7 | 128));
      buf.writeByte((byte)(value >>> 14 | 128));
      buf.writeByte((byte)(value >>> 21 | 128));
      buf.writeByte((byte)(value >>> 28 | 128));
      buf.writeByte((byte)(value >>> 35 | 128));
      buf.writeByte((byte)(value >>> 42 | 128));
      buf.writeByte((byte)(value >>> 49));
    } else {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7 | 128));
      buf.writeByte((byte)(value >>> 14 | 128));
      buf.writeByte((byte)(value >>> 21 | 128));
      buf.writeByte((byte)(value >>> 28 | 128));
      buf.writeByte((byte)(value >>> 35 | 128));
      buf.writeByte((byte)(value >>> 42 | 128));
      buf.writeByte((byte)(value >>> 49 | 128));
      buf.writeByte((byte)(value >>> 56));
    }
  }

  public static long readVar(final ByteBuf buf) {
    byte aByte = buf.readByte();
    long value = aByte & 127;
    if ((aByte & 128) != 0) {
      aByte = buf.readByte();
      value |= (aByte & 127) << 7;
      if ((aByte & 128) != 0) {
        aByte = buf.readByte();
        value |= (aByte & 127) << 14;
        if ((aByte & 128) != 0) {
          aByte = buf.readByte();
          value |= (aByte & 127) << 21;
          if ((aByte & 128) != 0) {
            aByte = buf.readByte();
            value |= (long)(aByte & 127) << 28;
            if ((aByte & 128) != 0) {
              aByte = buf.readByte();
              value |= (long)(aByte & 127) << 35;
              if ((aByte & 128) != 0) {
                aByte = buf.readByte();
                value |= (long)(aByte & 127) << 42;
                if ((aByte & 128) != 0) {
                  aByte = buf.readByte();
                  value |= (long)(aByte & 127) << 49;
                  if ((aByte & 128) != 0) {
                    value |= (long)buf.readByte() << 56;
                  }
                }
              }
            }
          }
        }
      }
    }
    return value;
  }

  public static void compressVariable(final long[] in, final int startIndex, final int endIndex,
      final ByteBuf buf) {
    long initValue = 0;
    for (int index = startIndex; index < endIndex; index++) {
      final long value = (in[index] - initValue);
      initValue = in[index];
      if (value >>> 7 == 0) {
        buf.writeByte((byte)value);
      } else if (value >>> 14 == 0) {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7));
      } else if (value >>> 21 == 0) {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7 | 128));
        buf.writeByte((byte)(value >>> 14));
      } else if (value >>> 28 == 0) {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7 | 128));
        buf.writeByte((byte)(value >>> 14 | 128));
        buf.writeByte((byte)(value >>> 21));
      } else if (value >>> 35 == 0) {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7 | 128));
        buf.writeByte((byte)(value >>> 14 | 128));
        buf.writeByte((byte)(value >>> 21 | 128));
        buf.writeByte((byte)(value >>> 28));
      } else if (value >>> 42 == 0) {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7 | 128));
        buf.writeByte((byte)(value >>> 14 | 128));
        buf.writeByte((byte)(value >>> 21 | 128));
        buf.writeByte((byte)(value >>> 28 | 128));
        buf.writeByte((byte)(value >>> 35));
      } else if (value >>> 49 == 0) {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7 | 128));
        buf.writeByte((byte)(value >>> 14 | 128));
        buf.writeByte((byte)(value >>> 21 | 128));
        buf.writeByte((byte)(value >>> 28 | 128));
        buf.writeByte((byte)(value >>> 35 | 128));
        buf.writeByte((byte)(value >>> 42));
      } else if (value >>> 56 == 0) {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7 | 128));
        buf.writeByte((byte)(value >>> 14 | 128));
        buf.writeByte((byte)(value >>> 21 | 128));
        buf.writeByte((byte)(value >>> 28 | 128));
        buf.writeByte((byte)(value >>> 35 | 128));
        buf.writeByte((byte)(value >>> 42 | 128));
        buf.writeByte((byte)(value >>> 49));
      } else {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7 | 128));
        buf.writeByte((byte)(value >>> 14 | 128));
        buf.writeByte((byte)(value >>> 21 | 128));
        buf.writeByte((byte)(value >>> 28 | 128));
        buf.writeByte((byte)(value >>> 35 | 128));
        buf.writeByte((byte)(value >>> 42 | 128));
        buf.writeByte((byte)(value >>> 49 | 128));
        buf.writeByte((byte)(value >>> 56));
      }
    }
  }

  public static void decompressVariable(final ByteBuf buf, final long[] out, final int endIndex) {
    long initValue = 0;
    long value;
    for (int index = 0; index < endIndex; out[index++] = (initValue += value)) {
      byte aByte = buf.readByte();
      value = aByte & 127;
      if ((aByte & 128) != 0) {
        aByte = buf.readByte();
        value |= (aByte & 127) << 7;
        if ((aByte & 128) != 0) {
          aByte = buf.readByte();
          value |= (aByte & 127) << 14;
          if ((aByte & 128) != 0) {
            aByte = buf.readByte();
            value |= (aByte & 127) << 21;
            if ((aByte & 128) != 0) {
              aByte = buf.readByte();
              value |= (long)(aByte & 127) << 28;
              if ((aByte & 128) != 0) {
                aByte = buf.readByte();
                value |= (long)(aByte & 127) << 35;
                if ((aByte & 128) != 0) {
                  aByte = buf.readByte();
                  value |= (long)(aByte & 127) << 42;
                  if ((aByte & 128) != 0) {
                    aByte = buf.readByte();
                    value |= (long)(aByte & 127) << 49;
                    if ((aByte & 128) != 0) {
                      value |= (long)buf.readByte() << 56;
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private static int maxDiffBits(long initValue, long[] in, int position) {
    long mask = in[position] - initValue;
    for (int i = position + 1; i < position + 64; ++i) {
      mask |= in[i] - in[i - 1];
    }
    return 64 - Long.numberOfLeadingZeros(mask);
  }

  /**
   * Pack 32 64-bit integers as deltas with an initial value.
   *
   * @param initValue initial value (used to compute first delta)
   * @param in         input array
   * @param inPos      initial position in input array
   * @param out        output array
   * @param outPos     initial position in output array
   * @param bitCount        number of bits to use per integer
   */
  private static void pack(final long initValue, final long[] in, final int inPos, final long[] out,
      final int outPos, final long bitCount) {
    switch ((byte)bitCount) {
      case 0: {
        break;
      }
      case 1: {
        pack1(initValue, in, inPos, out, outPos);
        break;
      }
      case 2: {
        pack2(initValue, in, inPos, out, outPos);
        break;
      }
      case 3: {
        pack3(initValue, in, inPos, out, outPos);
        break;
      }
      case 4: {
        pack4(initValue, in, inPos, out, outPos);
        break;
      }
      case 5: {
        pack5(initValue, in, inPos, out, outPos);
        break;
      }
      case 6: {
        pack6(initValue, in, inPos, out, outPos);
        break;
      }
      case 7: {
        pack7(initValue, in, inPos, out, outPos);
        break;
      }
      case 8: {
        pack8(initValue, in, inPos, out, outPos);
        break;
      }
      case 9: {
        pack9(initValue, in, inPos, out, outPos);
        break;
      }
      case 10: {
        pack10(initValue, in, inPos, out, outPos);
        break;
      }
      case 11: {
        pack11(initValue, in, inPos, out, outPos);
        break;
      }
      case 12: {
        pack12(initValue, in, inPos, out, outPos);
        break;
      }
      case 13: {
        pack13(initValue, in, inPos, out, outPos);
        break;
      }
      case 14: {
        pack14(initValue, in, inPos, out, outPos);
        break;
      }
      case 15: {
        pack15(initValue, in, inPos, out, outPos);
        break;
      }
      case 16: {
        pack16(initValue, in, inPos, out, outPos);
        break;
      }
      case 17: {
        pack17(initValue, in, inPos, out, outPos);
        break;
      }
      case 18: {
        pack18(initValue, in, inPos, out, outPos);
        break;
      }
      case 19: {
        pack19(initValue, in, inPos, out, outPos);
        break;
      }
      case 20: {
        pack20(initValue, in, inPos, out, outPos);
        break;
      }
      case 21: {
        pack21(initValue, in, inPos, out, outPos);
        break;
      }
      case 22: {
        pack22(initValue, in, inPos, out, outPos);
        break;
      }
      case 23: {
        pack23(initValue, in, inPos, out, outPos);
        break;
      }
      case 24: {
        pack24(initValue, in, inPos, out, outPos);
        break;
      }
      case 25: {
        pack25(initValue, in, inPos, out, outPos);
        break;
      }
      case 26: {
        pack26(initValue, in, inPos, out, outPos);
        break;
      }
      case 27: {
        pack27(initValue, in, inPos, out, outPos);
        break;
      }
      case 28: {
        pack28(initValue, in, inPos, out, outPos);
        break;
      }
      case 29: {
        pack29(initValue, in, inPos, out, outPos);
        break;
      }
      case 30: {
        pack30(initValue, in, inPos, out, outPos);
        break;
      }
      case 31: {
        pack31(initValue, in, inPos, out, outPos);
        break;
      }
      case 32: {
        pack32(initValue, in, inPos, out, outPos);
        break;
      }
      case 33: {
        pack33(initValue, in, inPos, out, outPos);
        break;
      }
      case 34: {
        pack34(initValue, in, inPos, out, outPos);
        break;
      }
      case 35: {
        pack35(initValue, in, inPos, out, outPos);
        break;
      }
      case 36: {
        pack36(initValue, in, inPos, out, outPos);
        break;
      }
      case 37: {
        pack37(initValue, in, inPos, out, outPos);
        break;
      }
      case 38: {
        pack38(initValue, in, inPos, out, outPos);
        break;
      }
      case 39: {
        pack39(initValue, in, inPos, out, outPos);
        break;
      }
      case 40: {
        pack40(initValue, in, inPos, out, outPos);
        break;
      }
      case 41: {
        pack41(initValue, in, inPos, out, outPos);
        break;
      }
      case 42: {
        pack42(initValue, in, inPos, out, outPos);
        break;
      }
      case 43: {
        pack43(initValue, in, inPos, out, outPos);
        break;
      }
      case 44: {
        pack44(initValue, in, inPos, out, outPos);
        break;
      }
      case 45: {
        pack45(initValue, in, inPos, out, outPos);
        break;
      }
      case 46: {
        pack46(initValue, in, inPos, out, outPos);
        break;
      }
      case 47: {
        pack47(initValue, in, inPos, out, outPos);
        break;
      }
      case 48: {
        pack48(initValue, in, inPos, out, outPos);
        break;
      }
      case 49: {
        pack49(initValue, in, inPos, out, outPos);
        break;
      }
      case 50: {
        pack50(initValue, in, inPos, out, outPos);
        break;
      }
      case 51: {
        pack51(initValue, in, inPos, out, outPos);
        break;
      }
      case 52: {
        pack52(initValue, in, inPos, out, outPos);
        break;
      }
      case 53: {
        pack53(initValue, in, inPos, out, outPos);
        break;
      }
      case 54: {
        pack54(initValue, in, inPos, out, outPos);
        break;
      }
      case 55: {
        pack55(initValue, in, inPos, out, outPos);
        break;
      }
      case 56: {
        pack56(initValue, in, inPos, out, outPos);
        break;
      }
      case 57: {
        pack57(initValue, in, inPos, out, outPos);
        break;
      }
      case 58: {
        pack58(initValue, in, inPos, out, outPos);
        break;
      }
      case 59: {
        pack59(initValue, in, inPos, out, outPos);
        break;
      }
      case 60: {
        pack60(initValue, in, inPos, out, outPos);
        break;
      }
      case 61: {
        pack61(initValue, in, inPos, out, outPos);
        break;
      }
      case 62: {
        pack62(initValue, in, inPos, out, outPos);
        break;
      }
      case 63: {
        pack63(initValue, in, inPos, out, outPos);
        break;
      }
      case 64: {
        System.arraycopy(in, inPos, out, outPos, 64);
        break;
      }
      default: {
        throw new IllegalArgumentException("Unsupported bit width: " + bitCount);
      }
    }
  }

  /**
   * Unpack 32 64-bit integers as deltas with an initial value.
   *
   * @param initValue initial value (used to compute first delta)
   * @param in         input array
   * @param inPos      initial position in input array
   * @param out        output array
   * @param outPos     initial position in output array
   * @param bitCount        number of bits to use per integer
   */
  private static void unpack(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos, final long bitCount) {
    switch ((byte)bitCount) {
      case 0: {
        Arrays.fill(out, outPos, outPos + 64, initValue);
        break;
      }
      case 1: {
        unpack1(initValue, in, inPos, out, outPos);
        break;
      }
      case 2: {
        unpack2(initValue, in, inPos, out, outPos);
        break;
      }
      case 3: {
        unpack3(initValue, in, inPos, out, outPos);
        break;
      }
      case 4: {
        unpack4(initValue, in, inPos, out, outPos);
        break;
      }
      case 5: {
        unpack5(initValue, in, inPos, out, outPos);
        break;
      }
      case 6: {
        unpack6(initValue, in, inPos, out, outPos);
        break;
      }
      case 7: {
        unpack7(initValue, in, inPos, out, outPos);
        break;
      }
      case 8: {
        unpack8(initValue, in, inPos, out, outPos);
        break;
      }
      case 9: {
        unpack9(initValue, in, inPos, out, outPos);
        break;
      }
      case 10: {
        unpack10(initValue, in, inPos, out, outPos);
        break;
      }
      case 11: {
        unpack11(initValue, in, inPos, out, outPos);
        break;
      }
      case 12: {
        unpack12(initValue, in, inPos, out, outPos);
        break;
      }
      case 13: {
        unpack13(initValue, in, inPos, out, outPos);
        break;
      }
      case 14: {
        unpack14(initValue, in, inPos, out, outPos);
        break;
      }
      case 15: {
        unpack15(initValue, in, inPos, out, outPos);
        break;
      }
      case 16: {
        unpack16(initValue, in, inPos, out, outPos);
        break;
      }
      case 17: {
        unpack17(initValue, in, inPos, out, outPos);
        break;
      }
      case 18: {
        unpack18(initValue, in, inPos, out, outPos);
        break;
      }
      case 19: {
        unpack19(initValue, in, inPos, out, outPos);
        break;
      }
      case 20: {
        unpack20(initValue, in, inPos, out, outPos);
        break;
      }
      case 21: {
        unpack21(initValue, in, inPos, out, outPos);
        break;
      }
      case 22: {
        unpack22(initValue, in, inPos, out, outPos);
        break;
      }
      case 23: {
        unpack23(initValue, in, inPos, out, outPos);
        break;
      }
      case 24: {
        unpack24(initValue, in, inPos, out, outPos);
        break;
      }
      case 25: {
        unpack25(initValue, in, inPos, out, outPos);
        break;
      }
      case 26: {
        unpack26(initValue, in, inPos, out, outPos);
        break;
      }
      case 27: {
        unpack27(initValue, in, inPos, out, outPos);
        break;
      }
      case 28: {
        unpack28(initValue, in, inPos, out, outPos);
        break;
      }
      case 29: {
        unpack29(initValue, in, inPos, out, outPos);
        break;
      }
      case 30: {
        unpack30(initValue, in, inPos, out, outPos);
        break;
      }
      case 31: {
        unpack31(initValue, in, inPos, out, outPos);
        break;
      }
      case 32: {
        unpack32(initValue, in, inPos, out, outPos);
        break;
      }
      case 33: {
        unpack33(initValue, in, inPos, out, outPos);
        break;
      }
      case 34: {
        unpack34(initValue, in, inPos, out, outPos);
        break;
      }
      case 35: {
        unpack35(initValue, in, inPos, out, outPos);
        break;
      }
      case 36: {
        unpack36(initValue, in, inPos, out, outPos);
        break;
      }
      case 37: {
        unpack37(initValue, in, inPos, out, outPos);
        break;
      }
      case 38: {
        unpack38(initValue, in, inPos, out, outPos);
        break;
      }
      case 39: {
        unpack39(initValue, in, inPos, out, outPos);
        break;
      }
      case 40: {
        unpack40(initValue, in, inPos, out, outPos);
        break;
      }
      case 41: {
        unpack41(initValue, in, inPos, out, outPos);
        break;
      }
      case 42: {
        unpack42(initValue, in, inPos, out, outPos);
        break;
      }
      case 43: {
        unpack43(initValue, in, inPos, out, outPos);
        break;
      }
      case 44: {
        unpack44(initValue, in, inPos, out, outPos);
        break;
      }
      case 45: {
        unpack45(initValue, in, inPos, out, outPos);
        break;
      }
      case 46: {
        unpack46(initValue, in, inPos, out, outPos);
        break;
      }
      case 47: {
        unpack47(initValue, in, inPos, out, outPos);
        break;
      }
      case 48: {
        unpack48(initValue, in, inPos, out, outPos);
        break;
      }
      case 49: {
        unpack49(initValue, in, inPos, out, outPos);
        break;
      }
      case 50: {
        unpack50(initValue, in, inPos, out, outPos);
        break;
      }
      case 51: {
        unpack51(initValue, in, inPos, out, outPos);
        break;
      }
      case 52: {
        unpack52(initValue, in, inPos, out, outPos);
        break;
      }
      case 53: {
        unpack53(initValue, in, inPos, out, outPos);
        break;
      }
      case 54: {
        unpack54(initValue, in, inPos, out, outPos);
        break;
      }
      case 55: {
        unpack55(initValue, in, inPos, out, outPos);
        break;
      }
      case 56: {
        unpack56(initValue, in, inPos, out, outPos);
        break;
      }
      case 57: {
        unpack57(initValue, in, inPos, out, outPos);
        break;
      }
      case 58: {
        unpack58(initValue, in, inPos, out, outPos);
        break;
      }
      case 59: {
        unpack59(initValue, in, inPos, out, outPos);
        break;
      }
      case 60: {
        unpack60(initValue, in, inPos, out, outPos);
        break;
      }
      case 61: {
        unpack61(initValue, in, inPos, out, outPos);
        break;
      }
      case 62: {
        unpack62(initValue, in, inPos, out, outPos);
        break;
      }
      case 63: {
        unpack63(initValue, in, inPos, out, outPos);
        break;
      }
      case 64: {
        System.arraycopy(in, inPos, out, outPos, 64);
        break;
      }
      default: {
        throw new IllegalArgumentException("Unsupported bit width: " + bitCount);
      }
    }
  }

  private static void pack1(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 1 | (in[inPos + 2] - in[inPos + 1]) << 2 | (in[inPos + 3] - in[inPos + 2]) << 3 | (in[inPos + 4] - in[inPos + 3]) << 4 | (in[inPos + 5] - in[inPos + 4]) << 5 | (in[inPos + 6] - in[inPos + 5]) << 6 | (in[inPos + 7] - in[inPos + 6]) << 7 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 9 | (in[inPos + 10] - in[inPos + 9]) << 10 | (in[inPos + 11] - in[inPos + 10]) << 11 | (in[inPos + 12] - in[inPos + 11]) << 12 | (in[inPos + 13] - in[inPos + 12]) << 13 | (in[inPos + 14] - in[inPos + 13]) << 14 | (in[inPos + 15] - in[inPos + 14]) << 15 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 17 | (in[inPos + 18] - in[inPos + 17]) << 18 | (in[inPos + 19] - in[inPos + 18]) << 19 | (in[inPos + 20] - in[inPos + 19]) << 20 | (in[inPos + 21] - in[inPos + 20]) << 21 | (in[inPos + 22] - in[inPos + 21]) << 22 | (in[inPos + 23] - in[inPos + 22]) << 23 | (in[inPos + 24] - in[inPos + 23]) << 24 | (in[inPos + 25] - in[inPos + 24]) << 25 | (in[inPos + 26] - in[inPos + 25]) << 26 | (in[inPos + 27] - in[inPos + 26]) << 27 | (in[inPos + 28] - in[inPos + 27]) << 28 | (in[inPos + 29] - in[inPos + 28]) << 29 | (in[inPos + 30] - in[inPos + 29]) << 30 | (in[inPos + 31] - in[inPos + 30]) << 31 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 33 | (in[inPos + 34] - in[inPos + 33]) << 34 | (in[inPos + 35] - in[inPos + 34]) << 35 | (in[inPos + 36] - in[inPos + 35]) << 36 | (in[inPos + 37] - in[inPos + 36]) << 37 | (in[inPos + 38] - in[inPos + 37]) << 38 | (in[inPos + 39] - in[inPos + 38]) << 39 | (in[inPos + 40] - in[inPos + 39]) << 40 | (in[inPos + 41] - in[inPos + 40]) << 41 | (in[inPos + 42] - in[inPos + 41]) << 42 | (in[inPos + 43] - in[inPos + 42]) << 43 | (in[inPos + 44] - in[inPos + 43]) << 44 | (in[inPos + 45] - in[inPos + 44]) << 45 | (in[inPos + 46] - in[inPos + 45]) << 46 | (in[inPos + 47] - in[inPos + 46]) << 47 | (in[inPos + 48] - in[inPos + 47]) << 48 | (in[inPos + 49] - in[inPos + 48]) << 49 | (in[inPos + 50] - in[inPos + 49]) << 50 | (in[inPos + 51] - in[inPos + 50]) << 51 | (in[inPos + 52] - in[inPos + 51]) << 52 | (in[inPos + 53] - in[inPos + 52]) << 53 | (in[inPos + 54] - in[inPos + 53]) << 54 | (in[inPos + 55] - in[inPos + 54]) << 55 | (in[inPos + 56] - in[inPos + 55]) << 56 | (in[inPos + 57] - in[inPos + 56]) << 57 | (in[inPos + 58] - in[inPos + 57]) << 58 | (in[inPos + 59] - in[inPos + 58]) << 59 | (in[inPos + 60] - in[inPos + 59]) << 60 | (in[inPos + 61] - in[inPos + 60]) << 61 | (in[inPos + 62] - in[inPos + 61]) << 62 | (in[inPos + 63] - in[inPos + 62]) << 63;
  }

  private static void unpack1(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1) + initValue;
    out[outPos + 1] = (in[inPos] >>> 1 & 1) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 2 & 1) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 3 & 1) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 4 & 1) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 5 & 1) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 6 & 1) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 7 & 1) + out[outPos + 6];
    out[outPos + 8] = (in[inPos] >>> 8 & 1) + out[outPos + 7];
    out[outPos + 9] = (in[inPos] >>> 9 & 1) + out[outPos + 8];
    out[outPos + 10] = (in[inPos] >>> 10 & 1) + out[outPos + 9];
    out[outPos + 11] = (in[inPos] >>> 11 & 1) + out[outPos + 10];
    out[outPos + 12] = (in[inPos] >>> 12 & 1) + out[outPos + 11];
    out[outPos + 13] = (in[inPos] >>> 13 & 1) + out[outPos + 12];
    out[outPos + 14] = (in[inPos] >>> 14 & 1) + out[outPos + 13];
    out[outPos + 15] = (in[inPos] >>> 15 & 1) + out[outPos + 14];
    out[outPos + 16] = (in[inPos] >>> 16 & 1) + out[outPos + 15];
    out[outPos + 17] = (in[inPos] >>> 17 & 1) + out[outPos + 16];
    out[outPos + 18] = (in[inPos] >>> 18 & 1) + out[outPos + 17];
    out[outPos + 19] = (in[inPos] >>> 19 & 1) + out[outPos + 18];
    out[outPos + 20] = (in[inPos] >>> 20 & 1) + out[outPos + 19];
    out[outPos + 21] = (in[inPos] >>> 21 & 1) + out[outPos + 20];
    out[outPos + 22] = (in[inPos] >>> 22 & 1) + out[outPos + 21];
    out[outPos + 23] = (in[inPos] >>> 23 & 1) + out[outPos + 22];
    out[outPos + 24] = (in[inPos] >>> 24 & 1) + out[outPos + 23];
    out[outPos + 25] = (in[inPos] >>> 25 & 1) + out[outPos + 24];
    out[outPos + 26] = (in[inPos] >>> 26 & 1) + out[outPos + 25];
    out[outPos + 27] = (in[inPos] >>> 27 & 1) + out[outPos + 26];
    out[outPos + 28] = (in[inPos] >>> 28 & 1) + out[outPos + 27];
    out[outPos + 29] = (in[inPos] >>> 29 & 1) + out[outPos + 28];
    out[outPos + 30] = (in[inPos] >>> 30 & 1) + out[outPos + 29];
    out[outPos + 31] = (in[inPos] >>> 31 & 1) + out[outPos + 30];
    out[outPos + 32] = (in[inPos] >>> 32 & 1) + out[outPos + 31];
    out[outPos + 33] = (in[inPos] >>> 33 & 1) + out[outPos + 32];
    out[outPos + 34] = (in[inPos] >>> 34 & 1) + out[outPos + 33];
    out[outPos + 35] = (in[inPos] >>> 35 & 1) + out[outPos + 34];
    out[outPos + 36] = (in[inPos] >>> 36 & 1) + out[outPos + 35];
    out[outPos + 37] = (in[inPos] >>> 37 & 1) + out[outPos + 36];
    out[outPos + 38] = (in[inPos] >>> 38 & 1) + out[outPos + 37];
    out[outPos + 39] = (in[inPos] >>> 39 & 1) + out[outPos + 38];
    out[outPos + 40] = (in[inPos] >>> 40 & 1) + out[outPos + 39];
    out[outPos + 41] = (in[inPos] >>> 41 & 1) + out[outPos + 40];
    out[outPos + 42] = (in[inPos] >>> 42 & 1) + out[outPos + 41];
    out[outPos + 43] = (in[inPos] >>> 43 & 1) + out[outPos + 42];
    out[outPos + 44] = (in[inPos] >>> 44 & 1) + out[outPos + 43];
    out[outPos + 45] = (in[inPos] >>> 45 & 1) + out[outPos + 44];
    out[outPos + 46] = (in[inPos] >>> 46 & 1) + out[outPos + 45];
    out[outPos + 47] = (in[inPos] >>> 47 & 1) + out[outPos + 46];
    out[outPos + 48] = (in[inPos] >>> 48 & 1) + out[outPos + 47];
    out[outPos + 49] = (in[inPos] >>> 49 & 1) + out[outPos + 48];
    out[outPos + 50] = (in[inPos] >>> 50 & 1) + out[outPos + 49];
    out[outPos + 51] = (in[inPos] >>> 51 & 1) + out[outPos + 50];
    out[outPos + 52] = (in[inPos] >>> 52 & 1) + out[outPos + 51];
    out[outPos + 53] = (in[inPos] >>> 53 & 1) + out[outPos + 52];
    out[outPos + 54] = (in[inPos] >>> 54 & 1) + out[outPos + 53];
    out[outPos + 55] = (in[inPos] >>> 55 & 1) + out[outPos + 54];
    out[outPos + 56] = (in[inPos] >>> 56 & 1) + out[outPos + 55];
    out[outPos + 57] = (in[inPos] >>> 57 & 1) + out[outPos + 56];
    out[outPos + 58] = (in[inPos] >>> 58 & 1) + out[outPos + 57];
    out[outPos + 59] = (in[inPos] >>> 59 & 1) + out[outPos + 58];
    out[outPos + 60] = (in[inPos] >>> 60 & 1) + out[outPos + 59];
    out[outPos + 61] = (in[inPos] >>> 61 & 1) + out[outPos + 60];
    out[outPos + 62] = (in[inPos] >>> 62 & 1) + out[outPos + 61];
    out[outPos + 63] = (in[inPos] >>> 63) + out[outPos + 62];
  }

  private static void pack2(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 2 | (in[inPos + 2] - in[inPos + 1]) << 4 | (in[inPos + 3] - in[inPos + 2]) << 6 | (in[inPos + 4] - in[inPos + 3]) << 8 | (in[inPos + 5] - in[inPos + 4]) << 10 | (in[inPos + 6] - in[inPos + 5]) << 12 | (in[inPos + 7] - in[inPos + 6]) << 14 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 18 | (in[inPos + 10] - in[inPos + 9]) << 20 | (in[inPos + 11] - in[inPos + 10]) << 22 | (in[inPos + 12] - in[inPos + 11]) << 24 | (in[inPos + 13] - in[inPos + 12]) << 26 | (in[inPos + 14] - in[inPos + 13]) << 28 | (in[inPos + 15] - in[inPos + 14]) << 30 | (in[inPos + 16] - in[inPos + 15]) << 32 | (in[inPos + 17] - in[inPos + 16]) << 34 | (in[inPos + 18] - in[inPos + 17]) << 36 | (in[inPos + 19] - in[inPos + 18]) << 38 | (in[inPos + 20] - in[inPos + 19]) << 40 | (in[inPos + 21] - in[inPos + 20]) << 42 | (in[inPos + 22] - in[inPos + 21]) << 44 | (in[inPos + 23] - in[inPos + 22]) << 46 | (in[inPos + 24] - in[inPos + 23]) << 48 | (in[inPos + 25] - in[inPos + 24]) << 50 | (in[inPos + 26] - in[inPos + 25]) << 52 | (in[inPos + 27] - in[inPos + 26]) << 54 | (in[inPos + 28] - in[inPos + 27]) << 56 | (in[inPos + 29] - in[inPos + 28]) << 58 | (in[inPos + 30] - in[inPos + 29]) << 60 | (in[inPos + 31] - in[inPos + 30]) << 62;
    out[outPos + 1] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 2 | (in[inPos + 34] - in[inPos + 33]) << 4 | (in[inPos + 35] - in[inPos + 34]) << 6 | (in[inPos + 36] - in[inPos + 35]) << 8 | (in[inPos + 37] - in[inPos + 36]) << 10 | (in[inPos + 38] - in[inPos + 37]) << 12 | (in[inPos + 39] - in[inPos + 38]) << 14 | (in[inPos + 40] - in[inPos + 39]) << 16 | (in[inPos + 41] - in[inPos + 40]) << 18 | (in[inPos + 42] - in[inPos + 41]) << 20 | (in[inPos + 43] - in[inPos + 42]) << 22 | (in[inPos + 44] - in[inPos + 43]) << 24 | (in[inPos + 45] - in[inPos + 44]) << 26 | (in[inPos + 46] - in[inPos + 45]) << 28 | (in[inPos + 47] - in[inPos + 46]) << 30 | (in[inPos + 48] - in[inPos + 47]) << 32 | (in[inPos + 49] - in[inPos + 48]) << 34 | (in[inPos + 50] - in[inPos + 49]) << 36 | (in[inPos + 51] - in[inPos + 50]) << 38 | (in[inPos + 52] - in[inPos + 51]) << 40 | (in[inPos + 53] - in[inPos + 52]) << 42 | (in[inPos + 54] - in[inPos + 53]) << 44 | (in[inPos + 55] - in[inPos + 54]) << 46 | (in[inPos + 56] - in[inPos + 55]) << 48 | (in[inPos + 57] - in[inPos + 56]) << 50 | (in[inPos + 58] - in[inPos + 57]) << 52 | (in[inPos + 59] - in[inPos + 58]) << 54 | (in[inPos + 60] - in[inPos + 59]) << 56 | (in[inPos + 61] - in[inPos + 60]) << 58 | (in[inPos + 62] - in[inPos + 61]) << 60 | (in[inPos + 63] - in[inPos + 62]) << 62;
  }

  private static void unpack2(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 3) + initValue;
    out[outPos + 1] = (in[inPos] >>> 2 & 3) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 4 & 3) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 6 & 3) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 8 & 3) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 10 & 3) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 12 & 3) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 14 & 3) + out[outPos + 6];
    out[outPos + 8] = (in[inPos] >>> 16 & 3) + out[outPos + 7];
    out[outPos + 9] = (in[inPos] >>> 18 & 3) + out[outPos + 8];
    out[outPos + 10] = (in[inPos] >>> 20 & 3) + out[outPos + 9];
    out[outPos + 11] = (in[inPos] >>> 22 & 3) + out[outPos + 10];
    out[outPos + 12] = (in[inPos] >>> 24 & 3) + out[outPos + 11];
    out[outPos + 13] = (in[inPos] >>> 26 & 3) + out[outPos + 12];
    out[outPos + 14] = (in[inPos] >>> 28 & 3) + out[outPos + 13];
    out[outPos + 15] = (in[inPos] >>> 30 & 3) + out[outPos + 14];
    out[outPos + 16] = (in[inPos] >>> 32 & 3) + out[outPos + 15];
    out[outPos + 17] = (in[inPos] >>> 34 & 3) + out[outPos + 16];
    out[outPos + 18] = (in[inPos] >>> 36 & 3) + out[outPos + 17];
    out[outPos + 19] = (in[inPos] >>> 38 & 3) + out[outPos + 18];
    out[outPos + 20] = (in[inPos] >>> 40 & 3) + out[outPos + 19];
    out[outPos + 21] = (in[inPos] >>> 42 & 3) + out[outPos + 20];
    out[outPos + 22] = (in[inPos] >>> 44 & 3) + out[outPos + 21];
    out[outPos + 23] = (in[inPos] >>> 46 & 3) + out[outPos + 22];
    out[outPos + 24] = (in[inPos] >>> 48 & 3) + out[outPos + 23];
    out[outPos + 25] = (in[inPos] >>> 50 & 3) + out[outPos + 24];
    out[outPos + 26] = (in[inPos] >>> 52 & 3) + out[outPos + 25];
    out[outPos + 27] = (in[inPos] >>> 54 & 3) + out[outPos + 26];
    out[outPos + 28] = (in[inPos] >>> 56 & 3) + out[outPos + 27];
    out[outPos + 29] = (in[inPos] >>> 58 & 3) + out[outPos + 28];
    out[outPos + 30] = (in[inPos] >>> 60 & 3) + out[outPos + 29];
    out[outPos + 31] = (in[inPos] >>> 62) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 1] & 3) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 1] >>> 2 & 3) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 1] >>> 4 & 3) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 1] >>> 6 & 3) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 1] >>> 8 & 3) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 1] >>> 10 & 3) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 1] >>> 12 & 3) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 1] >>> 14 & 3) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 1] >>> 16 & 3) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 1] >>> 18 & 3) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 1] >>> 20 & 3) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 1] >>> 22 & 3) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 1] >>> 24 & 3) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 1] >>> 26 & 3) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 1] >>> 28 & 3) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 1] >>> 30 & 3) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 1] >>> 32 & 3) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 1] >>> 34 & 3) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 1] >>> 36 & 3) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 1] >>> 38 & 3) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 1] >>> 40 & 3) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 1] >>> 42 & 3) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 1] >>> 44 & 3) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 1] >>> 46 & 3) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 1] >>> 48 & 3) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 1] >>> 50 & 3) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 1] >>> 52 & 3) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 1] >>> 54 & 3) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 1] >>> 56 & 3) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 1] >>> 58 & 3) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 1] >>> 60 & 3) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 1] >>> 62) + out[outPos + 62];
  }

  private static void pack3(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 3 | (in[inPos + 2] - in[inPos + 1]) << 6 | (in[inPos + 3] - in[inPos + 2]) << 9 | (in[inPos + 4] - in[inPos + 3]) << 12 | (in[inPos + 5] - in[inPos + 4]) << 15 | (in[inPos + 6] - in[inPos + 5]) << 18 | (in[inPos + 7] - in[inPos + 6]) << 21 | (in[inPos + 8] - in[inPos + 7]) << 24 | (in[inPos + 9] - in[inPos + 8]) << 27 | (in[inPos + 10] - in[inPos + 9]) << 30 | (in[inPos + 11] - in[inPos + 10]) << 33 | (in[inPos + 12] - in[inPos + 11]) << 36 | (in[inPos + 13] - in[inPos + 12]) << 39 | (in[inPos + 14] - in[inPos + 13]) << 42 | (in[inPos + 15] - in[inPos + 14]) << 45 | (in[inPos + 16] - in[inPos + 15]) << 48 | (in[inPos + 17] - in[inPos + 16]) << 51 | (in[inPos + 18] - in[inPos + 17]) << 54 | (in[inPos + 19] - in[inPos + 18]) << 57 | (in[inPos + 20] - in[inPos + 19]) << 60 | (in[inPos + 21] - in[inPos + 20]) << 63;
    out[outPos + 1] = (in[inPos + 21] - in[inPos + 20]) >>> 1 | (in[inPos + 22] - in[inPos + 21]) << 2 | (in[inPos + 23] - in[inPos + 22]) << 5 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 11 | (in[inPos + 26] - in[inPos + 25]) << 14 | (in[inPos + 27] - in[inPos + 26]) << 17 | (in[inPos + 28] - in[inPos + 27]) << 20 | (in[inPos + 29] - in[inPos + 28]) << 23 | (in[inPos + 30] - in[inPos + 29]) << 26 | (in[inPos + 31] - in[inPos + 30]) << 29 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 35 | (in[inPos + 34] - in[inPos + 33]) << 38 | (in[inPos + 35] - in[inPos + 34]) << 41 | (in[inPos + 36] - in[inPos + 35]) << 44 | (in[inPos + 37] - in[inPos + 36]) << 47 | (in[inPos + 38] - in[inPos + 37]) << 50 | (in[inPos + 39] - in[inPos + 38]) << 53 | (in[inPos + 40] - in[inPos + 39]) << 56 | (in[inPos + 41] - in[inPos + 40]) << 59 | (in[inPos + 42] - in[inPos + 41]) << 62;
    out[outPos + 2] = (in[inPos + 42] - in[inPos + 41]) >>> 2 | (in[inPos + 43] - in[inPos + 42]) << 1 | (in[inPos + 44] - in[inPos + 43]) << 4 | (in[inPos + 45] - in[inPos + 44]) << 7 | (in[inPos + 46] - in[inPos + 45]) << 10 | (in[inPos + 47] - in[inPos + 46]) << 13 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 19 | (in[inPos + 50] - in[inPos + 49]) << 22 | (in[inPos + 51] - in[inPos + 50]) << 25 | (in[inPos + 52] - in[inPos + 51]) << 28 | (in[inPos + 53] - in[inPos + 52]) << 31 | (in[inPos + 54] - in[inPos + 53]) << 34 | (in[inPos + 55] - in[inPos + 54]) << 37 | (in[inPos + 56] - in[inPos + 55]) << 40 | (in[inPos + 57] - in[inPos + 56]) << 43 | (in[inPos + 58] - in[inPos + 57]) << 46 | (in[inPos + 59] - in[inPos + 58]) << 49 | (in[inPos + 60] - in[inPos + 59]) << 52 | (in[inPos + 61] - in[inPos + 60]) << 55 | (in[inPos + 62] - in[inPos + 61]) << 58 | (in[inPos + 63] - in[inPos + 62]) << 61;
  }

  private static void unpack3(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 7) + initValue;
    out[outPos + 1] = (in[inPos] >>> 3 & 7) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 6 & 7) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 9 & 7) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 12 & 7) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 15 & 7) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 18 & 7) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 21 & 7) + out[outPos + 6];
    out[outPos + 8] = (in[inPos] >>> 24 & 7) + out[outPos + 7];
    out[outPos + 9] = (in[inPos] >>> 27 & 7) + out[outPos + 8];
    out[outPos + 10] = (in[inPos] >>> 30 & 7) + out[outPos + 9];
    out[outPos + 11] = (in[inPos] >>> 33 & 7) + out[outPos + 10];
    out[outPos + 12] = (in[inPos] >>> 36 & 7) + out[outPos + 11];
    out[outPos + 13] = (in[inPos] >>> 39 & 7) + out[outPos + 12];
    out[outPos + 14] = (in[inPos] >>> 42 & 7) + out[outPos + 13];
    out[outPos + 15] = (in[inPos] >>> 45 & 7) + out[outPos + 14];
    out[outPos + 16] = (in[inPos] >>> 48 & 7) + out[outPos + 15];
    out[outPos + 17] = (in[inPos] >>> 51 & 7) + out[outPos + 16];
    out[outPos + 18] = (in[inPos] >>> 54 & 7) + out[outPos + 17];
    out[outPos + 19] = (in[inPos] >>> 57 & 7) + out[outPos + 18];
    out[outPos + 20] = (in[inPos] >>> 60 & 7) + out[outPos + 19];
    out[outPos + 21] = (in[inPos] >>> 63 | (in[inPos + 1] & 3) << 1) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 1] >>> 2 & 7) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 1] >>> 5 & 7) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 1] >>> 8 & 7) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 1] >>> 11 & 7) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 1] >>> 14 & 7) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 1] >>> 17 & 7) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 1] >>> 20 & 7) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 1] >>> 23 & 7) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 1] >>> 26 & 7) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 1] >>> 29 & 7) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 1] >>> 32 & 7) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 1] >>> 35 & 7) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 1] >>> 38 & 7) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 1] >>> 41 & 7) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 1] >>> 44 & 7) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 1] >>> 47 & 7) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 1] >>> 50 & 7) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 1] >>> 53 & 7) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 1] >>> 56 & 7) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 1] >>> 59 & 7) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 1) << 2) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 2] >>> 1 & 7) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 2] >>> 4 & 7) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 2] >>> 7 & 7) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 2] >>> 10 & 7) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 2] >>> 13 & 7) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 2] >>> 16 & 7) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 2] >>> 19 & 7) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 2] >>> 22 & 7) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 2] >>> 25 & 7) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 2] >>> 28 & 7) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 2] >>> 31 & 7) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 2] >>> 34 & 7) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 2] >>> 37 & 7) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 2] >>> 40 & 7) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 2] >>> 43 & 7) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 2] >>> 46 & 7) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 2] >>> 49 & 7) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 2] >>> 52 & 7) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 2] >>> 55 & 7) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 2] >>> 58 & 7) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 2] >>> 61) + out[outPos + 62];
  }

  private static void pack4(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 4 | (in[inPos + 2] - in[inPos + 1]) << 8 | (in[inPos + 3] - in[inPos + 2]) << 12 | (in[inPos + 4] - in[inPos + 3]) << 16 | (in[inPos + 5] - in[inPos + 4]) << 20 | (in[inPos + 6] - in[inPos + 5]) << 24 | (in[inPos + 7] - in[inPos + 6]) << 28 | (in[inPos + 8] - in[inPos + 7]) << 32 | (in[inPos + 9] - in[inPos + 8]) << 36 | (in[inPos + 10] - in[inPos + 9]) << 40 | (in[inPos + 11] - in[inPos + 10]) << 44 | (in[inPos + 12] - in[inPos + 11]) << 48 | (in[inPos + 13] - in[inPos + 12]) << 52 | (in[inPos + 14] - in[inPos + 13]) << 56 | (in[inPos + 15] - in[inPos + 14]) << 60;
    out[outPos + 1] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 4 | (in[inPos + 18] - in[inPos + 17]) << 8 | (in[inPos + 19] - in[inPos + 18]) << 12 | (in[inPos + 20] - in[inPos + 19]) << 16 | (in[inPos + 21] - in[inPos + 20]) << 20 | (in[inPos + 22] - in[inPos + 21]) << 24 | (in[inPos + 23] - in[inPos + 22]) << 28 | (in[inPos + 24] - in[inPos + 23]) << 32 | (in[inPos + 25] - in[inPos + 24]) << 36 | (in[inPos + 26] - in[inPos + 25]) << 40 | (in[inPos + 27] - in[inPos + 26]) << 44 | (in[inPos + 28] - in[inPos + 27]) << 48 | (in[inPos + 29] - in[inPos + 28]) << 52 | (in[inPos + 30] - in[inPos + 29]) << 56 | (in[inPos + 31] - in[inPos + 30]) << 60;
    out[outPos + 2] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 4 | (in[inPos + 34] - in[inPos + 33]) << 8 | (in[inPos + 35] - in[inPos + 34]) << 12 | (in[inPos + 36] - in[inPos + 35]) << 16 | (in[inPos + 37] - in[inPos + 36]) << 20 | (in[inPos + 38] - in[inPos + 37]) << 24 | (in[inPos + 39] - in[inPos + 38]) << 28 | (in[inPos + 40] - in[inPos + 39]) << 32 | (in[inPos + 41] - in[inPos + 40]) << 36 | (in[inPos + 42] - in[inPos + 41]) << 40 | (in[inPos + 43] - in[inPos + 42]) << 44 | (in[inPos + 44] - in[inPos + 43]) << 48 | (in[inPos + 45] - in[inPos + 44]) << 52 | (in[inPos + 46] - in[inPos + 45]) << 56 | (in[inPos + 47] - in[inPos + 46]) << 60;
    out[outPos + 3] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 4 | (in[inPos + 50] - in[inPos + 49]) << 8 | (in[inPos + 51] - in[inPos + 50]) << 12 | (in[inPos + 52] - in[inPos + 51]) << 16 | (in[inPos + 53] - in[inPos + 52]) << 20 | (in[inPos + 54] - in[inPos + 53]) << 24 | (in[inPos + 55] - in[inPos + 54]) << 28 | (in[inPos + 56] - in[inPos + 55]) << 32 | (in[inPos + 57] - in[inPos + 56]) << 36 | (in[inPos + 58] - in[inPos + 57]) << 40 | (in[inPos + 59] - in[inPos + 58]) << 44 | (in[inPos + 60] - in[inPos + 59]) << 48 | (in[inPos + 61] - in[inPos + 60]) << 52 | (in[inPos + 62] - in[inPos + 61]) << 56 | (in[inPos + 63] - in[inPos + 62]) << 60;
  }

  private static void unpack4(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 15) + initValue;
    out[outPos + 1] = (in[inPos] >>> 4 & 15) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 8 & 15) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 12 & 15) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 16 & 15) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 20 & 15) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 24 & 15) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 28 & 15) + out[outPos + 6];
    out[outPos + 8] = (in[inPos] >>> 32 & 15) + out[outPos + 7];
    out[outPos + 9] = (in[inPos] >>> 36 & 15) + out[outPos + 8];
    out[outPos + 10] = (in[inPos] >>> 40 & 15) + out[outPos + 9];
    out[outPos + 11] = (in[inPos] >>> 44 & 15) + out[outPos + 10];
    out[outPos + 12] = (in[inPos] >>> 48 & 15) + out[outPos + 11];
    out[outPos + 13] = (in[inPos] >>> 52 & 15) + out[outPos + 12];
    out[outPos + 14] = (in[inPos] >>> 56 & 15) + out[outPos + 13];
    out[outPos + 15] = (in[inPos] >>> 60) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 1] & 15) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 1] >>> 4 & 15) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 1] >>> 8 & 15) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 1] >>> 12 & 15) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 1] >>> 16 & 15) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 1] >>> 20 & 15) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 1] >>> 24 & 15) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 1] >>> 28 & 15) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 1] >>> 32 & 15) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 1] >>> 36 & 15) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 1] >>> 40 & 15) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 1] >>> 44 & 15) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 1] >>> 48 & 15) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 1] >>> 52 & 15) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 1] >>> 56 & 15) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 1] >>> 60) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 2] & 15) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 2] >>> 4 & 15) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 2] >>> 8 & 15) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 2] >>> 12 & 15) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 2] >>> 16 & 15) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 2] >>> 20 & 15) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 2] >>> 24 & 15) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 2] >>> 28 & 15) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 2] >>> 32 & 15) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 2] >>> 36 & 15) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 2] >>> 40 & 15) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 2] >>> 44 & 15) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 2] >>> 48 & 15) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 2] >>> 52 & 15) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 2] >>> 56 & 15) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 2] >>> 60) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 3] & 15) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 3] >>> 4 & 15) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 3] >>> 8 & 15) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 3] >>> 12 & 15) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 3] >>> 16 & 15) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 3] >>> 20 & 15) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 3] >>> 24 & 15) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 3] >>> 28 & 15) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 3] >>> 32 & 15) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 3] >>> 36 & 15) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 3] >>> 40 & 15) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 3] >>> 44 & 15) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 3] >>> 48 & 15) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 3] >>> 52 & 15) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 3] >>> 56 & 15) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 3] >>> 60) + out[outPos + 62];
  }

  private static void pack5(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 5 | (in[inPos + 2] - in[inPos + 1]) << 10 | (in[inPos + 3] - in[inPos + 2]) << 15 | (in[inPos + 4] - in[inPos + 3]) << 20 | (in[inPos + 5] - in[inPos + 4]) << 25 | (in[inPos + 6] - in[inPos + 5]) << 30 | (in[inPos + 7] - in[inPos + 6]) << 35 | (in[inPos + 8] - in[inPos + 7]) << 40 | (in[inPos + 9] - in[inPos + 8]) << 45 | (in[inPos + 10] - in[inPos + 9]) << 50 | (in[inPos + 11] - in[inPos + 10]) << 55 | (in[inPos + 12] - in[inPos + 11]) << 60;
    out[outPos + 1] = (in[inPos + 12] - in[inPos + 11]) >>> 4 | (in[inPos + 13] - in[inPos + 12]) << 1 | (in[inPos + 14] - in[inPos + 13]) << 6 | (in[inPos + 15] - in[inPos + 14]) << 11 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 21 | (in[inPos + 18] - in[inPos + 17]) << 26 | (in[inPos + 19] - in[inPos + 18]) << 31 | (in[inPos + 20] - in[inPos + 19]) << 36 | (in[inPos + 21] - in[inPos + 20]) << 41 | (in[inPos + 22] - in[inPos + 21]) << 46 | (in[inPos + 23] - in[inPos + 22]) << 51 | (in[inPos + 24] - in[inPos + 23]) << 56 | (in[inPos + 25] - in[inPos + 24]) << 61;
    out[outPos + 2] = (in[inPos + 25] - in[inPos + 24]) >>> 3 | (in[inPos + 26] - in[inPos + 25]) << 2 | (in[inPos + 27] - in[inPos + 26]) << 7 | (in[inPos + 28] - in[inPos + 27]) << 12 | (in[inPos + 29] - in[inPos + 28]) << 17 | (in[inPos + 30] - in[inPos + 29]) << 22 | (in[inPos + 31] - in[inPos + 30]) << 27 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 37 | (in[inPos + 34] - in[inPos + 33]) << 42 | (in[inPos + 35] - in[inPos + 34]) << 47 | (in[inPos + 36] - in[inPos + 35]) << 52 | (in[inPos + 37] - in[inPos + 36]) << 57 | (in[inPos + 38] - in[inPos + 37]) << 62;
    out[outPos + 3] = (in[inPos + 38] - in[inPos + 37]) >>> 2 | (in[inPos + 39] - in[inPos + 38]) << 3 | (in[inPos + 40] - in[inPos + 39]) << 8 | (in[inPos + 41] - in[inPos + 40]) << 13 | (in[inPos + 42] - in[inPos + 41]) << 18 | (in[inPos + 43] - in[inPos + 42]) << 23 | (in[inPos + 44] - in[inPos + 43]) << 28 | (in[inPos + 45] - in[inPos + 44]) << 33 | (in[inPos + 46] - in[inPos + 45]) << 38 | (in[inPos + 47] - in[inPos + 46]) << 43 | (in[inPos + 48] - in[inPos + 47]) << 48 | (in[inPos + 49] - in[inPos + 48]) << 53 | (in[inPos + 50] - in[inPos + 49]) << 58 | (in[inPos + 51] - in[inPos + 50]) << 63;
    out[outPos + 4] = (in[inPos + 51] - in[inPos + 50]) >>> 1 | (in[inPos + 52] - in[inPos + 51]) << 4 | (in[inPos + 53] - in[inPos + 52]) << 9 | (in[inPos + 54] - in[inPos + 53]) << 14 | (in[inPos + 55] - in[inPos + 54]) << 19 | (in[inPos + 56] - in[inPos + 55]) << 24 | (in[inPos + 57] - in[inPos + 56]) << 29 | (in[inPos + 58] - in[inPos + 57]) << 34 | (in[inPos + 59] - in[inPos + 58]) << 39 | (in[inPos + 60] - in[inPos + 59]) << 44 | (in[inPos + 61] - in[inPos + 60]) << 49 | (in[inPos + 62] - in[inPos + 61]) << 54 | (in[inPos + 63] - in[inPos + 62]) << 59;
  }

  private static void unpack5(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 31) + initValue;
    out[outPos + 1] = (in[inPos] >>> 5 & 31) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 10 & 31) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 15 & 31) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 20 & 31) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 25 & 31) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 30 & 31) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 35 & 31) + out[outPos + 6];
    out[outPos + 8] = (in[inPos] >>> 40 & 31) + out[outPos + 7];
    out[outPos + 9] = (in[inPos] >>> 45 & 31) + out[outPos + 8];
    out[outPos + 10] = (in[inPos] >>> 50 & 31) + out[outPos + 9];
    out[outPos + 11] = (in[inPos] >>> 55 & 31) + out[outPos + 10];
    out[outPos + 12] = (in[inPos] >>> 60 | (in[inPos + 1] & 1) << 4) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 1] >>> 1 & 31) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 1] >>> 6 & 31) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 1] >>> 11 & 31) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 1] >>> 16 & 31) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 1] >>> 21 & 31) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 1] >>> 26 & 31) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 1] >>> 31 & 31) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 1] >>> 36 & 31) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 1] >>> 41 & 31) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 1] >>> 46 & 31) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 1] >>> 51 & 31) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 1] >>> 56 & 31) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 1] >>> 61 | (in[inPos + 2] & 3) << 3) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 2] >>> 2 & 31) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 2] >>> 7 & 31) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 2] >>> 12 & 31) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 2] >>> 17 & 31) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 2] >>> 22 & 31) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 2] >>> 27 & 31) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 2] >>> 32 & 31) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 2] >>> 37 & 31) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 2] >>> 42 & 31) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 2] >>> 47 & 31) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 2] >>> 52 & 31) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 2] >>> 57 & 31) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 2] >>> 62 | (in[inPos + 3] & 7) << 2) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 3] >>> 3 & 31) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 3] >>> 8 & 31) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 3] >>> 13 & 31) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 3] >>> 18 & 31) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 3] >>> 23 & 31) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 3] >>> 28 & 31) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 3] >>> 33 & 31) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 3] >>> 38 & 31) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 3] >>> 43 & 31) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 3] >>> 48 & 31) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 3] >>> 53 & 31) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 3] >>> 58 & 31) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 3] >>> 63 | (in[inPos + 4] & 15) << 1) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 4] >>> 4 & 31) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 4] >>> 9 & 31) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 4] >>> 14 & 31) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 4] >>> 19 & 31) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 4] >>> 24 & 31) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 4] >>> 29 & 31) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 4] >>> 34 & 31) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 4] >>> 39 & 31) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 4] >>> 44 & 31) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 4] >>> 49 & 31) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 4] >>> 54 & 31) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 4] >>> 59) + out[outPos + 62];
  }

  private static void pack6(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 6 | (in[inPos + 2] - in[inPos + 1]) << 12 | (in[inPos + 3] - in[inPos + 2]) << 18 | (in[inPos + 4] - in[inPos + 3]) << 24 | (in[inPos + 5] - in[inPos + 4]) << 30 | (in[inPos + 6] - in[inPos + 5]) << 36 | (in[inPos + 7] - in[inPos + 6]) << 42 | (in[inPos + 8] - in[inPos + 7]) << 48 | (in[inPos + 9] - in[inPos + 8]) << 54 | (in[inPos + 10] - in[inPos + 9]) << 60;
    out[outPos + 1] = (in[inPos + 10] - in[inPos + 9]) >>> 4 | (in[inPos + 11] - in[inPos + 10]) << 2 | (in[inPos + 12] - in[inPos + 11]) << 8 | (in[inPos + 13] - in[inPos + 12]) << 14 | (in[inPos + 14] - in[inPos + 13]) << 20 | (in[inPos + 15] - in[inPos + 14]) << 26 | (in[inPos + 16] - in[inPos + 15]) << 32 | (in[inPos + 17] - in[inPos + 16]) << 38 | (in[inPos + 18] - in[inPos + 17]) << 44 | (in[inPos + 19] - in[inPos + 18]) << 50 | (in[inPos + 20] - in[inPos + 19]) << 56 | (in[inPos + 21] - in[inPos + 20]) << 62;
    out[outPos + 2] = (in[inPos + 21] - in[inPos + 20]) >>> 2 | (in[inPos + 22] - in[inPos + 21]) << 4 | (in[inPos + 23] - in[inPos + 22]) << 10 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 22 | (in[inPos + 26] - in[inPos + 25]) << 28 | (in[inPos + 27] - in[inPos + 26]) << 34 | (in[inPos + 28] - in[inPos + 27]) << 40 | (in[inPos + 29] - in[inPos + 28]) << 46 | (in[inPos + 30] - in[inPos + 29]) << 52 | (in[inPos + 31] - in[inPos + 30]) << 58;
    out[outPos + 3] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 6 | (in[inPos + 34] - in[inPos + 33]) << 12 | (in[inPos + 35] - in[inPos + 34]) << 18 | (in[inPos + 36] - in[inPos + 35]) << 24 | (in[inPos + 37] - in[inPos + 36]) << 30 | (in[inPos + 38] - in[inPos + 37]) << 36 | (in[inPos + 39] - in[inPos + 38]) << 42 | (in[inPos + 40] - in[inPos + 39]) << 48 | (in[inPos + 41] - in[inPos + 40]) << 54 | (in[inPos + 42] - in[inPos + 41]) << 60;
    out[outPos + 4] = (in[inPos + 42] - in[inPos + 41]) >>> 4 | (in[inPos + 43] - in[inPos + 42]) << 2 | (in[inPos + 44] - in[inPos + 43]) << 8 | (in[inPos + 45] - in[inPos + 44]) << 14 | (in[inPos + 46] - in[inPos + 45]) << 20 | (in[inPos + 47] - in[inPos + 46]) << 26 | (in[inPos + 48] - in[inPos + 47]) << 32 | (in[inPos + 49] - in[inPos + 48]) << 38 | (in[inPos + 50] - in[inPos + 49]) << 44 | (in[inPos + 51] - in[inPos + 50]) << 50 | (in[inPos + 52] - in[inPos + 51]) << 56 | (in[inPos + 53] - in[inPos + 52]) << 62;
    out[outPos + 5] = (in[inPos + 53] - in[inPos + 52]) >>> 2 | (in[inPos + 54] - in[inPos + 53]) << 4 | (in[inPos + 55] - in[inPos + 54]) << 10 | (in[inPos + 56] - in[inPos + 55]) << 16 | (in[inPos + 57] - in[inPos + 56]) << 22 | (in[inPos + 58] - in[inPos + 57]) << 28 | (in[inPos + 59] - in[inPos + 58]) << 34 | (in[inPos + 60] - in[inPos + 59]) << 40 | (in[inPos + 61] - in[inPos + 60]) << 46 | (in[inPos + 62] - in[inPos + 61]) << 52 | (in[inPos + 63] - in[inPos + 62]) << 58;
  }

  private static void unpack6(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 63) + initValue;
    out[outPos + 1] = (in[inPos] >>> 6 & 63) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 12 & 63) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 18 & 63) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 24 & 63) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 30 & 63) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 36 & 63) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 42 & 63) + out[outPos + 6];
    out[outPos + 8] = (in[inPos] >>> 48 & 63) + out[outPos + 7];
    out[outPos + 9] = (in[inPos] >>> 54 & 63) + out[outPos + 8];
    out[outPos + 10] = (in[inPos] >>> 60 | (in[inPos + 1] & 3) << 4) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 2 & 63) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 1] >>> 8 & 63) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 1] >>> 14 & 63) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 1] >>> 20 & 63) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 1] >>> 26 & 63) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 1] >>> 32 & 63) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 1] >>> 38 & 63) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 1] >>> 44 & 63) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 1] >>> 50 & 63) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 1] >>> 56 & 63) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 15) << 2) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 2] >>> 4 & 63) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 2] >>> 10 & 63) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 2] >>> 16 & 63) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 2] >>> 22 & 63) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 2] >>> 28 & 63) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 2] >>> 34 & 63) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 2] >>> 40 & 63) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 2] >>> 46 & 63) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 2] >>> 52 & 63) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 2] >>> 58) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 3] & 63) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 3] >>> 6 & 63) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 3] >>> 12 & 63) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 3] >>> 18 & 63) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 3] >>> 24 & 63) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 3] >>> 30 & 63) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 3] >>> 36 & 63) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 3] >>> 42 & 63) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 3] >>> 48 & 63) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 3] >>> 54 & 63) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 3) << 4) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 4] >>> 2 & 63) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 4] >>> 8 & 63) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 4] >>> 14 & 63) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 4] >>> 20 & 63) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 4] >>> 26 & 63) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 4] >>> 32 & 63) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 4] >>> 38 & 63) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 4] >>> 44 & 63) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 4] >>> 50 & 63) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 4] >>> 56 & 63) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 4] >>> 62 | (in[inPos + 5] & 15) << 2) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 5] >>> 4 & 63) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 5] >>> 10 & 63) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 5] >>> 16 & 63) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 5] >>> 22 & 63) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 5] >>> 28 & 63) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 5] >>> 34 & 63) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 5] >>> 40 & 63) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 5] >>> 46 & 63) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 5] >>> 52 & 63) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 5] >>> 58) + out[outPos + 62];
  }

  private static void pack7(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 7 | (in[inPos + 2] - in[inPos + 1]) << 14 | (in[inPos + 3] - in[inPos + 2]) << 21 | (in[inPos + 4] - in[inPos + 3]) << 28 | (in[inPos + 5] - in[inPos + 4]) << 35 | (in[inPos + 6] - in[inPos + 5]) << 42 | (in[inPos + 7] - in[inPos + 6]) << 49 | (in[inPos + 8] - in[inPos + 7]) << 56 | (in[inPos + 9] - in[inPos + 8]) << 63;
    out[outPos + 1] = (in[inPos + 9] - in[inPos + 8]) >>> 1 | (in[inPos + 10] - in[inPos + 9]) << 6 | (in[inPos + 11] - in[inPos + 10]) << 13 | (in[inPos + 12] - in[inPos + 11]) << 20 | (in[inPos + 13] - in[inPos + 12]) << 27 | (in[inPos + 14] - in[inPos + 13]) << 34 | (in[inPos + 15] - in[inPos + 14]) << 41 | (in[inPos + 16] - in[inPos + 15]) << 48 | (in[inPos + 17] - in[inPos + 16]) << 55 | (in[inPos + 18] - in[inPos + 17]) << 62;
    out[outPos + 2] = (in[inPos + 18] - in[inPos + 17]) >>> 2 | (in[inPos + 19] - in[inPos + 18]) << 5 | (in[inPos + 20] - in[inPos + 19]) << 12 | (in[inPos + 21] - in[inPos + 20]) << 19 | (in[inPos + 22] - in[inPos + 21]) << 26 | (in[inPos + 23] - in[inPos + 22]) << 33 | (in[inPos + 24] - in[inPos + 23]) << 40 | (in[inPos + 25] - in[inPos + 24]) << 47 | (in[inPos + 26] - in[inPos + 25]) << 54 | (in[inPos + 27] - in[inPos + 26]) << 61;
    out[outPos + 3] = (in[inPos + 27] - in[inPos + 26]) >>> 3 | (in[inPos + 28] - in[inPos + 27]) << 4 | (in[inPos + 29] - in[inPos + 28]) << 11 | (in[inPos + 30] - in[inPos + 29]) << 18 | (in[inPos + 31] - in[inPos + 30]) << 25 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 39 | (in[inPos + 34] - in[inPos + 33]) << 46 | (in[inPos + 35] - in[inPos + 34]) << 53 | (in[inPos + 36] - in[inPos + 35]) << 60;
    out[outPos + 4] = (in[inPos + 36] - in[inPos + 35]) >>> 4 | (in[inPos + 37] - in[inPos + 36]) << 3 | (in[inPos + 38] - in[inPos + 37]) << 10 | (in[inPos + 39] - in[inPos + 38]) << 17 | (in[inPos + 40] - in[inPos + 39]) << 24 | (in[inPos + 41] - in[inPos + 40]) << 31 | (in[inPos + 42] - in[inPos + 41]) << 38 | (in[inPos + 43] - in[inPos + 42]) << 45 | (in[inPos + 44] - in[inPos + 43]) << 52 | (in[inPos + 45] - in[inPos + 44]) << 59;
    out[outPos + 5] = (in[inPos + 45] - in[inPos + 44]) >>> 5 | (in[inPos + 46] - in[inPos + 45]) << 2 | (in[inPos + 47] - in[inPos + 46]) << 9 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 23 | (in[inPos + 50] - in[inPos + 49]) << 30 | (in[inPos + 51] - in[inPos + 50]) << 37 | (in[inPos + 52] - in[inPos + 51]) << 44 | (in[inPos + 53] - in[inPos + 52]) << 51 | (in[inPos + 54] - in[inPos + 53]) << 58;
    out[outPos + 6] = (in[inPos + 54] - in[inPos + 53]) >>> 6 | (in[inPos + 55] - in[inPos + 54]) << 1 | (in[inPos + 56] - in[inPos + 55]) << 8 | (in[inPos + 57] - in[inPos + 56]) << 15 | (in[inPos + 58] - in[inPos + 57]) << 22 | (in[inPos + 59] - in[inPos + 58]) << 29 | (in[inPos + 60] - in[inPos + 59]) << 36 | (in[inPos + 61] - in[inPos + 60]) << 43 | (in[inPos + 62] - in[inPos + 61]) << 50 | (in[inPos + 63] - in[inPos + 62]) << 57;
  }

  private static void unpack7(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 127) + initValue;
    out[outPos + 1] = (in[inPos] >>> 7 & 127) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 14 & 127) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 21 & 127) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 28 & 127) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 35 & 127) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 42 & 127) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 49 & 127) + out[outPos + 6];
    out[outPos + 8] = (in[inPos] >>> 56 & 127) + out[outPos + 7];
    out[outPos + 9] = (in[inPos] >>> 63 | (in[inPos + 1] & 63) << 1) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 6 & 127) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 13 & 127) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 1] >>> 20 & 127) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 1] >>> 27 & 127) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 1] >>> 34 & 127) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 1] >>> 41 & 127) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 1] >>> 48 & 127) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 1] >>> 55 & 127) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 31) << 2) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 2] >>> 5 & 127) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 2] >>> 12 & 127) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 2] >>> 19 & 127) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 2] >>> 26 & 127) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 2] >>> 33 & 127) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 2] >>> 40 & 127) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 2] >>> 47 & 127) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 2] >>> 54 & 127) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 2] >>> 61 | (in[inPos + 3] & 15) << 3) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 3] >>> 4 & 127) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 3] >>> 11 & 127) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 3] >>> 18 & 127) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 3] >>> 25 & 127) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 3] >>> 32 & 127) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 3] >>> 39 & 127) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 3] >>> 46 & 127) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 3] >>> 53 & 127) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 7) << 4) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 4] >>> 3 & 127) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 4] >>> 10 & 127) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 4] >>> 17 & 127) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 4] >>> 24 & 127) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 4] >>> 31 & 127) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 4] >>> 38 & 127) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 4] >>> 45 & 127) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 4] >>> 52 & 127) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 4] >>> 59 | (in[inPos + 5] & 3) << 5) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 5] >>> 2 & 127) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 5] >>> 9 & 127) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 5] >>> 16 & 127) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 5] >>> 23 & 127) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 5] >>> 30 & 127) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 5] >>> 37 & 127) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 5] >>> 44 & 127) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 5] >>> 51 & 127) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 1) << 6) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 6] >>> 1 & 127) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 6] >>> 8 & 127) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 6] >>> 15 & 127) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 6] >>> 22 & 127) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 6] >>> 29 & 127) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 6] >>> 36 & 127) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 6] >>> 43 & 127) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 6] >>> 50 & 127) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 6] >>> 57) + out[outPos + 62];
  }

  private static void pack8(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 8 | (in[inPos + 2] - in[inPos + 1]) << 16 | (in[inPos + 3] - in[inPos + 2]) << 24 | (in[inPos + 4] - in[inPos + 3]) << 32 | (in[inPos + 5] - in[inPos + 4]) << 40 | (in[inPos + 6] - in[inPos + 5]) << 48 | (in[inPos + 7] - in[inPos + 6]) << 56;
    out[outPos + 1] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 8 | (in[inPos + 10] - in[inPos + 9]) << 16 | (in[inPos + 11] - in[inPos + 10]) << 24 | (in[inPos + 12] - in[inPos + 11]) << 32 | (in[inPos + 13] - in[inPos + 12]) << 40 | (in[inPos + 14] - in[inPos + 13]) << 48 | (in[inPos + 15] - in[inPos + 14]) << 56;
    out[outPos + 2] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 8 | (in[inPos + 18] - in[inPos + 17]) << 16 | (in[inPos + 19] - in[inPos + 18]) << 24 | (in[inPos + 20] - in[inPos + 19]) << 32 | (in[inPos + 21] - in[inPos + 20]) << 40 | (in[inPos + 22] - in[inPos + 21]) << 48 | (in[inPos + 23] - in[inPos + 22]) << 56;
    out[outPos + 3] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 8 | (in[inPos + 26] - in[inPos + 25]) << 16 | (in[inPos + 27] - in[inPos + 26]) << 24 | (in[inPos + 28] - in[inPos + 27]) << 32 | (in[inPos + 29] - in[inPos + 28]) << 40 | (in[inPos + 30] - in[inPos + 29]) << 48 | (in[inPos + 31] - in[inPos + 30]) << 56;
    out[outPos + 4] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 8 | (in[inPos + 34] - in[inPos + 33]) << 16 | (in[inPos + 35] - in[inPos + 34]) << 24 | (in[inPos + 36] - in[inPos + 35]) << 32 | (in[inPos + 37] - in[inPos + 36]) << 40 | (in[inPos + 38] - in[inPos + 37]) << 48 | (in[inPos + 39] - in[inPos + 38]) << 56;
    out[outPos + 5] = (in[inPos + 40] - in[inPos + 39]) | (in[inPos + 41] - in[inPos + 40]) << 8 | (in[inPos + 42] - in[inPos + 41]) << 16 | (in[inPos + 43] - in[inPos + 42]) << 24 | (in[inPos + 44] - in[inPos + 43]) << 32 | (in[inPos + 45] - in[inPos + 44]) << 40 | (in[inPos + 46] - in[inPos + 45]) << 48 | (in[inPos + 47] - in[inPos + 46]) << 56;
    out[outPos + 6] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 8 | (in[inPos + 50] - in[inPos + 49]) << 16 | (in[inPos + 51] - in[inPos + 50]) << 24 | (in[inPos + 52] - in[inPos + 51]) << 32 | (in[inPos + 53] - in[inPos + 52]) << 40 | (in[inPos + 54] - in[inPos + 53]) << 48 | (in[inPos + 55] - in[inPos + 54]) << 56;
    out[outPos + 7] = (in[inPos + 56] - in[inPos + 55]) | (in[inPos + 57] - in[inPos + 56]) << 8 | (in[inPos + 58] - in[inPos + 57]) << 16 | (in[inPos + 59] - in[inPos + 58]) << 24 | (in[inPos + 60] - in[inPos + 59]) << 32 | (in[inPos + 61] - in[inPos + 60]) << 40 | (in[inPos + 62] - in[inPos + 61]) << 48 | (in[inPos + 63] - in[inPos + 62]) << 56;
  }

  private static void unpack8(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 255) + initValue;
    out[outPos + 1] = (in[inPos] >>> 8 & 255) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 16 & 255) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 24 & 255) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 32 & 255) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 40 & 255) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 48 & 255) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 56) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] & 255) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 8 & 255) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 16 & 255) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 24 & 255) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 1] >>> 32 & 255) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 1] >>> 40 & 255) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 1] >>> 48 & 255) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 1] >>> 56) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 2] & 255) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 2] >>> 8 & 255) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 2] >>> 16 & 255) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 2] >>> 24 & 255) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 2] >>> 32 & 255) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 2] >>> 40 & 255) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 2] >>> 48 & 255) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 2] >>> 56) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 3] & 255) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 3] >>> 8 & 255) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 3] >>> 16 & 255) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 3] >>> 24 & 255) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 3] >>> 32 & 255) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 3] >>> 40 & 255) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 3] >>> 48 & 255) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 3] >>> 56) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 4] & 255) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 4] >>> 8 & 255) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 4] >>> 16 & 255) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 4] >>> 24 & 255) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 4] >>> 32 & 255) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 4] >>> 40 & 255) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 4] >>> 48 & 255) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 4] >>> 56) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 5] & 255) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 5] >>> 8 & 255) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 5] >>> 16 & 255) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 5] >>> 24 & 255) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 5] >>> 32 & 255) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 5] >>> 40 & 255) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 5] >>> 48 & 255) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 5] >>> 56) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 6] & 255) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 6] >>> 8 & 255) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 6] >>> 16 & 255) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 6] >>> 24 & 255) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 6] >>> 32 & 255) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 6] >>> 40 & 255) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 6] >>> 48 & 255) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 6] >>> 56) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 7] & 255) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 7] >>> 8 & 255) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 7] >>> 16 & 255) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 7] >>> 24 & 255) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 7] >>> 32 & 255) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 7] >>> 40 & 255) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 7] >>> 48 & 255) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 7] >>> 56) + out[outPos + 62];
  }

  private static void pack9(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 9 | (in[inPos + 2] - in[inPos + 1]) << 18 | (in[inPos + 3] - in[inPos + 2]) << 27 | (in[inPos + 4] - in[inPos + 3]) << 36 | (in[inPos + 5] - in[inPos + 4]) << 45 | (in[inPos + 6] - in[inPos + 5]) << 54 | (in[inPos + 7] - in[inPos + 6]) << 63;
    out[outPos + 1] = (in[inPos + 7] - in[inPos + 6]) >>> 1 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 17 | (in[inPos + 10] - in[inPos + 9]) << 26 | (in[inPos + 11] - in[inPos + 10]) << 35 | (in[inPos + 12] - in[inPos + 11]) << 44 | (in[inPos + 13] - in[inPos + 12]) << 53 | (in[inPos + 14] - in[inPos + 13]) << 62;
    out[outPos + 2] = (in[inPos + 14] - in[inPos + 13]) >>> 2 | (in[inPos + 15] - in[inPos + 14]) << 7 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 25 | (in[inPos + 18] - in[inPos + 17]) << 34 | (in[inPos + 19] - in[inPos + 18]) << 43 | (in[inPos + 20] - in[inPos + 19]) << 52 | (in[inPos + 21] - in[inPos + 20]) << 61;
    out[outPos + 3] = (in[inPos + 21] - in[inPos + 20]) >>> 3 | (in[inPos + 22] - in[inPos + 21]) << 6 | (in[inPos + 23] - in[inPos + 22]) << 15 | (in[inPos + 24] - in[inPos + 23]) << 24 | (in[inPos + 25] - in[inPos + 24]) << 33 | (in[inPos + 26] - in[inPos + 25]) << 42 | (in[inPos + 27] - in[inPos + 26]) << 51 | (in[inPos + 28] - in[inPos + 27]) << 60;
    out[outPos + 4] = (in[inPos + 28] - in[inPos + 27]) >>> 4 | (in[inPos + 29] - in[inPos + 28]) << 5 | (in[inPos + 30] - in[inPos + 29]) << 14 | (in[inPos + 31] - in[inPos + 30]) << 23 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 41 | (in[inPos + 34] - in[inPos + 33]) << 50 | (in[inPos + 35] - in[inPos + 34]) << 59;
    out[outPos + 5] = (in[inPos + 35] - in[inPos + 34]) >>> 5 | (in[inPos + 36] - in[inPos + 35]) << 4 | (in[inPos + 37] - in[inPos + 36]) << 13 | (in[inPos + 38] - in[inPos + 37]) << 22 | (in[inPos + 39] - in[inPos + 38]) << 31 | (in[inPos + 40] - in[inPos + 39]) << 40 | (in[inPos + 41] - in[inPos + 40]) << 49 | (in[inPos + 42] - in[inPos + 41]) << 58;
    out[outPos + 6] = (in[inPos + 42] - in[inPos + 41]) >>> 6 | (in[inPos + 43] - in[inPos + 42]) << 3 | (in[inPos + 44] - in[inPos + 43]) << 12 | (in[inPos + 45] - in[inPos + 44]) << 21 | (in[inPos + 46] - in[inPos + 45]) << 30 | (in[inPos + 47] - in[inPos + 46]) << 39 | (in[inPos + 48] - in[inPos + 47]) << 48 | (in[inPos + 49] - in[inPos + 48]) << 57;
    out[outPos + 7] = (in[inPos + 49] - in[inPos + 48]) >>> 7 | (in[inPos + 50] - in[inPos + 49]) << 2 | (in[inPos + 51] - in[inPos + 50]) << 11 | (in[inPos + 52] - in[inPos + 51]) << 20 | (in[inPos + 53] - in[inPos + 52]) << 29 | (in[inPos + 54] - in[inPos + 53]) << 38 | (in[inPos + 55] - in[inPos + 54]) << 47 | (in[inPos + 56] - in[inPos + 55]) << 56;
    out[outPos + 8] = (in[inPos + 56] - in[inPos + 55]) >>> 8 | (in[inPos + 57] - in[inPos + 56]) << 1 | (in[inPos + 58] - in[inPos + 57]) << 10 | (in[inPos + 59] - in[inPos + 58]) << 19 | (in[inPos + 60] - in[inPos + 59]) << 28 | (in[inPos + 61] - in[inPos + 60]) << 37 | (in[inPos + 62] - in[inPos + 61]) << 46 | (in[inPos + 63] - in[inPos + 62]) << 55;
  }

  private static void unpack9(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 511) + initValue;
    out[outPos + 1] = (in[inPos] >>> 9 & 511) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 18 & 511) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 27 & 511) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 36 & 511) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 45 & 511) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 54 & 511) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 63 | (in[inPos + 1] & 255) << 1) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 8 & 511) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 17 & 511) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 26 & 511) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 35 & 511) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 1] >>> 44 & 511) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 1] >>> 53 & 511) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 127) << 2) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 2] >>> 7 & 511) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 2] >>> 16 & 511) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 2] >>> 25 & 511) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 2] >>> 34 & 511) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 2] >>> 43 & 511) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 2] >>> 52 & 511) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 2] >>> 61 | (in[inPos + 3] & 63) << 3) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 3] >>> 6 & 511) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 3] >>> 15 & 511) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 3] >>> 24 & 511) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 3] >>> 33 & 511) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 3] >>> 42 & 511) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 3] >>> 51 & 511) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 31) << 4) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 4] >>> 5 & 511) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 4] >>> 14 & 511) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 4] >>> 23 & 511) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 4] >>> 32 & 511) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 4] >>> 41 & 511) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 4] >>> 50 & 511) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 4] >>> 59 | (in[inPos + 5] & 15) << 5) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 5] >>> 4 & 511) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 5] >>> 13 & 511) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 5] >>> 22 & 511) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 5] >>> 31 & 511) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 5] >>> 40 & 511) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 5] >>> 49 & 511) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 7) << 6) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 6] >>> 3 & 511) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 6] >>> 12 & 511) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 6] >>> 21 & 511) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 6] >>> 30 & 511) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 6] >>> 39 & 511) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 6] >>> 48 & 511) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 6] >>> 57 | (in[inPos + 7] & 3) << 7) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 7] >>> 2 & 511) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 7] >>> 11 & 511) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 7] >>> 20 & 511) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 7] >>> 29 & 511) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 7] >>> 38 & 511) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 7] >>> 47 & 511) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 1) << 8) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 8] >>> 1 & 511) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 8] >>> 10 & 511) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 8] >>> 19 & 511) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 8] >>> 28 & 511) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 8] >>> 37 & 511) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 8] >>> 46 & 511) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 8] >>> 55) + out[outPos + 62];
  }

  private static void pack10(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 10 | (in[inPos + 2] - in[inPos + 1]) << 20 | (in[inPos + 3] - in[inPos + 2]) << 30 | (in[inPos + 4] - in[inPos + 3]) << 40 | (in[inPos + 5] - in[inPos + 4]) << 50 | (in[inPos + 6] - in[inPos + 5]) << 60;
    out[outPos + 1] = (in[inPos + 6] - in[inPos + 5]) >>> 4 | (in[inPos + 7] - in[inPos + 6]) << 6 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 26 | (in[inPos + 10] - in[inPos + 9]) << 36 | (in[inPos + 11] - in[inPos + 10]) << 46 | (in[inPos + 12] - in[inPos + 11]) << 56;
    out[outPos + 2] = (in[inPos + 12] - in[inPos + 11]) >>> 8 | (in[inPos + 13] - in[inPos + 12]) << 2 | (in[inPos + 14] - in[inPos + 13]) << 12 | (in[inPos + 15] - in[inPos + 14]) << 22 | (in[inPos + 16] - in[inPos + 15]) << 32 | (in[inPos + 17] - in[inPos + 16]) << 42 | (in[inPos + 18] - in[inPos + 17]) << 52 | (in[inPos + 19] - in[inPos + 18]) << 62;
    out[outPos + 3] = (in[inPos + 19] - in[inPos + 18]) >>> 2 | (in[inPos + 20] - in[inPos + 19]) << 8 | (in[inPos + 21] - in[inPos + 20]) << 18 | (in[inPos + 22] - in[inPos + 21]) << 28 | (in[inPos + 23] - in[inPos + 22]) << 38 | (in[inPos + 24] - in[inPos + 23]) << 48 | (in[inPos + 25] - in[inPos + 24]) << 58;
    out[outPos + 4] = (in[inPos + 25] - in[inPos + 24]) >>> 6 | (in[inPos + 26] - in[inPos + 25]) << 4 | (in[inPos + 27] - in[inPos + 26]) << 14 | (in[inPos + 28] - in[inPos + 27]) << 24 | (in[inPos + 29] - in[inPos + 28]) << 34 | (in[inPos + 30] - in[inPos + 29]) << 44 | (in[inPos + 31] - in[inPos + 30]) << 54;
    out[outPos + 5] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 10 | (in[inPos + 34] - in[inPos + 33]) << 20 | (in[inPos + 35] - in[inPos + 34]) << 30 | (in[inPos + 36] - in[inPos + 35]) << 40 | (in[inPos + 37] - in[inPos + 36]) << 50 | (in[inPos + 38] - in[inPos + 37]) << 60;
    out[outPos + 6] = (in[inPos + 38] - in[inPos + 37]) >>> 4 | (in[inPos + 39] - in[inPos + 38]) << 6 | (in[inPos + 40] - in[inPos + 39]) << 16 | (in[inPos + 41] - in[inPos + 40]) << 26 | (in[inPos + 42] - in[inPos + 41]) << 36 | (in[inPos + 43] - in[inPos + 42]) << 46 | (in[inPos + 44] - in[inPos + 43]) << 56;
    out[outPos + 7] = (in[inPos + 44] - in[inPos + 43]) >>> 8 | (in[inPos + 45] - in[inPos + 44]) << 2 | (in[inPos + 46] - in[inPos + 45]) << 12 | (in[inPos + 47] - in[inPos + 46]) << 22 | (in[inPos + 48] - in[inPos + 47]) << 32 | (in[inPos + 49] - in[inPos + 48]) << 42 | (in[inPos + 50] - in[inPos + 49]) << 52 | (in[inPos + 51] - in[inPos + 50]) << 62;
    out[outPos + 8] = (in[inPos + 51] - in[inPos + 50]) >>> 2 | (in[inPos + 52] - in[inPos + 51]) << 8 | (in[inPos + 53] - in[inPos + 52]) << 18 | (in[inPos + 54] - in[inPos + 53]) << 28 | (in[inPos + 55] - in[inPos + 54]) << 38 | (in[inPos + 56] - in[inPos + 55]) << 48 | (in[inPos + 57] - in[inPos + 56]) << 58;
    out[outPos + 9] = (in[inPos + 57] - in[inPos + 56]) >>> 6 | (in[inPos + 58] - in[inPos + 57]) << 4 | (in[inPos + 59] - in[inPos + 58]) << 14 | (in[inPos + 60] - in[inPos + 59]) << 24 | (in[inPos + 61] - in[inPos + 60]) << 34 | (in[inPos + 62] - in[inPos + 61]) << 44 | (in[inPos + 63] - in[inPos + 62]) << 54;
  }

  private static void unpack10(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1023) + initValue;
    out[outPos + 1] = (in[inPos] >>> 10 & 1023) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 20 & 1023) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 30 & 1023) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 40 & 1023) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 50 & 1023) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 60 | (in[inPos + 1] & 63) << 4) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 6 & 1023) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 16 & 1023) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 26 & 1023) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 36 & 1023) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 46 & 1023) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 1] >>> 56 | (in[inPos + 2] & 3) << 8) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 2] >>> 2 & 1023) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 2] >>> 12 & 1023) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 2] >>> 22 & 1023) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 2] >>> 32 & 1023) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 2] >>> 42 & 1023) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 2] >>> 52 & 1023) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 2] >>> 62 | (in[inPos + 3] & 255) << 2) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 3] >>> 8 & 1023) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 3] >>> 18 & 1023) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 3] >>> 28 & 1023) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 3] >>> 38 & 1023) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 3] >>> 48 & 1023) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 3] >>> 58 | (in[inPos + 4] & 15) << 6) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 4] >>> 4 & 1023) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 4] >>> 14 & 1023) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 4] >>> 24 & 1023) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 4] >>> 34 & 1023) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 4] >>> 44 & 1023) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 4] >>> 54) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 5] & 1023) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 5] >>> 10 & 1023) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 5] >>> 20 & 1023) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 5] >>> 30 & 1023) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 5] >>> 40 & 1023) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 5] >>> 50 & 1023) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 5] >>> 60 | (in[inPos + 6] & 63) << 4) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 6] >>> 6 & 1023) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 6] >>> 16 & 1023) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 6] >>> 26 & 1023) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 6] >>> 36 & 1023) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 6] >>> 46 & 1023) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 6] >>> 56 | (in[inPos + 7] & 3) << 8) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 7] >>> 2 & 1023) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 7] >>> 12 & 1023) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 7] >>> 22 & 1023) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 7] >>> 32 & 1023) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 7] >>> 42 & 1023) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 7] >>> 52 & 1023) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 7] >>> 62 | (in[inPos + 8] & 255) << 2) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 8] >>> 8 & 1023) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 8] >>> 18 & 1023) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 8] >>> 28 & 1023) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 8] >>> 38 & 1023) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 8] >>> 48 & 1023) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 8] >>> 58 | (in[inPos + 9] & 15) << 6) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 9] >>> 4 & 1023) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 9] >>> 14 & 1023) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 9] >>> 24 & 1023) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 9] >>> 34 & 1023) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 9] >>> 44 & 1023) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 9] >>> 54) + out[outPos + 62];
  }

  private static void pack11(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 11 | (in[inPos + 2] - in[inPos + 1]) << 22 | (in[inPos + 3] - in[inPos + 2]) << 33 | (in[inPos + 4] - in[inPos + 3]) << 44 | (in[inPos + 5] - in[inPos + 4]) << 55;
    out[outPos + 1] = (in[inPos + 5] - in[inPos + 4]) >>> 9 | (in[inPos + 6] - in[inPos + 5]) << 2 | (in[inPos + 7] - in[inPos + 6]) << 13 | (in[inPos + 8] - in[inPos + 7]) << 24 | (in[inPos + 9] - in[inPos + 8]) << 35 | (in[inPos + 10] - in[inPos + 9]) << 46 | (in[inPos + 11] - in[inPos + 10]) << 57;
    out[outPos + 2] = (in[inPos + 11] - in[inPos + 10]) >>> 7 | (in[inPos + 12] - in[inPos + 11]) << 4 | (in[inPos + 13] - in[inPos + 12]) << 15 | (in[inPos + 14] - in[inPos + 13]) << 26 | (in[inPos + 15] - in[inPos + 14]) << 37 | (in[inPos + 16] - in[inPos + 15]) << 48 | (in[inPos + 17] - in[inPos + 16]) << 59;
    out[outPos + 3] = (in[inPos + 17] - in[inPos + 16]) >>> 5 | (in[inPos + 18] - in[inPos + 17]) << 6 | (in[inPos + 19] - in[inPos + 18]) << 17 | (in[inPos + 20] - in[inPos + 19]) << 28 | (in[inPos + 21] - in[inPos + 20]) << 39 | (in[inPos + 22] - in[inPos + 21]) << 50 | (in[inPos + 23] - in[inPos + 22]) << 61;
    out[outPos + 4] = (in[inPos + 23] - in[inPos + 22]) >>> 3 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 19 | (in[inPos + 26] - in[inPos + 25]) << 30 | (in[inPos + 27] - in[inPos + 26]) << 41 | (in[inPos + 28] - in[inPos + 27]) << 52 | (in[inPos + 29] - in[inPos + 28]) << 63;
    out[outPos + 5] = (in[inPos + 29] - in[inPos + 28]) >>> 1 | (in[inPos + 30] - in[inPos + 29]) << 10 | (in[inPos + 31] - in[inPos + 30]) << 21 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 43 | (in[inPos + 34] - in[inPos + 33]) << 54;
    out[outPos + 6] = (in[inPos + 34] - in[inPos + 33]) >>> 10 | (in[inPos + 35] - in[inPos + 34]) << 1 | (in[inPos + 36] - in[inPos + 35]) << 12 | (in[inPos + 37] - in[inPos + 36]) << 23 | (in[inPos + 38] - in[inPos + 37]) << 34 | (in[inPos + 39] - in[inPos + 38]) << 45 | (in[inPos + 40] - in[inPos + 39]) << 56;
    out[outPos + 7] = (in[inPos + 40] - in[inPos + 39]) >>> 8 | (in[inPos + 41] - in[inPos + 40]) << 3 | (in[inPos + 42] - in[inPos + 41]) << 14 | (in[inPos + 43] - in[inPos + 42]) << 25 | (in[inPos + 44] - in[inPos + 43]) << 36 | (in[inPos + 45] - in[inPos + 44]) << 47 | (in[inPos + 46] - in[inPos + 45]) << 58;
    out[outPos + 8] = (in[inPos + 46] - in[inPos + 45]) >>> 6 | (in[inPos + 47] - in[inPos + 46]) << 5 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 27 | (in[inPos + 50] - in[inPos + 49]) << 38 | (in[inPos + 51] - in[inPos + 50]) << 49 | (in[inPos + 52] - in[inPos + 51]) << 60;
    out[outPos + 9] = (in[inPos + 52] - in[inPos + 51]) >>> 4 | (in[inPos + 53] - in[inPos + 52]) << 7 | (in[inPos + 54] - in[inPos + 53]) << 18 | (in[inPos + 55] - in[inPos + 54]) << 29 | (in[inPos + 56] - in[inPos + 55]) << 40 | (in[inPos + 57] - in[inPos + 56]) << 51 | (in[inPos + 58] - in[inPos + 57]) << 62;
    out[outPos + 10] = (in[inPos + 58] - in[inPos + 57]) >>> 2 | (in[inPos + 59] - in[inPos + 58]) << 9 | (in[inPos + 60] - in[inPos + 59]) << 20 | (in[inPos + 61] - in[inPos + 60]) << 31 | (in[inPos + 62] - in[inPos + 61]) << 42 | (in[inPos + 63] - in[inPos + 62]) << 53;
  }

  private static void unpack11(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2047) + initValue;
    out[outPos + 1] = (in[inPos] >>> 11 & 2047) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 22 & 2047) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 33 & 2047) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 44 & 2047) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 55 | (in[inPos + 1] & 3) << 9) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 2 & 2047) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 13 & 2047) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 24 & 2047) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 35 & 2047) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 46 & 2047) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 57 | (in[inPos + 2] & 15) << 7) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 2] >>> 4 & 2047) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 2] >>> 15 & 2047) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 2] >>> 26 & 2047) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 2] >>> 37 & 2047) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 2] >>> 48 & 2047) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 2] >>> 59 | (in[inPos + 3] & 63) << 5) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 3] >>> 6 & 2047) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 3] >>> 17 & 2047) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 3] >>> 28 & 2047) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 3] >>> 39 & 2047) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 3] >>> 50 & 2047) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 3] >>> 61 | (in[inPos + 4] & 255) << 3) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 4] >>> 8 & 2047) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 4] >>> 19 & 2047) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 4] >>> 30 & 2047) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 4] >>> 41 & 2047) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 4] >>> 52 & 2047) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 4] >>> 63 | (in[inPos + 5] & 1023) << 1) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 5] >>> 10 & 2047) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 5] >>> 21 & 2047) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 5] >>> 32 & 2047) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 5] >>> 43 & 2047) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 5] >>> 54 | (in[inPos + 6] & 1) << 10) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 6] >>> 1 & 2047) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 6] >>> 12 & 2047) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 6] >>> 23 & 2047) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 6] >>> 34 & 2047) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 6] >>> 45 & 2047) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 6] >>> 56 | (in[inPos + 7] & 7) << 8) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 7] >>> 3 & 2047) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 7] >>> 14 & 2047) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 7] >>> 25 & 2047) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 7] >>> 36 & 2047) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 7] >>> 47 & 2047) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 7] >>> 58 | (in[inPos + 8] & 31) << 6) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 8] >>> 5 & 2047) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 8] >>> 16 & 2047) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 8] >>> 27 & 2047) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 8] >>> 38 & 2047) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 8] >>> 49 & 2047) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 8] >>> 60 | (in[inPos + 9] & 127) << 4) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 9] >>> 7 & 2047) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 9] >>> 18 & 2047) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 9] >>> 29 & 2047) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 9] >>> 40 & 2047) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 9] >>> 51 & 2047) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 9] >>> 62 | (in[inPos + 10] & 511) << 2) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 10] >>> 9 & 2047) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 10] >>> 20 & 2047) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 10] >>> 31 & 2047) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 10] >>> 42 & 2047) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 10] >>> 53) + out[outPos + 62];
  }

  private static void pack12(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 12 | (in[inPos + 2] - in[inPos + 1]) << 24 | (in[inPos + 3] - in[inPos + 2]) << 36 | (in[inPos + 4] - in[inPos + 3]) << 48 | (in[inPos + 5] - in[inPos + 4]) << 60;
    out[outPos + 1] = (in[inPos + 5] - in[inPos + 4]) >>> 4 | (in[inPos + 6] - in[inPos + 5]) << 8 | (in[inPos + 7] - in[inPos + 6]) << 20 | (in[inPos + 8] - in[inPos + 7]) << 32 | (in[inPos + 9] - in[inPos + 8]) << 44 | (in[inPos + 10] - in[inPos + 9]) << 56;
    out[outPos + 2] = (in[inPos + 10] - in[inPos + 9]) >>> 8 | (in[inPos + 11] - in[inPos + 10]) << 4 | (in[inPos + 12] - in[inPos + 11]) << 16 | (in[inPos + 13] - in[inPos + 12]) << 28 | (in[inPos + 14] - in[inPos + 13]) << 40 | (in[inPos + 15] - in[inPos + 14]) << 52;
    out[outPos + 3] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 12 | (in[inPos + 18] - in[inPos + 17]) << 24 | (in[inPos + 19] - in[inPos + 18]) << 36 | (in[inPos + 20] - in[inPos + 19]) << 48 | (in[inPos + 21] - in[inPos + 20]) << 60;
    out[outPos + 4] = (in[inPos + 21] - in[inPos + 20]) >>> 4 | (in[inPos + 22] - in[inPos + 21]) << 8 | (in[inPos + 23] - in[inPos + 22]) << 20 | (in[inPos + 24] - in[inPos + 23]) << 32 | (in[inPos + 25] - in[inPos + 24]) << 44 | (in[inPos + 26] - in[inPos + 25]) << 56;
    out[outPos + 5] = (in[inPos + 26] - in[inPos + 25]) >>> 8 | (in[inPos + 27] - in[inPos + 26]) << 4 | (in[inPos + 28] - in[inPos + 27]) << 16 | (in[inPos + 29] - in[inPos + 28]) << 28 | (in[inPos + 30] - in[inPos + 29]) << 40 | (in[inPos + 31] - in[inPos + 30]) << 52;
    out[outPos + 6] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 12 | (in[inPos + 34] - in[inPos + 33]) << 24 | (in[inPos + 35] - in[inPos + 34]) << 36 | (in[inPos + 36] - in[inPos + 35]) << 48 | (in[inPos + 37] - in[inPos + 36]) << 60;
    out[outPos + 7] = (in[inPos + 37] - in[inPos + 36]) >>> 4 | (in[inPos + 38] - in[inPos + 37]) << 8 | (in[inPos + 39] - in[inPos + 38]) << 20 | (in[inPos + 40] - in[inPos + 39]) << 32 | (in[inPos + 41] - in[inPos + 40]) << 44 | (in[inPos + 42] - in[inPos + 41]) << 56;
    out[outPos + 8] = (in[inPos + 42] - in[inPos + 41]) >>> 8 | (in[inPos + 43] - in[inPos + 42]) << 4 | (in[inPos + 44] - in[inPos + 43]) << 16 | (in[inPos + 45] - in[inPos + 44]) << 28 | (in[inPos + 46] - in[inPos + 45]) << 40 | (in[inPos + 47] - in[inPos + 46]) << 52;
    out[outPos + 9] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 12 | (in[inPos + 50] - in[inPos + 49]) << 24 | (in[inPos + 51] - in[inPos + 50]) << 36 | (in[inPos + 52] - in[inPos + 51]) << 48 | (in[inPos + 53] - in[inPos + 52]) << 60;
    out[outPos + 10] = (in[inPos + 53] - in[inPos + 52]) >>> 4 | (in[inPos + 54] - in[inPos + 53]) << 8 | (in[inPos + 55] - in[inPos + 54]) << 20 | (in[inPos + 56] - in[inPos + 55]) << 32 | (in[inPos + 57] - in[inPos + 56]) << 44 | (in[inPos + 58] - in[inPos + 57]) << 56;
    out[outPos + 11] = (in[inPos + 58] - in[inPos + 57]) >>> 8 | (in[inPos + 59] - in[inPos + 58]) << 4 | (in[inPos + 60] - in[inPos + 59]) << 16 | (in[inPos + 61] - in[inPos + 60]) << 28 | (in[inPos + 62] - in[inPos + 61]) << 40 | (in[inPos + 63] - in[inPos + 62]) << 52;
  }

  private static void unpack12(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 4095) + initValue;
    out[outPos + 1] = (in[inPos] >>> 12 & 4095) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 24 & 4095) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 36 & 4095) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 48 & 4095) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 60 | (in[inPos + 1] & 255) << 4) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 8 & 4095) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 20 & 4095) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 32 & 4095) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 44 & 4095) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 56 | (in[inPos + 2] & 15) << 8) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 4 & 4095) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 2] >>> 16 & 4095) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 2] >>> 28 & 4095) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 2] >>> 40 & 4095) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 2] >>> 52) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 3] & 4095) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 3] >>> 12 & 4095) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 3] >>> 24 & 4095) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 3] >>> 36 & 4095) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 3] >>> 48 & 4095) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 255) << 4) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 4] >>> 8 & 4095) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 4] >>> 20 & 4095) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 4] >>> 32 & 4095) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 4] >>> 44 & 4095) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 4] >>> 56 | (in[inPos + 5] & 15) << 8) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 5] >>> 4 & 4095) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 5] >>> 16 & 4095) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 5] >>> 28 & 4095) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 5] >>> 40 & 4095) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 5] >>> 52) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 6] & 4095) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 6] >>> 12 & 4095) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 6] >>> 24 & 4095) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 6] >>> 36 & 4095) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 6] >>> 48 & 4095) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 6] >>> 60 | (in[inPos + 7] & 255) << 4) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 7] >>> 8 & 4095) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 7] >>> 20 & 4095) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 7] >>> 32 & 4095) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 7] >>> 44 & 4095) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 15) << 8) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 8] >>> 4 & 4095) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 8] >>> 16 & 4095) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 8] >>> 28 & 4095) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 8] >>> 40 & 4095) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 8] >>> 52) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 9] & 4095) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 9] >>> 12 & 4095) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 9] >>> 24 & 4095) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 9] >>> 36 & 4095) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 9] >>> 48 & 4095) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 9] >>> 60 | (in[inPos + 10] & 255) << 4) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 10] >>> 8 & 4095) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 10] >>> 20 & 4095) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 10] >>> 32 & 4095) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 10] >>> 44 & 4095) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 10] >>> 56 | (in[inPos + 11] & 15) << 8) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 11] >>> 4 & 4095) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 11] >>> 16 & 4095) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 11] >>> 28 & 4095) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 11] >>> 40 & 4095) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 11] >>> 52) + out[outPos + 62];
  }

  private static void pack13(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 13 | (in[inPos + 2] - in[inPos + 1]) << 26 | (in[inPos + 3] - in[inPos + 2]) << 39 | (in[inPos + 4] - in[inPos + 3]) << 52;
    out[outPos + 1] = (in[inPos + 4] - in[inPos + 3]) >>> 12 | (in[inPos + 5] - in[inPos + 4]) << 1 | (in[inPos + 6] - in[inPos + 5]) << 14 | (in[inPos + 7] - in[inPos + 6]) << 27 | (in[inPos + 8] - in[inPos + 7]) << 40 | (in[inPos + 9] - in[inPos + 8]) << 53;
    out[outPos + 2] = (in[inPos + 9] - in[inPos + 8]) >>> 11 | (in[inPos + 10] - in[inPos + 9]) << 2 | (in[inPos + 11] - in[inPos + 10]) << 15 | (in[inPos + 12] - in[inPos + 11]) << 28 | (in[inPos + 13] - in[inPos + 12]) << 41 | (in[inPos + 14] - in[inPos + 13]) << 54;
    out[outPos + 3] = (in[inPos + 14] - in[inPos + 13]) >>> 10 | (in[inPos + 15] - in[inPos + 14]) << 3 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 29 | (in[inPos + 18] - in[inPos + 17]) << 42 | (in[inPos + 19] - in[inPos + 18]) << 55;
    out[outPos + 4] = (in[inPos + 19] - in[inPos + 18]) >>> 9 | (in[inPos + 20] - in[inPos + 19]) << 4 | (in[inPos + 21] - in[inPos + 20]) << 17 | (in[inPos + 22] - in[inPos + 21]) << 30 | (in[inPos + 23] - in[inPos + 22]) << 43 | (in[inPos + 24] - in[inPos + 23]) << 56;
    out[outPos + 5] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 5 | (in[inPos + 26] - in[inPos + 25]) << 18 | (in[inPos + 27] - in[inPos + 26]) << 31 | (in[inPos + 28] - in[inPos + 27]) << 44 | (in[inPos + 29] - in[inPos + 28]) << 57;
    out[outPos + 6] = (in[inPos + 29] - in[inPos + 28]) >>> 7 | (in[inPos + 30] - in[inPos + 29]) << 6 | (in[inPos + 31] - in[inPos + 30]) << 19 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 45 | (in[inPos + 34] - in[inPos + 33]) << 58;
    out[outPos + 7] = (in[inPos + 34] - in[inPos + 33]) >>> 6 | (in[inPos + 35] - in[inPos + 34]) << 7 | (in[inPos + 36] - in[inPos + 35]) << 20 | (in[inPos + 37] - in[inPos + 36]) << 33 | (in[inPos + 38] - in[inPos + 37]) << 46 | (in[inPos + 39] - in[inPos + 38]) << 59;
    out[outPos + 8] = (in[inPos + 39] - in[inPos + 38]) >>> 5 | (in[inPos + 40] - in[inPos + 39]) << 8 | (in[inPos + 41] - in[inPos + 40]) << 21 | (in[inPos + 42] - in[inPos + 41]) << 34 | (in[inPos + 43] - in[inPos + 42]) << 47 | (in[inPos + 44] - in[inPos + 43]) << 60;
    out[outPos + 9] = (in[inPos + 44] - in[inPos + 43]) >>> 4 | (in[inPos + 45] - in[inPos + 44]) << 9 | (in[inPos + 46] - in[inPos + 45]) << 22 | (in[inPos + 47] - in[inPos + 46]) << 35 | (in[inPos + 48] - in[inPos + 47]) << 48 | (in[inPos + 49] - in[inPos + 48]) << 61;
    out[outPos + 10] = (in[inPos + 49] - in[inPos + 48]) >>> 3 | (in[inPos + 50] - in[inPos + 49]) << 10 | (in[inPos + 51] - in[inPos + 50]) << 23 | (in[inPos + 52] - in[inPos + 51]) << 36 | (in[inPos + 53] - in[inPos + 52]) << 49 | (in[inPos + 54] - in[inPos + 53]) << 62;
    out[outPos + 11] = (in[inPos + 54] - in[inPos + 53]) >>> 2 | (in[inPos + 55] - in[inPos + 54]) << 11 | (in[inPos + 56] - in[inPos + 55]) << 24 | (in[inPos + 57] - in[inPos + 56]) << 37 | (in[inPos + 58] - in[inPos + 57]) << 50 | (in[inPos + 59] - in[inPos + 58]) << 63;
    out[outPos + 12] = (in[inPos + 59] - in[inPos + 58]) >>> 1 | (in[inPos + 60] - in[inPos + 59]) << 12 | (in[inPos + 61] - in[inPos + 60]) << 25 | (in[inPos + 62] - in[inPos + 61]) << 38 | (in[inPos + 63] - in[inPos + 62]) << 51;
  }

  private static void unpack13(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 8191) + initValue;
    out[outPos + 1] = (in[inPos] >>> 13 & 8191) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 26 & 8191) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 39 & 8191) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 52 | (in[inPos + 1] & 1) << 12) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 1 & 8191) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 14 & 8191) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 27 & 8191) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 40 & 8191) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 53 | (in[inPos + 2] & 3) << 11) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 2 & 8191) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 15 & 8191) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 2] >>> 28 & 8191) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 2] >>> 41 & 8191) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 2] >>> 54 | (in[inPos + 3] & 7) << 10) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 3] >>> 3 & 8191) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 3] >>> 16 & 8191) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 3] >>> 29 & 8191) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 3] >>> 42 & 8191) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 3] >>> 55 | (in[inPos + 4] & 15) << 9) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 4] >>> 4 & 8191) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 4] >>> 17 & 8191) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 4] >>> 30 & 8191) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 4] >>> 43 & 8191) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 4] >>> 56 | (in[inPos + 5] & 31) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 5] >>> 5 & 8191) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 5] >>> 18 & 8191) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 5] >>> 31 & 8191) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 5] >>> 44 & 8191) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 5] >>> 57 | (in[inPos + 6] & 63) << 7) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 6] >>> 6 & 8191) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 6] >>> 19 & 8191) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 6] >>> 32 & 8191) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 6] >>> 45 & 8191) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 6] >>> 58 | (in[inPos + 7] & 127) << 6) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 7] >>> 7 & 8191) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 7] >>> 20 & 8191) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 7] >>> 33 & 8191) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 7] >>> 46 & 8191) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 7] >>> 59 | (in[inPos + 8] & 255) << 5) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 8] >>> 8 & 8191) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 8] >>> 21 & 8191) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 8] >>> 34 & 8191) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 8] >>> 47 & 8191) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 8] >>> 60 | (in[inPos + 9] & 511) << 4) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 9] >>> 9 & 8191) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 9] >>> 22 & 8191) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 9] >>> 35 & 8191) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 9] >>> 48 & 8191) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 9] >>> 61 | (in[inPos + 10] & 1023) << 3) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 10] >>> 10 & 8191) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 10] >>> 23 & 8191) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 10] >>> 36 & 8191) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 10] >>> 49 & 8191) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 10] >>> 62 | (in[inPos + 11] & 2047) << 2) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 11] >>> 11 & 8191) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 11] >>> 24 & 8191) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 11] >>> 37 & 8191) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 11] >>> 50 & 8191) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 11] >>> 63 | (in[inPos + 12] & 4095) << 1) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 12] >>> 12 & 8191) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 12] >>> 25 & 8191) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 12] >>> 38 & 8191) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 12] >>> 51) + out[outPos + 62];
  }

  private static void pack14(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 14 | (in[inPos + 2] - in[inPos + 1]) << 28 | (in[inPos + 3] - in[inPos + 2]) << 42 | (in[inPos + 4] - in[inPos + 3]) << 56;
    out[outPos + 1] = (in[inPos + 4] - in[inPos + 3]) >>> 8 | (in[inPos + 5] - in[inPos + 4]) << 6 | (in[inPos + 6] - in[inPos + 5]) << 20 | (in[inPos + 7] - in[inPos + 6]) << 34 | (in[inPos + 8] - in[inPos + 7]) << 48 | (in[inPos + 9] - in[inPos + 8]) << 62;
    out[outPos + 2] = (in[inPos + 9] - in[inPos + 8]) >>> 2 | (in[inPos + 10] - in[inPos + 9]) << 12 | (in[inPos + 11] - in[inPos + 10]) << 26 | (in[inPos + 12] - in[inPos + 11]) << 40 | (in[inPos + 13] - in[inPos + 12]) << 54;
    out[outPos + 3] = (in[inPos + 13] - in[inPos + 12]) >>> 10 | (in[inPos + 14] - in[inPos + 13]) << 4 | (in[inPos + 15] - in[inPos + 14]) << 18 | (in[inPos + 16] - in[inPos + 15]) << 32 | (in[inPos + 17] - in[inPos + 16]) << 46 | (in[inPos + 18] - in[inPos + 17]) << 60;
    out[outPos + 4] = (in[inPos + 18] - in[inPos + 17]) >>> 4 | (in[inPos + 19] - in[inPos + 18]) << 10 | (in[inPos + 20] - in[inPos + 19]) << 24 | (in[inPos + 21] - in[inPos + 20]) << 38 | (in[inPos + 22] - in[inPos + 21]) << 52;
    out[outPos + 5] = (in[inPos + 22] - in[inPos + 21]) >>> 12 | (in[inPos + 23] - in[inPos + 22]) << 2 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 30 | (in[inPos + 26] - in[inPos + 25]) << 44 | (in[inPos + 27] - in[inPos + 26]) << 58;
    out[outPos + 6] = (in[inPos + 27] - in[inPos + 26]) >>> 6 | (in[inPos + 28] - in[inPos + 27]) << 8 | (in[inPos + 29] - in[inPos + 28]) << 22 | (in[inPos + 30] - in[inPos + 29]) << 36 | (in[inPos + 31] - in[inPos + 30]) << 50;
    out[outPos + 7] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 14 | (in[inPos + 34] - in[inPos + 33]) << 28 | (in[inPos + 35] - in[inPos + 34]) << 42 | (in[inPos + 36] - in[inPos + 35]) << 56;
    out[outPos + 8] = (in[inPos + 36] - in[inPos + 35]) >>> 8 | (in[inPos + 37] - in[inPos + 36]) << 6 | (in[inPos + 38] - in[inPos + 37]) << 20 | (in[inPos + 39] - in[inPos + 38]) << 34 | (in[inPos + 40] - in[inPos + 39]) << 48 | (in[inPos + 41] - in[inPos + 40]) << 62;
    out[outPos + 9] = (in[inPos + 41] - in[inPos + 40]) >>> 2 | (in[inPos + 42] - in[inPos + 41]) << 12 | (in[inPos + 43] - in[inPos + 42]) << 26 | (in[inPos + 44] - in[inPos + 43]) << 40 | (in[inPos + 45] - in[inPos + 44]) << 54;
    out[outPos + 10] = (in[inPos + 45] - in[inPos + 44]) >>> 10 | (in[inPos + 46] - in[inPos + 45]) << 4 | (in[inPos + 47] - in[inPos + 46]) << 18 | (in[inPos + 48] - in[inPos + 47]) << 32 | (in[inPos + 49] - in[inPos + 48]) << 46 | (in[inPos + 50] - in[inPos + 49]) << 60;
    out[outPos + 11] = (in[inPos + 50] - in[inPos + 49]) >>> 4 | (in[inPos + 51] - in[inPos + 50]) << 10 | (in[inPos + 52] - in[inPos + 51]) << 24 | (in[inPos + 53] - in[inPos + 52]) << 38 | (in[inPos + 54] - in[inPos + 53]) << 52;
    out[outPos + 12] = (in[inPos + 54] - in[inPos + 53]) >>> 12 | (in[inPos + 55] - in[inPos + 54]) << 2 | (in[inPos + 56] - in[inPos + 55]) << 16 | (in[inPos + 57] - in[inPos + 56]) << 30 | (in[inPos + 58] - in[inPos + 57]) << 44 | (in[inPos + 59] - in[inPos + 58]) << 58;
    out[outPos + 13] = (in[inPos + 59] - in[inPos + 58]) >>> 6 | (in[inPos + 60] - in[inPos + 59]) << 8 | (in[inPos + 61] - in[inPos + 60]) << 22 | (in[inPos + 62] - in[inPos + 61]) << 36 | (in[inPos + 63] - in[inPos + 62]) << 50;
  }

  private static void unpack14(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 16383) + initValue;
    out[outPos + 1] = (in[inPos] >>> 14 & 16383) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 28 & 16383) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 42 & 16383) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 56 | (in[inPos + 1] & 63) << 8) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 6 & 16383) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 20 & 16383) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 34 & 16383) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 48 & 16383) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 4095) << 2) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 12 & 16383) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 26 & 16383) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 2] >>> 40 & 16383) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 2] >>> 54 | (in[inPos + 3] & 15) << 10) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 3] >>> 4 & 16383) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 3] >>> 18 & 16383) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 3] >>> 32 & 16383) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 3] >>> 46 & 16383) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 1023) << 4) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 4] >>> 10 & 16383) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 4] >>> 24 & 16383) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 4] >>> 38 & 16383) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 4] >>> 52 | (in[inPos + 5] & 3) << 12) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 5] >>> 2 & 16383) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 5] >>> 16 & 16383) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 5] >>> 30 & 16383) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 5] >>> 44 & 16383) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 255) << 6) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 6] >>> 8 & 16383) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 6] >>> 22 & 16383) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 6] >>> 36 & 16383) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 6] >>> 50) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 7] & 16383) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 7] >>> 14 & 16383) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 7] >>> 28 & 16383) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 7] >>> 42 & 16383) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 63) << 8) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 8] >>> 6 & 16383) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 8] >>> 20 & 16383) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 8] >>> 34 & 16383) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 8] >>> 48 & 16383) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 8] >>> 62 | (in[inPos + 9] & 4095) << 2) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 9] >>> 12 & 16383) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 9] >>> 26 & 16383) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 9] >>> 40 & 16383) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 15) << 10) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 10] >>> 4 & 16383) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 10] >>> 18 & 16383) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 10] >>> 32 & 16383) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 10] >>> 46 & 16383) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 10] >>> 60 | (in[inPos + 11] & 1023) << 4) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 11] >>> 10 & 16383) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 11] >>> 24 & 16383) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 11] >>> 38 & 16383) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 3) << 12) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 12] >>> 2 & 16383) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 12] >>> 16 & 16383) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 12] >>> 30 & 16383) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 12] >>> 44 & 16383) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 12] >>> 58 | (in[inPos + 13] & 255) << 6) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 13] >>> 8 & 16383) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 13] >>> 22 & 16383) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 13] >>> 36 & 16383) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 13] >>> 50) + out[outPos + 62];
  }

  private static void pack15(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 15 | (in[inPos + 2] - in[inPos + 1]) << 30 | (in[inPos + 3] - in[inPos + 2]) << 45 | (in[inPos + 4] - in[inPos + 3]) << 60;
    out[outPos + 1] = (in[inPos + 4] - in[inPos + 3]) >>> 4 | (in[inPos + 5] - in[inPos + 4]) << 11 | (in[inPos + 6] - in[inPos + 5]) << 26 | (in[inPos + 7] - in[inPos + 6]) << 41 | (in[inPos + 8] - in[inPos + 7]) << 56;
    out[outPos + 2] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 7 | (in[inPos + 10] - in[inPos + 9]) << 22 | (in[inPos + 11] - in[inPos + 10]) << 37 | (in[inPos + 12] - in[inPos + 11]) << 52;
    out[outPos + 3] = (in[inPos + 12] - in[inPos + 11]) >>> 12 | (in[inPos + 13] - in[inPos + 12]) << 3 | (in[inPos + 14] - in[inPos + 13]) << 18 | (in[inPos + 15] - in[inPos + 14]) << 33 | (in[inPos + 16] - in[inPos + 15]) << 48 | (in[inPos + 17] - in[inPos + 16]) << 63;
    out[outPos + 4] = (in[inPos + 17] - in[inPos + 16]) >>> 1 | (in[inPos + 18] - in[inPos + 17]) << 14 | (in[inPos + 19] - in[inPos + 18]) << 29 | (in[inPos + 20] - in[inPos + 19]) << 44 | (in[inPos + 21] - in[inPos + 20]) << 59;
    out[outPos + 5] = (in[inPos + 21] - in[inPos + 20]) >>> 5 | (in[inPos + 22] - in[inPos + 21]) << 10 | (in[inPos + 23] - in[inPos + 22]) << 25 | (in[inPos + 24] - in[inPos + 23]) << 40 | (in[inPos + 25] - in[inPos + 24]) << 55;
    out[outPos + 6] = (in[inPos + 25] - in[inPos + 24]) >>> 9 | (in[inPos + 26] - in[inPos + 25]) << 6 | (in[inPos + 27] - in[inPos + 26]) << 21 | (in[inPos + 28] - in[inPos + 27]) << 36 | (in[inPos + 29] - in[inPos + 28]) << 51;
    out[outPos + 7] = (in[inPos + 29] - in[inPos + 28]) >>> 13 | (in[inPos + 30] - in[inPos + 29]) << 2 | (in[inPos + 31] - in[inPos + 30]) << 17 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 47 | (in[inPos + 34] - in[inPos + 33]) << 62;
    out[outPos + 8] = (in[inPos + 34] - in[inPos + 33]) >>> 2 | (in[inPos + 35] - in[inPos + 34]) << 13 | (in[inPos + 36] - in[inPos + 35]) << 28 | (in[inPos + 37] - in[inPos + 36]) << 43 | (in[inPos + 38] - in[inPos + 37]) << 58;
    out[outPos + 9] = (in[inPos + 38] - in[inPos + 37]) >>> 6 | (in[inPos + 39] - in[inPos + 38]) << 9 | (in[inPos + 40] - in[inPos + 39]) << 24 | (in[inPos + 41] - in[inPos + 40]) << 39 | (in[inPos + 42] - in[inPos + 41]) << 54;
    out[outPos + 10] = (in[inPos + 42] - in[inPos + 41]) >>> 10 | (in[inPos + 43] - in[inPos + 42]) << 5 | (in[inPos + 44] - in[inPos + 43]) << 20 | (in[inPos + 45] - in[inPos + 44]) << 35 | (in[inPos + 46] - in[inPos + 45]) << 50;
    out[outPos + 11] = (in[inPos + 46] - in[inPos + 45]) >>> 14 | (in[inPos + 47] - in[inPos + 46]) << 1 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 31 | (in[inPos + 50] - in[inPos + 49]) << 46 | (in[inPos + 51] - in[inPos + 50]) << 61;
    out[outPos + 12] = (in[inPos + 51] - in[inPos + 50]) >>> 3 | (in[inPos + 52] - in[inPos + 51]) << 12 | (in[inPos + 53] - in[inPos + 52]) << 27 | (in[inPos + 54] - in[inPos + 53]) << 42 | (in[inPos + 55] - in[inPos + 54]) << 57;
    out[outPos + 13] = (in[inPos + 55] - in[inPos + 54]) >>> 7 | (in[inPos + 56] - in[inPos + 55]) << 8 | (in[inPos + 57] - in[inPos + 56]) << 23 | (in[inPos + 58] - in[inPos + 57]) << 38 | (in[inPos + 59] - in[inPos + 58]) << 53;
    out[outPos + 14] = (in[inPos + 59] - in[inPos + 58]) >>> 11 | (in[inPos + 60] - in[inPos + 59]) << 4 | (in[inPos + 61] - in[inPos + 60]) << 19 | (in[inPos + 62] - in[inPos + 61]) << 34 | (in[inPos + 63] - in[inPos + 62]) << 49;
  }

  private static void unpack15(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 32767) + initValue;
    out[outPos + 1] = (in[inPos] >>> 15 & 32767) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 30 & 32767) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 45 & 32767) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 60 | (in[inPos + 1] & 2047) << 4) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 11 & 32767) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 26 & 32767) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 41 & 32767) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 56 | (in[inPos + 2] & 127) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 7 & 32767) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 22 & 32767) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 37 & 32767) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 2] >>> 52 | (in[inPos + 3] & 7) << 12) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 3] >>> 3 & 32767) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 3] >>> 18 & 32767) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 3] >>> 33 & 32767) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 3] >>> 48 & 32767) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 3] >>> 63 | (in[inPos + 4] & 16383) << 1) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 4] >>> 14 & 32767) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 4] >>> 29 & 32767) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 4] >>> 44 & 32767) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 4] >>> 59 | (in[inPos + 5] & 1023) << 5) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 5] >>> 10 & 32767) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 5] >>> 25 & 32767) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 5] >>> 40 & 32767) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 5] >>> 55 | (in[inPos + 6] & 63) << 9) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 6] >>> 6 & 32767) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 6] >>> 21 & 32767) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 6] >>> 36 & 32767) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 6] >>> 51 | (in[inPos + 7] & 3) << 13) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 7] >>> 2 & 32767) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 7] >>> 17 & 32767) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 7] >>> 32 & 32767) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 7] >>> 47 & 32767) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 7] >>> 62 | (in[inPos + 8] & 8191) << 2) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 8] >>> 13 & 32767) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 8] >>> 28 & 32767) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 8] >>> 43 & 32767) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 8] >>> 58 | (in[inPos + 9] & 511) << 6) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 9] >>> 9 & 32767) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 9] >>> 24 & 32767) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 9] >>> 39 & 32767) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 31) << 10) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 10] >>> 5 & 32767) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 10] >>> 20 & 32767) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 10] >>> 35 & 32767) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 10] >>> 50 | (in[inPos + 11] & 1) << 14) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 11] >>> 1 & 32767) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 11] >>> 16 & 32767) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 11] >>> 31 & 32767) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 11] >>> 46 & 32767) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 11] >>> 61 | (in[inPos + 12] & 4095) << 3) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 12] >>> 12 & 32767) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 12] >>> 27 & 32767) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 12] >>> 42 & 32767) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 12] >>> 57 | (in[inPos + 13] & 255) << 7) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 13] >>> 8 & 32767) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 13] >>> 23 & 32767) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 13] >>> 38 & 32767) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 13] >>> 53 | (in[inPos + 14] & 15) << 11) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 14] >>> 4 & 32767) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 14] >>> 19 & 32767) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 14] >>> 34 & 32767) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 14] >>> 49) + out[outPos + 62];
  }

  private static void pack16(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 16 | (in[inPos + 2] - in[inPos + 1]) << 32 | (in[inPos + 3] - in[inPos + 2]) << 48;
    out[outPos + 1] = (in[inPos + 4] - in[inPos + 3]) | (in[inPos + 5] - in[inPos + 4]) << 16 | (in[inPos + 6] - in[inPos + 5]) << 32 | (in[inPos + 7] - in[inPos + 6]) << 48;
    out[outPos + 2] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 16 | (in[inPos + 10] - in[inPos + 9]) << 32 | (in[inPos + 11] - in[inPos + 10]) << 48;
    out[outPos + 3] = (in[inPos + 12] - in[inPos + 11]) | (in[inPos + 13] - in[inPos + 12]) << 16 | (in[inPos + 14] - in[inPos + 13]) << 32 | (in[inPos + 15] - in[inPos + 14]) << 48;
    out[outPos + 4] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 16 | (in[inPos + 18] - in[inPos + 17]) << 32 | (in[inPos + 19] - in[inPos + 18]) << 48;
    out[outPos + 5] = (in[inPos + 20] - in[inPos + 19]) | (in[inPos + 21] - in[inPos + 20]) << 16 | (in[inPos + 22] - in[inPos + 21]) << 32 | (in[inPos + 23] - in[inPos + 22]) << 48;
    out[outPos + 6] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 16 | (in[inPos + 26] - in[inPos + 25]) << 32 | (in[inPos + 27] - in[inPos + 26]) << 48;
    out[outPos + 7] = (in[inPos + 28] - in[inPos + 27]) | (in[inPos + 29] - in[inPos + 28]) << 16 | (in[inPos + 30] - in[inPos + 29]) << 32 | (in[inPos + 31] - in[inPos + 30]) << 48;
    out[outPos + 8] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 16 | (in[inPos + 34] - in[inPos + 33]) << 32 | (in[inPos + 35] - in[inPos + 34]) << 48;
    out[outPos + 9] = (in[inPos + 36] - in[inPos + 35]) | (in[inPos + 37] - in[inPos + 36]) << 16 | (in[inPos + 38] - in[inPos + 37]) << 32 | (in[inPos + 39] - in[inPos + 38]) << 48;
    out[outPos + 10] = (in[inPos + 40] - in[inPos + 39]) | (in[inPos + 41] - in[inPos + 40]) << 16 | (in[inPos + 42] - in[inPos + 41]) << 32 | (in[inPos + 43] - in[inPos + 42]) << 48;
    out[outPos + 11] = (in[inPos + 44] - in[inPos + 43]) | (in[inPos + 45] - in[inPos + 44]) << 16 | (in[inPos + 46] - in[inPos + 45]) << 32 | (in[inPos + 47] - in[inPos + 46]) << 48;
    out[outPos + 12] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 16 | (in[inPos + 50] - in[inPos + 49]) << 32 | (in[inPos + 51] - in[inPos + 50]) << 48;
    out[outPos + 13] = (in[inPos + 52] - in[inPos + 51]) | (in[inPos + 53] - in[inPos + 52]) << 16 | (in[inPos + 54] - in[inPos + 53]) << 32 | (in[inPos + 55] - in[inPos + 54]) << 48;
    out[outPos + 14] = (in[inPos + 56] - in[inPos + 55]) | (in[inPos + 57] - in[inPos + 56]) << 16 | (in[inPos + 58] - in[inPos + 57]) << 32 | (in[inPos + 59] - in[inPos + 58]) << 48;
    out[outPos + 15] = (in[inPos + 60] - in[inPos + 59]) | (in[inPos + 61] - in[inPos + 60]) << 16 | (in[inPos + 62] - in[inPos + 61]) << 32 | (in[inPos + 63] - in[inPos + 62]) << 48;
  }

  private static void unpack16(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 65535) + initValue;
    out[outPos + 1] = (in[inPos] >>> 16 & 65535) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 32 & 65535) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 48) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] & 65535) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 16 & 65535) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 32 & 65535) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 48) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] & 65535) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 16 & 65535) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 32 & 65535) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 48) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] & 65535) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 3] >>> 16 & 65535) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 3] >>> 32 & 65535) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 3] >>> 48) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 4] & 65535) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 4] >>> 16 & 65535) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 4] >>> 32 & 65535) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 4] >>> 48) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 5] & 65535) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 5] >>> 16 & 65535) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 5] >>> 32 & 65535) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 5] >>> 48) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 6] & 65535) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 6] >>> 16 & 65535) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 6] >>> 32 & 65535) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 6] >>> 48) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 7] & 65535) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 7] >>> 16 & 65535) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 7] >>> 32 & 65535) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 7] >>> 48) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 8] & 65535) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 8] >>> 16 & 65535) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 8] >>> 32 & 65535) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 8] >>> 48) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 9] & 65535) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 9] >>> 16 & 65535) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 9] >>> 32 & 65535) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 9] >>> 48) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 10] & 65535) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 10] >>> 16 & 65535) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 10] >>> 32 & 65535) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 10] >>> 48) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 11] & 65535) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 11] >>> 16 & 65535) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 11] >>> 32 & 65535) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 11] >>> 48) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 12] & 65535) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 12] >>> 16 & 65535) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 12] >>> 32 & 65535) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 12] >>> 48) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 13] & 65535) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 13] >>> 16 & 65535) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 13] >>> 32 & 65535) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 13] >>> 48) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 14] & 65535) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 14] >>> 16 & 65535) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 14] >>> 32 & 65535) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 14] >>> 48) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 15] & 65535) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 15] >>> 16 & 65535) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 15] >>> 32 & 65535) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 15] >>> 48) + out[outPos + 62];
  }

  private static void pack17(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 17 | (in[inPos + 2] - in[inPos + 1]) << 34 | (in[inPos + 3] - in[inPos + 2]) << 51;
    out[outPos + 1] = (in[inPos + 3] - in[inPos + 2]) >>> 13 | (in[inPos + 4] - in[inPos + 3]) << 4 | (in[inPos + 5] - in[inPos + 4]) << 21 | (in[inPos + 6] - in[inPos + 5]) << 38 | (in[inPos + 7] - in[inPos + 6]) << 55;
    out[outPos + 2] = (in[inPos + 7] - in[inPos + 6]) >>> 9 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 25 | (in[inPos + 10] - in[inPos + 9]) << 42 | (in[inPos + 11] - in[inPos + 10]) << 59;
    out[outPos + 3] = (in[inPos + 11] - in[inPos + 10]) >>> 5 | (in[inPos + 12] - in[inPos + 11]) << 12 | (in[inPos + 13] - in[inPos + 12]) << 29 | (in[inPos + 14] - in[inPos + 13]) << 46 | (in[inPos + 15] - in[inPos + 14]) << 63;
    out[outPos + 4] = (in[inPos + 15] - in[inPos + 14]) >>> 1 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 33 | (in[inPos + 18] - in[inPos + 17]) << 50;
    out[outPos + 5] = (in[inPos + 18] - in[inPos + 17]) >>> 14 | (in[inPos + 19] - in[inPos + 18]) << 3 | (in[inPos + 20] - in[inPos + 19]) << 20 | (in[inPos + 21] - in[inPos + 20]) << 37 | (in[inPos + 22] - in[inPos + 21]) << 54;
    out[outPos + 6] = (in[inPos + 22] - in[inPos + 21]) >>> 10 | (in[inPos + 23] - in[inPos + 22]) << 7 | (in[inPos + 24] - in[inPos + 23]) << 24 | (in[inPos + 25] - in[inPos + 24]) << 41 | (in[inPos + 26] - in[inPos + 25]) << 58;
    out[outPos + 7] = (in[inPos + 26] - in[inPos + 25]) >>> 6 | (in[inPos + 27] - in[inPos + 26]) << 11 | (in[inPos + 28] - in[inPos + 27]) << 28 | (in[inPos + 29] - in[inPos + 28]) << 45 | (in[inPos + 30] - in[inPos + 29]) << 62;
    out[outPos + 8] = (in[inPos + 30] - in[inPos + 29]) >>> 2 | (in[inPos + 31] - in[inPos + 30]) << 15 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 49;
    out[outPos + 9] = (in[inPos + 33] - in[inPos + 32]) >>> 15 | (in[inPos + 34] - in[inPos + 33]) << 2 | (in[inPos + 35] - in[inPos + 34]) << 19 | (in[inPos + 36] - in[inPos + 35]) << 36 | (in[inPos + 37] - in[inPos + 36]) << 53;
    out[outPos + 10] = (in[inPos + 37] - in[inPos + 36]) >>> 11 | (in[inPos + 38] - in[inPos + 37]) << 6 | (in[inPos + 39] - in[inPos + 38]) << 23 | (in[inPos + 40] - in[inPos + 39]) << 40 | (in[inPos + 41] - in[inPos + 40]) << 57;
    out[outPos + 11] = (in[inPos + 41] - in[inPos + 40]) >>> 7 | (in[inPos + 42] - in[inPos + 41]) << 10 | (in[inPos + 43] - in[inPos + 42]) << 27 | (in[inPos + 44] - in[inPos + 43]) << 44 | (in[inPos + 45] - in[inPos + 44]) << 61;
    out[outPos + 12] = (in[inPos + 45] - in[inPos + 44]) >>> 3 | (in[inPos + 46] - in[inPos + 45]) << 14 | (in[inPos + 47] - in[inPos + 46]) << 31 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 13] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 1 | (in[inPos + 50] - in[inPos + 49]) << 18 | (in[inPos + 51] - in[inPos + 50]) << 35 | (in[inPos + 52] - in[inPos + 51]) << 52;
    out[outPos + 14] = (in[inPos + 52] - in[inPos + 51]) >>> 12 | (in[inPos + 53] - in[inPos + 52]) << 5 | (in[inPos + 54] - in[inPos + 53]) << 22 | (in[inPos + 55] - in[inPos + 54]) << 39 | (in[inPos + 56] - in[inPos + 55]) << 56;
    out[outPos + 15] = (in[inPos + 56] - in[inPos + 55]) >>> 8 | (in[inPos + 57] - in[inPos + 56]) << 9 | (in[inPos + 58] - in[inPos + 57]) << 26 | (in[inPos + 59] - in[inPos + 58]) << 43 | (in[inPos + 60] - in[inPos + 59]) << 60;
    out[outPos + 16] = (in[inPos + 60] - in[inPos + 59]) >>> 4 | (in[inPos + 61] - in[inPos + 60]) << 13 | (in[inPos + 62] - in[inPos + 61]) << 30 | (in[inPos + 63] - in[inPos + 62]) << 47;
  }

  private static void unpack17(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 131071) + initValue;
    out[outPos + 1] = (in[inPos] >>> 17 & 131071) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 34 & 131071) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 51 | (in[inPos + 1] & 15) << 13) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 4 & 131071) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 21 & 131071) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 38 & 131071) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 55 | (in[inPos + 2] & 255) << 9) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 8 & 131071) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 25 & 131071) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 42 & 131071) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 59 | (in[inPos + 3] & 4095) << 5) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] >>> 12 & 131071) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 3] >>> 29 & 131071) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 3] >>> 46 & 131071) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 3] >>> 63 | (in[inPos + 4] & 65535) << 1) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 4] >>> 16 & 131071) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 4] >>> 33 & 131071) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 4] >>> 50 | (in[inPos + 5] & 7) << 14) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 5] >>> 3 & 131071) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 5] >>> 20 & 131071) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 5] >>> 37 & 131071) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 5] >>> 54 | (in[inPos + 6] & 127) << 10) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 6] >>> 7 & 131071) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 6] >>> 24 & 131071) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 6] >>> 41 & 131071) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 6] >>> 58 | (in[inPos + 7] & 2047) << 6) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 7] >>> 11 & 131071) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 7] >>> 28 & 131071) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 7] >>> 45 & 131071) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 7] >>> 62 | (in[inPos + 8] & 32767) << 2) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 8] >>> 15 & 131071) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 8] >>> 32 & 131071) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 8] >>> 49 | (in[inPos + 9] & 3) << 15) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 9] >>> 2 & 131071) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 9] >>> 19 & 131071) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 9] >>> 36 & 131071) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 9] >>> 53 | (in[inPos + 10] & 63) << 11) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 10] >>> 6 & 131071) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 10] >>> 23 & 131071) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 10] >>> 40 & 131071) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 10] >>> 57 | (in[inPos + 11] & 1023) << 7) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 11] >>> 10 & 131071) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 11] >>> 27 & 131071) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 11] >>> 44 & 131071) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 11] >>> 61 | (in[inPos + 12] & 16383) << 3) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 12] >>> 14 & 131071) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 12] >>> 31 & 131071) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 12] >>> 48 | (in[inPos + 13] & 1) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 13] >>> 1 & 131071) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 13] >>> 18 & 131071) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 13] >>> 35 & 131071) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 13] >>> 52 | (in[inPos + 14] & 31) << 12) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 14] >>> 5 & 131071) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 14] >>> 22 & 131071) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 14] >>> 39 & 131071) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 14] >>> 56 | (in[inPos + 15] & 511) << 8) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 15] >>> 9 & 131071) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 15] >>> 26 & 131071) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 15] >>> 43 & 131071) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 15] >>> 60 | (in[inPos + 16] & 8191) << 4) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 16] >>> 13 & 131071) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 16] >>> 30 & 131071) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 16] >>> 47) + out[outPos + 62];
  }

  private static void pack18(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 18 | (in[inPos + 2] - in[inPos + 1]) << 36 | (in[inPos + 3] - in[inPos + 2]) << 54;
    out[outPos + 1] = (in[inPos + 3] - in[inPos + 2]) >>> 10 | (in[inPos + 4] - in[inPos + 3]) << 8 | (in[inPos + 5] - in[inPos + 4]) << 26 | (in[inPos + 6] - in[inPos + 5]) << 44 | (in[inPos + 7] - in[inPos + 6]) << 62;
    out[outPos + 2] = (in[inPos + 7] - in[inPos + 6]) >>> 2 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 34 | (in[inPos + 10] - in[inPos + 9]) << 52;
    out[outPos + 3] = (in[inPos + 10] - in[inPos + 9]) >>> 12 | (in[inPos + 11] - in[inPos + 10]) << 6 | (in[inPos + 12] - in[inPos + 11]) << 24 | (in[inPos + 13] - in[inPos + 12]) << 42 | (in[inPos + 14] - in[inPos + 13]) << 60;
    out[outPos + 4] = (in[inPos + 14] - in[inPos + 13]) >>> 4 | (in[inPos + 15] - in[inPos + 14]) << 14 | (in[inPos + 16] - in[inPos + 15]) << 32 | (in[inPos + 17] - in[inPos + 16]) << 50;
    out[outPos + 5] = (in[inPos + 17] - in[inPos + 16]) >>> 14 | (in[inPos + 18] - in[inPos + 17]) << 4 | (in[inPos + 19] - in[inPos + 18]) << 22 | (in[inPos + 20] - in[inPos + 19]) << 40 | (in[inPos + 21] - in[inPos + 20]) << 58;
    out[outPos + 6] = (in[inPos + 21] - in[inPos + 20]) >>> 6 | (in[inPos + 22] - in[inPos + 21]) << 12 | (in[inPos + 23] - in[inPos + 22]) << 30 | (in[inPos + 24] - in[inPos + 23]) << 48;
    out[outPos + 7] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 2 | (in[inPos + 26] - in[inPos + 25]) << 20 | (in[inPos + 27] - in[inPos + 26]) << 38 | (in[inPos + 28] - in[inPos + 27]) << 56;
    out[outPos + 8] = (in[inPos + 28] - in[inPos + 27]) >>> 8 | (in[inPos + 29] - in[inPos + 28]) << 10 | (in[inPos + 30] - in[inPos + 29]) << 28 | (in[inPos + 31] - in[inPos + 30]) << 46;
    out[outPos + 9] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 18 | (in[inPos + 34] - in[inPos + 33]) << 36 | (in[inPos + 35] - in[inPos + 34]) << 54;
    out[outPos + 10] = (in[inPos + 35] - in[inPos + 34]) >>> 10 | (in[inPos + 36] - in[inPos + 35]) << 8 | (in[inPos + 37] - in[inPos + 36]) << 26 | (in[inPos + 38] - in[inPos + 37]) << 44 | (in[inPos + 39] - in[inPos + 38]) << 62;
    out[outPos + 11] = (in[inPos + 39] - in[inPos + 38]) >>> 2 | (in[inPos + 40] - in[inPos + 39]) << 16 | (in[inPos + 41] - in[inPos + 40]) << 34 | (in[inPos + 42] - in[inPos + 41]) << 52;
    out[outPos + 12] = (in[inPos + 42] - in[inPos + 41]) >>> 12 | (in[inPos + 43] - in[inPos + 42]) << 6 | (in[inPos + 44] - in[inPos + 43]) << 24 | (in[inPos + 45] - in[inPos + 44]) << 42 | (in[inPos + 46] - in[inPos + 45]) << 60;
    out[outPos + 13] = (in[inPos + 46] - in[inPos + 45]) >>> 4 | (in[inPos + 47] - in[inPos + 46]) << 14 | (in[inPos + 48] - in[inPos + 47]) << 32 | (in[inPos + 49] - in[inPos + 48]) << 50;
    out[outPos + 14] = (in[inPos + 49] - in[inPos + 48]) >>> 14 | (in[inPos + 50] - in[inPos + 49]) << 4 | (in[inPos + 51] - in[inPos + 50]) << 22 | (in[inPos + 52] - in[inPos + 51]) << 40 | (in[inPos + 53] - in[inPos + 52]) << 58;
    out[outPos + 15] = (in[inPos + 53] - in[inPos + 52]) >>> 6 | (in[inPos + 54] - in[inPos + 53]) << 12 | (in[inPos + 55] - in[inPos + 54]) << 30 | (in[inPos + 56] - in[inPos + 55]) << 48;
    out[outPos + 16] = (in[inPos + 56] - in[inPos + 55]) >>> 16 | (in[inPos + 57] - in[inPos + 56]) << 2 | (in[inPos + 58] - in[inPos + 57]) << 20 | (in[inPos + 59] - in[inPos + 58]) << 38 | (in[inPos + 60] - in[inPos + 59]) << 56;
    out[outPos + 17] = (in[inPos + 60] - in[inPos + 59]) >>> 8 | (in[inPos + 61] - in[inPos + 60]) << 10 | (in[inPos + 62] - in[inPos + 61]) << 28 | (in[inPos + 63] - in[inPos + 62]) << 46;
  }

  private static void unpack18(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 262143) + initValue;
    out[outPos + 1] = (in[inPos] >>> 18 & 262143) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 36 & 262143) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 54 | (in[inPos + 1] & 255) << 10) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 8 & 262143) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 26 & 262143) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 44 & 262143) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 65535) << 2) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 16 & 262143) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 34 & 262143) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 52 | (in[inPos + 3] & 63) << 12) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 6 & 262143) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] >>> 24 & 262143) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 3] >>> 42 & 262143) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 16383) << 4) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 4] >>> 14 & 262143) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 4] >>> 32 & 262143) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 4] >>> 50 | (in[inPos + 5] & 15) << 14) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 5] >>> 4 & 262143) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 5] >>> 22 & 262143) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 5] >>> 40 & 262143) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 4095) << 6) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 6] >>> 12 & 262143) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 6] >>> 30 & 262143) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 6] >>> 48 | (in[inPos + 7] & 3) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 7] >>> 2 & 262143) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 7] >>> 20 & 262143) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 7] >>> 38 & 262143) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 1023) << 8) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 8] >>> 10 & 262143) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 8] >>> 28 & 262143) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 8] >>> 46) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 9] & 262143) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 9] >>> 18 & 262143) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 9] >>> 36 & 262143) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 255) << 10) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 10] >>> 8 & 262143) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 10] >>> 26 & 262143) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 10] >>> 44 & 262143) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 10] >>> 62 | (in[inPos + 11] & 65535) << 2) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 11] >>> 16 & 262143) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 11] >>> 34 & 262143) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 63) << 12) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 12] >>> 6 & 262143) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 12] >>> 24 & 262143) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 12] >>> 42 & 262143) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 12] >>> 60 | (in[inPos + 13] & 16383) << 4) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 13] >>> 14 & 262143) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 13] >>> 32 & 262143) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 13] >>> 50 | (in[inPos + 14] & 15) << 14) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 14] >>> 4 & 262143) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 14] >>> 22 & 262143) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 14] >>> 40 & 262143) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 14] >>> 58 | (in[inPos + 15] & 4095) << 6) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 15] >>> 12 & 262143) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 15] >>> 30 & 262143) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 3) << 16) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 16] >>> 2 & 262143) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 16] >>> 20 & 262143) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 16] >>> 38 & 262143) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 1023) << 8) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 17] >>> 10 & 262143) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 17] >>> 28 & 262143) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 17] >>> 46) + out[outPos + 62];
  }

  private static void pack19(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 19 | (in[inPos + 2] - in[inPos + 1]) << 38 | (in[inPos + 3] - in[inPos + 2]) << 57;
    out[outPos + 1] = (in[inPos + 3] - in[inPos + 2]) >>> 7 | (in[inPos + 4] - in[inPos + 3]) << 12 | (in[inPos + 5] - in[inPos + 4]) << 31 | (in[inPos + 6] - in[inPos + 5]) << 50;
    out[outPos + 2] = (in[inPos + 6] - in[inPos + 5]) >>> 14 | (in[inPos + 7] - in[inPos + 6]) << 5 | (in[inPos + 8] - in[inPos + 7]) << 24 | (in[inPos + 9] - in[inPos + 8]) << 43 | (in[inPos + 10] - in[inPos + 9]) << 62;
    out[outPos + 3] = (in[inPos + 10] - in[inPos + 9]) >>> 2 | (in[inPos + 11] - in[inPos + 10]) << 17 | (in[inPos + 12] - in[inPos + 11]) << 36 | (in[inPos + 13] - in[inPos + 12]) << 55;
    out[outPos + 4] = (in[inPos + 13] - in[inPos + 12]) >>> 9 | (in[inPos + 14] - in[inPos + 13]) << 10 | (in[inPos + 15] - in[inPos + 14]) << 29 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 5] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 3 | (in[inPos + 18] - in[inPos + 17]) << 22 | (in[inPos + 19] - in[inPos + 18]) << 41 | (in[inPos + 20] - in[inPos + 19]) << 60;
    out[outPos + 6] = (in[inPos + 20] - in[inPos + 19]) >>> 4 | (in[inPos + 21] - in[inPos + 20]) << 15 | (in[inPos + 22] - in[inPos + 21]) << 34 | (in[inPos + 23] - in[inPos + 22]) << 53;
    out[outPos + 7] = (in[inPos + 23] - in[inPos + 22]) >>> 11 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 27 | (in[inPos + 26] - in[inPos + 25]) << 46;
    out[outPos + 8] = (in[inPos + 26] - in[inPos + 25]) >>> 18 | (in[inPos + 27] - in[inPos + 26]) << 1 | (in[inPos + 28] - in[inPos + 27]) << 20 | (in[inPos + 29] - in[inPos + 28]) << 39 | (in[inPos + 30] - in[inPos + 29]) << 58;
    out[outPos + 9] = (in[inPos + 30] - in[inPos + 29]) >>> 6 | (in[inPos + 31] - in[inPos + 30]) << 13 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 51;
    out[outPos + 10] = (in[inPos + 33] - in[inPos + 32]) >>> 13 | (in[inPos + 34] - in[inPos + 33]) << 6 | (in[inPos + 35] - in[inPos + 34]) << 25 | (in[inPos + 36] - in[inPos + 35]) << 44 | (in[inPos + 37] - in[inPos + 36]) << 63;
    out[outPos + 11] = (in[inPos + 37] - in[inPos + 36]) >>> 1 | (in[inPos + 38] - in[inPos + 37]) << 18 | (in[inPos + 39] - in[inPos + 38]) << 37 | (in[inPos + 40] - in[inPos + 39]) << 56;
    out[outPos + 12] = (in[inPos + 40] - in[inPos + 39]) >>> 8 | (in[inPos + 41] - in[inPos + 40]) << 11 | (in[inPos + 42] - in[inPos + 41]) << 30 | (in[inPos + 43] - in[inPos + 42]) << 49;
    out[outPos + 13] = (in[inPos + 43] - in[inPos + 42]) >>> 15 | (in[inPos + 44] - in[inPos + 43]) << 4 | (in[inPos + 45] - in[inPos + 44]) << 23 | (in[inPos + 46] - in[inPos + 45]) << 42 | (in[inPos + 47] - in[inPos + 46]) << 61;
    out[outPos + 14] = (in[inPos + 47] - in[inPos + 46]) >>> 3 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 35 | (in[inPos + 50] - in[inPos + 49]) << 54;
    out[outPos + 15] = (in[inPos + 50] - in[inPos + 49]) >>> 10 | (in[inPos + 51] - in[inPos + 50]) << 9 | (in[inPos + 52] - in[inPos + 51]) << 28 | (in[inPos + 53] - in[inPos + 52]) << 47;
    out[outPos + 16] = (in[inPos + 53] - in[inPos + 52]) >>> 17 | (in[inPos + 54] - in[inPos + 53]) << 2 | (in[inPos + 55] - in[inPos + 54]) << 21 | (in[inPos + 56] - in[inPos + 55]) << 40 | (in[inPos + 57] - in[inPos + 56]) << 59;
    out[outPos + 17] = (in[inPos + 57] - in[inPos + 56]) >>> 5 | (in[inPos + 58] - in[inPos + 57]) << 14 | (in[inPos + 59] - in[inPos + 58]) << 33 | (in[inPos + 60] - in[inPos + 59]) << 52;
    out[outPos + 18] = (in[inPos + 60] - in[inPos + 59]) >>> 12 | (in[inPos + 61] - in[inPos + 60]) << 7 | (in[inPos + 62] - in[inPos + 61]) << 26 | (in[inPos + 63] - in[inPos + 62]) << 45;
  }

  private static void unpack19(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 524287) + initValue;
    out[outPos + 1] = (in[inPos] >>> 19 & 524287) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 38 & 524287) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 57 | (in[inPos + 1] & 4095) << 7) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 12 & 524287) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 31 & 524287) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 50 | (in[inPos + 2] & 31) << 14) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 5 & 524287) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 24 & 524287) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 43 & 524287) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 62 | (in[inPos + 3] & 131071) << 2) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 17 & 524287) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] >>> 36 & 524287) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 3] >>> 55 | (in[inPos + 4] & 1023) << 9) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 4] >>> 10 & 524287) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 4] >>> 29 & 524287) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 4] >>> 48 | (in[inPos + 5] & 7) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 5] >>> 3 & 524287) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 5] >>> 22 & 524287) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 5] >>> 41 & 524287) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 5] >>> 60 | (in[inPos + 6] & 32767) << 4) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 6] >>> 15 & 524287) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 6] >>> 34 & 524287) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 6] >>> 53 | (in[inPos + 7] & 255) << 11) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 7] >>> 8 & 524287) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 7] >>> 27 & 524287) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 7] >>> 46 | (in[inPos + 8] & 1) << 18) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 8] >>> 1 & 524287) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 8] >>> 20 & 524287) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 8] >>> 39 & 524287) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 8] >>> 58 | (in[inPos + 9] & 8191) << 6) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 9] >>> 13 & 524287) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 9] >>> 32 & 524287) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 9] >>> 51 | (in[inPos + 10] & 63) << 13) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 10] >>> 6 & 524287) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 10] >>> 25 & 524287) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 10] >>> 44 & 524287) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 10] >>> 63 | (in[inPos + 11] & 262143) << 1) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 11] >>> 18 & 524287) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 11] >>> 37 & 524287) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 11] >>> 56 | (in[inPos + 12] & 2047) << 8) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 12] >>> 11 & 524287) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 12] >>> 30 & 524287) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 12] >>> 49 | (in[inPos + 13] & 15) << 15) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 13] >>> 4 & 524287) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 13] >>> 23 & 524287) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 13] >>> 42 & 524287) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 13] >>> 61 | (in[inPos + 14] & 65535) << 3) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 14] >>> 16 & 524287) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 14] >>> 35 & 524287) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 14] >>> 54 | (in[inPos + 15] & 511) << 10) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 15] >>> 9 & 524287) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 15] >>> 28 & 524287) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 15] >>> 47 | (in[inPos + 16] & 3) << 17) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 16] >>> 2 & 524287) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 16] >>> 21 & 524287) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 16] >>> 40 & 524287) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 16] >>> 59 | (in[inPos + 17] & 16383) << 5) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 17] >>> 14 & 524287) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 17] >>> 33 & 524287) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 17] >>> 52 | (in[inPos + 18] & 127) << 12) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 18] >>> 7 & 524287) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 18] >>> 26 & 524287) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 18] >>> 45) + out[outPos + 62];
  }

  private static void pack20(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 20 | (in[inPos + 2] - in[inPos + 1]) << 40 | (in[inPos + 3] - in[inPos + 2]) << 60;
    out[outPos + 1] = (in[inPos + 3] - in[inPos + 2]) >>> 4 | (in[inPos + 4] - in[inPos + 3]) << 16 | (in[inPos + 5] - in[inPos + 4]) << 36 | (in[inPos + 6] - in[inPos + 5]) << 56;
    out[outPos + 2] = (in[inPos + 6] - in[inPos + 5]) >>> 8 | (in[inPos + 7] - in[inPos + 6]) << 12 | (in[inPos + 8] - in[inPos + 7]) << 32 | (in[inPos + 9] - in[inPos + 8]) << 52;
    out[outPos + 3] = (in[inPos + 9] - in[inPos + 8]) >>> 12 | (in[inPos + 10] - in[inPos + 9]) << 8 | (in[inPos + 11] - in[inPos + 10]) << 28 | (in[inPos + 12] - in[inPos + 11]) << 48;
    out[outPos + 4] = (in[inPos + 12] - in[inPos + 11]) >>> 16 | (in[inPos + 13] - in[inPos + 12]) << 4 | (in[inPos + 14] - in[inPos + 13]) << 24 | (in[inPos + 15] - in[inPos + 14]) << 44;
    out[outPos + 5] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 20 | (in[inPos + 18] - in[inPos + 17]) << 40 | (in[inPos + 19] - in[inPos + 18]) << 60;
    out[outPos + 6] = (in[inPos + 19] - in[inPos + 18]) >>> 4 | (in[inPos + 20] - in[inPos + 19]) << 16 | (in[inPos + 21] - in[inPos + 20]) << 36 | (in[inPos + 22] - in[inPos + 21]) << 56;
    out[outPos + 7] = (in[inPos + 22] - in[inPos + 21]) >>> 8 | (in[inPos + 23] - in[inPos + 22]) << 12 | (in[inPos + 24] - in[inPos + 23]) << 32 | (in[inPos + 25] - in[inPos + 24]) << 52;
    out[outPos + 8] = (in[inPos + 25] - in[inPos + 24]) >>> 12 | (in[inPos + 26] - in[inPos + 25]) << 8 | (in[inPos + 27] - in[inPos + 26]) << 28 | (in[inPos + 28] - in[inPos + 27]) << 48;
    out[outPos + 9] = (in[inPos + 28] - in[inPos + 27]) >>> 16 | (in[inPos + 29] - in[inPos + 28]) << 4 | (in[inPos + 30] - in[inPos + 29]) << 24 | (in[inPos + 31] - in[inPos + 30]) << 44;
    out[outPos + 10] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 20 | (in[inPos + 34] - in[inPos + 33]) << 40 | (in[inPos + 35] - in[inPos + 34]) << 60;
    out[outPos + 11] = (in[inPos + 35] - in[inPos + 34]) >>> 4 | (in[inPos + 36] - in[inPos + 35]) << 16 | (in[inPos + 37] - in[inPos + 36]) << 36 | (in[inPos + 38] - in[inPos + 37]) << 56;
    out[outPos + 12] = (in[inPos + 38] - in[inPos + 37]) >>> 8 | (in[inPos + 39] - in[inPos + 38]) << 12 | (in[inPos + 40] - in[inPos + 39]) << 32 | (in[inPos + 41] - in[inPos + 40]) << 52;
    out[outPos + 13] = (in[inPos + 41] - in[inPos + 40]) >>> 12 | (in[inPos + 42] - in[inPos + 41]) << 8 | (in[inPos + 43] - in[inPos + 42]) << 28 | (in[inPos + 44] - in[inPos + 43]) << 48;
    out[outPos + 14] = (in[inPos + 44] - in[inPos + 43]) >>> 16 | (in[inPos + 45] - in[inPos + 44]) << 4 | (in[inPos + 46] - in[inPos + 45]) << 24 | (in[inPos + 47] - in[inPos + 46]) << 44;
    out[outPos + 15] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 20 | (in[inPos + 50] - in[inPos + 49]) << 40 | (in[inPos + 51] - in[inPos + 50]) << 60;
    out[outPos + 16] = (in[inPos + 51] - in[inPos + 50]) >>> 4 | (in[inPos + 52] - in[inPos + 51]) << 16 | (in[inPos + 53] - in[inPos + 52]) << 36 | (in[inPos + 54] - in[inPos + 53]) << 56;
    out[outPos + 17] = (in[inPos + 54] - in[inPos + 53]) >>> 8 | (in[inPos + 55] - in[inPos + 54]) << 12 | (in[inPos + 56] - in[inPos + 55]) << 32 | (in[inPos + 57] - in[inPos + 56]) << 52;
    out[outPos + 18] = (in[inPos + 57] - in[inPos + 56]) >>> 12 | (in[inPos + 58] - in[inPos + 57]) << 8 | (in[inPos + 59] - in[inPos + 58]) << 28 | (in[inPos + 60] - in[inPos + 59]) << 48;
    out[outPos + 19] = (in[inPos + 60] - in[inPos + 59]) >>> 16 | (in[inPos + 61] - in[inPos + 60]) << 4 | (in[inPos + 62] - in[inPos + 61]) << 24 | (in[inPos + 63] - in[inPos + 62]) << 44;
  }

  private static void unpack20(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1048575) + initValue;
    out[outPos + 1] = (in[inPos] >>> 20 & 1048575) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 40 & 1048575) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 60 | (in[inPos + 1] & 65535) << 4) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 16 & 1048575) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 36 & 1048575) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 56 | (in[inPos + 2] & 4095) << 8) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 12 & 1048575) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 32 & 1048575) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 52 | (in[inPos + 3] & 255) << 12) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 8 & 1048575) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 28 & 1048575) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] >>> 48 | (in[inPos + 4] & 15) << 16) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 4] >>> 4 & 1048575) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 4] >>> 24 & 1048575) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 4] >>> 44) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 5] & 1048575) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 5] >>> 20 & 1048575) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 5] >>> 40 & 1048575) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 5] >>> 60 | (in[inPos + 6] & 65535) << 4) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 6] >>> 16 & 1048575) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 6] >>> 36 & 1048575) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 6] >>> 56 | (in[inPos + 7] & 4095) << 8) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 7] >>> 12 & 1048575) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 7] >>> 32 & 1048575) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 7] >>> 52 | (in[inPos + 8] & 255) << 12) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 8] >>> 8 & 1048575) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 8] >>> 28 & 1048575) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 8] >>> 48 | (in[inPos + 9] & 15) << 16) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 9] >>> 4 & 1048575) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 9] >>> 24 & 1048575) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 9] >>> 44) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 10] & 1048575) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 10] >>> 20 & 1048575) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 10] >>> 40 & 1048575) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 10] >>> 60 | (in[inPos + 11] & 65535) << 4) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 11] >>> 16 & 1048575) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 11] >>> 36 & 1048575) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 11] >>> 56 | (in[inPos + 12] & 4095) << 8) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 12] >>> 12 & 1048575) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 12] >>> 32 & 1048575) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 12] >>> 52 | (in[inPos + 13] & 255) << 12) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 13] >>> 8 & 1048575) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 13] >>> 28 & 1048575) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 13] >>> 48 | (in[inPos + 14] & 15) << 16) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 14] >>> 4 & 1048575) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 14] >>> 24 & 1048575) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 14] >>> 44) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 15] & 1048575) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 15] >>> 20 & 1048575) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 15] >>> 40 & 1048575) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 15] >>> 60 | (in[inPos + 16] & 65535) << 4) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 16] >>> 16 & 1048575) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 16] >>> 36 & 1048575) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 4095) << 8) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 17] >>> 12 & 1048575) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 17] >>> 32 & 1048575) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 17] >>> 52 | (in[inPos + 18] & 255) << 12) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 18] >>> 8 & 1048575) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 18] >>> 28 & 1048575) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 18] >>> 48 | (in[inPos + 19] & 15) << 16) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 19] >>> 4 & 1048575) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 19] >>> 24 & 1048575) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 19] >>> 44) + out[outPos + 62];
  }

  private static void pack21(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 21 | (in[inPos + 2] - in[inPos + 1]) << 42 | (in[inPos + 3] - in[inPos + 2]) << 63;
    out[outPos + 1] = (in[inPos + 3] - in[inPos + 2]) >>> 1 | (in[inPos + 4] - in[inPos + 3]) << 20 | (in[inPos + 5] - in[inPos + 4]) << 41 | (in[inPos + 6] - in[inPos + 5]) << 62;
    out[outPos + 2] = (in[inPos + 6] - in[inPos + 5]) >>> 2 | (in[inPos + 7] - in[inPos + 6]) << 19 | (in[inPos + 8] - in[inPos + 7]) << 40 | (in[inPos + 9] - in[inPos + 8]) << 61;
    out[outPos + 3] = (in[inPos + 9] - in[inPos + 8]) >>> 3 | (in[inPos + 10] - in[inPos + 9]) << 18 | (in[inPos + 11] - in[inPos + 10]) << 39 | (in[inPos + 12] - in[inPos + 11]) << 60;
    out[outPos + 4] = (in[inPos + 12] - in[inPos + 11]) >>> 4 | (in[inPos + 13] - in[inPos + 12]) << 17 | (in[inPos + 14] - in[inPos + 13]) << 38 | (in[inPos + 15] - in[inPos + 14]) << 59;
    out[outPos + 5] = (in[inPos + 15] - in[inPos + 14]) >>> 5 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 37 | (in[inPos + 18] - in[inPos + 17]) << 58;
    out[outPos + 6] = (in[inPos + 18] - in[inPos + 17]) >>> 6 | (in[inPos + 19] - in[inPos + 18]) << 15 | (in[inPos + 20] - in[inPos + 19]) << 36 | (in[inPos + 21] - in[inPos + 20]) << 57;
    out[outPos + 7] = (in[inPos + 21] - in[inPos + 20]) >>> 7 | (in[inPos + 22] - in[inPos + 21]) << 14 | (in[inPos + 23] - in[inPos + 22]) << 35 | (in[inPos + 24] - in[inPos + 23]) << 56;
    out[outPos + 8] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 13 | (in[inPos + 26] - in[inPos + 25]) << 34 | (in[inPos + 27] - in[inPos + 26]) << 55;
    out[outPos + 9] = (in[inPos + 27] - in[inPos + 26]) >>> 9 | (in[inPos + 28] - in[inPos + 27]) << 12 | (in[inPos + 29] - in[inPos + 28]) << 33 | (in[inPos + 30] - in[inPos + 29]) << 54;
    out[outPos + 10] = (in[inPos + 30] - in[inPos + 29]) >>> 10 | (in[inPos + 31] - in[inPos + 30]) << 11 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 53;
    out[outPos + 11] = (in[inPos + 33] - in[inPos + 32]) >>> 11 | (in[inPos + 34] - in[inPos + 33]) << 10 | (in[inPos + 35] - in[inPos + 34]) << 31 | (in[inPos + 36] - in[inPos + 35]) << 52;
    out[outPos + 12] = (in[inPos + 36] - in[inPos + 35]) >>> 12 | (in[inPos + 37] - in[inPos + 36]) << 9 | (in[inPos + 38] - in[inPos + 37]) << 30 | (in[inPos + 39] - in[inPos + 38]) << 51;
    out[outPos + 13] = (in[inPos + 39] - in[inPos + 38]) >>> 13 | (in[inPos + 40] - in[inPos + 39]) << 8 | (in[inPos + 41] - in[inPos + 40]) << 29 | (in[inPos + 42] - in[inPos + 41]) << 50;
    out[outPos + 14] = (in[inPos + 42] - in[inPos + 41]) >>> 14 | (in[inPos + 43] - in[inPos + 42]) << 7 | (in[inPos + 44] - in[inPos + 43]) << 28 | (in[inPos + 45] - in[inPos + 44]) << 49;
    out[outPos + 15] = (in[inPos + 45] - in[inPos + 44]) >>> 15 | (in[inPos + 46] - in[inPos + 45]) << 6 | (in[inPos + 47] - in[inPos + 46]) << 27 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 16] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 5 | (in[inPos + 50] - in[inPos + 49]) << 26 | (in[inPos + 51] - in[inPos + 50]) << 47;
    out[outPos + 17] = (in[inPos + 51] - in[inPos + 50]) >>> 17 | (in[inPos + 52] - in[inPos + 51]) << 4 | (in[inPos + 53] - in[inPos + 52]) << 25 | (in[inPos + 54] - in[inPos + 53]) << 46;
    out[outPos + 18] = (in[inPos + 54] - in[inPos + 53]) >>> 18 | (in[inPos + 55] - in[inPos + 54]) << 3 | (in[inPos + 56] - in[inPos + 55]) << 24 | (in[inPos + 57] - in[inPos + 56]) << 45;
    out[outPos + 19] = (in[inPos + 57] - in[inPos + 56]) >>> 19 | (in[inPos + 58] - in[inPos + 57]) << 2 | (in[inPos + 59] - in[inPos + 58]) << 23 | (in[inPos + 60] - in[inPos + 59]) << 44;
    out[outPos + 20] = (in[inPos + 60] - in[inPos + 59]) >>> 20 | (in[inPos + 61] - in[inPos + 60]) << 1 | (in[inPos + 62] - in[inPos + 61]) << 22 | (in[inPos + 63] - in[inPos + 62]) << 43;
  }

  private static void unpack21(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2097151) + initValue;
    out[outPos + 1] = (in[inPos] >>> 21 & 2097151) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 42 & 2097151) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 63 | (in[inPos + 1] & 1048575) << 1) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 20 & 2097151) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 41 & 2097151) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 524287) << 2) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 19 & 2097151) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 40 & 2097151) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 61 | (in[inPos + 3] & 262143) << 3) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 18 & 2097151) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 39 & 2097151) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 131071) << 4) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 4] >>> 17 & 2097151) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 4] >>> 38 & 2097151) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 4] >>> 59 | (in[inPos + 5] & 65535) << 5) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 5] >>> 16 & 2097151) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 5] >>> 37 & 2097151) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 32767) << 6) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 6] >>> 15 & 2097151) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 6] >>> 36 & 2097151) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 6] >>> 57 | (in[inPos + 7] & 16383) << 7) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 7] >>> 14 & 2097151) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 7] >>> 35 & 2097151) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 8191) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 8] >>> 13 & 2097151) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 8] >>> 34 & 2097151) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 8] >>> 55 | (in[inPos + 9] & 4095) << 9) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 9] >>> 12 & 2097151) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 9] >>> 33 & 2097151) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 2047) << 10) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 10] >>> 11 & 2097151) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 10] >>> 32 & 2097151) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 10] >>> 53 | (in[inPos + 11] & 1023) << 11) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 11] >>> 10 & 2097151) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 11] >>> 31 & 2097151) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 511) << 12) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 12] >>> 9 & 2097151) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 12] >>> 30 & 2097151) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 12] >>> 51 | (in[inPos + 13] & 255) << 13) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 13] >>> 8 & 2097151) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 13] >>> 29 & 2097151) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 13] >>> 50 | (in[inPos + 14] & 127) << 14) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 14] >>> 7 & 2097151) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 14] >>> 28 & 2097151) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 14] >>> 49 | (in[inPos + 15] & 63) << 15) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 15] >>> 6 & 2097151) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 15] >>> 27 & 2097151) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 31) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 16] >>> 5 & 2097151) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 16] >>> 26 & 2097151) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 16] >>> 47 | (in[inPos + 17] & 15) << 17) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 17] >>> 4 & 2097151) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 17] >>> 25 & 2097151) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 17] >>> 46 | (in[inPos + 18] & 7) << 18) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 18] >>> 3 & 2097151) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 18] >>> 24 & 2097151) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 18] >>> 45 | (in[inPos + 19] & 3) << 19) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 19] >>> 2 & 2097151) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 19] >>> 23 & 2097151) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 1) << 20) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 20] >>> 1 & 2097151) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 20] >>> 22 & 2097151) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 20] >>> 43) + out[outPos + 62];
  }

  private static void pack22(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 22 | (in[inPos + 2] - in[inPos + 1]) << 44;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 20 | (in[inPos + 3] - in[inPos + 2]) << 2 | (in[inPos + 4] - in[inPos + 3]) << 24 | (in[inPos + 5] - in[inPos + 4]) << 46;
    out[outPos + 2] = (in[inPos + 5] - in[inPos + 4]) >>> 18 | (in[inPos + 6] - in[inPos + 5]) << 4 | (in[inPos + 7] - in[inPos + 6]) << 26 | (in[inPos + 8] - in[inPos + 7]) << 48;
    out[outPos + 3] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 6 | (in[inPos + 10] - in[inPos + 9]) << 28 | (in[inPos + 11] - in[inPos + 10]) << 50;
    out[outPos + 4] = (in[inPos + 11] - in[inPos + 10]) >>> 14 | (in[inPos + 12] - in[inPos + 11]) << 8 | (in[inPos + 13] - in[inPos + 12]) << 30 | (in[inPos + 14] - in[inPos + 13]) << 52;
    out[outPos + 5] = (in[inPos + 14] - in[inPos + 13]) >>> 12 | (in[inPos + 15] - in[inPos + 14]) << 10 | (in[inPos + 16] - in[inPos + 15]) << 32 | (in[inPos + 17] - in[inPos + 16]) << 54;
    out[outPos + 6] = (in[inPos + 17] - in[inPos + 16]) >>> 10 | (in[inPos + 18] - in[inPos + 17]) << 12 | (in[inPos + 19] - in[inPos + 18]) << 34 | (in[inPos + 20] - in[inPos + 19]) << 56;
    out[outPos + 7] = (in[inPos + 20] - in[inPos + 19]) >>> 8 | (in[inPos + 21] - in[inPos + 20]) << 14 | (in[inPos + 22] - in[inPos + 21]) << 36 | (in[inPos + 23] - in[inPos + 22]) << 58;
    out[outPos + 8] = (in[inPos + 23] - in[inPos + 22]) >>> 6 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 38 | (in[inPos + 26] - in[inPos + 25]) << 60;
    out[outPos + 9] = (in[inPos + 26] - in[inPos + 25]) >>> 4 | (in[inPos + 27] - in[inPos + 26]) << 18 | (in[inPos + 28] - in[inPos + 27]) << 40 | (in[inPos + 29] - in[inPos + 28]) << 62;
    out[outPos + 10] = (in[inPos + 29] - in[inPos + 28]) >>> 2 | (in[inPos + 30] - in[inPos + 29]) << 20 | (in[inPos + 31] - in[inPos + 30]) << 42;
    out[outPos + 11] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 22 | (in[inPos + 34] - in[inPos + 33]) << 44;
    out[outPos + 12] = (in[inPos + 34] - in[inPos + 33]) >>> 20 | (in[inPos + 35] - in[inPos + 34]) << 2 | (in[inPos + 36] - in[inPos + 35]) << 24 | (in[inPos + 37] - in[inPos + 36]) << 46;
    out[outPos + 13] = (in[inPos + 37] - in[inPos + 36]) >>> 18 | (in[inPos + 38] - in[inPos + 37]) << 4 | (in[inPos + 39] - in[inPos + 38]) << 26 | (in[inPos + 40] - in[inPos + 39]) << 48;
    out[outPos + 14] = (in[inPos + 40] - in[inPos + 39]) >>> 16 | (in[inPos + 41] - in[inPos + 40]) << 6 | (in[inPos + 42] - in[inPos + 41]) << 28 | (in[inPos + 43] - in[inPos + 42]) << 50;
    out[outPos + 15] = (in[inPos + 43] - in[inPos + 42]) >>> 14 | (in[inPos + 44] - in[inPos + 43]) << 8 | (in[inPos + 45] - in[inPos + 44]) << 30 | (in[inPos + 46] - in[inPos + 45]) << 52;
    out[outPos + 16] = (in[inPos + 46] - in[inPos + 45]) >>> 12 | (in[inPos + 47] - in[inPos + 46]) << 10 | (in[inPos + 48] - in[inPos + 47]) << 32 | (in[inPos + 49] - in[inPos + 48]) << 54;
    out[outPos + 17] = (in[inPos + 49] - in[inPos + 48]) >>> 10 | (in[inPos + 50] - in[inPos + 49]) << 12 | (in[inPos + 51] - in[inPos + 50]) << 34 | (in[inPos + 52] - in[inPos + 51]) << 56;
    out[outPos + 18] = (in[inPos + 52] - in[inPos + 51]) >>> 8 | (in[inPos + 53] - in[inPos + 52]) << 14 | (in[inPos + 54] - in[inPos + 53]) << 36 | (in[inPos + 55] - in[inPos + 54]) << 58;
    out[outPos + 19] = (in[inPos + 55] - in[inPos + 54]) >>> 6 | (in[inPos + 56] - in[inPos + 55]) << 16 | (in[inPos + 57] - in[inPos + 56]) << 38 | (in[inPos + 58] - in[inPos + 57]) << 60;
    out[outPos + 20] = (in[inPos + 58] - in[inPos + 57]) >>> 4 | (in[inPos + 59] - in[inPos + 58]) << 18 | (in[inPos + 60] - in[inPos + 59]) << 40 | (in[inPos + 61] - in[inPos + 60]) << 62;
    out[outPos + 21] = (in[inPos + 61] - in[inPos + 60]) >>> 2 | (in[inPos + 62] - in[inPos + 61]) << 20 | (in[inPos + 63] - in[inPos + 62]) << 42;
  }

  private static void unpack22(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 4194303) + initValue;
    out[outPos + 1] = (in[inPos] >>> 22 & 4194303) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 44 | (in[inPos + 1] & 3) << 20) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 2 & 4194303) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 24 & 4194303) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 46 | (in[inPos + 2] & 15) << 18) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 4 & 4194303) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 26 & 4194303) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 48 | (in[inPos + 3] & 63) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 6 & 4194303) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 28 & 4194303) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 50 | (in[inPos + 4] & 255) << 14) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 4] >>> 8 & 4194303) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 4] >>> 30 & 4194303) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 4] >>> 52 | (in[inPos + 5] & 1023) << 12) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 5] >>> 10 & 4194303) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 5] >>> 32 & 4194303) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 5] >>> 54 | (in[inPos + 6] & 4095) << 10) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 6] >>> 12 & 4194303) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 6] >>> 34 & 4194303) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 6] >>> 56 | (in[inPos + 7] & 16383) << 8) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 7] >>> 14 & 4194303) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 7] >>> 36 & 4194303) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 7] >>> 58 | (in[inPos + 8] & 65535) << 6) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 8] >>> 16 & 4194303) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 8] >>> 38 & 4194303) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 8] >>> 60 | (in[inPos + 9] & 262143) << 4) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 9] >>> 18 & 4194303) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 9] >>> 40 & 4194303) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 9] >>> 62 | (in[inPos + 10] & 1048575) << 2) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 10] >>> 20 & 4194303) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 10] >>> 42) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 11] & 4194303) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 11] >>> 22 & 4194303) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 11] >>> 44 | (in[inPos + 12] & 3) << 20) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 12] >>> 2 & 4194303) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 12] >>> 24 & 4194303) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 12] >>> 46 | (in[inPos + 13] & 15) << 18) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 13] >>> 4 & 4194303) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 13] >>> 26 & 4194303) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 13] >>> 48 | (in[inPos + 14] & 63) << 16) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 14] >>> 6 & 4194303) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 14] >>> 28 & 4194303) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 14] >>> 50 | (in[inPos + 15] & 255) << 14) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 15] >>> 8 & 4194303) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 15] >>> 30 & 4194303) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 15] >>> 52 | (in[inPos + 16] & 1023) << 12) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 16] >>> 10 & 4194303) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 16] >>> 32 & 4194303) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 16] >>> 54 | (in[inPos + 17] & 4095) << 10) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 17] >>> 12 & 4194303) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 17] >>> 34 & 4194303) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 17] >>> 56 | (in[inPos + 18] & 16383) << 8) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 18] >>> 14 & 4194303) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 18] >>> 36 & 4194303) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 18] >>> 58 | (in[inPos + 19] & 65535) << 6) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 19] >>> 16 & 4194303) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 19] >>> 38 & 4194303) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 19] >>> 60 | (in[inPos + 20] & 262143) << 4) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 20] >>> 18 & 4194303) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 20] >>> 40 & 4194303) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 20] >>> 62 | (in[inPos + 21] & 1048575) << 2) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 21] >>> 20 & 4194303) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 21] >>> 42) + out[outPos + 62];
  }

  private static void pack23(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 23 | (in[inPos + 2] - in[inPos + 1]) << 46;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 18 | (in[inPos + 3] - in[inPos + 2]) << 5 | (in[inPos + 4] - in[inPos + 3]) << 28 | (in[inPos + 5] - in[inPos + 4]) << 51;
    out[outPos + 2] = (in[inPos + 5] - in[inPos + 4]) >>> 13 | (in[inPos + 6] - in[inPos + 5]) << 10 | (in[inPos + 7] - in[inPos + 6]) << 33 | (in[inPos + 8] - in[inPos + 7]) << 56;
    out[outPos + 3] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 15 | (in[inPos + 10] - in[inPos + 9]) << 38 | (in[inPos + 11] - in[inPos + 10]) << 61;
    out[outPos + 4] = (in[inPos + 11] - in[inPos + 10]) >>> 3 | (in[inPos + 12] - in[inPos + 11]) << 20 | (in[inPos + 13] - in[inPos + 12]) << 43;
    out[outPos + 5] = (in[inPos + 13] - in[inPos + 12]) >>> 21 | (in[inPos + 14] - in[inPos + 13]) << 2 | (in[inPos + 15] - in[inPos + 14]) << 25 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 6] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 7 | (in[inPos + 18] - in[inPos + 17]) << 30 | (in[inPos + 19] - in[inPos + 18]) << 53;
    out[outPos + 7] = (in[inPos + 19] - in[inPos + 18]) >>> 11 | (in[inPos + 20] - in[inPos + 19]) << 12 | (in[inPos + 21] - in[inPos + 20]) << 35 | (in[inPos + 22] - in[inPos + 21]) << 58;
    out[outPos + 8] = (in[inPos + 22] - in[inPos + 21]) >>> 6 | (in[inPos + 23] - in[inPos + 22]) << 17 | (in[inPos + 24] - in[inPos + 23]) << 40 | (in[inPos + 25] - in[inPos + 24]) << 63;
    out[outPos + 9] = (in[inPos + 25] - in[inPos + 24]) >>> 1 | (in[inPos + 26] - in[inPos + 25]) << 22 | (in[inPos + 27] - in[inPos + 26]) << 45;
    out[outPos + 10] = (in[inPos + 27] - in[inPos + 26]) >>> 19 | (in[inPos + 28] - in[inPos + 27]) << 4 | (in[inPos + 29] - in[inPos + 28]) << 27 | (in[inPos + 30] - in[inPos + 29]) << 50;
    out[outPos + 11] = (in[inPos + 30] - in[inPos + 29]) >>> 14 | (in[inPos + 31] - in[inPos + 30]) << 9 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 55;
    out[outPos + 12] = (in[inPos + 33] - in[inPos + 32]) >>> 9 | (in[inPos + 34] - in[inPos + 33]) << 14 | (in[inPos + 35] - in[inPos + 34]) << 37 | (in[inPos + 36] - in[inPos + 35]) << 60;
    out[outPos + 13] = (in[inPos + 36] - in[inPos + 35]) >>> 4 | (in[inPos + 37] - in[inPos + 36]) << 19 | (in[inPos + 38] - in[inPos + 37]) << 42;
    out[outPos + 14] = (in[inPos + 38] - in[inPos + 37]) >>> 22 | (in[inPos + 39] - in[inPos + 38]) << 1 | (in[inPos + 40] - in[inPos + 39]) << 24 | (in[inPos + 41] - in[inPos + 40]) << 47;
    out[outPos + 15] = (in[inPos + 41] - in[inPos + 40]) >>> 17 | (in[inPos + 42] - in[inPos + 41]) << 6 | (in[inPos + 43] - in[inPos + 42]) << 29 | (in[inPos + 44] - in[inPos + 43]) << 52;
    out[outPos + 16] = (in[inPos + 44] - in[inPos + 43]) >>> 12 | (in[inPos + 45] - in[inPos + 44]) << 11 | (in[inPos + 46] - in[inPos + 45]) << 34 | (in[inPos + 47] - in[inPos + 46]) << 57;
    out[outPos + 17] = (in[inPos + 47] - in[inPos + 46]) >>> 7 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 39 | (in[inPos + 50] - in[inPos + 49]) << 62;
    out[outPos + 18] = (in[inPos + 50] - in[inPos + 49]) >>> 2 | (in[inPos + 51] - in[inPos + 50]) << 21 | (in[inPos + 52] - in[inPos + 51]) << 44;
    out[outPos + 19] = (in[inPos + 52] - in[inPos + 51]) >>> 20 | (in[inPos + 53] - in[inPos + 52]) << 3 | (in[inPos + 54] - in[inPos + 53]) << 26 | (in[inPos + 55] - in[inPos + 54]) << 49;
    out[outPos + 20] = (in[inPos + 55] - in[inPos + 54]) >>> 15 | (in[inPos + 56] - in[inPos + 55]) << 8 | (in[inPos + 57] - in[inPos + 56]) << 31 | (in[inPos + 58] - in[inPos + 57]) << 54;
    out[outPos + 21] = (in[inPos + 58] - in[inPos + 57]) >>> 10 | (in[inPos + 59] - in[inPos + 58]) << 13 | (in[inPos + 60] - in[inPos + 59]) << 36 | (in[inPos + 61] - in[inPos + 60]) << 59;
    out[outPos + 22] = (in[inPos + 61] - in[inPos + 60]) >>> 5 | (in[inPos + 62] - in[inPos + 61]) << 18 | (in[inPos + 63] - in[inPos + 62]) << 41;
  }

  private static void unpack23(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 8388607) + initValue;
    out[outPos + 1] = (in[inPos] >>> 23 & 8388607) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 46 | (in[inPos + 1] & 31) << 18) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 5 & 8388607) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 28 & 8388607) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 51 | (in[inPos + 2] & 1023) << 13) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 10 & 8388607) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 33 & 8388607) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 56 | (in[inPos + 3] & 32767) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 15 & 8388607) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 38 & 8388607) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 61 | (in[inPos + 4] & 1048575) << 3) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 4] >>> 20 & 8388607) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 4] >>> 43 | (in[inPos + 5] & 3) << 21) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 5] >>> 2 & 8388607) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 5] >>> 25 & 8388607) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 5] >>> 48 | (in[inPos + 6] & 127) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 6] >>> 7 & 8388607) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 6] >>> 30 & 8388607) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 6] >>> 53 | (in[inPos + 7] & 4095) << 11) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 7] >>> 12 & 8388607) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 7] >>> 35 & 8388607) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 7] >>> 58 | (in[inPos + 8] & 131071) << 6) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 8] >>> 17 & 8388607) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 8] >>> 40 & 8388607) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 8] >>> 63 | (in[inPos + 9] & 4194303) << 1) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 9] >>> 22 & 8388607) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 9] >>> 45 | (in[inPos + 10] & 15) << 19) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 10] >>> 4 & 8388607) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 10] >>> 27 & 8388607) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 10] >>> 50 | (in[inPos + 11] & 511) << 14) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 11] >>> 9 & 8388607) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 11] >>> 32 & 8388607) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 11] >>> 55 | (in[inPos + 12] & 16383) << 9) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 12] >>> 14 & 8388607) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 12] >>> 37 & 8388607) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 12] >>> 60 | (in[inPos + 13] & 524287) << 4) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 13] >>> 19 & 8388607) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 13] >>> 42 | (in[inPos + 14] & 1) << 22) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 14] >>> 1 & 8388607) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 14] >>> 24 & 8388607) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 14] >>> 47 | (in[inPos + 15] & 63) << 17) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 15] >>> 6 & 8388607) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 15] >>> 29 & 8388607) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 15] >>> 52 | (in[inPos + 16] & 2047) << 12) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 16] >>> 11 & 8388607) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 16] >>> 34 & 8388607) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 16] >>> 57 | (in[inPos + 17] & 65535) << 7) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 17] >>> 16 & 8388607) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 17] >>> 39 & 8388607) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 17] >>> 62 | (in[inPos + 18] & 2097151) << 2) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 18] >>> 21 & 8388607) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 18] >>> 44 | (in[inPos + 19] & 7) << 20) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 19] >>> 3 & 8388607) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 19] >>> 26 & 8388607) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 19] >>> 49 | (in[inPos + 20] & 255) << 15) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 20] >>> 8 & 8388607) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 20] >>> 31 & 8388607) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 20] >>> 54 | (in[inPos + 21] & 8191) << 10) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 21] >>> 13 & 8388607) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 21] >>> 36 & 8388607) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 21] >>> 59 | (in[inPos + 22] & 262143) << 5) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 22] >>> 18 & 8388607) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 22] >>> 41) + out[outPos + 62];
  }

  private static void pack24(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 24 | (in[inPos + 2] - in[inPos + 1]) << 48;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 16 | (in[inPos + 3] - in[inPos + 2]) << 8 | (in[inPos + 4] - in[inPos + 3]) << 32 | (in[inPos + 5] - in[inPos + 4]) << 56;
    out[outPos + 2] = (in[inPos + 5] - in[inPos + 4]) >>> 8 | (in[inPos + 6] - in[inPos + 5]) << 16 | (in[inPos + 7] - in[inPos + 6]) << 40;
    out[outPos + 3] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 24 | (in[inPos + 10] - in[inPos + 9]) << 48;
    out[outPos + 4] = (in[inPos + 10] - in[inPos + 9]) >>> 16 | (in[inPos + 11] - in[inPos + 10]) << 8 | (in[inPos + 12] - in[inPos + 11]) << 32 | (in[inPos + 13] - in[inPos + 12]) << 56;
    out[outPos + 5] = (in[inPos + 13] - in[inPos + 12]) >>> 8 | (in[inPos + 14] - in[inPos + 13]) << 16 | (in[inPos + 15] - in[inPos + 14]) << 40;
    out[outPos + 6] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 24 | (in[inPos + 18] - in[inPos + 17]) << 48;
    out[outPos + 7] = (in[inPos + 18] - in[inPos + 17]) >>> 16 | (in[inPos + 19] - in[inPos + 18]) << 8 | (in[inPos + 20] - in[inPos + 19]) << 32 | (in[inPos + 21] - in[inPos + 20]) << 56;
    out[outPos + 8] = (in[inPos + 21] - in[inPos + 20]) >>> 8 | (in[inPos + 22] - in[inPos + 21]) << 16 | (in[inPos + 23] - in[inPos + 22]) << 40;
    out[outPos + 9] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 24 | (in[inPos + 26] - in[inPos + 25]) << 48;
    out[outPos + 10] = (in[inPos + 26] - in[inPos + 25]) >>> 16 | (in[inPos + 27] - in[inPos + 26]) << 8 | (in[inPos + 28] - in[inPos + 27]) << 32 | (in[inPos + 29] - in[inPos + 28]) << 56;
    out[outPos + 11] = (in[inPos + 29] - in[inPos + 28]) >>> 8 | (in[inPos + 30] - in[inPos + 29]) << 16 | (in[inPos + 31] - in[inPos + 30]) << 40;
    out[outPos + 12] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 24 | (in[inPos + 34] - in[inPos + 33]) << 48;
    out[outPos + 13] = (in[inPos + 34] - in[inPos + 33]) >>> 16 | (in[inPos + 35] - in[inPos + 34]) << 8 | (in[inPos + 36] - in[inPos + 35]) << 32 | (in[inPos + 37] - in[inPos + 36]) << 56;
    out[outPos + 14] = (in[inPos + 37] - in[inPos + 36]) >>> 8 | (in[inPos + 38] - in[inPos + 37]) << 16 | (in[inPos + 39] - in[inPos + 38]) << 40;
    out[outPos + 15] = (in[inPos + 40] - in[inPos + 39]) | (in[inPos + 41] - in[inPos + 40]) << 24 | (in[inPos + 42] - in[inPos + 41]) << 48;
    out[outPos + 16] = (in[inPos + 42] - in[inPos + 41]) >>> 16 | (in[inPos + 43] - in[inPos + 42]) << 8 | (in[inPos + 44] - in[inPos + 43]) << 32 | (in[inPos + 45] - in[inPos + 44]) << 56;
    out[outPos + 17] = (in[inPos + 45] - in[inPos + 44]) >>> 8 | (in[inPos + 46] - in[inPos + 45]) << 16 | (in[inPos + 47] - in[inPos + 46]) << 40;
    out[outPos + 18] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 24 | (in[inPos + 50] - in[inPos + 49]) << 48;
    out[outPos + 19] = (in[inPos + 50] - in[inPos + 49]) >>> 16 | (in[inPos + 51] - in[inPos + 50]) << 8 | (in[inPos + 52] - in[inPos + 51]) << 32 | (in[inPos + 53] - in[inPos + 52]) << 56;
    out[outPos + 20] = (in[inPos + 53] - in[inPos + 52]) >>> 8 | (in[inPos + 54] - in[inPos + 53]) << 16 | (in[inPos + 55] - in[inPos + 54]) << 40;
    out[outPos + 21] = (in[inPos + 56] - in[inPos + 55]) | (in[inPos + 57] - in[inPos + 56]) << 24 | (in[inPos + 58] - in[inPos + 57]) << 48;
    out[outPos + 22] = (in[inPos + 58] - in[inPos + 57]) >>> 16 | (in[inPos + 59] - in[inPos + 58]) << 8 | (in[inPos + 60] - in[inPos + 59]) << 32 | (in[inPos + 61] - in[inPos + 60]) << 56;
    out[outPos + 23] = (in[inPos + 61] - in[inPos + 60]) >>> 8 | (in[inPos + 62] - in[inPos + 61]) << 16 | (in[inPos + 63] - in[inPos + 62]) << 40;
  }

  private static void unpack24(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 16777215) + initValue;
    out[outPos + 1] = (in[inPos] >>> 24 & 16777215) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 48 | (in[inPos + 1] & 255) << 16) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 8 & 16777215) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 32 & 16777215) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 56 | (in[inPos + 2] & 65535) << 8) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 16 & 16777215) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 40) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] & 16777215) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 24 & 16777215) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 48 | (in[inPos + 4] & 255) << 16) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 8 & 16777215) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 4] >>> 32 & 16777215) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 4] >>> 56 | (in[inPos + 5] & 65535) << 8) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 5] >>> 16 & 16777215) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 5] >>> 40) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 6] & 16777215) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 6] >>> 24 & 16777215) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 6] >>> 48 | (in[inPos + 7] & 255) << 16) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 7] >>> 8 & 16777215) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 7] >>> 32 & 16777215) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 65535) << 8) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 8] >>> 16 & 16777215) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 8] >>> 40) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 9] & 16777215) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 9] >>> 24 & 16777215) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 9] >>> 48 | (in[inPos + 10] & 255) << 16) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 10] >>> 8 & 16777215) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 10] >>> 32 & 16777215) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 10] >>> 56 | (in[inPos + 11] & 65535) << 8) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 11] >>> 16 & 16777215) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 11] >>> 40) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 12] & 16777215) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 12] >>> 24 & 16777215) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 12] >>> 48 | (in[inPos + 13] & 255) << 16) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 13] >>> 8 & 16777215) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 13] >>> 32 & 16777215) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 13] >>> 56 | (in[inPos + 14] & 65535) << 8) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 14] >>> 16 & 16777215) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 14] >>> 40) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 15] & 16777215) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 15] >>> 24 & 16777215) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 255) << 16) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 16] >>> 8 & 16777215) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 16] >>> 32 & 16777215) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 65535) << 8) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 17] >>> 16 & 16777215) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 17] >>> 40) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 18] & 16777215) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 18] >>> 24 & 16777215) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 18] >>> 48 | (in[inPos + 19] & 255) << 16) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 19] >>> 8 & 16777215) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 19] >>> 32 & 16777215) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 19] >>> 56 | (in[inPos + 20] & 65535) << 8) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 20] >>> 16 & 16777215) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 20] >>> 40) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 21] & 16777215) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 21] >>> 24 & 16777215) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 21] >>> 48 | (in[inPos + 22] & 255) << 16) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 22] >>> 8 & 16777215) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 22] >>> 32 & 16777215) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 22] >>> 56 | (in[inPos + 23] & 65535) << 8) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 23] >>> 16 & 16777215) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 23] >>> 40) + out[outPos + 62];
  }

  private static void pack25(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 25 | (in[inPos + 2] - in[inPos + 1]) << 50;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 14 | (in[inPos + 3] - in[inPos + 2]) << 11 | (in[inPos + 4] - in[inPos + 3]) << 36 | (in[inPos + 5] - in[inPos + 4]) << 61;
    out[outPos + 2] = (in[inPos + 5] - in[inPos + 4]) >>> 3 | (in[inPos + 6] - in[inPos + 5]) << 22 | (in[inPos + 7] - in[inPos + 6]) << 47;
    out[outPos + 3] = (in[inPos + 7] - in[inPos + 6]) >>> 17 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 33 | (in[inPos + 10] - in[inPos + 9]) << 58;
    out[outPos + 4] = (in[inPos + 10] - in[inPos + 9]) >>> 6 | (in[inPos + 11] - in[inPos + 10]) << 19 | (in[inPos + 12] - in[inPos + 11]) << 44;
    out[outPos + 5] = (in[inPos + 12] - in[inPos + 11]) >>> 20 | (in[inPos + 13] - in[inPos + 12]) << 5 | (in[inPos + 14] - in[inPos + 13]) << 30 | (in[inPos + 15] - in[inPos + 14]) << 55;
    out[outPos + 6] = (in[inPos + 15] - in[inPos + 14]) >>> 9 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 41;
    out[outPos + 7] = (in[inPos + 17] - in[inPos + 16]) >>> 23 | (in[inPos + 18] - in[inPos + 17]) << 2 | (in[inPos + 19] - in[inPos + 18]) << 27 | (in[inPos + 20] - in[inPos + 19]) << 52;
    out[outPos + 8] = (in[inPos + 20] - in[inPos + 19]) >>> 12 | (in[inPos + 21] - in[inPos + 20]) << 13 | (in[inPos + 22] - in[inPos + 21]) << 38 | (in[inPos + 23] - in[inPos + 22]) << 63;
    out[outPos + 9] = (in[inPos + 23] - in[inPos + 22]) >>> 1 | (in[inPos + 24] - in[inPos + 23]) << 24 | (in[inPos + 25] - in[inPos + 24]) << 49;
    out[outPos + 10] = (in[inPos + 25] - in[inPos + 24]) >>> 15 | (in[inPos + 26] - in[inPos + 25]) << 10 | (in[inPos + 27] - in[inPos + 26]) << 35 | (in[inPos + 28] - in[inPos + 27]) << 60;
    out[outPos + 11] = (in[inPos + 28] - in[inPos + 27]) >>> 4 | (in[inPos + 29] - in[inPos + 28]) << 21 | (in[inPos + 30] - in[inPos + 29]) << 46;
    out[outPos + 12] = (in[inPos + 30] - in[inPos + 29]) >>> 18 | (in[inPos + 31] - in[inPos + 30]) << 7 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 57;
    out[outPos + 13] = (in[inPos + 33] - in[inPos + 32]) >>> 7 | (in[inPos + 34] - in[inPos + 33]) << 18 | (in[inPos + 35] - in[inPos + 34]) << 43;
    out[outPos + 14] = (in[inPos + 35] - in[inPos + 34]) >>> 21 | (in[inPos + 36] - in[inPos + 35]) << 4 | (in[inPos + 37] - in[inPos + 36]) << 29 | (in[inPos + 38] - in[inPos + 37]) << 54;
    out[outPos + 15] = (in[inPos + 38] - in[inPos + 37]) >>> 10 | (in[inPos + 39] - in[inPos + 38]) << 15 | (in[inPos + 40] - in[inPos + 39]) << 40;
    out[outPos + 16] = (in[inPos + 40] - in[inPos + 39]) >>> 24 | (in[inPos + 41] - in[inPos + 40]) << 1 | (in[inPos + 42] - in[inPos + 41]) << 26 | (in[inPos + 43] - in[inPos + 42]) << 51;
    out[outPos + 17] = (in[inPos + 43] - in[inPos + 42]) >>> 13 | (in[inPos + 44] - in[inPos + 43]) << 12 | (in[inPos + 45] - in[inPos + 44]) << 37 | (in[inPos + 46] - in[inPos + 45]) << 62;
    out[outPos + 18] = (in[inPos + 46] - in[inPos + 45]) >>> 2 | (in[inPos + 47] - in[inPos + 46]) << 23 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 19] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 9 | (in[inPos + 50] - in[inPos + 49]) << 34 | (in[inPos + 51] - in[inPos + 50]) << 59;
    out[outPos + 20] = (in[inPos + 51] - in[inPos + 50]) >>> 5 | (in[inPos + 52] - in[inPos + 51]) << 20 | (in[inPos + 53] - in[inPos + 52]) << 45;
    out[outPos + 21] = (in[inPos + 53] - in[inPos + 52]) >>> 19 | (in[inPos + 54] - in[inPos + 53]) << 6 | (in[inPos + 55] - in[inPos + 54]) << 31 | (in[inPos + 56] - in[inPos + 55]) << 56;
    out[outPos + 22] = (in[inPos + 56] - in[inPos + 55]) >>> 8 | (in[inPos + 57] - in[inPos + 56]) << 17 | (in[inPos + 58] - in[inPos + 57]) << 42;
    out[outPos + 23] = (in[inPos + 58] - in[inPos + 57]) >>> 22 | (in[inPos + 59] - in[inPos + 58]) << 3 | (in[inPos + 60] - in[inPos + 59]) << 28 | (in[inPos + 61] - in[inPos + 60]) << 53;
    out[outPos + 24] = (in[inPos + 61] - in[inPos + 60]) >>> 11 | (in[inPos + 62] - in[inPos + 61]) << 14 | (in[inPos + 63] - in[inPos + 62]) << 39;
  }

  private static void unpack25(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 33554431) + initValue;
    out[outPos + 1] = (in[inPos] >>> 25 & 33554431) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 50 | (in[inPos + 1] & 2047) << 14) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 11 & 33554431) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 36 & 33554431) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 61 | (in[inPos + 2] & 4194303) << 3) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 22 & 33554431) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 47 | (in[inPos + 3] & 255) << 17) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 8 & 33554431) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 33 & 33554431) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 58 | (in[inPos + 4] & 524287) << 6) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 19 & 33554431) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 4] >>> 44 | (in[inPos + 5] & 31) << 20) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 5] >>> 5 & 33554431) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 5] >>> 30 & 33554431) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 5] >>> 55 | (in[inPos + 6] & 65535) << 9) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 6] >>> 16 & 33554431) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 6] >>> 41 | (in[inPos + 7] & 3) << 23) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 7] >>> 2 & 33554431) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 7] >>> 27 & 33554431) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 7] >>> 52 | (in[inPos + 8] & 8191) << 12) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 8] >>> 13 & 33554431) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 8] >>> 38 & 33554431) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 8] >>> 63 | (in[inPos + 9] & 16777215) << 1) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 9] >>> 24 & 33554431) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 9] >>> 49 | (in[inPos + 10] & 1023) << 15) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 10] >>> 10 & 33554431) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 10] >>> 35 & 33554431) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 10] >>> 60 | (in[inPos + 11] & 2097151) << 4) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 11] >>> 21 & 33554431) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 11] >>> 46 | (in[inPos + 12] & 127) << 18) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 12] >>> 7 & 33554431) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 12] >>> 32 & 33554431) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 12] >>> 57 | (in[inPos + 13] & 262143) << 7) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 13] >>> 18 & 33554431) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 13] >>> 43 | (in[inPos + 14] & 15) << 21) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 14] >>> 4 & 33554431) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 14] >>> 29 & 33554431) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 14] >>> 54 | (in[inPos + 15] & 32767) << 10) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 15] >>> 15 & 33554431) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 15] >>> 40 | (in[inPos + 16] & 1) << 24) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 16] >>> 1 & 33554431) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 16] >>> 26 & 33554431) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 16] >>> 51 | (in[inPos + 17] & 4095) << 13) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 17] >>> 12 & 33554431) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 17] >>> 37 & 33554431) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 17] >>> 62 | (in[inPos + 18] & 8388607) << 2) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 18] >>> 23 & 33554431) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 18] >>> 48 | (in[inPos + 19] & 511) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 19] >>> 9 & 33554431) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 19] >>> 34 & 33554431) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 19] >>> 59 | (in[inPos + 20] & 1048575) << 5) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 20] >>> 20 & 33554431) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 20] >>> 45 | (in[inPos + 21] & 63) << 19) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 21] >>> 6 & 33554431) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 21] >>> 31 & 33554431) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 21] >>> 56 | (in[inPos + 22] & 131071) << 8) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 22] >>> 17 & 33554431) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 22] >>> 42 | (in[inPos + 23] & 7) << 22) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 23] >>> 3 & 33554431) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 23] >>> 28 & 33554431) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 23] >>> 53 | (in[inPos + 24] & 16383) << 11) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 24] >>> 14 & 33554431) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 24] >>> 39) + out[outPos + 62];
  }

  private static void pack26(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 26 | (in[inPos + 2] - in[inPos + 1]) << 52;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 12 | (in[inPos + 3] - in[inPos + 2]) << 14 | (in[inPos + 4] - in[inPos + 3]) << 40;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 24 | (in[inPos + 5] - in[inPos + 4]) << 2 | (in[inPos + 6] - in[inPos + 5]) << 28 | (in[inPos + 7] - in[inPos + 6]) << 54;
    out[outPos + 3] = (in[inPos + 7] - in[inPos + 6]) >>> 10 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 42;
    out[outPos + 4] = (in[inPos + 9] - in[inPos + 8]) >>> 22 | (in[inPos + 10] - in[inPos + 9]) << 4 | (in[inPos + 11] - in[inPos + 10]) << 30 | (in[inPos + 12] - in[inPos + 11]) << 56;
    out[outPos + 5] = (in[inPos + 12] - in[inPos + 11]) >>> 8 | (in[inPos + 13] - in[inPos + 12]) << 18 | (in[inPos + 14] - in[inPos + 13]) << 44;
    out[outPos + 6] = (in[inPos + 14] - in[inPos + 13]) >>> 20 | (in[inPos + 15] - in[inPos + 14]) << 6 | (in[inPos + 16] - in[inPos + 15]) << 32 | (in[inPos + 17] - in[inPos + 16]) << 58;
    out[outPos + 7] = (in[inPos + 17] - in[inPos + 16]) >>> 6 | (in[inPos + 18] - in[inPos + 17]) << 20 | (in[inPos + 19] - in[inPos + 18]) << 46;
    out[outPos + 8] = (in[inPos + 19] - in[inPos + 18]) >>> 18 | (in[inPos + 20] - in[inPos + 19]) << 8 | (in[inPos + 21] - in[inPos + 20]) << 34 | (in[inPos + 22] - in[inPos + 21]) << 60;
    out[outPos + 9] = (in[inPos + 22] - in[inPos + 21]) >>> 4 | (in[inPos + 23] - in[inPos + 22]) << 22 | (in[inPos + 24] - in[inPos + 23]) << 48;
    out[outPos + 10] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 10 | (in[inPos + 26] - in[inPos + 25]) << 36 | (in[inPos + 27] - in[inPos + 26]) << 62;
    out[outPos + 11] = (in[inPos + 27] - in[inPos + 26]) >>> 2 | (in[inPos + 28] - in[inPos + 27]) << 24 | (in[inPos + 29] - in[inPos + 28]) << 50;
    out[outPos + 12] = (in[inPos + 29] - in[inPos + 28]) >>> 14 | (in[inPos + 30] - in[inPos + 29]) << 12 | (in[inPos + 31] - in[inPos + 30]) << 38;
    out[outPos + 13] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 26 | (in[inPos + 34] - in[inPos + 33]) << 52;
    out[outPos + 14] = (in[inPos + 34] - in[inPos + 33]) >>> 12 | (in[inPos + 35] - in[inPos + 34]) << 14 | (in[inPos + 36] - in[inPos + 35]) << 40;
    out[outPos + 15] = (in[inPos + 36] - in[inPos + 35]) >>> 24 | (in[inPos + 37] - in[inPos + 36]) << 2 | (in[inPos + 38] - in[inPos + 37]) << 28 | (in[inPos + 39] - in[inPos + 38]) << 54;
    out[outPos + 16] = (in[inPos + 39] - in[inPos + 38]) >>> 10 | (in[inPos + 40] - in[inPos + 39]) << 16 | (in[inPos + 41] - in[inPos + 40]) << 42;
    out[outPos + 17] = (in[inPos + 41] - in[inPos + 40]) >>> 22 | (in[inPos + 42] - in[inPos + 41]) << 4 | (in[inPos + 43] - in[inPos + 42]) << 30 | (in[inPos + 44] - in[inPos + 43]) << 56;
    out[outPos + 18] = (in[inPos + 44] - in[inPos + 43]) >>> 8 | (in[inPos + 45] - in[inPos + 44]) << 18 | (in[inPos + 46] - in[inPos + 45]) << 44;
    out[outPos + 19] = (in[inPos + 46] - in[inPos + 45]) >>> 20 | (in[inPos + 47] - in[inPos + 46]) << 6 | (in[inPos + 48] - in[inPos + 47]) << 32 | (in[inPos + 49] - in[inPos + 48]) << 58;
    out[outPos + 20] = (in[inPos + 49] - in[inPos + 48]) >>> 6 | (in[inPos + 50] - in[inPos + 49]) << 20 | (in[inPos + 51] - in[inPos + 50]) << 46;
    out[outPos + 21] = (in[inPos + 51] - in[inPos + 50]) >>> 18 | (in[inPos + 52] - in[inPos + 51]) << 8 | (in[inPos + 53] - in[inPos + 52]) << 34 | (in[inPos + 54] - in[inPos + 53]) << 60;
    out[outPos + 22] = (in[inPos + 54] - in[inPos + 53]) >>> 4 | (in[inPos + 55] - in[inPos + 54]) << 22 | (in[inPos + 56] - in[inPos + 55]) << 48;
    out[outPos + 23] = (in[inPos + 56] - in[inPos + 55]) >>> 16 | (in[inPos + 57] - in[inPos + 56]) << 10 | (in[inPos + 58] - in[inPos + 57]) << 36 | (in[inPos + 59] - in[inPos + 58]) << 62;
    out[outPos + 24] = (in[inPos + 59] - in[inPos + 58]) >>> 2 | (in[inPos + 60] - in[inPos + 59]) << 24 | (in[inPos + 61] - in[inPos + 60]) << 50;
    out[outPos + 25] = (in[inPos + 61] - in[inPos + 60]) >>> 14 | (in[inPos + 62] - in[inPos + 61]) << 12 | (in[inPos + 63] - in[inPos + 62]) << 38;
  }

  private static void unpack26(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 67108863) + initValue;
    out[outPos + 1] = (in[inPos] >>> 26 & 67108863) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 52 | (in[inPos + 1] & 16383) << 12) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 14 & 67108863) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 40 | (in[inPos + 2] & 3) << 24) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 2 & 67108863) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 28 & 67108863) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 54 | (in[inPos + 3] & 65535) << 10) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 16 & 67108863) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 42 | (in[inPos + 4] & 15) << 22) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 4 & 67108863) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 30 & 67108863) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 4] >>> 56 | (in[inPos + 5] & 262143) << 8) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 5] >>> 18 & 67108863) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 5] >>> 44 | (in[inPos + 6] & 63) << 20) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 6] >>> 6 & 67108863) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 6] >>> 32 & 67108863) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 6] >>> 58 | (in[inPos + 7] & 1048575) << 6) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 7] >>> 20 & 67108863) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 7] >>> 46 | (in[inPos + 8] & 255) << 18) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 8] >>> 8 & 67108863) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 8] >>> 34 & 67108863) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 8] >>> 60 | (in[inPos + 9] & 4194303) << 4) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 9] >>> 22 & 67108863) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 9] >>> 48 | (in[inPos + 10] & 1023) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 10] >>> 10 & 67108863) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 10] >>> 36 & 67108863) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 10] >>> 62 | (in[inPos + 11] & 16777215) << 2) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 11] >>> 24 & 67108863) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 11] >>> 50 | (in[inPos + 12] & 4095) << 14) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 12] >>> 12 & 67108863) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 12] >>> 38) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 13] & 67108863) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 13] >>> 26 & 67108863) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 13] >>> 52 | (in[inPos + 14] & 16383) << 12) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 14] >>> 14 & 67108863) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 14] >>> 40 | (in[inPos + 15] & 3) << 24) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 15] >>> 2 & 67108863) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 15] >>> 28 & 67108863) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 15] >>> 54 | (in[inPos + 16] & 65535) << 10) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 16] >>> 16 & 67108863) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 16] >>> 42 | (in[inPos + 17] & 15) << 22) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 17] >>> 4 & 67108863) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 17] >>> 30 & 67108863) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 17] >>> 56 | (in[inPos + 18] & 262143) << 8) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 18] >>> 18 & 67108863) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 18] >>> 44 | (in[inPos + 19] & 63) << 20) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 19] >>> 6 & 67108863) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 19] >>> 32 & 67108863) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 19] >>> 58 | (in[inPos + 20] & 1048575) << 6) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 20] >>> 20 & 67108863) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 20] >>> 46 | (in[inPos + 21] & 255) << 18) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 21] >>> 8 & 67108863) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 21] >>> 34 & 67108863) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 21] >>> 60 | (in[inPos + 22] & 4194303) << 4) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 22] >>> 22 & 67108863) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 22] >>> 48 | (in[inPos + 23] & 1023) << 16) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 23] >>> 10 & 67108863) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 23] >>> 36 & 67108863) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 23] >>> 62 | (in[inPos + 24] & 16777215) << 2) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 24] >>> 24 & 67108863) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 24] >>> 50 | (in[inPos + 25] & 4095) << 14) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 25] >>> 12 & 67108863) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 25] >>> 38) + out[outPos + 62];
  }

  private static void pack27(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 27 | (in[inPos + 2] - in[inPos + 1]) << 54;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 10 | (in[inPos + 3] - in[inPos + 2]) << 17 | (in[inPos + 4] - in[inPos + 3]) << 44;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 20 | (in[inPos + 5] - in[inPos + 4]) << 7 | (in[inPos + 6] - in[inPos + 5]) << 34 | (in[inPos + 7] - in[inPos + 6]) << 61;
    out[outPos + 3] = (in[inPos + 7] - in[inPos + 6]) >>> 3 | (in[inPos + 8] - in[inPos + 7]) << 24 | (in[inPos + 9] - in[inPos + 8]) << 51;
    out[outPos + 4] = (in[inPos + 9] - in[inPos + 8]) >>> 13 | (in[inPos + 10] - in[inPos + 9]) << 14 | (in[inPos + 11] - in[inPos + 10]) << 41;
    out[outPos + 5] = (in[inPos + 11] - in[inPos + 10]) >>> 23 | (in[inPos + 12] - in[inPos + 11]) << 4 | (in[inPos + 13] - in[inPos + 12]) << 31 | (in[inPos + 14] - in[inPos + 13]) << 58;
    out[outPos + 6] = (in[inPos + 14] - in[inPos + 13]) >>> 6 | (in[inPos + 15] - in[inPos + 14]) << 21 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 7] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 11 | (in[inPos + 18] - in[inPos + 17]) << 38;
    out[outPos + 8] = (in[inPos + 18] - in[inPos + 17]) >>> 26 | (in[inPos + 19] - in[inPos + 18]) << 1 | (in[inPos + 20] - in[inPos + 19]) << 28 | (in[inPos + 21] - in[inPos + 20]) << 55;
    out[outPos + 9] = (in[inPos + 21] - in[inPos + 20]) >>> 9 | (in[inPos + 22] - in[inPos + 21]) << 18 | (in[inPos + 23] - in[inPos + 22]) << 45;
    out[outPos + 10] = (in[inPos + 23] - in[inPos + 22]) >>> 19 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 35 | (in[inPos + 26] - in[inPos + 25]) << 62;
    out[outPos + 11] = (in[inPos + 26] - in[inPos + 25]) >>> 2 | (in[inPos + 27] - in[inPos + 26]) << 25 | (in[inPos + 28] - in[inPos + 27]) << 52;
    out[outPos + 12] = (in[inPos + 28] - in[inPos + 27]) >>> 12 | (in[inPos + 29] - in[inPos + 28]) << 15 | (in[inPos + 30] - in[inPos + 29]) << 42;
    out[outPos + 13] = (in[inPos + 30] - in[inPos + 29]) >>> 22 | (in[inPos + 31] - in[inPos + 30]) << 5 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 59;
    out[outPos + 14] = (in[inPos + 33] - in[inPos + 32]) >>> 5 | (in[inPos + 34] - in[inPos + 33]) << 22 | (in[inPos + 35] - in[inPos + 34]) << 49;
    out[outPos + 15] = (in[inPos + 35] - in[inPos + 34]) >>> 15 | (in[inPos + 36] - in[inPos + 35]) << 12 | (in[inPos + 37] - in[inPos + 36]) << 39;
    out[outPos + 16] = (in[inPos + 37] - in[inPos + 36]) >>> 25 | (in[inPos + 38] - in[inPos + 37]) << 2 | (in[inPos + 39] - in[inPos + 38]) << 29 | (in[inPos + 40] - in[inPos + 39]) << 56;
    out[outPos + 17] = (in[inPos + 40] - in[inPos + 39]) >>> 8 | (in[inPos + 41] - in[inPos + 40]) << 19 | (in[inPos + 42] - in[inPos + 41]) << 46;
    out[outPos + 18] = (in[inPos + 42] - in[inPos + 41]) >>> 18 | (in[inPos + 43] - in[inPos + 42]) << 9 | (in[inPos + 44] - in[inPos + 43]) << 36 | (in[inPos + 45] - in[inPos + 44]) << 63;
    out[outPos + 19] = (in[inPos + 45] - in[inPos + 44]) >>> 1 | (in[inPos + 46] - in[inPos + 45]) << 26 | (in[inPos + 47] - in[inPos + 46]) << 53;
    out[outPos + 20] = (in[inPos + 47] - in[inPos + 46]) >>> 11 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 43;
    out[outPos + 21] = (in[inPos + 49] - in[inPos + 48]) >>> 21 | (in[inPos + 50] - in[inPos + 49]) << 6 | (in[inPos + 51] - in[inPos + 50]) << 33 | (in[inPos + 52] - in[inPos + 51]) << 60;
    out[outPos + 22] = (in[inPos + 52] - in[inPos + 51]) >>> 4 | (in[inPos + 53] - in[inPos + 52]) << 23 | (in[inPos + 54] - in[inPos + 53]) << 50;
    out[outPos + 23] = (in[inPos + 54] - in[inPos + 53]) >>> 14 | (in[inPos + 55] - in[inPos + 54]) << 13 | (in[inPos + 56] - in[inPos + 55]) << 40;
    out[outPos + 24] = (in[inPos + 56] - in[inPos + 55]) >>> 24 | (in[inPos + 57] - in[inPos + 56]) << 3 | (in[inPos + 58] - in[inPos + 57]) << 30 | (in[inPos + 59] - in[inPos + 58]) << 57;
    out[outPos + 25] = (in[inPos + 59] - in[inPos + 58]) >>> 7 | (in[inPos + 60] - in[inPos + 59]) << 20 | (in[inPos + 61] - in[inPos + 60]) << 47;
    out[outPos + 26] = (in[inPos + 61] - in[inPos + 60]) >>> 17 | (in[inPos + 62] - in[inPos + 61]) << 10 | (in[inPos + 63] - in[inPos + 62]) << 37;
  }

  private static void unpack27(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 134217727) + initValue;
    out[outPos + 1] = (in[inPos] >>> 27 & 134217727) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 54 | (in[inPos + 1] & 131071) << 10) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 17 & 134217727) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 44 | (in[inPos + 2] & 127) << 20) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 7 & 134217727) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 34 & 134217727) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 61 | (in[inPos + 3] & 16777215) << 3) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 24 & 134217727) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 51 | (in[inPos + 4] & 16383) << 13) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 14 & 134217727) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 41 | (in[inPos + 5] & 15) << 23) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 5] >>> 4 & 134217727) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 5] >>> 31 & 134217727) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 2097151) << 6) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 6] >>> 21 & 134217727) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 6] >>> 48 | (in[inPos + 7] & 2047) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 7] >>> 11 & 134217727) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 7] >>> 38 | (in[inPos + 8] & 1) << 26) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 8] >>> 1 & 134217727) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 8] >>> 28 & 134217727) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 8] >>> 55 | (in[inPos + 9] & 262143) << 9) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 9] >>> 18 & 134217727) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 9] >>> 45 | (in[inPos + 10] & 255) << 19) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 10] >>> 8 & 134217727) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 10] >>> 35 & 134217727) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 10] >>> 62 | (in[inPos + 11] & 33554431) << 2) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 11] >>> 25 & 134217727) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 32767) << 12) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 12] >>> 15 & 134217727) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 12] >>> 42 | (in[inPos + 13] & 31) << 22) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 13] >>> 5 & 134217727) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 13] >>> 32 & 134217727) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 13] >>> 59 | (in[inPos + 14] & 4194303) << 5) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 14] >>> 22 & 134217727) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 14] >>> 49 | (in[inPos + 15] & 4095) << 15) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 15] >>> 12 & 134217727) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 15] >>> 39 | (in[inPos + 16] & 3) << 25) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 16] >>> 2 & 134217727) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 16] >>> 29 & 134217727) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 524287) << 8) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 17] >>> 19 & 134217727) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 17] >>> 46 | (in[inPos + 18] & 511) << 18) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 18] >>> 9 & 134217727) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 18] >>> 36 & 134217727) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 18] >>> 63 | (in[inPos + 19] & 67108863) << 1) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 19] >>> 26 & 134217727) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 19] >>> 53 | (in[inPos + 20] & 65535) << 11) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 20] >>> 16 & 134217727) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 20] >>> 43 | (in[inPos + 21] & 63) << 21) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 21] >>> 6 & 134217727) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 21] >>> 33 & 134217727) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 21] >>> 60 | (in[inPos + 22] & 8388607) << 4) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 22] >>> 23 & 134217727) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 22] >>> 50 | (in[inPos + 23] & 8191) << 14) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 23] >>> 13 & 134217727) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 23] >>> 40 | (in[inPos + 24] & 7) << 24) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 24] >>> 3 & 134217727) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 24] >>> 30 & 134217727) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 24] >>> 57 | (in[inPos + 25] & 1048575) << 7) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 25] >>> 20 & 134217727) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 25] >>> 47 | (in[inPos + 26] & 1023) << 17) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 26] >>> 10 & 134217727) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 26] >>> 37) + out[outPos + 62];
  }

  private static void pack28(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 28 | (in[inPos + 2] - in[inPos + 1]) << 56;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 8 | (in[inPos + 3] - in[inPos + 2]) << 20 | (in[inPos + 4] - in[inPos + 3]) << 48;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 16 | (in[inPos + 5] - in[inPos + 4]) << 12 | (in[inPos + 6] - in[inPos + 5]) << 40;
    out[outPos + 3] = (in[inPos + 6] - in[inPos + 5]) >>> 24 | (in[inPos + 7] - in[inPos + 6]) << 4 | (in[inPos + 8] - in[inPos + 7]) << 32 | (in[inPos + 9] - in[inPos + 8]) << 60;
    out[outPos + 4] = (in[inPos + 9] - in[inPos + 8]) >>> 4 | (in[inPos + 10] - in[inPos + 9]) << 24 | (in[inPos + 11] - in[inPos + 10]) << 52;
    out[outPos + 5] = (in[inPos + 11] - in[inPos + 10]) >>> 12 | (in[inPos + 12] - in[inPos + 11]) << 16 | (in[inPos + 13] - in[inPos + 12]) << 44;
    out[outPos + 6] = (in[inPos + 13] - in[inPos + 12]) >>> 20 | (in[inPos + 14] - in[inPos + 13]) << 8 | (in[inPos + 15] - in[inPos + 14]) << 36;
    out[outPos + 7] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 28 | (in[inPos + 18] - in[inPos + 17]) << 56;
    out[outPos + 8] = (in[inPos + 18] - in[inPos + 17]) >>> 8 | (in[inPos + 19] - in[inPos + 18]) << 20 | (in[inPos + 20] - in[inPos + 19]) << 48;
    out[outPos + 9] = (in[inPos + 20] - in[inPos + 19]) >>> 16 | (in[inPos + 21] - in[inPos + 20]) << 12 | (in[inPos + 22] - in[inPos + 21]) << 40;
    out[outPos + 10] = (in[inPos + 22] - in[inPos + 21]) >>> 24 | (in[inPos + 23] - in[inPos + 22]) << 4 | (in[inPos + 24] - in[inPos + 23]) << 32 | (in[inPos + 25] - in[inPos + 24]) << 60;
    out[outPos + 11] = (in[inPos + 25] - in[inPos + 24]) >>> 4 | (in[inPos + 26] - in[inPos + 25]) << 24 | (in[inPos + 27] - in[inPos + 26]) << 52;
    out[outPos + 12] = (in[inPos + 27] - in[inPos + 26]) >>> 12 | (in[inPos + 28] - in[inPos + 27]) << 16 | (in[inPos + 29] - in[inPos + 28]) << 44;
    out[outPos + 13] = (in[inPos + 29] - in[inPos + 28]) >>> 20 | (in[inPos + 30] - in[inPos + 29]) << 8 | (in[inPos + 31] - in[inPos + 30]) << 36;
    out[outPos + 14] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 28 | (in[inPos + 34] - in[inPos + 33]) << 56;
    out[outPos + 15] = (in[inPos + 34] - in[inPos + 33]) >>> 8 | (in[inPos + 35] - in[inPos + 34]) << 20 | (in[inPos + 36] - in[inPos + 35]) << 48;
    out[outPos + 16] = (in[inPos + 36] - in[inPos + 35]) >>> 16 | (in[inPos + 37] - in[inPos + 36]) << 12 | (in[inPos + 38] - in[inPos + 37]) << 40;
    out[outPos + 17] = (in[inPos + 38] - in[inPos + 37]) >>> 24 | (in[inPos + 39] - in[inPos + 38]) << 4 | (in[inPos + 40] - in[inPos + 39]) << 32 | (in[inPos + 41] - in[inPos + 40]) << 60;
    out[outPos + 18] = (in[inPos + 41] - in[inPos + 40]) >>> 4 | (in[inPos + 42] - in[inPos + 41]) << 24 | (in[inPos + 43] - in[inPos + 42]) << 52;
    out[outPos + 19] = (in[inPos + 43] - in[inPos + 42]) >>> 12 | (in[inPos + 44] - in[inPos + 43]) << 16 | (in[inPos + 45] - in[inPos + 44]) << 44;
    out[outPos + 20] = (in[inPos + 45] - in[inPos + 44]) >>> 20 | (in[inPos + 46] - in[inPos + 45]) << 8 | (in[inPos + 47] - in[inPos + 46]) << 36;
    out[outPos + 21] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 28 | (in[inPos + 50] - in[inPos + 49]) << 56;
    out[outPos + 22] = (in[inPos + 50] - in[inPos + 49]) >>> 8 | (in[inPos + 51] - in[inPos + 50]) << 20 | (in[inPos + 52] - in[inPos + 51]) << 48;
    out[outPos + 23] = (in[inPos + 52] - in[inPos + 51]) >>> 16 | (in[inPos + 53] - in[inPos + 52]) << 12 | (in[inPos + 54] - in[inPos + 53]) << 40;
    out[outPos + 24] = (in[inPos + 54] - in[inPos + 53]) >>> 24 | (in[inPos + 55] - in[inPos + 54]) << 4 | (in[inPos + 56] - in[inPos + 55]) << 32 | (in[inPos + 57] - in[inPos + 56]) << 60;
    out[outPos + 25] = (in[inPos + 57] - in[inPos + 56]) >>> 4 | (in[inPos + 58] - in[inPos + 57]) << 24 | (in[inPos + 59] - in[inPos + 58]) << 52;
    out[outPos + 26] = (in[inPos + 59] - in[inPos + 58]) >>> 12 | (in[inPos + 60] - in[inPos + 59]) << 16 | (in[inPos + 61] - in[inPos + 60]) << 44;
    out[outPos + 27] = (in[inPos + 61] - in[inPos + 60]) >>> 20 | (in[inPos + 62] - in[inPos + 61]) << 8 | (in[inPos + 63] - in[inPos + 62]) << 36;
  }

  private static void unpack28(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 268435455) + initValue;
    out[outPos + 1] = (in[inPos] >>> 28 & 268435455) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 56 | (in[inPos + 1] & 1048575) << 8) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 20 & 268435455) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 48 | (in[inPos + 2] & 4095) << 16) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 12 & 268435455) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 40 | (in[inPos + 3] & 15) << 24) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 4 & 268435455) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 32 & 268435455) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 16777215) << 4) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 24 & 268435455) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 52 | (in[inPos + 5] & 65535) << 12) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 5] >>> 16 & 268435455) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 5] >>> 44 | (in[inPos + 6] & 255) << 20) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 6] >>> 8 & 268435455) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 6] >>> 36) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 7] & 268435455) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 7] >>> 28 & 268435455) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 1048575) << 8) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 8] >>> 20 & 268435455) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 8] >>> 48 | (in[inPos + 9] & 4095) << 16) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 9] >>> 12 & 268435455) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 9] >>> 40 | (in[inPos + 10] & 15) << 24) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 10] >>> 4 & 268435455) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 10] >>> 32 & 268435455) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 10] >>> 60 | (in[inPos + 11] & 16777215) << 4) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 11] >>> 24 & 268435455) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 65535) << 12) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 12] >>> 16 & 268435455) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 12] >>> 44 | (in[inPos + 13] & 255) << 20) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 13] >>> 8 & 268435455) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 13] >>> 36) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 14] & 268435455) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 14] >>> 28 & 268435455) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 14] >>> 56 | (in[inPos + 15] & 1048575) << 8) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 15] >>> 20 & 268435455) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 4095) << 16) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 16] >>> 12 & 268435455) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 16] >>> 40 | (in[inPos + 17] & 15) << 24) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 17] >>> 4 & 268435455) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 17] >>> 32 & 268435455) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 17] >>> 60 | (in[inPos + 18] & 16777215) << 4) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 18] >>> 24 & 268435455) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 18] >>> 52 | (in[inPos + 19] & 65535) << 12) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 19] >>> 16 & 268435455) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 255) << 20) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 20] >>> 8 & 268435455) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 20] >>> 36) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 21] & 268435455) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 21] >>> 28 & 268435455) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 21] >>> 56 | (in[inPos + 22] & 1048575) << 8) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 22] >>> 20 & 268435455) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 22] >>> 48 | (in[inPos + 23] & 4095) << 16) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 23] >>> 12 & 268435455) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 23] >>> 40 | (in[inPos + 24] & 15) << 24) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 24] >>> 4 & 268435455) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 24] >>> 32 & 268435455) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 24] >>> 60 | (in[inPos + 25] & 16777215) << 4) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 25] >>> 24 & 268435455) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 25] >>> 52 | (in[inPos + 26] & 65535) << 12) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 26] >>> 16 & 268435455) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 26] >>> 44 | (in[inPos + 27] & 255) << 20) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 27] >>> 8 & 268435455) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 27] >>> 36) + out[outPos + 62];
  }

  private static void pack29(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 29 | (in[inPos + 2] - in[inPos + 1]) << 58;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 6 | (in[inPos + 3] - in[inPos + 2]) << 23 | (in[inPos + 4] - in[inPos + 3]) << 52;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 12 | (in[inPos + 5] - in[inPos + 4]) << 17 | (in[inPos + 6] - in[inPos + 5]) << 46;
    out[outPos + 3] = (in[inPos + 6] - in[inPos + 5]) >>> 18 | (in[inPos + 7] - in[inPos + 6]) << 11 | (in[inPos + 8] - in[inPos + 7]) << 40;
    out[outPos + 4] = (in[inPos + 8] - in[inPos + 7]) >>> 24 | (in[inPos + 9] - in[inPos + 8]) << 5 | (in[inPos + 10] - in[inPos + 9]) << 34 | (in[inPos + 11] - in[inPos + 10]) << 63;
    out[outPos + 5] = (in[inPos + 11] - in[inPos + 10]) >>> 1 | (in[inPos + 12] - in[inPos + 11]) << 28 | (in[inPos + 13] - in[inPos + 12]) << 57;
    out[outPos + 6] = (in[inPos + 13] - in[inPos + 12]) >>> 7 | (in[inPos + 14] - in[inPos + 13]) << 22 | (in[inPos + 15] - in[inPos + 14]) << 51;
    out[outPos + 7] = (in[inPos + 15] - in[inPos + 14]) >>> 13 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 45;
    out[outPos + 8] = (in[inPos + 17] - in[inPos + 16]) >>> 19 | (in[inPos + 18] - in[inPos + 17]) << 10 | (in[inPos + 19] - in[inPos + 18]) << 39;
    out[outPos + 9] = (in[inPos + 19] - in[inPos + 18]) >>> 25 | (in[inPos + 20] - in[inPos + 19]) << 4 | (in[inPos + 21] - in[inPos + 20]) << 33 | (in[inPos + 22] - in[inPos + 21]) << 62;
    out[outPos + 10] = (in[inPos + 22] - in[inPos + 21]) >>> 2 | (in[inPos + 23] - in[inPos + 22]) << 27 | (in[inPos + 24] - in[inPos + 23]) << 56;
    out[outPos + 11] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 21 | (in[inPos + 26] - in[inPos + 25]) << 50;
    out[outPos + 12] = (in[inPos + 26] - in[inPos + 25]) >>> 14 | (in[inPos + 27] - in[inPos + 26]) << 15 | (in[inPos + 28] - in[inPos + 27]) << 44;
    out[outPos + 13] = (in[inPos + 28] - in[inPos + 27]) >>> 20 | (in[inPos + 29] - in[inPos + 28]) << 9 | (in[inPos + 30] - in[inPos + 29]) << 38;
    out[outPos + 14] = (in[inPos + 30] - in[inPos + 29]) >>> 26 | (in[inPos + 31] - in[inPos + 30]) << 3 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 61;
    out[outPos + 15] = (in[inPos + 33] - in[inPos + 32]) >>> 3 | (in[inPos + 34] - in[inPos + 33]) << 26 | (in[inPos + 35] - in[inPos + 34]) << 55;
    out[outPos + 16] = (in[inPos + 35] - in[inPos + 34]) >>> 9 | (in[inPos + 36] - in[inPos + 35]) << 20 | (in[inPos + 37] - in[inPos + 36]) << 49;
    out[outPos + 17] = (in[inPos + 37] - in[inPos + 36]) >>> 15 | (in[inPos + 38] - in[inPos + 37]) << 14 | (in[inPos + 39] - in[inPos + 38]) << 43;
    out[outPos + 18] = (in[inPos + 39] - in[inPos + 38]) >>> 21 | (in[inPos + 40] - in[inPos + 39]) << 8 | (in[inPos + 41] - in[inPos + 40]) << 37;
    out[outPos + 19] = (in[inPos + 41] - in[inPos + 40]) >>> 27 | (in[inPos + 42] - in[inPos + 41]) << 2 | (in[inPos + 43] - in[inPos + 42]) << 31 | (in[inPos + 44] - in[inPos + 43]) << 60;
    out[outPos + 20] = (in[inPos + 44] - in[inPos + 43]) >>> 4 | (in[inPos + 45] - in[inPos + 44]) << 25 | (in[inPos + 46] - in[inPos + 45]) << 54;
    out[outPos + 21] = (in[inPos + 46] - in[inPos + 45]) >>> 10 | (in[inPos + 47] - in[inPos + 46]) << 19 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 22] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 13 | (in[inPos + 50] - in[inPos + 49]) << 42;
    out[outPos + 23] = (in[inPos + 50] - in[inPos + 49]) >>> 22 | (in[inPos + 51] - in[inPos + 50]) << 7 | (in[inPos + 52] - in[inPos + 51]) << 36;
    out[outPos + 24] = (in[inPos + 52] - in[inPos + 51]) >>> 28 | (in[inPos + 53] - in[inPos + 52]) << 1 | (in[inPos + 54] - in[inPos + 53]) << 30 | (in[inPos + 55] - in[inPos + 54]) << 59;
    out[outPos + 25] = (in[inPos + 55] - in[inPos + 54]) >>> 5 | (in[inPos + 56] - in[inPos + 55]) << 24 | (in[inPos + 57] - in[inPos + 56]) << 53;
    out[outPos + 26] = (in[inPos + 57] - in[inPos + 56]) >>> 11 | (in[inPos + 58] - in[inPos + 57]) << 18 | (in[inPos + 59] - in[inPos + 58]) << 47;
    out[outPos + 27] = (in[inPos + 59] - in[inPos + 58]) >>> 17 | (in[inPos + 60] - in[inPos + 59]) << 12 | (in[inPos + 61] - in[inPos + 60]) << 41;
    out[outPos + 28] = (in[inPos + 61] - in[inPos + 60]) >>> 23 | (in[inPos + 62] - in[inPos + 61]) << 6 | (in[inPos + 63] - in[inPos + 62]) << 35;
  }

  private static void unpack29(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 536870911) + initValue;
    out[outPos + 1] = (in[inPos] >>> 29 & 536870911) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 58 | (in[inPos + 1] & 8388607) << 6) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 23 & 536870911) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 52 | (in[inPos + 2] & 131071) << 12) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 17 & 536870911) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 46 | (in[inPos + 3] & 2047) << 18) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 11 & 536870911) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 40 | (in[inPos + 4] & 31) << 24) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 5 & 536870911) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 34 & 536870911) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 63 | (in[inPos + 5] & 268435455) << 1) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 5] >>> 28 & 536870911) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 5] >>> 57 | (in[inPos + 6] & 4194303) << 7) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 6] >>> 22 & 536870911) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 6] >>> 51 | (in[inPos + 7] & 65535) << 13) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 7] >>> 16 & 536870911) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 7] >>> 45 | (in[inPos + 8] & 1023) << 19) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 8] >>> 10 & 536870911) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 8] >>> 39 | (in[inPos + 9] & 15) << 25) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 9] >>> 4 & 536870911) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 9] >>> 33 & 536870911) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 9] >>> 62 | (in[inPos + 10] & 134217727) << 2) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 10] >>> 27 & 536870911) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 10] >>> 56 | (in[inPos + 11] & 2097151) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 11] >>> 21 & 536870911) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 11] >>> 50 | (in[inPos + 12] & 32767) << 14) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 12] >>> 15 & 536870911) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 12] >>> 44 | (in[inPos + 13] & 511) << 20) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 13] >>> 9 & 536870911) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 13] >>> 38 | (in[inPos + 14] & 7) << 26) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 14] >>> 3 & 536870911) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 14] >>> 32 & 536870911) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 14] >>> 61 | (in[inPos + 15] & 67108863) << 3) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 15] >>> 26 & 536870911) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 15] >>> 55 | (in[inPos + 16] & 1048575) << 9) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 16] >>> 20 & 536870911) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 16] >>> 49 | (in[inPos + 17] & 16383) << 15) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 17] >>> 14 & 536870911) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 17] >>> 43 | (in[inPos + 18] & 255) << 21) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 18] >>> 8 & 536870911) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 18] >>> 37 | (in[inPos + 19] & 3) << 27) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 19] >>> 2 & 536870911) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 19] >>> 31 & 536870911) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 19] >>> 60 | (in[inPos + 20] & 33554431) << 4) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 20] >>> 25 & 536870911) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 20] >>> 54 | (in[inPos + 21] & 524287) << 10) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 21] >>> 19 & 536870911) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 21] >>> 48 | (in[inPos + 22] & 8191) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 22] >>> 13 & 536870911) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 22] >>> 42 | (in[inPos + 23] & 127) << 22) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 23] >>> 7 & 536870911) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 23] >>> 36 | (in[inPos + 24] & 1) << 28) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 24] >>> 1 & 536870911) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 24] >>> 30 & 536870911) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 24] >>> 59 | (in[inPos + 25] & 16777215) << 5) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 25] >>> 24 & 536870911) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 25] >>> 53 | (in[inPos + 26] & 262143) << 11) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 26] >>> 18 & 536870911) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 26] >>> 47 | (in[inPos + 27] & 4095) << 17) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 27] >>> 12 & 536870911) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 27] >>> 41 | (in[inPos + 28] & 63) << 23) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 28] >>> 6 & 536870911) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 28] >>> 35) + out[outPos + 62];
  }

  private static void pack30(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 30 | (in[inPos + 2] - in[inPos + 1]) << 60;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 4 | (in[inPos + 3] - in[inPos + 2]) << 26 | (in[inPos + 4] - in[inPos + 3]) << 56;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 8 | (in[inPos + 5] - in[inPos + 4]) << 22 | (in[inPos + 6] - in[inPos + 5]) << 52;
    out[outPos + 3] = (in[inPos + 6] - in[inPos + 5]) >>> 12 | (in[inPos + 7] - in[inPos + 6]) << 18 | (in[inPos + 8] - in[inPos + 7]) << 48;
    out[outPos + 4] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 14 | (in[inPos + 10] - in[inPos + 9]) << 44;
    out[outPos + 5] = (in[inPos + 10] - in[inPos + 9]) >>> 20 | (in[inPos + 11] - in[inPos + 10]) << 10 | (in[inPos + 12] - in[inPos + 11]) << 40;
    out[outPos + 6] = (in[inPos + 12] - in[inPos + 11]) >>> 24 | (in[inPos + 13] - in[inPos + 12]) << 6 | (in[inPos + 14] - in[inPos + 13]) << 36;
    out[outPos + 7] = (in[inPos + 14] - in[inPos + 13]) >>> 28 | (in[inPos + 15] - in[inPos + 14]) << 2 | (in[inPos + 16] - in[inPos + 15]) << 32 | (in[inPos + 17] - in[inPos + 16]) << 62;
    out[outPos + 8] = (in[inPos + 17] - in[inPos + 16]) >>> 2 | (in[inPos + 18] - in[inPos + 17]) << 28 | (in[inPos + 19] - in[inPos + 18]) << 58;
    out[outPos + 9] = (in[inPos + 19] - in[inPos + 18]) >>> 6 | (in[inPos + 20] - in[inPos + 19]) << 24 | (in[inPos + 21] - in[inPos + 20]) << 54;
    out[outPos + 10] = (in[inPos + 21] - in[inPos + 20]) >>> 10 | (in[inPos + 22] - in[inPos + 21]) << 20 | (in[inPos + 23] - in[inPos + 22]) << 50;
    out[outPos + 11] = (in[inPos + 23] - in[inPos + 22]) >>> 14 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 46;
    out[outPos + 12] = (in[inPos + 25] - in[inPos + 24]) >>> 18 | (in[inPos + 26] - in[inPos + 25]) << 12 | (in[inPos + 27] - in[inPos + 26]) << 42;
    out[outPos + 13] = (in[inPos + 27] - in[inPos + 26]) >>> 22 | (in[inPos + 28] - in[inPos + 27]) << 8 | (in[inPos + 29] - in[inPos + 28]) << 38;
    out[outPos + 14] = (in[inPos + 29] - in[inPos + 28]) >>> 26 | (in[inPos + 30] - in[inPos + 29]) << 4 | (in[inPos + 31] - in[inPos + 30]) << 34;
    out[outPos + 15] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 30 | (in[inPos + 34] - in[inPos + 33]) << 60;
    out[outPos + 16] = (in[inPos + 34] - in[inPos + 33]) >>> 4 | (in[inPos + 35] - in[inPos + 34]) << 26 | (in[inPos + 36] - in[inPos + 35]) << 56;
    out[outPos + 17] = (in[inPos + 36] - in[inPos + 35]) >>> 8 | (in[inPos + 37] - in[inPos + 36]) << 22 | (in[inPos + 38] - in[inPos + 37]) << 52;
    out[outPos + 18] = (in[inPos + 38] - in[inPos + 37]) >>> 12 | (in[inPos + 39] - in[inPos + 38]) << 18 | (in[inPos + 40] - in[inPos + 39]) << 48;
    out[outPos + 19] = (in[inPos + 40] - in[inPos + 39]) >>> 16 | (in[inPos + 41] - in[inPos + 40]) << 14 | (in[inPos + 42] - in[inPos + 41]) << 44;
    out[outPos + 20] = (in[inPos + 42] - in[inPos + 41]) >>> 20 | (in[inPos + 43] - in[inPos + 42]) << 10 | (in[inPos + 44] - in[inPos + 43]) << 40;
    out[outPos + 21] = (in[inPos + 44] - in[inPos + 43]) >>> 24 | (in[inPos + 45] - in[inPos + 44]) << 6 | (in[inPos + 46] - in[inPos + 45]) << 36;
    out[outPos + 22] = (in[inPos + 46] - in[inPos + 45]) >>> 28 | (in[inPos + 47] - in[inPos + 46]) << 2 | (in[inPos + 48] - in[inPos + 47]) << 32 | (in[inPos + 49] - in[inPos + 48]) << 62;
    out[outPos + 23] = (in[inPos + 49] - in[inPos + 48]) >>> 2 | (in[inPos + 50] - in[inPos + 49]) << 28 | (in[inPos + 51] - in[inPos + 50]) << 58;
    out[outPos + 24] = (in[inPos + 51] - in[inPos + 50]) >>> 6 | (in[inPos + 52] - in[inPos + 51]) << 24 | (in[inPos + 53] - in[inPos + 52]) << 54;
    out[outPos + 25] = (in[inPos + 53] - in[inPos + 52]) >>> 10 | (in[inPos + 54] - in[inPos + 53]) << 20 | (in[inPos + 55] - in[inPos + 54]) << 50;
    out[outPos + 26] = (in[inPos + 55] - in[inPos + 54]) >>> 14 | (in[inPos + 56] - in[inPos + 55]) << 16 | (in[inPos + 57] - in[inPos + 56]) << 46;
    out[outPos + 27] = (in[inPos + 57] - in[inPos + 56]) >>> 18 | (in[inPos + 58] - in[inPos + 57]) << 12 | (in[inPos + 59] - in[inPos + 58]) << 42;
    out[outPos + 28] = (in[inPos + 59] - in[inPos + 58]) >>> 22 | (in[inPos + 60] - in[inPos + 59]) << 8 | (in[inPos + 61] - in[inPos + 60]) << 38;
    out[outPos + 29] = (in[inPos + 61] - in[inPos + 60]) >>> 26 | (in[inPos + 62] - in[inPos + 61]) << 4 | (in[inPos + 63] - in[inPos + 62]) << 34;
  }

  private static void unpack30(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1073741823) + initValue;
    out[outPos + 1] = (in[inPos] >>> 30 & 1073741823) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 60 | (in[inPos + 1] & 67108863) << 4) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 26 & 1073741823) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 56 | (in[inPos + 2] & 4194303) << 8) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 22 & 1073741823) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 52 | (in[inPos + 3] & 262143) << 12) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 18 & 1073741823) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 48 | (in[inPos + 4] & 16383) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 14 & 1073741823) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 44 | (in[inPos + 5] & 1023) << 20) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 5] >>> 10 & 1073741823) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 5] >>> 40 | (in[inPos + 6] & 63) << 24) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 6] >>> 6 & 1073741823) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 6] >>> 36 | (in[inPos + 7] & 3) << 28) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 7] >>> 2 & 1073741823) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 7] >>> 32 & 1073741823) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 7] >>> 62 | (in[inPos + 8] & 268435455) << 2) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 8] >>> 28 & 1073741823) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 8] >>> 58 | (in[inPos + 9] & 16777215) << 6) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 9] >>> 24 & 1073741823) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 1048575) << 10) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 10] >>> 20 & 1073741823) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 10] >>> 50 | (in[inPos + 11] & 65535) << 14) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 11] >>> 16 & 1073741823) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 11] >>> 46 | (in[inPos + 12] & 4095) << 18) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 12] >>> 12 & 1073741823) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 12] >>> 42 | (in[inPos + 13] & 255) << 22) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 13] >>> 8 & 1073741823) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 13] >>> 38 | (in[inPos + 14] & 15) << 26) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 14] >>> 4 & 1073741823) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 14] >>> 34) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 15] & 1073741823) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 15] >>> 30 & 1073741823) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 15] >>> 60 | (in[inPos + 16] & 67108863) << 4) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 16] >>> 26 & 1073741823) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 4194303) << 8) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 17] >>> 22 & 1073741823) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 17] >>> 52 | (in[inPos + 18] & 262143) << 12) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 18] >>> 18 & 1073741823) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 18] >>> 48 | (in[inPos + 19] & 16383) << 16) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 19] >>> 14 & 1073741823) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 1023) << 20) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 20] >>> 10 & 1073741823) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 20] >>> 40 | (in[inPos + 21] & 63) << 24) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 21] >>> 6 & 1073741823) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 21] >>> 36 | (in[inPos + 22] & 3) << 28) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 22] >>> 2 & 1073741823) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 22] >>> 32 & 1073741823) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 22] >>> 62 | (in[inPos + 23] & 268435455) << 2) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 23] >>> 28 & 1073741823) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 23] >>> 58 | (in[inPos + 24] & 16777215) << 6) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 24] >>> 24 & 1073741823) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 24] >>> 54 | (in[inPos + 25] & 1048575) << 10) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 25] >>> 20 & 1073741823) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 25] >>> 50 | (in[inPos + 26] & 65535) << 14) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 26] >>> 16 & 1073741823) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 26] >>> 46 | (in[inPos + 27] & 4095) << 18) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 27] >>> 12 & 1073741823) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 27] >>> 42 | (in[inPos + 28] & 255) << 22) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 28] >>> 8 & 1073741823) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 28] >>> 38 | (in[inPos + 29] & 15) << 26) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 29] >>> 4 & 1073741823) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 29] >>> 34) + out[outPos + 62];
  }

  private static void pack31(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 31 | (in[inPos + 2] - in[inPos + 1]) << 62;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 2 | (in[inPos + 3] - in[inPos + 2]) << 29 | (in[inPos + 4] - in[inPos + 3]) << 60;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 4 | (in[inPos + 5] - in[inPos + 4]) << 27 | (in[inPos + 6] - in[inPos + 5]) << 58;
    out[outPos + 3] = (in[inPos + 6] - in[inPos + 5]) >>> 6 | (in[inPos + 7] - in[inPos + 6]) << 25 | (in[inPos + 8] - in[inPos + 7]) << 56;
    out[outPos + 4] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 23 | (in[inPos + 10] - in[inPos + 9]) << 54;
    out[outPos + 5] = (in[inPos + 10] - in[inPos + 9]) >>> 10 | (in[inPos + 11] - in[inPos + 10]) << 21 | (in[inPos + 12] - in[inPos + 11]) << 52;
    out[outPos + 6] = (in[inPos + 12] - in[inPos + 11]) >>> 12 | (in[inPos + 13] - in[inPos + 12]) << 19 | (in[inPos + 14] - in[inPos + 13]) << 50;
    out[outPos + 7] = (in[inPos + 14] - in[inPos + 13]) >>> 14 | (in[inPos + 15] - in[inPos + 14]) << 17 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 8] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 15 | (in[inPos + 18] - in[inPos + 17]) << 46;
    out[outPos + 9] = (in[inPos + 18] - in[inPos + 17]) >>> 18 | (in[inPos + 19] - in[inPos + 18]) << 13 | (in[inPos + 20] - in[inPos + 19]) << 44;
    out[outPos + 10] = (in[inPos + 20] - in[inPos + 19]) >>> 20 | (in[inPos + 21] - in[inPos + 20]) << 11 | (in[inPos + 22] - in[inPos + 21]) << 42;
    out[outPos + 11] = (in[inPos + 22] - in[inPos + 21]) >>> 22 | (in[inPos + 23] - in[inPos + 22]) << 9 | (in[inPos + 24] - in[inPos + 23]) << 40;
    out[outPos + 12] = (in[inPos + 24] - in[inPos + 23]) >>> 24 | (in[inPos + 25] - in[inPos + 24]) << 7 | (in[inPos + 26] - in[inPos + 25]) << 38;
    out[outPos + 13] = (in[inPos + 26] - in[inPos + 25]) >>> 26 | (in[inPos + 27] - in[inPos + 26]) << 5 | (in[inPos + 28] - in[inPos + 27]) << 36;
    out[outPos + 14] = (in[inPos + 28] - in[inPos + 27]) >>> 28 | (in[inPos + 29] - in[inPos + 28]) << 3 | (in[inPos + 30] - in[inPos + 29]) << 34;
    out[outPos + 15] = (in[inPos + 30] - in[inPos + 29]) >>> 30 | (in[inPos + 31] - in[inPos + 30]) << 1 | (in[inPos + 32] - in[inPos + 31]) << 32 | (in[inPos + 33] - in[inPos + 32]) << 63;
    out[outPos + 16] = (in[inPos + 33] - in[inPos + 32]) >>> 1 | (in[inPos + 34] - in[inPos + 33]) << 30 | (in[inPos + 35] - in[inPos + 34]) << 61;
    out[outPos + 17] = (in[inPos + 35] - in[inPos + 34]) >>> 3 | (in[inPos + 36] - in[inPos + 35]) << 28 | (in[inPos + 37] - in[inPos + 36]) << 59;
    out[outPos + 18] = (in[inPos + 37] - in[inPos + 36]) >>> 5 | (in[inPos + 38] - in[inPos + 37]) << 26 | (in[inPos + 39] - in[inPos + 38]) << 57;
    out[outPos + 19] = (in[inPos + 39] - in[inPos + 38]) >>> 7 | (in[inPos + 40] - in[inPos + 39]) << 24 | (in[inPos + 41] - in[inPos + 40]) << 55;
    out[outPos + 20] = (in[inPos + 41] - in[inPos + 40]) >>> 9 | (in[inPos + 42] - in[inPos + 41]) << 22 | (in[inPos + 43] - in[inPos + 42]) << 53;
    out[outPos + 21] = (in[inPos + 43] - in[inPos + 42]) >>> 11 | (in[inPos + 44] - in[inPos + 43]) << 20 | (in[inPos + 45] - in[inPos + 44]) << 51;
    out[outPos + 22] = (in[inPos + 45] - in[inPos + 44]) >>> 13 | (in[inPos + 46] - in[inPos + 45]) << 18 | (in[inPos + 47] - in[inPos + 46]) << 49;
    out[outPos + 23] = (in[inPos + 47] - in[inPos + 46]) >>> 15 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 47;
    out[outPos + 24] = (in[inPos + 49] - in[inPos + 48]) >>> 17 | (in[inPos + 50] - in[inPos + 49]) << 14 | (in[inPos + 51] - in[inPos + 50]) << 45;
    out[outPos + 25] = (in[inPos + 51] - in[inPos + 50]) >>> 19 | (in[inPos + 52] - in[inPos + 51]) << 12 | (in[inPos + 53] - in[inPos + 52]) << 43;
    out[outPos + 26] = (in[inPos + 53] - in[inPos + 52]) >>> 21 | (in[inPos + 54] - in[inPos + 53]) << 10 | (in[inPos + 55] - in[inPos + 54]) << 41;
    out[outPos + 27] = (in[inPos + 55] - in[inPos + 54]) >>> 23 | (in[inPos + 56] - in[inPos + 55]) << 8 | (in[inPos + 57] - in[inPos + 56]) << 39;
    out[outPos + 28] = (in[inPos + 57] - in[inPos + 56]) >>> 25 | (in[inPos + 58] - in[inPos + 57]) << 6 | (in[inPos + 59] - in[inPos + 58]) << 37;
    out[outPos + 29] = (in[inPos + 59] - in[inPos + 58]) >>> 27 | (in[inPos + 60] - in[inPos + 59]) << 4 | (in[inPos + 61] - in[inPos + 60]) << 35;
    out[outPos + 30] = (in[inPos + 61] - in[inPos + 60]) >>> 29 | (in[inPos + 62] - in[inPos + 61]) << 2 | (in[inPos + 63] - in[inPos + 62]) << 33;
  }

  private static void unpack31(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2147483647) + initValue;
    out[outPos + 1] = (in[inPos] >>> 31 & 2147483647) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 62 | (in[inPos + 1] & 536870911) << 2) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 29 & 2147483647) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 60 | (in[inPos + 2] & 134217727) << 4) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 27 & 2147483647) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 58 | (in[inPos + 3] & 33554431) << 6) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 25 & 2147483647) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 56 | (in[inPos + 4] & 8388607) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 23 & 2147483647) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 54 | (in[inPos + 5] & 2097151) << 10) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 5] >>> 21 & 2147483647) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 5] >>> 52 | (in[inPos + 6] & 524287) << 12) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 6] >>> 19 & 2147483647) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 6] >>> 50 | (in[inPos + 7] & 131071) << 14) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 7] >>> 17 & 2147483647) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 7] >>> 48 | (in[inPos + 8] & 32767) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 8] >>> 15 & 2147483647) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 8] >>> 46 | (in[inPos + 9] & 8191) << 18) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 9] >>> 13 & 2147483647) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 9] >>> 44 | (in[inPos + 10] & 2047) << 20) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 10] >>> 11 & 2147483647) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 10] >>> 42 | (in[inPos + 11] & 511) << 22) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 11] >>> 9 & 2147483647) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 11] >>> 40 | (in[inPos + 12] & 127) << 24) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 12] >>> 7 & 2147483647) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 12] >>> 38 | (in[inPos + 13] & 31) << 26) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 13] >>> 5 & 2147483647) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 13] >>> 36 | (in[inPos + 14] & 7) << 28) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 14] >>> 3 & 2147483647) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 14] >>> 34 | (in[inPos + 15] & 1) << 30) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 15] >>> 1 & 2147483647) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 15] >>> 32 & 2147483647) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 15] >>> 63 | (in[inPos + 16] & 1073741823) << 1) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 16] >>> 30 & 2147483647) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 16] >>> 61 | (in[inPos + 17] & 268435455) << 3) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 17] >>> 28 & 2147483647) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 17] >>> 59 | (in[inPos + 18] & 67108863) << 5) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 18] >>> 26 & 2147483647) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 18] >>> 57 | (in[inPos + 19] & 16777215) << 7) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 19] >>> 24 & 2147483647) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 19] >>> 55 | (in[inPos + 20] & 4194303) << 9) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 20] >>> 22 & 2147483647) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 20] >>> 53 | (in[inPos + 21] & 1048575) << 11) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 21] >>> 20 & 2147483647) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 21] >>> 51 | (in[inPos + 22] & 262143) << 13) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 22] >>> 18 & 2147483647) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 22] >>> 49 | (in[inPos + 23] & 65535) << 15) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 23] >>> 16 & 2147483647) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 23] >>> 47 | (in[inPos + 24] & 16383) << 17) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 24] >>> 14 & 2147483647) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 24] >>> 45 | (in[inPos + 25] & 4095) << 19) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 25] >>> 12 & 2147483647) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 25] >>> 43 | (in[inPos + 26] & 1023) << 21) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 26] >>> 10 & 2147483647) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 26] >>> 41 | (in[inPos + 27] & 255) << 23) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 27] >>> 8 & 2147483647) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 27] >>> 39 | (in[inPos + 28] & 63) << 25) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 28] >>> 6 & 2147483647) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 28] >>> 37 | (in[inPos + 29] & 15) << 27) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 29] >>> 4 & 2147483647) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 29] >>> 35 | (in[inPos + 30] & 3) << 29) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 30] >>> 2 & 2147483647) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 30] >>> 33) + out[outPos + 62];
  }

  private static void pack32(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 32;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) | (in[inPos + 3] - in[inPos + 2]) << 32;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) | (in[inPos + 5] - in[inPos + 4]) << 32;
    out[outPos + 3] = (in[inPos + 6] - in[inPos + 5]) | (in[inPos + 7] - in[inPos + 6]) << 32;
    out[outPos + 4] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 32;
    out[outPos + 5] = (in[inPos + 10] - in[inPos + 9]) | (in[inPos + 11] - in[inPos + 10]) << 32;
    out[outPos + 6] = (in[inPos + 12] - in[inPos + 11]) | (in[inPos + 13] - in[inPos + 12]) << 32;
    out[outPos + 7] = (in[inPos + 14] - in[inPos + 13]) | (in[inPos + 15] - in[inPos + 14]) << 32;
    out[outPos + 8] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 32;
    out[outPos + 9] = (in[inPos + 18] - in[inPos + 17]) | (in[inPos + 19] - in[inPos + 18]) << 32;
    out[outPos + 10] = (in[inPos + 20] - in[inPos + 19]) | (in[inPos + 21] - in[inPos + 20]) << 32;
    out[outPos + 11] = (in[inPos + 22] - in[inPos + 21]) | (in[inPos + 23] - in[inPos + 22]) << 32;
    out[outPos + 12] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 32;
    out[outPos + 13] = (in[inPos + 26] - in[inPos + 25]) | (in[inPos + 27] - in[inPos + 26]) << 32;
    out[outPos + 14] = (in[inPos + 28] - in[inPos + 27]) | (in[inPos + 29] - in[inPos + 28]) << 32;
    out[outPos + 15] = (in[inPos + 30] - in[inPos + 29]) | (in[inPos + 31] - in[inPos + 30]) << 32;
    out[outPos + 16] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 32;
    out[outPos + 17] = (in[inPos + 34] - in[inPos + 33]) | (in[inPos + 35] - in[inPos + 34]) << 32;
    out[outPos + 18] = (in[inPos + 36] - in[inPos + 35]) | (in[inPos + 37] - in[inPos + 36]) << 32;
    out[outPos + 19] = (in[inPos + 38] - in[inPos + 37]) | (in[inPos + 39] - in[inPos + 38]) << 32;
    out[outPos + 20] = (in[inPos + 40] - in[inPos + 39]) | (in[inPos + 41] - in[inPos + 40]) << 32;
    out[outPos + 21] = (in[inPos + 42] - in[inPos + 41]) | (in[inPos + 43] - in[inPos + 42]) << 32;
    out[outPos + 22] = (in[inPos + 44] - in[inPos + 43]) | (in[inPos + 45] - in[inPos + 44]) << 32;
    out[outPos + 23] = (in[inPos + 46] - in[inPos + 45]) | (in[inPos + 47] - in[inPos + 46]) << 32;
    out[outPos + 24] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 32;
    out[outPos + 25] = (in[inPos + 50] - in[inPos + 49]) | (in[inPos + 51] - in[inPos + 50]) << 32;
    out[outPos + 26] = (in[inPos + 52] - in[inPos + 51]) | (in[inPos + 53] - in[inPos + 52]) << 32;
    out[outPos + 27] = (in[inPos + 54] - in[inPos + 53]) | (in[inPos + 55] - in[inPos + 54]) << 32;
    out[outPos + 28] = (in[inPos + 56] - in[inPos + 55]) | (in[inPos + 57] - in[inPos + 56]) << 32;
    out[outPos + 29] = (in[inPos + 58] - in[inPos + 57]) | (in[inPos + 59] - in[inPos + 58]) << 32;
    out[outPos + 30] = (in[inPos + 60] - in[inPos + 59]) | (in[inPos + 61] - in[inPos + 60]) << 32;
    out[outPos + 31] = (in[inPos + 62] - in[inPos + 61]) | (in[inPos + 63] - in[inPos + 62]) << 32;
  }

  private static void unpack32(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 4294967295L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 32) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] & 4294967295L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 32) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] & 4294967295L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 32) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] & 4294967295L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 32) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] & 4294967295L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 32) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] & 4294967295L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 5] >>> 32) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] & 4294967295L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 6] >>> 32) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 7] & 4294967295L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 7] >>> 32) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 8] & 4294967295L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 8] >>> 32) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 9] & 4294967295L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 9] >>> 32) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 10] & 4294967295L) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 10] >>> 32) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 11] & 4294967295L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 11] >>> 32) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 12] & 4294967295L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 12] >>> 32) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 13] & 4294967295L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 13] >>> 32) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 14] & 4294967295L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 14] >>> 32) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 15] & 4294967295L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 15] >>> 32) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 16] & 4294967295L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 16] >>> 32) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 17] & 4294967295L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 17] >>> 32) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 18] & 4294967295L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 18] >>> 32) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 19] & 4294967295L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 19] >>> 32) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 20] & 4294967295L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 20] >>> 32) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 21] & 4294967295L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 21] >>> 32) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 22] & 4294967295L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 22] >>> 32) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 23] & 4294967295L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 23] >>> 32) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 24] & 4294967295L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 24] >>> 32) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 25] & 4294967295L) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 25] >>> 32) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 26] & 4294967295L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 26] >>> 32) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 27] & 4294967295L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 27] >>> 32) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 28] & 4294967295L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 28] >>> 32) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 29] & 4294967295L) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 29] >>> 32) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 30] & 4294967295L) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 30] >>> 32) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 31] & 4294967295L) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 31] >>> 32) + out[outPos + 62];
  }

  private static void pack33(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 33;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 31 | (in[inPos + 2] - in[inPos + 1]) << 2 | (in[inPos + 3] - in[inPos + 2]) << 35;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 29 | (in[inPos + 4] - in[inPos + 3]) << 4 | (in[inPos + 5] - in[inPos + 4]) << 37;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 27 | (in[inPos + 6] - in[inPos + 5]) << 6 | (in[inPos + 7] - in[inPos + 6]) << 39;
    out[outPos + 4] = (in[inPos + 7] - in[inPos + 6]) >>> 25 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 41;
    out[outPos + 5] = (in[inPos + 9] - in[inPos + 8]) >>> 23 | (in[inPos + 10] - in[inPos + 9]) << 10 | (in[inPos + 11] - in[inPos + 10]) << 43;
    out[outPos + 6] = (in[inPos + 11] - in[inPos + 10]) >>> 21 | (in[inPos + 12] - in[inPos + 11]) << 12 | (in[inPos + 13] - in[inPos + 12]) << 45;
    out[outPos + 7] = (in[inPos + 13] - in[inPos + 12]) >>> 19 | (in[inPos + 14] - in[inPos + 13]) << 14 | (in[inPos + 15] - in[inPos + 14]) << 47;
    out[outPos + 8] = (in[inPos + 15] - in[inPos + 14]) >>> 17 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 49;
    out[outPos + 9] = (in[inPos + 17] - in[inPos + 16]) >>> 15 | (in[inPos + 18] - in[inPos + 17]) << 18 | (in[inPos + 19] - in[inPos + 18]) << 51;
    out[outPos + 10] = (in[inPos + 19] - in[inPos + 18]) >>> 13 | (in[inPos + 20] - in[inPos + 19]) << 20 | (in[inPos + 21] - in[inPos + 20]) << 53;
    out[outPos + 11] = (in[inPos + 21] - in[inPos + 20]) >>> 11 | (in[inPos + 22] - in[inPos + 21]) << 22 | (in[inPos + 23] - in[inPos + 22]) << 55;
    out[outPos + 12] = (in[inPos + 23] - in[inPos + 22]) >>> 9 | (in[inPos + 24] - in[inPos + 23]) << 24 | (in[inPos + 25] - in[inPos + 24]) << 57;
    out[outPos + 13] = (in[inPos + 25] - in[inPos + 24]) >>> 7 | (in[inPos + 26] - in[inPos + 25]) << 26 | (in[inPos + 27] - in[inPos + 26]) << 59;
    out[outPos + 14] = (in[inPos + 27] - in[inPos + 26]) >>> 5 | (in[inPos + 28] - in[inPos + 27]) << 28 | (in[inPos + 29] - in[inPos + 28]) << 61;
    out[outPos + 15] = (in[inPos + 29] - in[inPos + 28]) >>> 3 | (in[inPos + 30] - in[inPos + 29]) << 30 | (in[inPos + 31] - in[inPos + 30]) << 63;
    out[outPos + 16] = (in[inPos + 31] - in[inPos + 30]) >>> 1 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 17] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 1 | (in[inPos + 34] - in[inPos + 33]) << 34;
    out[outPos + 18] = (in[inPos + 34] - in[inPos + 33]) >>> 30 | (in[inPos + 35] - in[inPos + 34]) << 3 | (in[inPos + 36] - in[inPos + 35]) << 36;
    out[outPos + 19] = (in[inPos + 36] - in[inPos + 35]) >>> 28 | (in[inPos + 37] - in[inPos + 36]) << 5 | (in[inPos + 38] - in[inPos + 37]) << 38;
    out[outPos + 20] = (in[inPos + 38] - in[inPos + 37]) >>> 26 | (in[inPos + 39] - in[inPos + 38]) << 7 | (in[inPos + 40] - in[inPos + 39]) << 40;
    out[outPos + 21] = (in[inPos + 40] - in[inPos + 39]) >>> 24 | (in[inPos + 41] - in[inPos + 40]) << 9 | (in[inPos + 42] - in[inPos + 41]) << 42;
    out[outPos + 22] = (in[inPos + 42] - in[inPos + 41]) >>> 22 | (in[inPos + 43] - in[inPos + 42]) << 11 | (in[inPos + 44] - in[inPos + 43]) << 44;
    out[outPos + 23] = (in[inPos + 44] - in[inPos + 43]) >>> 20 | (in[inPos + 45] - in[inPos + 44]) << 13 | (in[inPos + 46] - in[inPos + 45]) << 46;
    out[outPos + 24] = (in[inPos + 46] - in[inPos + 45]) >>> 18 | (in[inPos + 47] - in[inPos + 46]) << 15 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 25] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 17 | (in[inPos + 50] - in[inPos + 49]) << 50;
    out[outPos + 26] = (in[inPos + 50] - in[inPos + 49]) >>> 14 | (in[inPos + 51] - in[inPos + 50]) << 19 | (in[inPos + 52] - in[inPos + 51]) << 52;
    out[outPos + 27] = (in[inPos + 52] - in[inPos + 51]) >>> 12 | (in[inPos + 53] - in[inPos + 52]) << 21 | (in[inPos + 54] - in[inPos + 53]) << 54;
    out[outPos + 28] = (in[inPos + 54] - in[inPos + 53]) >>> 10 | (in[inPos + 55] - in[inPos + 54]) << 23 | (in[inPos + 56] - in[inPos + 55]) << 56;
    out[outPos + 29] = (in[inPos + 56] - in[inPos + 55]) >>> 8 | (in[inPos + 57] - in[inPos + 56]) << 25 | (in[inPos + 58] - in[inPos + 57]) << 58;
    out[outPos + 30] = (in[inPos + 58] - in[inPos + 57]) >>> 6 | (in[inPos + 59] - in[inPos + 58]) << 27 | (in[inPos + 60] - in[inPos + 59]) << 60;
    out[outPos + 31] = (in[inPos + 60] - in[inPos + 59]) >>> 4 | (in[inPos + 61] - in[inPos + 60]) << 29 | (in[inPos + 62] - in[inPos + 61]) << 62;
    out[outPos + 32] = (in[inPos + 62] - in[inPos + 61]) >>> 2 | (in[inPos + 63] - in[inPos + 62]) << 31;
  }

  private static void unpack33(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 8589934591L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 33 | (in[inPos + 1] & 3) << 31) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 2 & 8589934591L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 35 | (in[inPos + 2] & 15) << 29) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 4 & 8589934591L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 37 | (in[inPos + 3] & 63) << 27) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 6 & 8589934591L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 39 | (in[inPos + 4] & 255) << 25) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 8 & 8589934591L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 41 | (in[inPos + 5] & 1023) << 23) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 10 & 8589934591L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 5] >>> 43 | (in[inPos + 6] & 4095) << 21) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] >>> 12 & 8589934591L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 6] >>> 45 | (in[inPos + 7] & 16383) << 19) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 7] >>> 14 & 8589934591L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 7] >>> 47 | (in[inPos + 8] & 65535) << 17) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 8] >>> 16 & 8589934591L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 8] >>> 49 | (in[inPos + 9] & 262143) << 15) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 9] >>> 18 & 8589934591L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 9] >>> 51 | (in[inPos + 10] & 1048575) << 13) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 10] >>> 20 & 8589934591L) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 10] >>> 53 | (in[inPos + 11] & 4194303) << 11) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 11] >>> 22 & 8589934591L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 11] >>> 55 | (in[inPos + 12] & 16777215) << 9) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 12] >>> 24 & 8589934591L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 12] >>> 57 | (in[inPos + 13] & 67108863) << 7) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 13] >>> 26 & 8589934591L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 13] >>> 59 | (in[inPos + 14] & 268435455) << 5) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 14] >>> 28 & 8589934591L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 14] >>> 61 | (in[inPos + 15] & 1073741823) << 3) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 15] >>> 30 & 8589934591L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 15] >>> 63 | (in[inPos + 16] & 4294967295L) << 1) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 16] >>> 32 | (in[inPos + 17] & 1) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 17] >>> 1 & 8589934591L) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 17] >>> 34 | (in[inPos + 18] & 7) << 30) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 18] >>> 3 & 8589934591L) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 18] >>> 36 | (in[inPos + 19] & 31) << 28) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 19] >>> 5 & 8589934591L) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 19] >>> 38 | (in[inPos + 20] & 127) << 26) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 20] >>> 7 & 8589934591L) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 20] >>> 40 | (in[inPos + 21] & 511) << 24) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 21] >>> 9 & 8589934591L) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 21] >>> 42 | (in[inPos + 22] & 2047) << 22) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 22] >>> 11 & 8589934591L) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 22] >>> 44 | (in[inPos + 23] & 8191) << 20) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 23] >>> 13 & 8589934591L) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 23] >>> 46 | (in[inPos + 24] & 32767) << 18) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 24] >>> 15 & 8589934591L) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 24] >>> 48 | (in[inPos + 25] & 131071) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 25] >>> 17 & 8589934591L) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 25] >>> 50 | (in[inPos + 26] & 524287) << 14) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 26] >>> 19 & 8589934591L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 26] >>> 52 | (in[inPos + 27] & 2097151) << 12) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 27] >>> 21 & 8589934591L) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 27] >>> 54 | (in[inPos + 28] & 8388607) << 10) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 28] >>> 23 & 8589934591L) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 28] >>> 56 | (in[inPos + 29] & 33554431) << 8) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 29] >>> 25 & 8589934591L) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 29] >>> 58 | (in[inPos + 30] & 134217727) << 6) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 30] >>> 27 & 8589934591L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 30] >>> 60 | (in[inPos + 31] & 536870911) << 4) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 31] >>> 29 & 8589934591L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 31] >>> 62 | (in[inPos + 32] & 2147483647) << 2) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 32] >>> 31) + out[outPos + 62];
  }

  private static void pack34(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 34;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 30 | (in[inPos + 2] - in[inPos + 1]) << 4 | (in[inPos + 3] - in[inPos + 2]) << 38;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 26 | (in[inPos + 4] - in[inPos + 3]) << 8 | (in[inPos + 5] - in[inPos + 4]) << 42;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 22 | (in[inPos + 6] - in[inPos + 5]) << 12 | (in[inPos + 7] - in[inPos + 6]) << 46;
    out[outPos + 4] = (in[inPos + 7] - in[inPos + 6]) >>> 18 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 50;
    out[outPos + 5] = (in[inPos + 9] - in[inPos + 8]) >>> 14 | (in[inPos + 10] - in[inPos + 9]) << 20 | (in[inPos + 11] - in[inPos + 10]) << 54;
    out[outPos + 6] = (in[inPos + 11] - in[inPos + 10]) >>> 10 | (in[inPos + 12] - in[inPos + 11]) << 24 | (in[inPos + 13] - in[inPos + 12]) << 58;
    out[outPos + 7] = (in[inPos + 13] - in[inPos + 12]) >>> 6 | (in[inPos + 14] - in[inPos + 13]) << 28 | (in[inPos + 15] - in[inPos + 14]) << 62;
    out[outPos + 8] = (in[inPos + 15] - in[inPos + 14]) >>> 2 | (in[inPos + 16] - in[inPos + 15]) << 32;
    out[outPos + 9] = (in[inPos + 16] - in[inPos + 15]) >>> 32 | (in[inPos + 17] - in[inPos + 16]) << 2 | (in[inPos + 18] - in[inPos + 17]) << 36;
    out[outPos + 10] = (in[inPos + 18] - in[inPos + 17]) >>> 28 | (in[inPos + 19] - in[inPos + 18]) << 6 | (in[inPos + 20] - in[inPos + 19]) << 40;
    out[outPos + 11] = (in[inPos + 20] - in[inPos + 19]) >>> 24 | (in[inPos + 21] - in[inPos + 20]) << 10 | (in[inPos + 22] - in[inPos + 21]) << 44;
    out[outPos + 12] = (in[inPos + 22] - in[inPos + 21]) >>> 20 | (in[inPos + 23] - in[inPos + 22]) << 14 | (in[inPos + 24] - in[inPos + 23]) << 48;
    out[outPos + 13] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 18 | (in[inPos + 26] - in[inPos + 25]) << 52;
    out[outPos + 14] = (in[inPos + 26] - in[inPos + 25]) >>> 12 | (in[inPos + 27] - in[inPos + 26]) << 22 | (in[inPos + 28] - in[inPos + 27]) << 56;
    out[outPos + 15] = (in[inPos + 28] - in[inPos + 27]) >>> 8 | (in[inPos + 29] - in[inPos + 28]) << 26 | (in[inPos + 30] - in[inPos + 29]) << 60;
    out[outPos + 16] = (in[inPos + 30] - in[inPos + 29]) >>> 4 | (in[inPos + 31] - in[inPos + 30]) << 30;
    out[outPos + 17] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 34;
    out[outPos + 18] = (in[inPos + 33] - in[inPos + 32]) >>> 30 | (in[inPos + 34] - in[inPos + 33]) << 4 | (in[inPos + 35] - in[inPos + 34]) << 38;
    out[outPos + 19] = (in[inPos + 35] - in[inPos + 34]) >>> 26 | (in[inPos + 36] - in[inPos + 35]) << 8 | (in[inPos + 37] - in[inPos + 36]) << 42;
    out[outPos + 20] = (in[inPos + 37] - in[inPos + 36]) >>> 22 | (in[inPos + 38] - in[inPos + 37]) << 12 | (in[inPos + 39] - in[inPos + 38]) << 46;
    out[outPos + 21] = (in[inPos + 39] - in[inPos + 38]) >>> 18 | (in[inPos + 40] - in[inPos + 39]) << 16 | (in[inPos + 41] - in[inPos + 40]) << 50;
    out[outPos + 22] = (in[inPos + 41] - in[inPos + 40]) >>> 14 | (in[inPos + 42] - in[inPos + 41]) << 20 | (in[inPos + 43] - in[inPos + 42]) << 54;
    out[outPos + 23] = (in[inPos + 43] - in[inPos + 42]) >>> 10 | (in[inPos + 44] - in[inPos + 43]) << 24 | (in[inPos + 45] - in[inPos + 44]) << 58;
    out[outPos + 24] = (in[inPos + 45] - in[inPos + 44]) >>> 6 | (in[inPos + 46] - in[inPos + 45]) << 28 | (in[inPos + 47] - in[inPos + 46]) << 62;
    out[outPos + 25] = (in[inPos + 47] - in[inPos + 46]) >>> 2 | (in[inPos + 48] - in[inPos + 47]) << 32;
    out[outPos + 26] = (in[inPos + 48] - in[inPos + 47]) >>> 32 | (in[inPos + 49] - in[inPos + 48]) << 2 | (in[inPos + 50] - in[inPos + 49]) << 36;
    out[outPos + 27] = (in[inPos + 50] - in[inPos + 49]) >>> 28 | (in[inPos + 51] - in[inPos + 50]) << 6 | (in[inPos + 52] - in[inPos + 51]) << 40;
    out[outPos + 28] = (in[inPos + 52] - in[inPos + 51]) >>> 24 | (in[inPos + 53] - in[inPos + 52]) << 10 | (in[inPos + 54] - in[inPos + 53]) << 44;
    out[outPos + 29] = (in[inPos + 54] - in[inPos + 53]) >>> 20 | (in[inPos + 55] - in[inPos + 54]) << 14 | (in[inPos + 56] - in[inPos + 55]) << 48;
    out[outPos + 30] = (in[inPos + 56] - in[inPos + 55]) >>> 16 | (in[inPos + 57] - in[inPos + 56]) << 18 | (in[inPos + 58] - in[inPos + 57]) << 52;
    out[outPos + 31] = (in[inPos + 58] - in[inPos + 57]) >>> 12 | (in[inPos + 59] - in[inPos + 58]) << 22 | (in[inPos + 60] - in[inPos + 59]) << 56;
    out[outPos + 32] = (in[inPos + 60] - in[inPos + 59]) >>> 8 | (in[inPos + 61] - in[inPos + 60]) << 26 | (in[inPos + 62] - in[inPos + 61]) << 60;
    out[outPos + 33] = (in[inPos + 62] - in[inPos + 61]) >>> 4 | (in[inPos + 63] - in[inPos + 62]) << 30;
  }

  private static void unpack34(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 17179869183L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 34 | (in[inPos + 1] & 15) << 30) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 4 & 17179869183L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 38 | (in[inPos + 2] & 255) << 26) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 8 & 17179869183L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 42 | (in[inPos + 3] & 4095) << 22) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 12 & 17179869183L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 46 | (in[inPos + 4] & 65535) << 18) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 16 & 17179869183L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 50 | (in[inPos + 5] & 1048575) << 14) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 20 & 17179869183L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 5] >>> 54 | (in[inPos + 6] & 16777215) << 10) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] >>> 24 & 17179869183L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 6] >>> 58 | (in[inPos + 7] & 268435455) << 6) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 7] >>> 28 & 17179869183L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 7] >>> 62 | (in[inPos + 8] & 4294967295L) << 2) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 8] >>> 32 | (in[inPos + 9] & 3) << 32) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 9] >>> 2 & 17179869183L) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 9] >>> 36 | (in[inPos + 10] & 63) << 28) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 10] >>> 6 & 17179869183L) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 10] >>> 40 | (in[inPos + 11] & 1023) << 24) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 11] >>> 10 & 17179869183L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 11] >>> 44 | (in[inPos + 12] & 16383) << 20) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 12] >>> 14 & 17179869183L) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 12] >>> 48 | (in[inPos + 13] & 262143) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 13] >>> 18 & 17179869183L) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 13] >>> 52 | (in[inPos + 14] & 4194303) << 12) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 14] >>> 22 & 17179869183L) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 14] >>> 56 | (in[inPos + 15] & 67108863) << 8) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 15] >>> 26 & 17179869183L) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 15] >>> 60 | (in[inPos + 16] & 1073741823) << 4) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 16] >>> 30) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 17] & 17179869183L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 17] >>> 34 | (in[inPos + 18] & 15) << 30) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 18] >>> 4 & 17179869183L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 18] >>> 38 | (in[inPos + 19] & 255) << 26) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 19] >>> 8 & 17179869183L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 19] >>> 42 | (in[inPos + 20] & 4095) << 22) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 20] >>> 12 & 17179869183L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 20] >>> 46 | (in[inPos + 21] & 65535) << 18) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 21] >>> 16 & 17179869183L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 21] >>> 50 | (in[inPos + 22] & 1048575) << 14) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 22] >>> 20 & 17179869183L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 22] >>> 54 | (in[inPos + 23] & 16777215) << 10) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 23] >>> 24 & 17179869183L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 23] >>> 58 | (in[inPos + 24] & 268435455) << 6) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 24] >>> 28 & 17179869183L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 24] >>> 62 | (in[inPos + 25] & 4294967295L) << 2) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 25] >>> 32 | (in[inPos + 26] & 3) << 32) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 26] >>> 2 & 17179869183L) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 26] >>> 36 | (in[inPos + 27] & 63) << 28) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 27] >>> 6 & 17179869183L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 27] >>> 40 | (in[inPos + 28] & 1023) << 24) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 28] >>> 10 & 17179869183L) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 28] >>> 44 | (in[inPos + 29] & 16383) << 20) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 29] >>> 14 & 17179869183L) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 29] >>> 48 | (in[inPos + 30] & 262143) << 16) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 30] >>> 18 & 17179869183L) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 30] >>> 52 | (in[inPos + 31] & 4194303) << 12) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 31] >>> 22 & 17179869183L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 31] >>> 56 | (in[inPos + 32] & 67108863) << 8) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 32] >>> 26 & 17179869183L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 32] >>> 60 | (in[inPos + 33] & 1073741823) << 4) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 33] >>> 30) + out[outPos + 62];
  }

  private static void pack35(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 35;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 29 | (in[inPos + 2] - in[inPos + 1]) << 6 | (in[inPos + 3] - in[inPos + 2]) << 41;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 23 | (in[inPos + 4] - in[inPos + 3]) << 12 | (in[inPos + 5] - in[inPos + 4]) << 47;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 17 | (in[inPos + 6] - in[inPos + 5]) << 18 | (in[inPos + 7] - in[inPos + 6]) << 53;
    out[outPos + 4] = (in[inPos + 7] - in[inPos + 6]) >>> 11 | (in[inPos + 8] - in[inPos + 7]) << 24 | (in[inPos + 9] - in[inPos + 8]) << 59;
    out[outPos + 5] = (in[inPos + 9] - in[inPos + 8]) >>> 5 | (in[inPos + 10] - in[inPos + 9]) << 30;
    out[outPos + 6] = (in[inPos + 10] - in[inPos + 9]) >>> 34 | (in[inPos + 11] - in[inPos + 10]) << 1 | (in[inPos + 12] - in[inPos + 11]) << 36;
    out[outPos + 7] = (in[inPos + 12] - in[inPos + 11]) >>> 28 | (in[inPos + 13] - in[inPos + 12]) << 7 | (in[inPos + 14] - in[inPos + 13]) << 42;
    out[outPos + 8] = (in[inPos + 14] - in[inPos + 13]) >>> 22 | (in[inPos + 15] - in[inPos + 14]) << 13 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 9] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 19 | (in[inPos + 18] - in[inPos + 17]) << 54;
    out[outPos + 10] = (in[inPos + 18] - in[inPos + 17]) >>> 10 | (in[inPos + 19] - in[inPos + 18]) << 25 | (in[inPos + 20] - in[inPos + 19]) << 60;
    out[outPos + 11] = (in[inPos + 20] - in[inPos + 19]) >>> 4 | (in[inPos + 21] - in[inPos + 20]) << 31;
    out[outPos + 12] = (in[inPos + 21] - in[inPos + 20]) >>> 33 | (in[inPos + 22] - in[inPos + 21]) << 2 | (in[inPos + 23] - in[inPos + 22]) << 37;
    out[outPos + 13] = (in[inPos + 23] - in[inPos + 22]) >>> 27 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 43;
    out[outPos + 14] = (in[inPos + 25] - in[inPos + 24]) >>> 21 | (in[inPos + 26] - in[inPos + 25]) << 14 | (in[inPos + 27] - in[inPos + 26]) << 49;
    out[outPos + 15] = (in[inPos + 27] - in[inPos + 26]) >>> 15 | (in[inPos + 28] - in[inPos + 27]) << 20 | (in[inPos + 29] - in[inPos + 28]) << 55;
    out[outPos + 16] = (in[inPos + 29] - in[inPos + 28]) >>> 9 | (in[inPos + 30] - in[inPos + 29]) << 26 | (in[inPos + 31] - in[inPos + 30]) << 61;
    out[outPos + 17] = (in[inPos + 31] - in[inPos + 30]) >>> 3 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 18] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 3 | (in[inPos + 34] - in[inPos + 33]) << 38;
    out[outPos + 19] = (in[inPos + 34] - in[inPos + 33]) >>> 26 | (in[inPos + 35] - in[inPos + 34]) << 9 | (in[inPos + 36] - in[inPos + 35]) << 44;
    out[outPos + 20] = (in[inPos + 36] - in[inPos + 35]) >>> 20 | (in[inPos + 37] - in[inPos + 36]) << 15 | (in[inPos + 38] - in[inPos + 37]) << 50;
    out[outPos + 21] = (in[inPos + 38] - in[inPos + 37]) >>> 14 | (in[inPos + 39] - in[inPos + 38]) << 21 | (in[inPos + 40] - in[inPos + 39]) << 56;
    out[outPos + 22] = (in[inPos + 40] - in[inPos + 39]) >>> 8 | (in[inPos + 41] - in[inPos + 40]) << 27 | (in[inPos + 42] - in[inPos + 41]) << 62;
    out[outPos + 23] = (in[inPos + 42] - in[inPos + 41]) >>> 2 | (in[inPos + 43] - in[inPos + 42]) << 33;
    out[outPos + 24] = (in[inPos + 43] - in[inPos + 42]) >>> 31 | (in[inPos + 44] - in[inPos + 43]) << 4 | (in[inPos + 45] - in[inPos + 44]) << 39;
    out[outPos + 25] = (in[inPos + 45] - in[inPos + 44]) >>> 25 | (in[inPos + 46] - in[inPos + 45]) << 10 | (in[inPos + 47] - in[inPos + 46]) << 45;
    out[outPos + 26] = (in[inPos + 47] - in[inPos + 46]) >>> 19 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 51;
    out[outPos + 27] = (in[inPos + 49] - in[inPos + 48]) >>> 13 | (in[inPos + 50] - in[inPos + 49]) << 22 | (in[inPos + 51] - in[inPos + 50]) << 57;
    out[outPos + 28] = (in[inPos + 51] - in[inPos + 50]) >>> 7 | (in[inPos + 52] - in[inPos + 51]) << 28 | (in[inPos + 53] - in[inPos + 52]) << 63;
    out[outPos + 29] = (in[inPos + 53] - in[inPos + 52]) >>> 1 | (in[inPos + 54] - in[inPos + 53]) << 34;
    out[outPos + 30] = (in[inPos + 54] - in[inPos + 53]) >>> 30 | (in[inPos + 55] - in[inPos + 54]) << 5 | (in[inPos + 56] - in[inPos + 55]) << 40;
    out[outPos + 31] = (in[inPos + 56] - in[inPos + 55]) >>> 24 | (in[inPos + 57] - in[inPos + 56]) << 11 | (in[inPos + 58] - in[inPos + 57]) << 46;
    out[outPos + 32] = (in[inPos + 58] - in[inPos + 57]) >>> 18 | (in[inPos + 59] - in[inPos + 58]) << 17 | (in[inPos + 60] - in[inPos + 59]) << 52;
    out[outPos + 33] = (in[inPos + 60] - in[inPos + 59]) >>> 12 | (in[inPos + 61] - in[inPos + 60]) << 23 | (in[inPos + 62] - in[inPos + 61]) << 58;
    out[outPos + 34] = (in[inPos + 62] - in[inPos + 61]) >>> 6 | (in[inPos + 63] - in[inPos + 62]) << 29;
  }

  private static void unpack35(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 34359738367L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 35 | (in[inPos + 1] & 63) << 29) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 6 & 34359738367L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 41 | (in[inPos + 2] & 4095) << 23) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 12 & 34359738367L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 47 | (in[inPos + 3] & 262143) << 17) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 18 & 34359738367L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 53 | (in[inPos + 4] & 16777215) << 11) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 24 & 34359738367L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 59 | (in[inPos + 5] & 1073741823) << 5) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 30 | (in[inPos + 6] & 1) << 34) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 1 & 34359738367L) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] >>> 36 | (in[inPos + 7] & 127) << 28) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 7] >>> 7 & 34359738367L) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 7] >>> 42 | (in[inPos + 8] & 8191) << 22) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 8] >>> 13 & 34359738367L) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 8] >>> 48 | (in[inPos + 9] & 524287) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 9] >>> 19 & 34359738367L) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 33554431) << 10) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 10] >>> 25 & 34359738367L) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 10] >>> 60 | (in[inPos + 11] & 2147483647) << 4) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 11] >>> 31 | (in[inPos + 12] & 3) << 33) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 12] >>> 2 & 34359738367L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 12] >>> 37 | (in[inPos + 13] & 255) << 27) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 13] >>> 8 & 34359738367L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 13] >>> 43 | (in[inPos + 14] & 16383) << 21) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 14] >>> 14 & 34359738367L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 14] >>> 49 | (in[inPos + 15] & 1048575) << 15) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 15] >>> 20 & 34359738367L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 15] >>> 55 | (in[inPos + 16] & 67108863) << 9) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 16] >>> 26 & 34359738367L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 16] >>> 61 | (in[inPos + 17] & 4294967295L) << 3) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 17] >>> 32 | (in[inPos + 18] & 7) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 18] >>> 3 & 34359738367L) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 18] >>> 38 | (in[inPos + 19] & 511) << 26) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 19] >>> 9 & 34359738367L) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 32767) << 20) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 20] >>> 15 & 34359738367L) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 20] >>> 50 | (in[inPos + 21] & 2097151) << 14) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 21] >>> 21 & 34359738367L) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 21] >>> 56 | (in[inPos + 22] & 134217727) << 8) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 22] >>> 27 & 34359738367L) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 22] >>> 62 | (in[inPos + 23] & 8589934591L) << 2) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 23] >>> 33 | (in[inPos + 24] & 15) << 31) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 24] >>> 4 & 34359738367L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 24] >>> 39 | (in[inPos + 25] & 1023) << 25) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 25] >>> 10 & 34359738367L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 25] >>> 45 | (in[inPos + 26] & 65535) << 19) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 26] >>> 16 & 34359738367L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 26] >>> 51 | (in[inPos + 27] & 4194303) << 13) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 27] >>> 22 & 34359738367L) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 27] >>> 57 | (in[inPos + 28] & 268435455) << 7) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 28] >>> 28 & 34359738367L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 28] >>> 63 | (in[inPos + 29] & 17179869183L) << 1) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 29] >>> 34 | (in[inPos + 30] & 31) << 30) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 30] >>> 5 & 34359738367L) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 30] >>> 40 | (in[inPos + 31] & 2047) << 24) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 31] >>> 11 & 34359738367L) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 31] >>> 46 | (in[inPos + 32] & 131071) << 18) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 32] >>> 17 & 34359738367L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 32] >>> 52 | (in[inPos + 33] & 8388607) << 12) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 33] >>> 23 & 34359738367L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 33] >>> 58 | (in[inPos + 34] & 536870911) << 6) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 34] >>> 29) + out[outPos + 62];
  }

  private static void pack36(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 36;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 28 | (in[inPos + 2] - in[inPos + 1]) << 8 | (in[inPos + 3] - in[inPos + 2]) << 44;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 20 | (in[inPos + 4] - in[inPos + 3]) << 16 | (in[inPos + 5] - in[inPos + 4]) << 52;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 12 | (in[inPos + 6] - in[inPos + 5]) << 24 | (in[inPos + 7] - in[inPos + 6]) << 60;
    out[outPos + 4] = (in[inPos + 7] - in[inPos + 6]) >>> 4 | (in[inPos + 8] - in[inPos + 7]) << 32;
    out[outPos + 5] = (in[inPos + 8] - in[inPos + 7]) >>> 32 | (in[inPos + 9] - in[inPos + 8]) << 4 | (in[inPos + 10] - in[inPos + 9]) << 40;
    out[outPos + 6] = (in[inPos + 10] - in[inPos + 9]) >>> 24 | (in[inPos + 11] - in[inPos + 10]) << 12 | (in[inPos + 12] - in[inPos + 11]) << 48;
    out[outPos + 7] = (in[inPos + 12] - in[inPos + 11]) >>> 16 | (in[inPos + 13] - in[inPos + 12]) << 20 | (in[inPos + 14] - in[inPos + 13]) << 56;
    out[outPos + 8] = (in[inPos + 14] - in[inPos + 13]) >>> 8 | (in[inPos + 15] - in[inPos + 14]) << 28;
    out[outPos + 9] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 36;
    out[outPos + 10] = (in[inPos + 17] - in[inPos + 16]) >>> 28 | (in[inPos + 18] - in[inPos + 17]) << 8 | (in[inPos + 19] - in[inPos + 18]) << 44;
    out[outPos + 11] = (in[inPos + 19] - in[inPos + 18]) >>> 20 | (in[inPos + 20] - in[inPos + 19]) << 16 | (in[inPos + 21] - in[inPos + 20]) << 52;
    out[outPos + 12] = (in[inPos + 21] - in[inPos + 20]) >>> 12 | (in[inPos + 22] - in[inPos + 21]) << 24 | (in[inPos + 23] - in[inPos + 22]) << 60;
    out[outPos + 13] = (in[inPos + 23] - in[inPos + 22]) >>> 4 | (in[inPos + 24] - in[inPos + 23]) << 32;
    out[outPos + 14] = (in[inPos + 24] - in[inPos + 23]) >>> 32 | (in[inPos + 25] - in[inPos + 24]) << 4 | (in[inPos + 26] - in[inPos + 25]) << 40;
    out[outPos + 15] = (in[inPos + 26] - in[inPos + 25]) >>> 24 | (in[inPos + 27] - in[inPos + 26]) << 12 | (in[inPos + 28] - in[inPos + 27]) << 48;
    out[outPos + 16] = (in[inPos + 28] - in[inPos + 27]) >>> 16 | (in[inPos + 29] - in[inPos + 28]) << 20 | (in[inPos + 30] - in[inPos + 29]) << 56;
    out[outPos + 17] = (in[inPos + 30] - in[inPos + 29]) >>> 8 | (in[inPos + 31] - in[inPos + 30]) << 28;
    out[outPos + 18] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 36;
    out[outPos + 19] = (in[inPos + 33] - in[inPos + 32]) >>> 28 | (in[inPos + 34] - in[inPos + 33]) << 8 | (in[inPos + 35] - in[inPos + 34]) << 44;
    out[outPos + 20] = (in[inPos + 35] - in[inPos + 34]) >>> 20 | (in[inPos + 36] - in[inPos + 35]) << 16 | (in[inPos + 37] - in[inPos + 36]) << 52;
    out[outPos + 21] = (in[inPos + 37] - in[inPos + 36]) >>> 12 | (in[inPos + 38] - in[inPos + 37]) << 24 | (in[inPos + 39] - in[inPos + 38]) << 60;
    out[outPos + 22] = (in[inPos + 39] - in[inPos + 38]) >>> 4 | (in[inPos + 40] - in[inPos + 39]) << 32;
    out[outPos + 23] = (in[inPos + 40] - in[inPos + 39]) >>> 32 | (in[inPos + 41] - in[inPos + 40]) << 4 | (in[inPos + 42] - in[inPos + 41]) << 40;
    out[outPos + 24] = (in[inPos + 42] - in[inPos + 41]) >>> 24 | (in[inPos + 43] - in[inPos + 42]) << 12 | (in[inPos + 44] - in[inPos + 43]) << 48;
    out[outPos + 25] = (in[inPos + 44] - in[inPos + 43]) >>> 16 | (in[inPos + 45] - in[inPos + 44]) << 20 | (in[inPos + 46] - in[inPos + 45]) << 56;
    out[outPos + 26] = (in[inPos + 46] - in[inPos + 45]) >>> 8 | (in[inPos + 47] - in[inPos + 46]) << 28;
    out[outPos + 27] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 36;
    out[outPos + 28] = (in[inPos + 49] - in[inPos + 48]) >>> 28 | (in[inPos + 50] - in[inPos + 49]) << 8 | (in[inPos + 51] - in[inPos + 50]) << 44;
    out[outPos + 29] = (in[inPos + 51] - in[inPos + 50]) >>> 20 | (in[inPos + 52] - in[inPos + 51]) << 16 | (in[inPos + 53] - in[inPos + 52]) << 52;
    out[outPos + 30] = (in[inPos + 53] - in[inPos + 52]) >>> 12 | (in[inPos + 54] - in[inPos + 53]) << 24 | (in[inPos + 55] - in[inPos + 54]) << 60;
    out[outPos + 31] = (in[inPos + 55] - in[inPos + 54]) >>> 4 | (in[inPos + 56] - in[inPos + 55]) << 32;
    out[outPos + 32] = (in[inPos + 56] - in[inPos + 55]) >>> 32 | (in[inPos + 57] - in[inPos + 56]) << 4 | (in[inPos + 58] - in[inPos + 57]) << 40;
    out[outPos + 33] = (in[inPos + 58] - in[inPos + 57]) >>> 24 | (in[inPos + 59] - in[inPos + 58]) << 12 | (in[inPos + 60] - in[inPos + 59]) << 48;
    out[outPos + 34] = (in[inPos + 60] - in[inPos + 59]) >>> 16 | (in[inPos + 61] - in[inPos + 60]) << 20 | (in[inPos + 62] - in[inPos + 61]) << 56;
    out[outPos + 35] = (in[inPos + 62] - in[inPos + 61]) >>> 8 | (in[inPos + 63] - in[inPos + 62]) << 28;
  }

  private static void unpack36(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 68719476735L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 36 | (in[inPos + 1] & 255) << 28) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 8 & 68719476735L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 44 | (in[inPos + 2] & 65535) << 20) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 16 & 68719476735L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 52 | (in[inPos + 3] & 16777215) << 12) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 24 & 68719476735L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 4294967295L) << 4) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 32 | (in[inPos + 5] & 15) << 32) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 4 & 68719476735L) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 40 | (in[inPos + 6] & 4095) << 24) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 12 & 68719476735L) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] >>> 48 | (in[inPos + 7] & 1048575) << 16) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 7] >>> 20 & 68719476735L) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 268435455) << 8) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 8] >>> 28) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 9] & 68719476735L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 9] >>> 36 | (in[inPos + 10] & 255) << 28) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 10] >>> 8 & 68719476735L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 10] >>> 44 | (in[inPos + 11] & 65535) << 20) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 11] >>> 16 & 68719476735L) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 16777215) << 12) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 12] >>> 24 & 68719476735L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 12] >>> 60 | (in[inPos + 13] & 4294967295L) << 4) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 13] >>> 32 | (in[inPos + 14] & 15) << 32) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 14] >>> 4 & 68719476735L) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 14] >>> 40 | (in[inPos + 15] & 4095) << 24) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 15] >>> 12 & 68719476735L) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 1048575) << 16) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 16] >>> 20 & 68719476735L) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 268435455) << 8) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 17] >>> 28) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 18] & 68719476735L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 18] >>> 36 | (in[inPos + 19] & 255) << 28) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 19] >>> 8 & 68719476735L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 65535) << 20) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 20] >>> 16 & 68719476735L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 20] >>> 52 | (in[inPos + 21] & 16777215) << 12) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 21] >>> 24 & 68719476735L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 21] >>> 60 | (in[inPos + 22] & 4294967295L) << 4) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 22] >>> 32 | (in[inPos + 23] & 15) << 32) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 23] >>> 4 & 68719476735L) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 23] >>> 40 | (in[inPos + 24] & 4095) << 24) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 24] >>> 12 & 68719476735L) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 24] >>> 48 | (in[inPos + 25] & 1048575) << 16) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 25] >>> 20 & 68719476735L) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 25] >>> 56 | (in[inPos + 26] & 268435455) << 8) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 26] >>> 28) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 27] & 68719476735L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 27] >>> 36 | (in[inPos + 28] & 255) << 28) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 28] >>> 8 & 68719476735L) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 28] >>> 44 | (in[inPos + 29] & 65535) << 20) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 29] >>> 16 & 68719476735L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 29] >>> 52 | (in[inPos + 30] & 16777215) << 12) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 30] >>> 24 & 68719476735L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 30] >>> 60 | (in[inPos + 31] & 4294967295L) << 4) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 31] >>> 32 | (in[inPos + 32] & 15) << 32) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 32] >>> 4 & 68719476735L) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 32] >>> 40 | (in[inPos + 33] & 4095) << 24) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 33] >>> 12 & 68719476735L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 33] >>> 48 | (in[inPos + 34] & 1048575) << 16) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 34] >>> 20 & 68719476735L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 34] >>> 56 | (in[inPos + 35] & 268435455) << 8) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 35] >>> 28) + out[outPos + 62];
  }

  private static void pack37(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 37;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 27 | (in[inPos + 2] - in[inPos + 1]) << 10 | (in[inPos + 3] - in[inPos + 2]) << 47;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 17 | (in[inPos + 4] - in[inPos + 3]) << 20 | (in[inPos + 5] - in[inPos + 4]) << 57;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 7 | (in[inPos + 6] - in[inPos + 5]) << 30;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 34 | (in[inPos + 7] - in[inPos + 6]) << 3 | (in[inPos + 8] - in[inPos + 7]) << 40;
    out[outPos + 5] = (in[inPos + 8] - in[inPos + 7]) >>> 24 | (in[inPos + 9] - in[inPos + 8]) << 13 | (in[inPos + 10] - in[inPos + 9]) << 50;
    out[outPos + 6] = (in[inPos + 10] - in[inPos + 9]) >>> 14 | (in[inPos + 11] - in[inPos + 10]) << 23 | (in[inPos + 12] - in[inPos + 11]) << 60;
    out[outPos + 7] = (in[inPos + 12] - in[inPos + 11]) >>> 4 | (in[inPos + 13] - in[inPos + 12]) << 33;
    out[outPos + 8] = (in[inPos + 13] - in[inPos + 12]) >>> 31 | (in[inPos + 14] - in[inPos + 13]) << 6 | (in[inPos + 15] - in[inPos + 14]) << 43;
    out[outPos + 9] = (in[inPos + 15] - in[inPos + 14]) >>> 21 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 53;
    out[outPos + 10] = (in[inPos + 17] - in[inPos + 16]) >>> 11 | (in[inPos + 18] - in[inPos + 17]) << 26 | (in[inPos + 19] - in[inPos + 18]) << 63;
    out[outPos + 11] = (in[inPos + 19] - in[inPos + 18]) >>> 1 | (in[inPos + 20] - in[inPos + 19]) << 36;
    out[outPos + 12] = (in[inPos + 20] - in[inPos + 19]) >>> 28 | (in[inPos + 21] - in[inPos + 20]) << 9 | (in[inPos + 22] - in[inPos + 21]) << 46;
    out[outPos + 13] = (in[inPos + 22] - in[inPos + 21]) >>> 18 | (in[inPos + 23] - in[inPos + 22]) << 19 | (in[inPos + 24] - in[inPos + 23]) << 56;
    out[outPos + 14] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 29;
    out[outPos + 15] = (in[inPos + 25] - in[inPos + 24]) >>> 35 | (in[inPos + 26] - in[inPos + 25]) << 2 | (in[inPos + 27] - in[inPos + 26]) << 39;
    out[outPos + 16] = (in[inPos + 27] - in[inPos + 26]) >>> 25 | (in[inPos + 28] - in[inPos + 27]) << 12 | (in[inPos + 29] - in[inPos + 28]) << 49;
    out[outPos + 17] = (in[inPos + 29] - in[inPos + 28]) >>> 15 | (in[inPos + 30] - in[inPos + 29]) << 22 | (in[inPos + 31] - in[inPos + 30]) << 59;
    out[outPos + 18] = (in[inPos + 31] - in[inPos + 30]) >>> 5 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 19] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 5 | (in[inPos + 34] - in[inPos + 33]) << 42;
    out[outPos + 20] = (in[inPos + 34] - in[inPos + 33]) >>> 22 | (in[inPos + 35] - in[inPos + 34]) << 15 | (in[inPos + 36] - in[inPos + 35]) << 52;
    out[outPos + 21] = (in[inPos + 36] - in[inPos + 35]) >>> 12 | (in[inPos + 37] - in[inPos + 36]) << 25 | (in[inPos + 38] - in[inPos + 37]) << 62;
    out[outPos + 22] = (in[inPos + 38] - in[inPos + 37]) >>> 2 | (in[inPos + 39] - in[inPos + 38]) << 35;
    out[outPos + 23] = (in[inPos + 39] - in[inPos + 38]) >>> 29 | (in[inPos + 40] - in[inPos + 39]) << 8 | (in[inPos + 41] - in[inPos + 40]) << 45;
    out[outPos + 24] = (in[inPos + 41] - in[inPos + 40]) >>> 19 | (in[inPos + 42] - in[inPos + 41]) << 18 | (in[inPos + 43] - in[inPos + 42]) << 55;
    out[outPos + 25] = (in[inPos + 43] - in[inPos + 42]) >>> 9 | (in[inPos + 44] - in[inPos + 43]) << 28;
    out[outPos + 26] = (in[inPos + 44] - in[inPos + 43]) >>> 36 | (in[inPos + 45] - in[inPos + 44]) << 1 | (in[inPos + 46] - in[inPos + 45]) << 38;
    out[outPos + 27] = (in[inPos + 46] - in[inPos + 45]) >>> 26 | (in[inPos + 47] - in[inPos + 46]) << 11 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 28] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 21 | (in[inPos + 50] - in[inPos + 49]) << 58;
    out[outPos + 29] = (in[inPos + 50] - in[inPos + 49]) >>> 6 | (in[inPos + 51] - in[inPos + 50]) << 31;
    out[outPos + 30] = (in[inPos + 51] - in[inPos + 50]) >>> 33 | (in[inPos + 52] - in[inPos + 51]) << 4 | (in[inPos + 53] - in[inPos + 52]) << 41;
    out[outPos + 31] = (in[inPos + 53] - in[inPos + 52]) >>> 23 | (in[inPos + 54] - in[inPos + 53]) << 14 | (in[inPos + 55] - in[inPos + 54]) << 51;
    out[outPos + 32] = (in[inPos + 55] - in[inPos + 54]) >>> 13 | (in[inPos + 56] - in[inPos + 55]) << 24 | (in[inPos + 57] - in[inPos + 56]) << 61;
    out[outPos + 33] = (in[inPos + 57] - in[inPos + 56]) >>> 3 | (in[inPos + 58] - in[inPos + 57]) << 34;
    out[outPos + 34] = (in[inPos + 58] - in[inPos + 57]) >>> 30 | (in[inPos + 59] - in[inPos + 58]) << 7 | (in[inPos + 60] - in[inPos + 59]) << 44;
    out[outPos + 35] = (in[inPos + 60] - in[inPos + 59]) >>> 20 | (in[inPos + 61] - in[inPos + 60]) << 17 | (in[inPos + 62] - in[inPos + 61]) << 54;
    out[outPos + 36] = (in[inPos + 62] - in[inPos + 61]) >>> 10 | (in[inPos + 63] - in[inPos + 62]) << 27;
  }

  private static void unpack37(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 137438953471L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 37 | (in[inPos + 1] & 1023) << 27) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 10 & 137438953471L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 47 | (in[inPos + 2] & 1048575) << 17) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 20 & 137438953471L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 57 | (in[inPos + 3] & 1073741823) << 7) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 30 | (in[inPos + 4] & 7) << 34) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 3 & 137438953471L) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 40 | (in[inPos + 5] & 8191) << 24) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 13 & 137438953471L) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 50 | (in[inPos + 6] & 8388607) << 14) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 23 & 137438953471L) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] >>> 60 | (in[inPos + 7] & 8589934591L) << 4) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 7] >>> 33 | (in[inPos + 8] & 63) << 31) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 8] >>> 6 & 137438953471L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 8] >>> 43 | (in[inPos + 9] & 65535) << 21) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 9] >>> 16 & 137438953471L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 9] >>> 53 | (in[inPos + 10] & 67108863) << 11) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 10] >>> 26 & 137438953471L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 10] >>> 63 | (in[inPos + 11] & 68719476735L) << 1) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 11] >>> 36 | (in[inPos + 12] & 511) << 28) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 12] >>> 9 & 137438953471L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 12] >>> 46 | (in[inPos + 13] & 524287) << 18) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 13] >>> 19 & 137438953471L) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 13] >>> 56 | (in[inPos + 14] & 536870911) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 14] >>> 29 | (in[inPos + 15] & 3) << 35) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 15] >>> 2 & 137438953471L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 15] >>> 39 | (in[inPos + 16] & 4095) << 25) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 16] >>> 12 & 137438953471L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 16] >>> 49 | (in[inPos + 17] & 4194303) << 15) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 17] >>> 22 & 137438953471L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 17] >>> 59 | (in[inPos + 18] & 4294967295L) << 5) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 18] >>> 32 | (in[inPos + 19] & 31) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 19] >>> 5 & 137438953471L) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 19] >>> 42 | (in[inPos + 20] & 32767) << 22) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 20] >>> 15 & 137438953471L) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 20] >>> 52 | (in[inPos + 21] & 33554431) << 12) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 21] >>> 25 & 137438953471L) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 21] >>> 62 | (in[inPos + 22] & 34359738367L) << 2) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 22] >>> 35 | (in[inPos + 23] & 255) << 29) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 23] >>> 8 & 137438953471L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 23] >>> 45 | (in[inPos + 24] & 262143) << 19) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 24] >>> 18 & 137438953471L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 24] >>> 55 | (in[inPos + 25] & 268435455) << 9) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 25] >>> 28 | (in[inPos + 26] & 1) << 36) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 26] >>> 1 & 137438953471L) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 26] >>> 38 | (in[inPos + 27] & 2047) << 26) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 27] >>> 11 & 137438953471L) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 27] >>> 48 | (in[inPos + 28] & 2097151) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 28] >>> 21 & 137438953471L) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 28] >>> 58 | (in[inPos + 29] & 2147483647) << 6) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 29] >>> 31 | (in[inPos + 30] & 15) << 33) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 30] >>> 4 & 137438953471L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 30] >>> 41 | (in[inPos + 31] & 16383) << 23) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 31] >>> 14 & 137438953471L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 31] >>> 51 | (in[inPos + 32] & 16777215) << 13) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 32] >>> 24 & 137438953471L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 32] >>> 61 | (in[inPos + 33] & 17179869183L) << 3) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 33] >>> 34 | (in[inPos + 34] & 127) << 30) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 34] >>> 7 & 137438953471L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 34] >>> 44 | (in[inPos + 35] & 131071) << 20) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 35] >>> 17 & 137438953471L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 35] >>> 54 | (in[inPos + 36] & 134217727) << 10) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 36] >>> 27) + out[outPos + 62];
  }

  private static void pack38(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 38;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 26 | (in[inPos + 2] - in[inPos + 1]) << 12 | (in[inPos + 3] - in[inPos + 2]) << 50;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 14 | (in[inPos + 4] - in[inPos + 3]) << 24 | (in[inPos + 5] - in[inPos + 4]) << 62;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 2 | (in[inPos + 6] - in[inPos + 5]) << 36;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 28 | (in[inPos + 7] - in[inPos + 6]) << 10 | (in[inPos + 8] - in[inPos + 7]) << 48;
    out[outPos + 5] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 22 | (in[inPos + 10] - in[inPos + 9]) << 60;
    out[outPos + 6] = (in[inPos + 10] - in[inPos + 9]) >>> 4 | (in[inPos + 11] - in[inPos + 10]) << 34;
    out[outPos + 7] = (in[inPos + 11] - in[inPos + 10]) >>> 30 | (in[inPos + 12] - in[inPos + 11]) << 8 | (in[inPos + 13] - in[inPos + 12]) << 46;
    out[outPos + 8] = (in[inPos + 13] - in[inPos + 12]) >>> 18 | (in[inPos + 14] - in[inPos + 13]) << 20 | (in[inPos + 15] - in[inPos + 14]) << 58;
    out[outPos + 9] = (in[inPos + 15] - in[inPos + 14]) >>> 6 | (in[inPos + 16] - in[inPos + 15]) << 32;
    out[outPos + 10] = (in[inPos + 16] - in[inPos + 15]) >>> 32 | (in[inPos + 17] - in[inPos + 16]) << 6 | (in[inPos + 18] - in[inPos + 17]) << 44;
    out[outPos + 11] = (in[inPos + 18] - in[inPos + 17]) >>> 20 | (in[inPos + 19] - in[inPos + 18]) << 18 | (in[inPos + 20] - in[inPos + 19]) << 56;
    out[outPos + 12] = (in[inPos + 20] - in[inPos + 19]) >>> 8 | (in[inPos + 21] - in[inPos + 20]) << 30;
    out[outPos + 13] = (in[inPos + 21] - in[inPos + 20]) >>> 34 | (in[inPos + 22] - in[inPos + 21]) << 4 | (in[inPos + 23] - in[inPos + 22]) << 42;
    out[outPos + 14] = (in[inPos + 23] - in[inPos + 22]) >>> 22 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 54;
    out[outPos + 15] = (in[inPos + 25] - in[inPos + 24]) >>> 10 | (in[inPos + 26] - in[inPos + 25]) << 28;
    out[outPos + 16] = (in[inPos + 26] - in[inPos + 25]) >>> 36 | (in[inPos + 27] - in[inPos + 26]) << 2 | (in[inPos + 28] - in[inPos + 27]) << 40;
    out[outPos + 17] = (in[inPos + 28] - in[inPos + 27]) >>> 24 | (in[inPos + 29] - in[inPos + 28]) << 14 | (in[inPos + 30] - in[inPos + 29]) << 52;
    out[outPos + 18] = (in[inPos + 30] - in[inPos + 29]) >>> 12 | (in[inPos + 31] - in[inPos + 30]) << 26;
    out[outPos + 19] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 38;
    out[outPos + 20] = (in[inPos + 33] - in[inPos + 32]) >>> 26 | (in[inPos + 34] - in[inPos + 33]) << 12 | (in[inPos + 35] - in[inPos + 34]) << 50;
    out[outPos + 21] = (in[inPos + 35] - in[inPos + 34]) >>> 14 | (in[inPos + 36] - in[inPos + 35]) << 24 | (in[inPos + 37] - in[inPos + 36]) << 62;
    out[outPos + 22] = (in[inPos + 37] - in[inPos + 36]) >>> 2 | (in[inPos + 38] - in[inPos + 37]) << 36;
    out[outPos + 23] = (in[inPos + 38] - in[inPos + 37]) >>> 28 | (in[inPos + 39] - in[inPos + 38]) << 10 | (in[inPos + 40] - in[inPos + 39]) << 48;
    out[outPos + 24] = (in[inPos + 40] - in[inPos + 39]) >>> 16 | (in[inPos + 41] - in[inPos + 40]) << 22 | (in[inPos + 42] - in[inPos + 41]) << 60;
    out[outPos + 25] = (in[inPos + 42] - in[inPos + 41]) >>> 4 | (in[inPos + 43] - in[inPos + 42]) << 34;
    out[outPos + 26] = (in[inPos + 43] - in[inPos + 42]) >>> 30 | (in[inPos + 44] - in[inPos + 43]) << 8 | (in[inPos + 45] - in[inPos + 44]) << 46;
    out[outPos + 27] = (in[inPos + 45] - in[inPos + 44]) >>> 18 | (in[inPos + 46] - in[inPos + 45]) << 20 | (in[inPos + 47] - in[inPos + 46]) << 58;
    out[outPos + 28] = (in[inPos + 47] - in[inPos + 46]) >>> 6 | (in[inPos + 48] - in[inPos + 47]) << 32;
    out[outPos + 29] = (in[inPos + 48] - in[inPos + 47]) >>> 32 | (in[inPos + 49] - in[inPos + 48]) << 6 | (in[inPos + 50] - in[inPos + 49]) << 44;
    out[outPos + 30] = (in[inPos + 50] - in[inPos + 49]) >>> 20 | (in[inPos + 51] - in[inPos + 50]) << 18 | (in[inPos + 52] - in[inPos + 51]) << 56;
    out[outPos + 31] = (in[inPos + 52] - in[inPos + 51]) >>> 8 | (in[inPos + 53] - in[inPos + 52]) << 30;
    out[outPos + 32] = (in[inPos + 53] - in[inPos + 52]) >>> 34 | (in[inPos + 54] - in[inPos + 53]) << 4 | (in[inPos + 55] - in[inPos + 54]) << 42;
    out[outPos + 33] = (in[inPos + 55] - in[inPos + 54]) >>> 22 | (in[inPos + 56] - in[inPos + 55]) << 16 | (in[inPos + 57] - in[inPos + 56]) << 54;
    out[outPos + 34] = (in[inPos + 57] - in[inPos + 56]) >>> 10 | (in[inPos + 58] - in[inPos + 57]) << 28;
    out[outPos + 35] = (in[inPos + 58] - in[inPos + 57]) >>> 36 | (in[inPos + 59] - in[inPos + 58]) << 2 | (in[inPos + 60] - in[inPos + 59]) << 40;
    out[outPos + 36] = (in[inPos + 60] - in[inPos + 59]) >>> 24 | (in[inPos + 61] - in[inPos + 60]) << 14 | (in[inPos + 62] - in[inPos + 61]) << 52;
    out[outPos + 37] = (in[inPos + 62] - in[inPos + 61]) >>> 12 | (in[inPos + 63] - in[inPos + 62]) << 26;
  }

  private static void unpack38(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 274877906943L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 38 | (in[inPos + 1] & 4095) << 26) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 12 & 274877906943L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 50 | (in[inPos + 2] & 16777215) << 14) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 24 & 274877906943L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 62 | (in[inPos + 3] & 68719476735L) << 2) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 36 | (in[inPos + 4] & 1023) << 28) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 10 & 274877906943L) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 48 | (in[inPos + 5] & 4194303) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 22 & 274877906943L) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 60 | (in[inPos + 6] & 17179869183L) << 4) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 34 | (in[inPos + 7] & 255) << 30) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 7] >>> 8 & 274877906943L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 7] >>> 46 | (in[inPos + 8] & 1048575) << 18) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 8] >>> 20 & 274877906943L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 8] >>> 58 | (in[inPos + 9] & 4294967295L) << 6) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 9] >>> 32 | (in[inPos + 10] & 63) << 32) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 10] >>> 6 & 274877906943L) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 10] >>> 44 | (in[inPos + 11] & 262143) << 20) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 11] >>> 18 & 274877906943L) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 11] >>> 56 | (in[inPos + 12] & 1073741823) << 8) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 12] >>> 30 | (in[inPos + 13] & 15) << 34) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 13] >>> 4 & 274877906943L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 13] >>> 42 | (in[inPos + 14] & 65535) << 22) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 14] >>> 16 & 274877906943L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 14] >>> 54 | (in[inPos + 15] & 268435455) << 10) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 15] >>> 28 | (in[inPos + 16] & 3) << 36) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 16] >>> 2 & 274877906943L) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 16] >>> 40 | (in[inPos + 17] & 16383) << 24) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 17] >>> 14 & 274877906943L) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 17] >>> 52 | (in[inPos + 18] & 67108863) << 12) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 18] >>> 26) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 19] & 274877906943L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 19] >>> 38 | (in[inPos + 20] & 4095) << 26) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 20] >>> 12 & 274877906943L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 20] >>> 50 | (in[inPos + 21] & 16777215) << 14) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 21] >>> 24 & 274877906943L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 21] >>> 62 | (in[inPos + 22] & 68719476735L) << 2) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 22] >>> 36 | (in[inPos + 23] & 1023) << 28) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 23] >>> 10 & 274877906943L) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 23] >>> 48 | (in[inPos + 24] & 4194303) << 16) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 24] >>> 22 & 274877906943L) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 24] >>> 60 | (in[inPos + 25] & 17179869183L) << 4) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 25] >>> 34 | (in[inPos + 26] & 255) << 30) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 26] >>> 8 & 274877906943L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 26] >>> 46 | (in[inPos + 27] & 1048575) << 18) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 27] >>> 20 & 274877906943L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 27] >>> 58 | (in[inPos + 28] & 4294967295L) << 6) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 28] >>> 32 | (in[inPos + 29] & 63) << 32) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 29] >>> 6 & 274877906943L) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 29] >>> 44 | (in[inPos + 30] & 262143) << 20) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 30] >>> 18 & 274877906943L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 30] >>> 56 | (in[inPos + 31] & 1073741823) << 8) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 31] >>> 30 | (in[inPos + 32] & 15) << 34) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 32] >>> 4 & 274877906943L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 32] >>> 42 | (in[inPos + 33] & 65535) << 22) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 33] >>> 16 & 274877906943L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 33] >>> 54 | (in[inPos + 34] & 268435455) << 10) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 34] >>> 28 | (in[inPos + 35] & 3) << 36) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 35] >>> 2 & 274877906943L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 35] >>> 40 | (in[inPos + 36] & 16383) << 24) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 36] >>> 14 & 274877906943L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 36] >>> 52 | (in[inPos + 37] & 67108863) << 12) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 37] >>> 26) + out[outPos + 62];
  }

  private static void pack39(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 39;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 25 | (in[inPos + 2] - in[inPos + 1]) << 14 | (in[inPos + 3] - in[inPos + 2]) << 53;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 11 | (in[inPos + 4] - in[inPos + 3]) << 28;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 36 | (in[inPos + 5] - in[inPos + 4]) << 3 | (in[inPos + 6] - in[inPos + 5]) << 42;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 22 | (in[inPos + 7] - in[inPos + 6]) << 17 | (in[inPos + 8] - in[inPos + 7]) << 56;
    out[outPos + 5] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 31;
    out[outPos + 6] = (in[inPos + 9] - in[inPos + 8]) >>> 33 | (in[inPos + 10] - in[inPos + 9]) << 6 | (in[inPos + 11] - in[inPos + 10]) << 45;
    out[outPos + 7] = (in[inPos + 11] - in[inPos + 10]) >>> 19 | (in[inPos + 12] - in[inPos + 11]) << 20 | (in[inPos + 13] - in[inPos + 12]) << 59;
    out[outPos + 8] = (in[inPos + 13] - in[inPos + 12]) >>> 5 | (in[inPos + 14] - in[inPos + 13]) << 34;
    out[outPos + 9] = (in[inPos + 14] - in[inPos + 13]) >>> 30 | (in[inPos + 15] - in[inPos + 14]) << 9 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 10] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 23 | (in[inPos + 18] - in[inPos + 17]) << 62;
    out[outPos + 11] = (in[inPos + 18] - in[inPos + 17]) >>> 2 | (in[inPos + 19] - in[inPos + 18]) << 37;
    out[outPos + 12] = (in[inPos + 19] - in[inPos + 18]) >>> 27 | (in[inPos + 20] - in[inPos + 19]) << 12 | (in[inPos + 21] - in[inPos + 20]) << 51;
    out[outPos + 13] = (in[inPos + 21] - in[inPos + 20]) >>> 13 | (in[inPos + 22] - in[inPos + 21]) << 26;
    out[outPos + 14] = (in[inPos + 22] - in[inPos + 21]) >>> 38 | (in[inPos + 23] - in[inPos + 22]) << 1 | (in[inPos + 24] - in[inPos + 23]) << 40;
    out[outPos + 15] = (in[inPos + 24] - in[inPos + 23]) >>> 24 | (in[inPos + 25] - in[inPos + 24]) << 15 | (in[inPos + 26] - in[inPos + 25]) << 54;
    out[outPos + 16] = (in[inPos + 26] - in[inPos + 25]) >>> 10 | (in[inPos + 27] - in[inPos + 26]) << 29;
    out[outPos + 17] = (in[inPos + 27] - in[inPos + 26]) >>> 35 | (in[inPos + 28] - in[inPos + 27]) << 4 | (in[inPos + 29] - in[inPos + 28]) << 43;
    out[outPos + 18] = (in[inPos + 29] - in[inPos + 28]) >>> 21 | (in[inPos + 30] - in[inPos + 29]) << 18 | (in[inPos + 31] - in[inPos + 30]) << 57;
    out[outPos + 19] = (in[inPos + 31] - in[inPos + 30]) >>> 7 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 20] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 7 | (in[inPos + 34] - in[inPos + 33]) << 46;
    out[outPos + 21] = (in[inPos + 34] - in[inPos + 33]) >>> 18 | (in[inPos + 35] - in[inPos + 34]) << 21 | (in[inPos + 36] - in[inPos + 35]) << 60;
    out[outPos + 22] = (in[inPos + 36] - in[inPos + 35]) >>> 4 | (in[inPos + 37] - in[inPos + 36]) << 35;
    out[outPos + 23] = (in[inPos + 37] - in[inPos + 36]) >>> 29 | (in[inPos + 38] - in[inPos + 37]) << 10 | (in[inPos + 39] - in[inPos + 38]) << 49;
    out[outPos + 24] = (in[inPos + 39] - in[inPos + 38]) >>> 15 | (in[inPos + 40] - in[inPos + 39]) << 24 | (in[inPos + 41] - in[inPos + 40]) << 63;
    out[outPos + 25] = (in[inPos + 41] - in[inPos + 40]) >>> 1 | (in[inPos + 42] - in[inPos + 41]) << 38;
    out[outPos + 26] = (in[inPos + 42] - in[inPos + 41]) >>> 26 | (in[inPos + 43] - in[inPos + 42]) << 13 | (in[inPos + 44] - in[inPos + 43]) << 52;
    out[outPos + 27] = (in[inPos + 44] - in[inPos + 43]) >>> 12 | (in[inPos + 45] - in[inPos + 44]) << 27;
    out[outPos + 28] = (in[inPos + 45] - in[inPos + 44]) >>> 37 | (in[inPos + 46] - in[inPos + 45]) << 2 | (in[inPos + 47] - in[inPos + 46]) << 41;
    out[outPos + 29] = (in[inPos + 47] - in[inPos + 46]) >>> 23 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 55;
    out[outPos + 30] = (in[inPos + 49] - in[inPos + 48]) >>> 9 | (in[inPos + 50] - in[inPos + 49]) << 30;
    out[outPos + 31] = (in[inPos + 50] - in[inPos + 49]) >>> 34 | (in[inPos + 51] - in[inPos + 50]) << 5 | (in[inPos + 52] - in[inPos + 51]) << 44;
    out[outPos + 32] = (in[inPos + 52] - in[inPos + 51]) >>> 20 | (in[inPos + 53] - in[inPos + 52]) << 19 | (in[inPos + 54] - in[inPos + 53]) << 58;
    out[outPos + 33] = (in[inPos + 54] - in[inPos + 53]) >>> 6 | (in[inPos + 55] - in[inPos + 54]) << 33;
    out[outPos + 34] = (in[inPos + 55] - in[inPos + 54]) >>> 31 | (in[inPos + 56] - in[inPos + 55]) << 8 | (in[inPos + 57] - in[inPos + 56]) << 47;
    out[outPos + 35] = (in[inPos + 57] - in[inPos + 56]) >>> 17 | (in[inPos + 58] - in[inPos + 57]) << 22 | (in[inPos + 59] - in[inPos + 58]) << 61;
    out[outPos + 36] = (in[inPos + 59] - in[inPos + 58]) >>> 3 | (in[inPos + 60] - in[inPos + 59]) << 36;
    out[outPos + 37] = (in[inPos + 60] - in[inPos + 59]) >>> 28 | (in[inPos + 61] - in[inPos + 60]) << 11 | (in[inPos + 62] - in[inPos + 61]) << 50;
    out[outPos + 38] = (in[inPos + 62] - in[inPos + 61]) >>> 14 | (in[inPos + 63] - in[inPos + 62]) << 25;
  }

  private static void unpack39(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 549755813887L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 39 | (in[inPos + 1] & 16383) << 25) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 14 & 549755813887L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 53 | (in[inPos + 2] & 268435455) << 11) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 28 | (in[inPos + 3] & 7) << 36) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 3 & 549755813887L) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 42 | (in[inPos + 4] & 131071) << 22) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 17 & 549755813887L) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 56 | (in[inPos + 5] & 2147483647) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 31 | (in[inPos + 6] & 63) << 33) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 6 & 549755813887L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 45 | (in[inPos + 7] & 1048575) << 19) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 7] >>> 20 & 549755813887L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 7] >>> 59 | (in[inPos + 8] & 17179869183L) << 5) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 8] >>> 34 | (in[inPos + 9] & 511) << 30) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 9] >>> 9 & 549755813887L) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 9] >>> 48 | (in[inPos + 10] & 8388607) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 10] >>> 23 & 549755813887L) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 10] >>> 62 | (in[inPos + 11] & 137438953471L) << 2) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 11] >>> 37 | (in[inPos + 12] & 4095) << 27) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 12] >>> 12 & 549755813887L) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 12] >>> 51 | (in[inPos + 13] & 67108863) << 13) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 13] >>> 26 | (in[inPos + 14] & 1) << 38) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 14] >>> 1 & 549755813887L) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 14] >>> 40 | (in[inPos + 15] & 32767) << 24) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 15] >>> 15 & 549755813887L) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 15] >>> 54 | (in[inPos + 16] & 536870911) << 10) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 16] >>> 29 | (in[inPos + 17] & 15) << 35) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 17] >>> 4 & 549755813887L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 17] >>> 43 | (in[inPos + 18] & 262143) << 21) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 18] >>> 18 & 549755813887L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 18] >>> 57 | (in[inPos + 19] & 4294967295L) << 7) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 19] >>> 32 | (in[inPos + 20] & 127) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 20] >>> 7 & 549755813887L) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 20] >>> 46 | (in[inPos + 21] & 2097151) << 18) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 21] >>> 21 & 549755813887L) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 21] >>> 60 | (in[inPos + 22] & 34359738367L) << 4) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 22] >>> 35 | (in[inPos + 23] & 1023) << 29) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 23] >>> 10 & 549755813887L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 23] >>> 49 | (in[inPos + 24] & 16777215) << 15) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 24] >>> 24 & 549755813887L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 24] >>> 63 | (in[inPos + 25] & 274877906943L) << 1) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 25] >>> 38 | (in[inPos + 26] & 8191) << 26) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 26] >>> 13 & 549755813887L) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 26] >>> 52 | (in[inPos + 27] & 134217727) << 12) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 27] >>> 27 | (in[inPos + 28] & 3) << 37) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 28] >>> 2 & 549755813887L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 28] >>> 41 | (in[inPos + 29] & 65535) << 23) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 29] >>> 16 & 549755813887L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 29] >>> 55 | (in[inPos + 30] & 1073741823) << 9) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 30] >>> 30 | (in[inPos + 31] & 31) << 34) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 31] >>> 5 & 549755813887L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 31] >>> 44 | (in[inPos + 32] & 524287) << 20) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 32] >>> 19 & 549755813887L) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 32] >>> 58 | (in[inPos + 33] & 8589934591L) << 6) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 33] >>> 33 | (in[inPos + 34] & 255) << 31) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 34] >>> 8 & 549755813887L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 34] >>> 47 | (in[inPos + 35] & 4194303) << 17) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 35] >>> 22 & 549755813887L) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 35] >>> 61 | (in[inPos + 36] & 68719476735L) << 3) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 36] >>> 36 | (in[inPos + 37] & 2047) << 28) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 37] >>> 11 & 549755813887L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 37] >>> 50 | (in[inPos + 38] & 33554431) << 14) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 38] >>> 25) + out[outPos + 62];
  }

  private static void pack40(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 40;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 24 | (in[inPos + 2] - in[inPos + 1]) << 16 | (in[inPos + 3] - in[inPos + 2]) << 56;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 8 | (in[inPos + 4] - in[inPos + 3]) << 32;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 32 | (in[inPos + 5] - in[inPos + 4]) << 8 | (in[inPos + 6] - in[inPos + 5]) << 48;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 16 | (in[inPos + 7] - in[inPos + 6]) << 24;
    out[outPos + 5] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 40;
    out[outPos + 6] = (in[inPos + 9] - in[inPos + 8]) >>> 24 | (in[inPos + 10] - in[inPos + 9]) << 16 | (in[inPos + 11] - in[inPos + 10]) << 56;
    out[outPos + 7] = (in[inPos + 11] - in[inPos + 10]) >>> 8 | (in[inPos + 12] - in[inPos + 11]) << 32;
    out[outPos + 8] = (in[inPos + 12] - in[inPos + 11]) >>> 32 | (in[inPos + 13] - in[inPos + 12]) << 8 | (in[inPos + 14] - in[inPos + 13]) << 48;
    out[outPos + 9] = (in[inPos + 14] - in[inPos + 13]) >>> 16 | (in[inPos + 15] - in[inPos + 14]) << 24;
    out[outPos + 10] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 40;
    out[outPos + 11] = (in[inPos + 17] - in[inPos + 16]) >>> 24 | (in[inPos + 18] - in[inPos + 17]) << 16 | (in[inPos + 19] - in[inPos + 18]) << 56;
    out[outPos + 12] = (in[inPos + 19] - in[inPos + 18]) >>> 8 | (in[inPos + 20] - in[inPos + 19]) << 32;
    out[outPos + 13] = (in[inPos + 20] - in[inPos + 19]) >>> 32 | (in[inPos + 21] - in[inPos + 20]) << 8 | (in[inPos + 22] - in[inPos + 21]) << 48;
    out[outPos + 14] = (in[inPos + 22] - in[inPos + 21]) >>> 16 | (in[inPos + 23] - in[inPos + 22]) << 24;
    out[outPos + 15] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 40;
    out[outPos + 16] = (in[inPos + 25] - in[inPos + 24]) >>> 24 | (in[inPos + 26] - in[inPos + 25]) << 16 | (in[inPos + 27] - in[inPos + 26]) << 56;
    out[outPos + 17] = (in[inPos + 27] - in[inPos + 26]) >>> 8 | (in[inPos + 28] - in[inPos + 27]) << 32;
    out[outPos + 18] = (in[inPos + 28] - in[inPos + 27]) >>> 32 | (in[inPos + 29] - in[inPos + 28]) << 8 | (in[inPos + 30] - in[inPos + 29]) << 48;
    out[outPos + 19] = (in[inPos + 30] - in[inPos + 29]) >>> 16 | (in[inPos + 31] - in[inPos + 30]) << 24;
    out[outPos + 20] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 40;
    out[outPos + 21] = (in[inPos + 33] - in[inPos + 32]) >>> 24 | (in[inPos + 34] - in[inPos + 33]) << 16 | (in[inPos + 35] - in[inPos + 34]) << 56;
    out[outPos + 22] = (in[inPos + 35] - in[inPos + 34]) >>> 8 | (in[inPos + 36] - in[inPos + 35]) << 32;
    out[outPos + 23] = (in[inPos + 36] - in[inPos + 35]) >>> 32 | (in[inPos + 37] - in[inPos + 36]) << 8 | (in[inPos + 38] - in[inPos + 37]) << 48;
    out[outPos + 24] = (in[inPos + 38] - in[inPos + 37]) >>> 16 | (in[inPos + 39] - in[inPos + 38]) << 24;
    out[outPos + 25] = (in[inPos + 40] - in[inPos + 39]) | (in[inPos + 41] - in[inPos + 40]) << 40;
    out[outPos + 26] = (in[inPos + 41] - in[inPos + 40]) >>> 24 | (in[inPos + 42] - in[inPos + 41]) << 16 | (in[inPos + 43] - in[inPos + 42]) << 56;
    out[outPos + 27] = (in[inPos + 43] - in[inPos + 42]) >>> 8 | (in[inPos + 44] - in[inPos + 43]) << 32;
    out[outPos + 28] = (in[inPos + 44] - in[inPos + 43]) >>> 32 | (in[inPos + 45] - in[inPos + 44]) << 8 | (in[inPos + 46] - in[inPos + 45]) << 48;
    out[outPos + 29] = (in[inPos + 46] - in[inPos + 45]) >>> 16 | (in[inPos + 47] - in[inPos + 46]) << 24;
    out[outPos + 30] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 40;
    out[outPos + 31] = (in[inPos + 49] - in[inPos + 48]) >>> 24 | (in[inPos + 50] - in[inPos + 49]) << 16 | (in[inPos + 51] - in[inPos + 50]) << 56;
    out[outPos + 32] = (in[inPos + 51] - in[inPos + 50]) >>> 8 | (in[inPos + 52] - in[inPos + 51]) << 32;
    out[outPos + 33] = (in[inPos + 52] - in[inPos + 51]) >>> 32 | (in[inPos + 53] - in[inPos + 52]) << 8 | (in[inPos + 54] - in[inPos + 53]) << 48;
    out[outPos + 34] = (in[inPos + 54] - in[inPos + 53]) >>> 16 | (in[inPos + 55] - in[inPos + 54]) << 24;
    out[outPos + 35] = (in[inPos + 56] - in[inPos + 55]) | (in[inPos + 57] - in[inPos + 56]) << 40;
    out[outPos + 36] = (in[inPos + 57] - in[inPos + 56]) >>> 24 | (in[inPos + 58] - in[inPos + 57]) << 16 | (in[inPos + 59] - in[inPos + 58]) << 56;
    out[outPos + 37] = (in[inPos + 59] - in[inPos + 58]) >>> 8 | (in[inPos + 60] - in[inPos + 59]) << 32;
    out[outPos + 38] = (in[inPos + 60] - in[inPos + 59]) >>> 32 | (in[inPos + 61] - in[inPos + 60]) << 8 | (in[inPos + 62] - in[inPos + 61]) << 48;
    out[outPos + 39] = (in[inPos + 62] - in[inPos + 61]) >>> 16 | (in[inPos + 63] - in[inPos + 62]) << 24;
  }

  private static void unpack40(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1099511627775L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 40 | (in[inPos + 1] & 65535) << 24) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 16 & 1099511627775L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 56 | (in[inPos + 2] & 4294967295L) << 8) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 32 | (in[inPos + 3] & 255) << 32) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 8 & 1099511627775L) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 48 | (in[inPos + 4] & 16777215) << 16) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 24) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] & 1099511627775L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 40 | (in[inPos + 6] & 65535) << 24) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 16 & 1099511627775L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 56 | (in[inPos + 7] & 4294967295L) << 8) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 7] >>> 32 | (in[inPos + 8] & 255) << 32) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 8] >>> 8 & 1099511627775L) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 8] >>> 48 | (in[inPos + 9] & 16777215) << 16) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 9] >>> 24) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 10] & 1099511627775L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 10] >>> 40 | (in[inPos + 11] & 65535) << 24) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 11] >>> 16 & 1099511627775L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 11] >>> 56 | (in[inPos + 12] & 4294967295L) << 8) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 12] >>> 32 | (in[inPos + 13] & 255) << 32) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 13] >>> 8 & 1099511627775L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 13] >>> 48 | (in[inPos + 14] & 16777215) << 16) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 14] >>> 24) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 15] & 1099511627775L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 15] >>> 40 | (in[inPos + 16] & 65535) << 24) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 16] >>> 16 & 1099511627775L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 4294967295L) << 8) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 17] >>> 32 | (in[inPos + 18] & 255) << 32) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 18] >>> 8 & 1099511627775L) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 18] >>> 48 | (in[inPos + 19] & 16777215) << 16) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 19] >>> 24) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 20] & 1099511627775L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 20] >>> 40 | (in[inPos + 21] & 65535) << 24) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 21] >>> 16 & 1099511627775L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 21] >>> 56 | (in[inPos + 22] & 4294967295L) << 8) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 22] >>> 32 | (in[inPos + 23] & 255) << 32) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 23] >>> 8 & 1099511627775L) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 23] >>> 48 | (in[inPos + 24] & 16777215) << 16) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 24] >>> 24) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 25] & 1099511627775L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 25] >>> 40 | (in[inPos + 26] & 65535) << 24) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 26] >>> 16 & 1099511627775L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 26] >>> 56 | (in[inPos + 27] & 4294967295L) << 8) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 27] >>> 32 | (in[inPos + 28] & 255) << 32) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 28] >>> 8 & 1099511627775L) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 28] >>> 48 | (in[inPos + 29] & 16777215) << 16) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 29] >>> 24) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 30] & 1099511627775L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 30] >>> 40 | (in[inPos + 31] & 65535) << 24) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 31] >>> 16 & 1099511627775L) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 31] >>> 56 | (in[inPos + 32] & 4294967295L) << 8) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 32] >>> 32 | (in[inPos + 33] & 255) << 32) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 33] >>> 8 & 1099511627775L) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 33] >>> 48 | (in[inPos + 34] & 16777215) << 16) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 34] >>> 24) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 35] & 1099511627775L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 35] >>> 40 | (in[inPos + 36] & 65535) << 24) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 36] >>> 16 & 1099511627775L) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 36] >>> 56 | (in[inPos + 37] & 4294967295L) << 8) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 37] >>> 32 | (in[inPos + 38] & 255) << 32) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 38] >>> 8 & 1099511627775L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 38] >>> 48 | (in[inPos + 39] & 16777215) << 16) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 39] >>> 24) + out[outPos + 62];
  }

  private static void pack41(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 41;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 23 | (in[inPos + 2] - in[inPos + 1]) << 18 | (in[inPos + 3] - in[inPos + 2]) << 59;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 5 | (in[inPos + 4] - in[inPos + 3]) << 36;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 28 | (in[inPos + 5] - in[inPos + 4]) << 13 | (in[inPos + 6] - in[inPos + 5]) << 54;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 10 | (in[inPos + 7] - in[inPos + 6]) << 31;
    out[outPos + 5] = (in[inPos + 7] - in[inPos + 6]) >>> 33 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 49;
    out[outPos + 6] = (in[inPos + 9] - in[inPos + 8]) >>> 15 | (in[inPos + 10] - in[inPos + 9]) << 26;
    out[outPos + 7] = (in[inPos + 10] - in[inPos + 9]) >>> 38 | (in[inPos + 11] - in[inPos + 10]) << 3 | (in[inPos + 12] - in[inPos + 11]) << 44;
    out[outPos + 8] = (in[inPos + 12] - in[inPos + 11]) >>> 20 | (in[inPos + 13] - in[inPos + 12]) << 21 | (in[inPos + 14] - in[inPos + 13]) << 62;
    out[outPos + 9] = (in[inPos + 14] - in[inPos + 13]) >>> 2 | (in[inPos + 15] - in[inPos + 14]) << 39;
    out[outPos + 10] = (in[inPos + 15] - in[inPos + 14]) >>> 25 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 57;
    out[outPos + 11] = (in[inPos + 17] - in[inPos + 16]) >>> 7 | (in[inPos + 18] - in[inPos + 17]) << 34;
    out[outPos + 12] = (in[inPos + 18] - in[inPos + 17]) >>> 30 | (in[inPos + 19] - in[inPos + 18]) << 11 | (in[inPos + 20] - in[inPos + 19]) << 52;
    out[outPos + 13] = (in[inPos + 20] - in[inPos + 19]) >>> 12 | (in[inPos + 21] - in[inPos + 20]) << 29;
    out[outPos + 14] = (in[inPos + 21] - in[inPos + 20]) >>> 35 | (in[inPos + 22] - in[inPos + 21]) << 6 | (in[inPos + 23] - in[inPos + 22]) << 47;
    out[outPos + 15] = (in[inPos + 23] - in[inPos + 22]) >>> 17 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 16] = (in[inPos + 24] - in[inPos + 23]) >>> 40 | (in[inPos + 25] - in[inPos + 24]) << 1 | (in[inPos + 26] - in[inPos + 25]) << 42;
    out[outPos + 17] = (in[inPos + 26] - in[inPos + 25]) >>> 22 | (in[inPos + 27] - in[inPos + 26]) << 19 | (in[inPos + 28] - in[inPos + 27]) << 60;
    out[outPos + 18] = (in[inPos + 28] - in[inPos + 27]) >>> 4 | (in[inPos + 29] - in[inPos + 28]) << 37;
    out[outPos + 19] = (in[inPos + 29] - in[inPos + 28]) >>> 27 | (in[inPos + 30] - in[inPos + 29]) << 14 | (in[inPos + 31] - in[inPos + 30]) << 55;
    out[outPos + 20] = (in[inPos + 31] - in[inPos + 30]) >>> 9 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 21] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 9 | (in[inPos + 34] - in[inPos + 33]) << 50;
    out[outPos + 22] = (in[inPos + 34] - in[inPos + 33]) >>> 14 | (in[inPos + 35] - in[inPos + 34]) << 27;
    out[outPos + 23] = (in[inPos + 35] - in[inPos + 34]) >>> 37 | (in[inPos + 36] - in[inPos + 35]) << 4 | (in[inPos + 37] - in[inPos + 36]) << 45;
    out[outPos + 24] = (in[inPos + 37] - in[inPos + 36]) >>> 19 | (in[inPos + 38] - in[inPos + 37]) << 22 | (in[inPos + 39] - in[inPos + 38]) << 63;
    out[outPos + 25] = (in[inPos + 39] - in[inPos + 38]) >>> 1 | (in[inPos + 40] - in[inPos + 39]) << 40;
    out[outPos + 26] = (in[inPos + 40] - in[inPos + 39]) >>> 24 | (in[inPos + 41] - in[inPos + 40]) << 17 | (in[inPos + 42] - in[inPos + 41]) << 58;
    out[outPos + 27] = (in[inPos + 42] - in[inPos + 41]) >>> 6 | (in[inPos + 43] - in[inPos + 42]) << 35;
    out[outPos + 28] = (in[inPos + 43] - in[inPos + 42]) >>> 29 | (in[inPos + 44] - in[inPos + 43]) << 12 | (in[inPos + 45] - in[inPos + 44]) << 53;
    out[outPos + 29] = (in[inPos + 45] - in[inPos + 44]) >>> 11 | (in[inPos + 46] - in[inPos + 45]) << 30;
    out[outPos + 30] = (in[inPos + 46] - in[inPos + 45]) >>> 34 | (in[inPos + 47] - in[inPos + 46]) << 7 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 31] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 25;
    out[outPos + 32] = (in[inPos + 49] - in[inPos + 48]) >>> 39 | (in[inPos + 50] - in[inPos + 49]) << 2 | (in[inPos + 51] - in[inPos + 50]) << 43;
    out[outPos + 33] = (in[inPos + 51] - in[inPos + 50]) >>> 21 | (in[inPos + 52] - in[inPos + 51]) << 20 | (in[inPos + 53] - in[inPos + 52]) << 61;
    out[outPos + 34] = (in[inPos + 53] - in[inPos + 52]) >>> 3 | (in[inPos + 54] - in[inPos + 53]) << 38;
    out[outPos + 35] = (in[inPos + 54] - in[inPos + 53]) >>> 26 | (in[inPos + 55] - in[inPos + 54]) << 15 | (in[inPos + 56] - in[inPos + 55]) << 56;
    out[outPos + 36] = (in[inPos + 56] - in[inPos + 55]) >>> 8 | (in[inPos + 57] - in[inPos + 56]) << 33;
    out[outPos + 37] = (in[inPos + 57] - in[inPos + 56]) >>> 31 | (in[inPos + 58] - in[inPos + 57]) << 10 | (in[inPos + 59] - in[inPos + 58]) << 51;
    out[outPos + 38] = (in[inPos + 59] - in[inPos + 58]) >>> 13 | (in[inPos + 60] - in[inPos + 59]) << 28;
    out[outPos + 39] = (in[inPos + 60] - in[inPos + 59]) >>> 36 | (in[inPos + 61] - in[inPos + 60]) << 5 | (in[inPos + 62] - in[inPos + 61]) << 46;
    out[outPos + 40] = (in[inPos + 62] - in[inPos + 61]) >>> 18 | (in[inPos + 63] - in[inPos + 62]) << 23;
  }

  private static void unpack41(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2199023255551L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 41 | (in[inPos + 1] & 262143) << 23) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 18 & 2199023255551L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 59 | (in[inPos + 2] & 68719476735L) << 5) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 36 | (in[inPos + 3] & 8191) << 28) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 13 & 2199023255551L) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 54 | (in[inPos + 4] & 2147483647) << 10) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 31 | (in[inPos + 5] & 255) << 33) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 8 & 2199023255551L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 49 | (in[inPos + 6] & 67108863) << 15) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 26 | (in[inPos + 7] & 7) << 38) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 3 & 2199023255551L) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 7] >>> 44 | (in[inPos + 8] & 2097151) << 20) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 8] >>> 21 & 2199023255551L) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 8] >>> 62 | (in[inPos + 9] & 549755813887L) << 2) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 9] >>> 39 | (in[inPos + 10] & 65535) << 25) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 10] >>> 16 & 2199023255551L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 10] >>> 57 | (in[inPos + 11] & 17179869183L) << 7) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 11] >>> 34 | (in[inPos + 12] & 2047) << 30) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 12] >>> 11 & 2199023255551L) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 12] >>> 52 | (in[inPos + 13] & 536870911) << 12) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 13] >>> 29 | (in[inPos + 14] & 63) << 35) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 14] >>> 6 & 2199023255551L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 14] >>> 47 | (in[inPos + 15] & 16777215) << 17) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 15] >>> 24 | (in[inPos + 16] & 1) << 40) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 16] >>> 1 & 2199023255551L) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 16] >>> 42 | (in[inPos + 17] & 524287) << 22) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 17] >>> 19 & 2199023255551L) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 17] >>> 60 | (in[inPos + 18] & 137438953471L) << 4) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 18] >>> 37 | (in[inPos + 19] & 16383) << 27) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 19] >>> 14 & 2199023255551L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 19] >>> 55 | (in[inPos + 20] & 4294967295L) << 9) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 20] >>> 32 | (in[inPos + 21] & 511) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 21] >>> 9 & 2199023255551L) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 21] >>> 50 | (in[inPos + 22] & 134217727) << 14) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 22] >>> 27 | (in[inPos + 23] & 15) << 37) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 23] >>> 4 & 2199023255551L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 23] >>> 45 | (in[inPos + 24] & 4194303) << 19) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 24] >>> 22 & 2199023255551L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 24] >>> 63 | (in[inPos + 25] & 1099511627775L) << 1) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 25] >>> 40 | (in[inPos + 26] & 131071) << 24) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 26] >>> 17 & 2199023255551L) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 26] >>> 58 | (in[inPos + 27] & 34359738367L) << 6) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 27] >>> 35 | (in[inPos + 28] & 4095) << 29) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 28] >>> 12 & 2199023255551L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 28] >>> 53 | (in[inPos + 29] & 1073741823) << 11) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 29] >>> 30 | (in[inPos + 30] & 127) << 34) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 30] >>> 7 & 2199023255551L) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 30] >>> 48 | (in[inPos + 31] & 33554431) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 31] >>> 25 | (in[inPos + 32] & 3) << 39) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 32] >>> 2 & 2199023255551L) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 32] >>> 43 | (in[inPos + 33] & 1048575) << 21) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 33] >>> 20 & 2199023255551L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 33] >>> 61 | (in[inPos + 34] & 274877906943L) << 3) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 34] >>> 38 | (in[inPos + 35] & 32767) << 26) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 35] >>> 15 & 2199023255551L) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 35] >>> 56 | (in[inPos + 36] & 8589934591L) << 8) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 36] >>> 33 | (in[inPos + 37] & 1023) << 31) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 37] >>> 10 & 2199023255551L) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 37] >>> 51 | (in[inPos + 38] & 268435455) << 13) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 38] >>> 28 | (in[inPos + 39] & 31) << 36) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 39] >>> 5 & 2199023255551L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 39] >>> 46 | (in[inPos + 40] & 8388607) << 18) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 40] >>> 23) + out[outPos + 62];
  }

  private static void pack42(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 42;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 22 | (in[inPos + 2] - in[inPos + 1]) << 20 | (in[inPos + 3] - in[inPos + 2]) << 62;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 2 | (in[inPos + 4] - in[inPos + 3]) << 40;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 24 | (in[inPos + 5] - in[inPos + 4]) << 18 | (in[inPos + 6] - in[inPos + 5]) << 60;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 4 | (in[inPos + 7] - in[inPos + 6]) << 38;
    out[outPos + 5] = (in[inPos + 7] - in[inPos + 6]) >>> 26 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 58;
    out[outPos + 6] = (in[inPos + 9] - in[inPos + 8]) >>> 6 | (in[inPos + 10] - in[inPos + 9]) << 36;
    out[outPos + 7] = (in[inPos + 10] - in[inPos + 9]) >>> 28 | (in[inPos + 11] - in[inPos + 10]) << 14 | (in[inPos + 12] - in[inPos + 11]) << 56;
    out[outPos + 8] = (in[inPos + 12] - in[inPos + 11]) >>> 8 | (in[inPos + 13] - in[inPos + 12]) << 34;
    out[outPos + 9] = (in[inPos + 13] - in[inPos + 12]) >>> 30 | (in[inPos + 14] - in[inPos + 13]) << 12 | (in[inPos + 15] - in[inPos + 14]) << 54;
    out[outPos + 10] = (in[inPos + 15] - in[inPos + 14]) >>> 10 | (in[inPos + 16] - in[inPos + 15]) << 32;
    out[outPos + 11] = (in[inPos + 16] - in[inPos + 15]) >>> 32 | (in[inPos + 17] - in[inPos + 16]) << 10 | (in[inPos + 18] - in[inPos + 17]) << 52;
    out[outPos + 12] = (in[inPos + 18] - in[inPos + 17]) >>> 12 | (in[inPos + 19] - in[inPos + 18]) << 30;
    out[outPos + 13] = (in[inPos + 19] - in[inPos + 18]) >>> 34 | (in[inPos + 20] - in[inPos + 19]) << 8 | (in[inPos + 21] - in[inPos + 20]) << 50;
    out[outPos + 14] = (in[inPos + 21] - in[inPos + 20]) >>> 14 | (in[inPos + 22] - in[inPos + 21]) << 28;
    out[outPos + 15] = (in[inPos + 22] - in[inPos + 21]) >>> 36 | (in[inPos + 23] - in[inPos + 22]) << 6 | (in[inPos + 24] - in[inPos + 23]) << 48;
    out[outPos + 16] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 26;
    out[outPos + 17] = (in[inPos + 25] - in[inPos + 24]) >>> 38 | (in[inPos + 26] - in[inPos + 25]) << 4 | (in[inPos + 27] - in[inPos + 26]) << 46;
    out[outPos + 18] = (in[inPos + 27] - in[inPos + 26]) >>> 18 | (in[inPos + 28] - in[inPos + 27]) << 24;
    out[outPos + 19] = (in[inPos + 28] - in[inPos + 27]) >>> 40 | (in[inPos + 29] - in[inPos + 28]) << 2 | (in[inPos + 30] - in[inPos + 29]) << 44;
    out[outPos + 20] = (in[inPos + 30] - in[inPos + 29]) >>> 20 | (in[inPos + 31] - in[inPos + 30]) << 22;
    out[outPos + 21] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 42;
    out[outPos + 22] = (in[inPos + 33] - in[inPos + 32]) >>> 22 | (in[inPos + 34] - in[inPos + 33]) << 20 | (in[inPos + 35] - in[inPos + 34]) << 62;
    out[outPos + 23] = (in[inPos + 35] - in[inPos + 34]) >>> 2 | (in[inPos + 36] - in[inPos + 35]) << 40;
    out[outPos + 24] = (in[inPos + 36] - in[inPos + 35]) >>> 24 | (in[inPos + 37] - in[inPos + 36]) << 18 | (in[inPos + 38] - in[inPos + 37]) << 60;
    out[outPos + 25] = (in[inPos + 38] - in[inPos + 37]) >>> 4 | (in[inPos + 39] - in[inPos + 38]) << 38;
    out[outPos + 26] = (in[inPos + 39] - in[inPos + 38]) >>> 26 | (in[inPos + 40] - in[inPos + 39]) << 16 | (in[inPos + 41] - in[inPos + 40]) << 58;
    out[outPos + 27] = (in[inPos + 41] - in[inPos + 40]) >>> 6 | (in[inPos + 42] - in[inPos + 41]) << 36;
    out[outPos + 28] = (in[inPos + 42] - in[inPos + 41]) >>> 28 | (in[inPos + 43] - in[inPos + 42]) << 14 | (in[inPos + 44] - in[inPos + 43]) << 56;
    out[outPos + 29] = (in[inPos + 44] - in[inPos + 43]) >>> 8 | (in[inPos + 45] - in[inPos + 44]) << 34;
    out[outPos + 30] = (in[inPos + 45] - in[inPos + 44]) >>> 30 | (in[inPos + 46] - in[inPos + 45]) << 12 | (in[inPos + 47] - in[inPos + 46]) << 54;
    out[outPos + 31] = (in[inPos + 47] - in[inPos + 46]) >>> 10 | (in[inPos + 48] - in[inPos + 47]) << 32;
    out[outPos + 32] = (in[inPos + 48] - in[inPos + 47]) >>> 32 | (in[inPos + 49] - in[inPos + 48]) << 10 | (in[inPos + 50] - in[inPos + 49]) << 52;
    out[outPos + 33] = (in[inPos + 50] - in[inPos + 49]) >>> 12 | (in[inPos + 51] - in[inPos + 50]) << 30;
    out[outPos + 34] = (in[inPos + 51] - in[inPos + 50]) >>> 34 | (in[inPos + 52] - in[inPos + 51]) << 8 | (in[inPos + 53] - in[inPos + 52]) << 50;
    out[outPos + 35] = (in[inPos + 53] - in[inPos + 52]) >>> 14 | (in[inPos + 54] - in[inPos + 53]) << 28;
    out[outPos + 36] = (in[inPos + 54] - in[inPos + 53]) >>> 36 | (in[inPos + 55] - in[inPos + 54]) << 6 | (in[inPos + 56] - in[inPos + 55]) << 48;
    out[outPos + 37] = (in[inPos + 56] - in[inPos + 55]) >>> 16 | (in[inPos + 57] - in[inPos + 56]) << 26;
    out[outPos + 38] = (in[inPos + 57] - in[inPos + 56]) >>> 38 | (in[inPos + 58] - in[inPos + 57]) << 4 | (in[inPos + 59] - in[inPos + 58]) << 46;
    out[outPos + 39] = (in[inPos + 59] - in[inPos + 58]) >>> 18 | (in[inPos + 60] - in[inPos + 59]) << 24;
    out[outPos + 40] = (in[inPos + 60] - in[inPos + 59]) >>> 40 | (in[inPos + 61] - in[inPos + 60]) << 2 | (in[inPos + 62] - in[inPos + 61]) << 44;
    out[outPos + 41] = (in[inPos + 62] - in[inPos + 61]) >>> 20 | (in[inPos + 63] - in[inPos + 62]) << 22;
  }

  private static void unpack42(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 4398046511103L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 42 | (in[inPos + 1] & 1048575) << 22) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 20 & 4398046511103L) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 1099511627775L) << 2) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 40 | (in[inPos + 3] & 262143) << 24) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 18 & 4398046511103L) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 274877906943L) << 4) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 38 | (in[inPos + 5] & 65535) << 26) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 16 & 4398046511103L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 68719476735L) << 6) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 36 | (in[inPos + 7] & 16383) << 28) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 14 & 4398046511103L) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 17179869183L) << 8) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 8] >>> 34 | (in[inPos + 9] & 4095) << 30) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 9] >>> 12 & 4398046511103L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 4294967295L) << 10) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 10] >>> 32 | (in[inPos + 11] & 1023) << 32) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 11] >>> 10 & 4398046511103L) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 1073741823) << 12) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 12] >>> 30 | (in[inPos + 13] & 255) << 34) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 13] >>> 8 & 4398046511103L) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 13] >>> 50 | (in[inPos + 14] & 268435455) << 14) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 14] >>> 28 | (in[inPos + 15] & 63) << 36) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 15] >>> 6 & 4398046511103L) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 67108863) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 16] >>> 26 | (in[inPos + 17] & 15) << 38) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 17] >>> 4 & 4398046511103L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 17] >>> 46 | (in[inPos + 18] & 16777215) << 18) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 18] >>> 24 | (in[inPos + 19] & 3) << 40) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 19] >>> 2 & 4398046511103L) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 4194303) << 20) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 20] >>> 22) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 21] & 4398046511103L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 21] >>> 42 | (in[inPos + 22] & 1048575) << 22) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 22] >>> 20 & 4398046511103L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 22] >>> 62 | (in[inPos + 23] & 1099511627775L) << 2) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 23] >>> 40 | (in[inPos + 24] & 262143) << 24) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 24] >>> 18 & 4398046511103L) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 24] >>> 60 | (in[inPos + 25] & 274877906943L) << 4) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 25] >>> 38 | (in[inPos + 26] & 65535) << 26) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 26] >>> 16 & 4398046511103L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 26] >>> 58 | (in[inPos + 27] & 68719476735L) << 6) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 27] >>> 36 | (in[inPos + 28] & 16383) << 28) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 28] >>> 14 & 4398046511103L) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 28] >>> 56 | (in[inPos + 29] & 17179869183L) << 8) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 29] >>> 34 | (in[inPos + 30] & 4095) << 30) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 30] >>> 12 & 4398046511103L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 30] >>> 54 | (in[inPos + 31] & 4294967295L) << 10) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 31] >>> 32 | (in[inPos + 32] & 1023) << 32) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 32] >>> 10 & 4398046511103L) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 32] >>> 52 | (in[inPos + 33] & 1073741823) << 12) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 33] >>> 30 | (in[inPos + 34] & 255) << 34) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 34] >>> 8 & 4398046511103L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 34] >>> 50 | (in[inPos + 35] & 268435455) << 14) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 35] >>> 28 | (in[inPos + 36] & 63) << 36) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 36] >>> 6 & 4398046511103L) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 36] >>> 48 | (in[inPos + 37] & 67108863) << 16) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 37] >>> 26 | (in[inPos + 38] & 15) << 38) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 38] >>> 4 & 4398046511103L) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 38] >>> 46 | (in[inPos + 39] & 16777215) << 18) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 39] >>> 24 | (in[inPos + 40] & 3) << 40) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 40] >>> 2 & 4398046511103L) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 40] >>> 44 | (in[inPos + 41] & 4194303) << 20) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 41] >>> 22) + out[outPos + 62];
  }

  private static void pack43(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 43;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 21 | (in[inPos + 2] - in[inPos + 1]) << 22;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 42 | (in[inPos + 3] - in[inPos + 2]) << 1 | (in[inPos + 4] - in[inPos + 3]) << 44;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 20 | (in[inPos + 5] - in[inPos + 4]) << 23;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 41 | (in[inPos + 6] - in[inPos + 5]) << 2 | (in[inPos + 7] - in[inPos + 6]) << 45;
    out[outPos + 5] = (in[inPos + 7] - in[inPos + 6]) >>> 19 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) >>> 40 | (in[inPos + 9] - in[inPos + 8]) << 3 | (in[inPos + 10] - in[inPos + 9]) << 46;
    out[outPos + 7] = (in[inPos + 10] - in[inPos + 9]) >>> 18 | (in[inPos + 11] - in[inPos + 10]) << 25;
    out[outPos + 8] = (in[inPos + 11] - in[inPos + 10]) >>> 39 | (in[inPos + 12] - in[inPos + 11]) << 4 | (in[inPos + 13] - in[inPos + 12]) << 47;
    out[outPos + 9] = (in[inPos + 13] - in[inPos + 12]) >>> 17 | (in[inPos + 14] - in[inPos + 13]) << 26;
    out[outPos + 10] = (in[inPos + 14] - in[inPos + 13]) >>> 38 | (in[inPos + 15] - in[inPos + 14]) << 5 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 11] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 27;
    out[outPos + 12] = (in[inPos + 17] - in[inPos + 16]) >>> 37 | (in[inPos + 18] - in[inPos + 17]) << 6 | (in[inPos + 19] - in[inPos + 18]) << 49;
    out[outPos + 13] = (in[inPos + 19] - in[inPos + 18]) >>> 15 | (in[inPos + 20] - in[inPos + 19]) << 28;
    out[outPos + 14] = (in[inPos + 20] - in[inPos + 19]) >>> 36 | (in[inPos + 21] - in[inPos + 20]) << 7 | (in[inPos + 22] - in[inPos + 21]) << 50;
    out[outPos + 15] = (in[inPos + 22] - in[inPos + 21]) >>> 14 | (in[inPos + 23] - in[inPos + 22]) << 29;
    out[outPos + 16] = (in[inPos + 23] - in[inPos + 22]) >>> 35 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 51;
    out[outPos + 17] = (in[inPos + 25] - in[inPos + 24]) >>> 13 | (in[inPos + 26] - in[inPos + 25]) << 30;
    out[outPos + 18] = (in[inPos + 26] - in[inPos + 25]) >>> 34 | (in[inPos + 27] - in[inPos + 26]) << 9 | (in[inPos + 28] - in[inPos + 27]) << 52;
    out[outPos + 19] = (in[inPos + 28] - in[inPos + 27]) >>> 12 | (in[inPos + 29] - in[inPos + 28]) << 31;
    out[outPos + 20] = (in[inPos + 29] - in[inPos + 28]) >>> 33 | (in[inPos + 30] - in[inPos + 29]) << 10 | (in[inPos + 31] - in[inPos + 30]) << 53;
    out[outPos + 21] = (in[inPos + 31] - in[inPos + 30]) >>> 11 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 22] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 11 | (in[inPos + 34] - in[inPos + 33]) << 54;
    out[outPos + 23] = (in[inPos + 34] - in[inPos + 33]) >>> 10 | (in[inPos + 35] - in[inPos + 34]) << 33;
    out[outPos + 24] = (in[inPos + 35] - in[inPos + 34]) >>> 31 | (in[inPos + 36] - in[inPos + 35]) << 12 | (in[inPos + 37] - in[inPos + 36]) << 55;
    out[outPos + 25] = (in[inPos + 37] - in[inPos + 36]) >>> 9 | (in[inPos + 38] - in[inPos + 37]) << 34;
    out[outPos + 26] = (in[inPos + 38] - in[inPos + 37]) >>> 30 | (in[inPos + 39] - in[inPos + 38]) << 13 | (in[inPos + 40] - in[inPos + 39]) << 56;
    out[outPos + 27] = (in[inPos + 40] - in[inPos + 39]) >>> 8 | (in[inPos + 41] - in[inPos + 40]) << 35;
    out[outPos + 28] = (in[inPos + 41] - in[inPos + 40]) >>> 29 | (in[inPos + 42] - in[inPos + 41]) << 14 | (in[inPos + 43] - in[inPos + 42]) << 57;
    out[outPos + 29] = (in[inPos + 43] - in[inPos + 42]) >>> 7 | (in[inPos + 44] - in[inPos + 43]) << 36;
    out[outPos + 30] = (in[inPos + 44] - in[inPos + 43]) >>> 28 | (in[inPos + 45] - in[inPos + 44]) << 15 | (in[inPos + 46] - in[inPos + 45]) << 58;
    out[outPos + 31] = (in[inPos + 46] - in[inPos + 45]) >>> 6 | (in[inPos + 47] - in[inPos + 46]) << 37;
    out[outPos + 32] = (in[inPos + 47] - in[inPos + 46]) >>> 27 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 59;
    out[outPos + 33] = (in[inPos + 49] - in[inPos + 48]) >>> 5 | (in[inPos + 50] - in[inPos + 49]) << 38;
    out[outPos + 34] = (in[inPos + 50] - in[inPos + 49]) >>> 26 | (in[inPos + 51] - in[inPos + 50]) << 17 | (in[inPos + 52] - in[inPos + 51]) << 60;
    out[outPos + 35] = (in[inPos + 52] - in[inPos + 51]) >>> 4 | (in[inPos + 53] - in[inPos + 52]) << 39;
    out[outPos + 36] = (in[inPos + 53] - in[inPos + 52]) >>> 25 | (in[inPos + 54] - in[inPos + 53]) << 18 | (in[inPos + 55] - in[inPos + 54]) << 61;
    out[outPos + 37] = (in[inPos + 55] - in[inPos + 54]) >>> 3 | (in[inPos + 56] - in[inPos + 55]) << 40;
    out[outPos + 38] = (in[inPos + 56] - in[inPos + 55]) >>> 24 | (in[inPos + 57] - in[inPos + 56]) << 19 | (in[inPos + 58] - in[inPos + 57]) << 62;
    out[outPos + 39] = (in[inPos + 58] - in[inPos + 57]) >>> 2 | (in[inPos + 59] - in[inPos + 58]) << 41;
    out[outPos + 40] = (in[inPos + 59] - in[inPos + 58]) >>> 23 | (in[inPos + 60] - in[inPos + 59]) << 20 | (in[inPos + 61] - in[inPos + 60]) << 63;
    out[outPos + 41] = (in[inPos + 61] - in[inPos + 60]) >>> 1 | (in[inPos + 62] - in[inPos + 61]) << 42;
    out[outPos + 42] = (in[inPos + 62] - in[inPos + 61]) >>> 22 | (in[inPos + 63] - in[inPos + 62]) << 21;
  }

  private static void unpack43(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 8796093022207L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 43 | (in[inPos + 1] & 4194303) << 21) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 22 | (in[inPos + 2] & 1) << 42) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 1 & 8796093022207L) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 44 | (in[inPos + 3] & 8388607) << 20) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 23 | (in[inPos + 4] & 3) << 41) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 2 & 8796093022207L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 45 | (in[inPos + 5] & 16777215) << 19) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 24 | (in[inPos + 6] & 7) << 40) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 3 & 8796093022207L) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 46 | (in[inPos + 7] & 33554431) << 18) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 25 | (in[inPos + 8] & 15) << 39) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 8] >>> 4 & 8796093022207L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 8] >>> 47 | (in[inPos + 9] & 67108863) << 17) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 9] >>> 26 | (in[inPos + 10] & 31) << 38) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 10] >>> 5 & 8796093022207L) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 10] >>> 48 | (in[inPos + 11] & 134217727) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 11] >>> 27 | (in[inPos + 12] & 63) << 37) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 12] >>> 6 & 8796093022207L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 12] >>> 49 | (in[inPos + 13] & 268435455) << 15) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 13] >>> 28 | (in[inPos + 14] & 127) << 36) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 14] >>> 7 & 8796093022207L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 14] >>> 50 | (in[inPos + 15] & 536870911) << 14) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 15] >>> 29 | (in[inPos + 16] & 255) << 35) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 16] >>> 8 & 8796093022207L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 16] >>> 51 | (in[inPos + 17] & 1073741823) << 13) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 17] >>> 30 | (in[inPos + 18] & 511) << 34) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 18] >>> 9 & 8796093022207L) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 18] >>> 52 | (in[inPos + 19] & 2147483647) << 12) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 19] >>> 31 | (in[inPos + 20] & 1023) << 33) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 20] >>> 10 & 8796093022207L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 20] >>> 53 | (in[inPos + 21] & 4294967295L) << 11) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 21] >>> 32 | (in[inPos + 22] & 2047) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 22] >>> 11 & 8796093022207L) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 22] >>> 54 | (in[inPos + 23] & 8589934591L) << 10) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 23] >>> 33 | (in[inPos + 24] & 4095) << 31) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 24] >>> 12 & 8796093022207L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 24] >>> 55 | (in[inPos + 25] & 17179869183L) << 9) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 25] >>> 34 | (in[inPos + 26] & 8191) << 30) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 26] >>> 13 & 8796093022207L) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 26] >>> 56 | (in[inPos + 27] & 34359738367L) << 8) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 27] >>> 35 | (in[inPos + 28] & 16383) << 29) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 28] >>> 14 & 8796093022207L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 28] >>> 57 | (in[inPos + 29] & 68719476735L) << 7) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 29] >>> 36 | (in[inPos + 30] & 32767) << 28) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 30] >>> 15 & 8796093022207L) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 30] >>> 58 | (in[inPos + 31] & 137438953471L) << 6) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 31] >>> 37 | (in[inPos + 32] & 65535) << 27) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 32] >>> 16 & 8796093022207L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 32] >>> 59 | (in[inPos + 33] & 274877906943L) << 5) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 33] >>> 38 | (in[inPos + 34] & 131071) << 26) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 34] >>> 17 & 8796093022207L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 34] >>> 60 | (in[inPos + 35] & 549755813887L) << 4) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 35] >>> 39 | (in[inPos + 36] & 262143) << 25) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 36] >>> 18 & 8796093022207L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 36] >>> 61 | (in[inPos + 37] & 1099511627775L) << 3) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 37] >>> 40 | (in[inPos + 38] & 524287) << 24) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 38] >>> 19 & 8796093022207L) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 38] >>> 62 | (in[inPos + 39] & 2199023255551L) << 2) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 39] >>> 41 | (in[inPos + 40] & 1048575) << 23) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 40] >>> 20 & 8796093022207L) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 40] >>> 63 | (in[inPos + 41] & 4398046511103L) << 1) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 41] >>> 42 | (in[inPos + 42] & 2097151) << 22) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 42] >>> 21) + out[outPos + 62];
  }

  private static void pack44(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 44;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 20 | (in[inPos + 2] - in[inPos + 1]) << 24;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 40 | (in[inPos + 3] - in[inPos + 2]) << 4 | (in[inPos + 4] - in[inPos + 3]) << 48;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 16 | (in[inPos + 5] - in[inPos + 4]) << 28;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 36 | (in[inPos + 6] - in[inPos + 5]) << 8 | (in[inPos + 7] - in[inPos + 6]) << 52;
    out[outPos + 5] = (in[inPos + 7] - in[inPos + 6]) >>> 12 | (in[inPos + 8] - in[inPos + 7]) << 32;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) >>> 32 | (in[inPos + 9] - in[inPos + 8]) << 12 | (in[inPos + 10] - in[inPos + 9]) << 56;
    out[outPos + 7] = (in[inPos + 10] - in[inPos + 9]) >>> 8 | (in[inPos + 11] - in[inPos + 10]) << 36;
    out[outPos + 8] = (in[inPos + 11] - in[inPos + 10]) >>> 28 | (in[inPos + 12] - in[inPos + 11]) << 16 | (in[inPos + 13] - in[inPos + 12]) << 60;
    out[outPos + 9] = (in[inPos + 13] - in[inPos + 12]) >>> 4 | (in[inPos + 14] - in[inPos + 13]) << 40;
    out[outPos + 10] = (in[inPos + 14] - in[inPos + 13]) >>> 24 | (in[inPos + 15] - in[inPos + 14]) << 20;
    out[outPos + 11] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 44;
    out[outPos + 12] = (in[inPos + 17] - in[inPos + 16]) >>> 20 | (in[inPos + 18] - in[inPos + 17]) << 24;
    out[outPos + 13] = (in[inPos + 18] - in[inPos + 17]) >>> 40 | (in[inPos + 19] - in[inPos + 18]) << 4 | (in[inPos + 20] - in[inPos + 19]) << 48;
    out[outPos + 14] = (in[inPos + 20] - in[inPos + 19]) >>> 16 | (in[inPos + 21] - in[inPos + 20]) << 28;
    out[outPos + 15] = (in[inPos + 21] - in[inPos + 20]) >>> 36 | (in[inPos + 22] - in[inPos + 21]) << 8 | (in[inPos + 23] - in[inPos + 22]) << 52;
    out[outPos + 16] = (in[inPos + 23] - in[inPos + 22]) >>> 12 | (in[inPos + 24] - in[inPos + 23]) << 32;
    out[outPos + 17] = (in[inPos + 24] - in[inPos + 23]) >>> 32 | (in[inPos + 25] - in[inPos + 24]) << 12 | (in[inPos + 26] - in[inPos + 25]) << 56;
    out[outPos + 18] = (in[inPos + 26] - in[inPos + 25]) >>> 8 | (in[inPos + 27] - in[inPos + 26]) << 36;
    out[outPos + 19] = (in[inPos + 27] - in[inPos + 26]) >>> 28 | (in[inPos + 28] - in[inPos + 27]) << 16 | (in[inPos + 29] - in[inPos + 28]) << 60;
    out[outPos + 20] = (in[inPos + 29] - in[inPos + 28]) >>> 4 | (in[inPos + 30] - in[inPos + 29]) << 40;
    out[outPos + 21] = (in[inPos + 30] - in[inPos + 29]) >>> 24 | (in[inPos + 31] - in[inPos + 30]) << 20;
    out[outPos + 22] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 44;
    out[outPos + 23] = (in[inPos + 33] - in[inPos + 32]) >>> 20 | (in[inPos + 34] - in[inPos + 33]) << 24;
    out[outPos + 24] = (in[inPos + 34] - in[inPos + 33]) >>> 40 | (in[inPos + 35] - in[inPos + 34]) << 4 | (in[inPos + 36] - in[inPos + 35]) << 48;
    out[outPos + 25] = (in[inPos + 36] - in[inPos + 35]) >>> 16 | (in[inPos + 37] - in[inPos + 36]) << 28;
    out[outPos + 26] = (in[inPos + 37] - in[inPos + 36]) >>> 36 | (in[inPos + 38] - in[inPos + 37]) << 8 | (in[inPos + 39] - in[inPos + 38]) << 52;
    out[outPos + 27] = (in[inPos + 39] - in[inPos + 38]) >>> 12 | (in[inPos + 40] - in[inPos + 39]) << 32;
    out[outPos + 28] = (in[inPos + 40] - in[inPos + 39]) >>> 32 | (in[inPos + 41] - in[inPos + 40]) << 12 | (in[inPos + 42] - in[inPos + 41]) << 56;
    out[outPos + 29] = (in[inPos + 42] - in[inPos + 41]) >>> 8 | (in[inPos + 43] - in[inPos + 42]) << 36;
    out[outPos + 30] = (in[inPos + 43] - in[inPos + 42]) >>> 28 | (in[inPos + 44] - in[inPos + 43]) << 16 | (in[inPos + 45] - in[inPos + 44]) << 60;
    out[outPos + 31] = (in[inPos + 45] - in[inPos + 44]) >>> 4 | (in[inPos + 46] - in[inPos + 45]) << 40;
    out[outPos + 32] = (in[inPos + 46] - in[inPos + 45]) >>> 24 | (in[inPos + 47] - in[inPos + 46]) << 20;
    out[outPos + 33] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 44;
    out[outPos + 34] = (in[inPos + 49] - in[inPos + 48]) >>> 20 | (in[inPos + 50] - in[inPos + 49]) << 24;
    out[outPos + 35] = (in[inPos + 50] - in[inPos + 49]) >>> 40 | (in[inPos + 51] - in[inPos + 50]) << 4 | (in[inPos + 52] - in[inPos + 51]) << 48;
    out[outPos + 36] = (in[inPos + 52] - in[inPos + 51]) >>> 16 | (in[inPos + 53] - in[inPos + 52]) << 28;
    out[outPos + 37] = (in[inPos + 53] - in[inPos + 52]) >>> 36 | (in[inPos + 54] - in[inPos + 53]) << 8 | (in[inPos + 55] - in[inPos + 54]) << 52;
    out[outPos + 38] = (in[inPos + 55] - in[inPos + 54]) >>> 12 | (in[inPos + 56] - in[inPos + 55]) << 32;
    out[outPos + 39] = (in[inPos + 56] - in[inPos + 55]) >>> 32 | (in[inPos + 57] - in[inPos + 56]) << 12 | (in[inPos + 58] - in[inPos + 57]) << 56;
    out[outPos + 40] = (in[inPos + 58] - in[inPos + 57]) >>> 8 | (in[inPos + 59] - in[inPos + 58]) << 36;
    out[outPos + 41] = (in[inPos + 59] - in[inPos + 58]) >>> 28 | (in[inPos + 60] - in[inPos + 59]) << 16 | (in[inPos + 61] - in[inPos + 60]) << 60;
    out[outPos + 42] = (in[inPos + 61] - in[inPos + 60]) >>> 4 | (in[inPos + 62] - in[inPos + 61]) << 40;
    out[outPos + 43] = (in[inPos + 62] - in[inPos + 61]) >>> 24 | (in[inPos + 63] - in[inPos + 62]) << 20;
  }

  private static void unpack44(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 17592186044415L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 44 | (in[inPos + 1] & 16777215) << 20) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 24 | (in[inPos + 2] & 15) << 40) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 4 & 17592186044415L) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 48 | (in[inPos + 3] & 268435455) << 16) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 28 | (in[inPos + 4] & 255) << 36) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 8 & 17592186044415L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 52 | (in[inPos + 5] & 4294967295L) << 12) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 32 | (in[inPos + 6] & 4095) << 32) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 12 & 17592186044415L) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 56 | (in[inPos + 7] & 68719476735L) << 8) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 36 | (in[inPos + 8] & 65535) << 28) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 8] >>> 16 & 17592186044415L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 8] >>> 60 | (in[inPos + 9] & 1099511627775L) << 4) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 9] >>> 40 | (in[inPos + 10] & 1048575) << 24) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 10] >>> 20) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 11] & 17592186044415L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 11] >>> 44 | (in[inPos + 12] & 16777215) << 20) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 12] >>> 24 | (in[inPos + 13] & 15) << 40) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 13] >>> 4 & 17592186044415L) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 13] >>> 48 | (in[inPos + 14] & 268435455) << 16) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 14] >>> 28 | (in[inPos + 15] & 255) << 36) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 15] >>> 8 & 17592186044415L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 15] >>> 52 | (in[inPos + 16] & 4294967295L) << 12) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 16] >>> 32 | (in[inPos + 17] & 4095) << 32) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 17] >>> 12 & 17592186044415L) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 17] >>> 56 | (in[inPos + 18] & 68719476735L) << 8) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 18] >>> 36 | (in[inPos + 19] & 65535) << 28) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 19] >>> 16 & 17592186044415L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 19] >>> 60 | (in[inPos + 20] & 1099511627775L) << 4) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 20] >>> 40 | (in[inPos + 21] & 1048575) << 24) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 21] >>> 20) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 22] & 17592186044415L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 22] >>> 44 | (in[inPos + 23] & 16777215) << 20) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 23] >>> 24 | (in[inPos + 24] & 15) << 40) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 24] >>> 4 & 17592186044415L) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 24] >>> 48 | (in[inPos + 25] & 268435455) << 16) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 25] >>> 28 | (in[inPos + 26] & 255) << 36) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 26] >>> 8 & 17592186044415L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 26] >>> 52 | (in[inPos + 27] & 4294967295L) << 12) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 27] >>> 32 | (in[inPos + 28] & 4095) << 32) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 28] >>> 12 & 17592186044415L) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 28] >>> 56 | (in[inPos + 29] & 68719476735L) << 8) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 29] >>> 36 | (in[inPos + 30] & 65535) << 28) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 30] >>> 16 & 17592186044415L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 30] >>> 60 | (in[inPos + 31] & 1099511627775L) << 4) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 31] >>> 40 | (in[inPos + 32] & 1048575) << 24) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 32] >>> 20) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 33] & 17592186044415L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 33] >>> 44 | (in[inPos + 34] & 16777215) << 20) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 34] >>> 24 | (in[inPos + 35] & 15) << 40) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 35] >>> 4 & 17592186044415L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 35] >>> 48 | (in[inPos + 36] & 268435455) << 16) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 36] >>> 28 | (in[inPos + 37] & 255) << 36) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 37] >>> 8 & 17592186044415L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 37] >>> 52 | (in[inPos + 38] & 4294967295L) << 12) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 38] >>> 32 | (in[inPos + 39] & 4095) << 32) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 39] >>> 12 & 17592186044415L) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 39] >>> 56 | (in[inPos + 40] & 68719476735L) << 8) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 40] >>> 36 | (in[inPos + 41] & 65535) << 28) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 41] >>> 16 & 17592186044415L) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 41] >>> 60 | (in[inPos + 42] & 1099511627775L) << 4) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 42] >>> 40 | (in[inPos + 43] & 1048575) << 24) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 43] >>> 20) + out[outPos + 62];
  }

  private static void pack45(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 45;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 19 | (in[inPos + 2] - in[inPos + 1]) << 26;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 38 | (in[inPos + 3] - in[inPos + 2]) << 7 | (in[inPos + 4] - in[inPos + 3]) << 52;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 12 | (in[inPos + 5] - in[inPos + 4]) << 33;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 31 | (in[inPos + 6] - in[inPos + 5]) << 14 | (in[inPos + 7] - in[inPos + 6]) << 59;
    out[outPos + 5] = (in[inPos + 7] - in[inPos + 6]) >>> 5 | (in[inPos + 8] - in[inPos + 7]) << 40;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) >>> 24 | (in[inPos + 9] - in[inPos + 8]) << 21;
    out[outPos + 7] = (in[inPos + 9] - in[inPos + 8]) >>> 43 | (in[inPos + 10] - in[inPos + 9]) << 2 | (in[inPos + 11] - in[inPos + 10]) << 47;
    out[outPos + 8] = (in[inPos + 11] - in[inPos + 10]) >>> 17 | (in[inPos + 12] - in[inPos + 11]) << 28;
    out[outPos + 9] = (in[inPos + 12] - in[inPos + 11]) >>> 36 | (in[inPos + 13] - in[inPos + 12]) << 9 | (in[inPos + 14] - in[inPos + 13]) << 54;
    out[outPos + 10] = (in[inPos + 14] - in[inPos + 13]) >>> 10 | (in[inPos + 15] - in[inPos + 14]) << 35;
    out[outPos + 11] = (in[inPos + 15] - in[inPos + 14]) >>> 29 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 61;
    out[outPos + 12] = (in[inPos + 17] - in[inPos + 16]) >>> 3 | (in[inPos + 18] - in[inPos + 17]) << 42;
    out[outPos + 13] = (in[inPos + 18] - in[inPos + 17]) >>> 22 | (in[inPos + 19] - in[inPos + 18]) << 23;
    out[outPos + 14] = (in[inPos + 19] - in[inPos + 18]) >>> 41 | (in[inPos + 20] - in[inPos + 19]) << 4 | (in[inPos + 21] - in[inPos + 20]) << 49;
    out[outPos + 15] = (in[inPos + 21] - in[inPos + 20]) >>> 15 | (in[inPos + 22] - in[inPos + 21]) << 30;
    out[outPos + 16] = (in[inPos + 22] - in[inPos + 21]) >>> 34 | (in[inPos + 23] - in[inPos + 22]) << 11 | (in[inPos + 24] - in[inPos + 23]) << 56;
    out[outPos + 17] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 37;
    out[outPos + 18] = (in[inPos + 25] - in[inPos + 24]) >>> 27 | (in[inPos + 26] - in[inPos + 25]) << 18 | (in[inPos + 27] - in[inPos + 26]) << 63;
    out[outPos + 19] = (in[inPos + 27] - in[inPos + 26]) >>> 1 | (in[inPos + 28] - in[inPos + 27]) << 44;
    out[outPos + 20] = (in[inPos + 28] - in[inPos + 27]) >>> 20 | (in[inPos + 29] - in[inPos + 28]) << 25;
    out[outPos + 21] = (in[inPos + 29] - in[inPos + 28]) >>> 39 | (in[inPos + 30] - in[inPos + 29]) << 6 | (in[inPos + 31] - in[inPos + 30]) << 51;
    out[outPos + 22] = (in[inPos + 31] - in[inPos + 30]) >>> 13 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 23] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 13 | (in[inPos + 34] - in[inPos + 33]) << 58;
    out[outPos + 24] = (in[inPos + 34] - in[inPos + 33]) >>> 6 | (in[inPos + 35] - in[inPos + 34]) << 39;
    out[outPos + 25] = (in[inPos + 35] - in[inPos + 34]) >>> 25 | (in[inPos + 36] - in[inPos + 35]) << 20;
    out[outPos + 26] = (in[inPos + 36] - in[inPos + 35]) >>> 44 | (in[inPos + 37] - in[inPos + 36]) << 1 | (in[inPos + 38] - in[inPos + 37]) << 46;
    out[outPos + 27] = (in[inPos + 38] - in[inPos + 37]) >>> 18 | (in[inPos + 39] - in[inPos + 38]) << 27;
    out[outPos + 28] = (in[inPos + 39] - in[inPos + 38]) >>> 37 | (in[inPos + 40] - in[inPos + 39]) << 8 | (in[inPos + 41] - in[inPos + 40]) << 53;
    out[outPos + 29] = (in[inPos + 41] - in[inPos + 40]) >>> 11 | (in[inPos + 42] - in[inPos + 41]) << 34;
    out[outPos + 30] = (in[inPos + 42] - in[inPos + 41]) >>> 30 | (in[inPos + 43] - in[inPos + 42]) << 15 | (in[inPos + 44] - in[inPos + 43]) << 60;
    out[outPos + 31] = (in[inPos + 44] - in[inPos + 43]) >>> 4 | (in[inPos + 45] - in[inPos + 44]) << 41;
    out[outPos + 32] = (in[inPos + 45] - in[inPos + 44]) >>> 23 | (in[inPos + 46] - in[inPos + 45]) << 22;
    out[outPos + 33] = (in[inPos + 46] - in[inPos + 45]) >>> 42 | (in[inPos + 47] - in[inPos + 46]) << 3 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 34] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 29;
    out[outPos + 35] = (in[inPos + 49] - in[inPos + 48]) >>> 35 | (in[inPos + 50] - in[inPos + 49]) << 10 | (in[inPos + 51] - in[inPos + 50]) << 55;
    out[outPos + 36] = (in[inPos + 51] - in[inPos + 50]) >>> 9 | (in[inPos + 52] - in[inPos + 51]) << 36;
    out[outPos + 37] = (in[inPos + 52] - in[inPos + 51]) >>> 28 | (in[inPos + 53] - in[inPos + 52]) << 17 | (in[inPos + 54] - in[inPos + 53]) << 62;
    out[outPos + 38] = (in[inPos + 54] - in[inPos + 53]) >>> 2 | (in[inPos + 55] - in[inPos + 54]) << 43;
    out[outPos + 39] = (in[inPos + 55] - in[inPos + 54]) >>> 21 | (in[inPos + 56] - in[inPos + 55]) << 24;
    out[outPos + 40] = (in[inPos + 56] - in[inPos + 55]) >>> 40 | (in[inPos + 57] - in[inPos + 56]) << 5 | (in[inPos + 58] - in[inPos + 57]) << 50;
    out[outPos + 41] = (in[inPos + 58] - in[inPos + 57]) >>> 14 | (in[inPos + 59] - in[inPos + 58]) << 31;
    out[outPos + 42] = (in[inPos + 59] - in[inPos + 58]) >>> 33 | (in[inPos + 60] - in[inPos + 59]) << 12 | (in[inPos + 61] - in[inPos + 60]) << 57;
    out[outPos + 43] = (in[inPos + 61] - in[inPos + 60]) >>> 7 | (in[inPos + 62] - in[inPos + 61]) << 38;
    out[outPos + 44] = (in[inPos + 62] - in[inPos + 61]) >>> 26 | (in[inPos + 63] - in[inPos + 62]) << 19;
  }

  private static void unpack45(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 35184372088831L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 45 | (in[inPos + 1] & 67108863) << 19) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 26 | (in[inPos + 2] & 127) << 38) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 7 & 35184372088831L) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 52 | (in[inPos + 3] & 8589934591L) << 12) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 33 | (in[inPos + 4] & 16383) << 31) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 14 & 35184372088831L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 59 | (in[inPos + 5] & 1099511627775L) << 5) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 40 | (in[inPos + 6] & 2097151) << 24) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 21 | (in[inPos + 7] & 3) << 43) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 2 & 35184372088831L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 47 | (in[inPos + 8] & 268435455) << 17) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 8] >>> 28 | (in[inPos + 9] & 511) << 36) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 9] >>> 9 & 35184372088831L) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 34359738367L) << 10) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 10] >>> 35 | (in[inPos + 11] & 65535) << 29) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 11] >>> 16 & 35184372088831L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 11] >>> 61 | (in[inPos + 12] & 4398046511103L) << 3) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 12] >>> 42 | (in[inPos + 13] & 8388607) << 22) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 13] >>> 23 | (in[inPos + 14] & 15) << 41) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 14] >>> 4 & 35184372088831L) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 14] >>> 49 | (in[inPos + 15] & 1073741823) << 15) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 15] >>> 30 | (in[inPos + 16] & 2047) << 34) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 16] >>> 11 & 35184372088831L) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 137438953471L) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 17] >>> 37 | (in[inPos + 18] & 262143) << 27) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 18] >>> 18 & 35184372088831L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 18] >>> 63 | (in[inPos + 19] & 17592186044415L) << 1) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 33554431) << 20) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 20] >>> 25 | (in[inPos + 21] & 63) << 39) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 21] >>> 6 & 35184372088831L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 21] >>> 51 | (in[inPos + 22] & 4294967295L) << 13) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 22] >>> 32 | (in[inPos + 23] & 8191) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 23] >>> 13 & 35184372088831L) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 23] >>> 58 | (in[inPos + 24] & 549755813887L) << 6) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 24] >>> 39 | (in[inPos + 25] & 1048575) << 25) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 25] >>> 20 | (in[inPos + 26] & 1) << 44) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 26] >>> 1 & 35184372088831L) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 26] >>> 46 | (in[inPos + 27] & 134217727) << 18) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 27] >>> 27 | (in[inPos + 28] & 255) << 37) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 28] >>> 8 & 35184372088831L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 28] >>> 53 | (in[inPos + 29] & 17179869183L) << 11) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 29] >>> 34 | (in[inPos + 30] & 32767) << 30) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 30] >>> 15 & 35184372088831L) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 30] >>> 60 | (in[inPos + 31] & 2199023255551L) << 4) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 31] >>> 41 | (in[inPos + 32] & 4194303) << 23) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 32] >>> 22 | (in[inPos + 33] & 7) << 42) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 33] >>> 3 & 35184372088831L) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 33] >>> 48 | (in[inPos + 34] & 536870911) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 34] >>> 29 | (in[inPos + 35] & 1023) << 35) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 35] >>> 10 & 35184372088831L) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 35] >>> 55 | (in[inPos + 36] & 68719476735L) << 9) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 36] >>> 36 | (in[inPos + 37] & 131071) << 28) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 37] >>> 17 & 35184372088831L) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 37] >>> 62 | (in[inPos + 38] & 8796093022207L) << 2) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 38] >>> 43 | (in[inPos + 39] & 16777215) << 21) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 39] >>> 24 | (in[inPos + 40] & 31) << 40) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 40] >>> 5 & 35184372088831L) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 40] >>> 50 | (in[inPos + 41] & 2147483647) << 14) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 41] >>> 31 | (in[inPos + 42] & 4095) << 33) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 42] >>> 12 & 35184372088831L) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 42] >>> 57 | (in[inPos + 43] & 274877906943L) << 7) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 43] >>> 38 | (in[inPos + 44] & 524287) << 26) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 44] >>> 19) + out[outPos + 62];
  }

  private static void pack46(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 46;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 18 | (in[inPos + 2] - in[inPos + 1]) << 28;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 36 | (in[inPos + 3] - in[inPos + 2]) << 10 | (in[inPos + 4] - in[inPos + 3]) << 56;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 8 | (in[inPos + 5] - in[inPos + 4]) << 38;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 26 | (in[inPos + 6] - in[inPos + 5]) << 20;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 44 | (in[inPos + 7] - in[inPos + 6]) << 2 | (in[inPos + 8] - in[inPos + 7]) << 48;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 30;
    out[outPos + 7] = (in[inPos + 9] - in[inPos + 8]) >>> 34 | (in[inPos + 10] - in[inPos + 9]) << 12 | (in[inPos + 11] - in[inPos + 10]) << 58;
    out[outPos + 8] = (in[inPos + 11] - in[inPos + 10]) >>> 6 | (in[inPos + 12] - in[inPos + 11]) << 40;
    out[outPos + 9] = (in[inPos + 12] - in[inPos + 11]) >>> 24 | (in[inPos + 13] - in[inPos + 12]) << 22;
    out[outPos + 10] = (in[inPos + 13] - in[inPos + 12]) >>> 42 | (in[inPos + 14] - in[inPos + 13]) << 4 | (in[inPos + 15] - in[inPos + 14]) << 50;
    out[outPos + 11] = (in[inPos + 15] - in[inPos + 14]) >>> 14 | (in[inPos + 16] - in[inPos + 15]) << 32;
    out[outPos + 12] = (in[inPos + 16] - in[inPos + 15]) >>> 32 | (in[inPos + 17] - in[inPos + 16]) << 14 | (in[inPos + 18] - in[inPos + 17]) << 60;
    out[outPos + 13] = (in[inPos + 18] - in[inPos + 17]) >>> 4 | (in[inPos + 19] - in[inPos + 18]) << 42;
    out[outPos + 14] = (in[inPos + 19] - in[inPos + 18]) >>> 22 | (in[inPos + 20] - in[inPos + 19]) << 24;
    out[outPos + 15] = (in[inPos + 20] - in[inPos + 19]) >>> 40 | (in[inPos + 21] - in[inPos + 20]) << 6 | (in[inPos + 22] - in[inPos + 21]) << 52;
    out[outPos + 16] = (in[inPos + 22] - in[inPos + 21]) >>> 12 | (in[inPos + 23] - in[inPos + 22]) << 34;
    out[outPos + 17] = (in[inPos + 23] - in[inPos + 22]) >>> 30 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 62;
    out[outPos + 18] = (in[inPos + 25] - in[inPos + 24]) >>> 2 | (in[inPos + 26] - in[inPos + 25]) << 44;
    out[outPos + 19] = (in[inPos + 26] - in[inPos + 25]) >>> 20 | (in[inPos + 27] - in[inPos + 26]) << 26;
    out[outPos + 20] = (in[inPos + 27] - in[inPos + 26]) >>> 38 | (in[inPos + 28] - in[inPos + 27]) << 8 | (in[inPos + 29] - in[inPos + 28]) << 54;
    out[outPos + 21] = (in[inPos + 29] - in[inPos + 28]) >>> 10 | (in[inPos + 30] - in[inPos + 29]) << 36;
    out[outPos + 22] = (in[inPos + 30] - in[inPos + 29]) >>> 28 | (in[inPos + 31] - in[inPos + 30]) << 18;
    out[outPos + 23] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 46;
    out[outPos + 24] = (in[inPos + 33] - in[inPos + 32]) >>> 18 | (in[inPos + 34] - in[inPos + 33]) << 28;
    out[outPos + 25] = (in[inPos + 34] - in[inPos + 33]) >>> 36 | (in[inPos + 35] - in[inPos + 34]) << 10 | (in[inPos + 36] - in[inPos + 35]) << 56;
    out[outPos + 26] = (in[inPos + 36] - in[inPos + 35]) >>> 8 | (in[inPos + 37] - in[inPos + 36]) << 38;
    out[outPos + 27] = (in[inPos + 37] - in[inPos + 36]) >>> 26 | (in[inPos + 38] - in[inPos + 37]) << 20;
    out[outPos + 28] = (in[inPos + 38] - in[inPos + 37]) >>> 44 | (in[inPos + 39] - in[inPos + 38]) << 2 | (in[inPos + 40] - in[inPos + 39]) << 48;
    out[outPos + 29] = (in[inPos + 40] - in[inPos + 39]) >>> 16 | (in[inPos + 41] - in[inPos + 40]) << 30;
    out[outPos + 30] = (in[inPos + 41] - in[inPos + 40]) >>> 34 | (in[inPos + 42] - in[inPos + 41]) << 12 | (in[inPos + 43] - in[inPos + 42]) << 58;
    out[outPos + 31] = (in[inPos + 43] - in[inPos + 42]) >>> 6 | (in[inPos + 44] - in[inPos + 43]) << 40;
    out[outPos + 32] = (in[inPos + 44] - in[inPos + 43]) >>> 24 | (in[inPos + 45] - in[inPos + 44]) << 22;
    out[outPos + 33] = (in[inPos + 45] - in[inPos + 44]) >>> 42 | (in[inPos + 46] - in[inPos + 45]) << 4 | (in[inPos + 47] - in[inPos + 46]) << 50;
    out[outPos + 34] = (in[inPos + 47] - in[inPos + 46]) >>> 14 | (in[inPos + 48] - in[inPos + 47]) << 32;
    out[outPos + 35] = (in[inPos + 48] - in[inPos + 47]) >>> 32 | (in[inPos + 49] - in[inPos + 48]) << 14 | (in[inPos + 50] - in[inPos + 49]) << 60;
    out[outPos + 36] = (in[inPos + 50] - in[inPos + 49]) >>> 4 | (in[inPos + 51] - in[inPos + 50]) << 42;
    out[outPos + 37] = (in[inPos + 51] - in[inPos + 50]) >>> 22 | (in[inPos + 52] - in[inPos + 51]) << 24;
    out[outPos + 38] = (in[inPos + 52] - in[inPos + 51]) >>> 40 | (in[inPos + 53] - in[inPos + 52]) << 6 | (in[inPos + 54] - in[inPos + 53]) << 52;
    out[outPos + 39] = (in[inPos + 54] - in[inPos + 53]) >>> 12 | (in[inPos + 55] - in[inPos + 54]) << 34;
    out[outPos + 40] = (in[inPos + 55] - in[inPos + 54]) >>> 30 | (in[inPos + 56] - in[inPos + 55]) << 16 | (in[inPos + 57] - in[inPos + 56]) << 62;
    out[outPos + 41] = (in[inPos + 57] - in[inPos + 56]) >>> 2 | (in[inPos + 58] - in[inPos + 57]) << 44;
    out[outPos + 42] = (in[inPos + 58] - in[inPos + 57]) >>> 20 | (in[inPos + 59] - in[inPos + 58]) << 26;
    out[outPos + 43] = (in[inPos + 59] - in[inPos + 58]) >>> 38 | (in[inPos + 60] - in[inPos + 59]) << 8 | (in[inPos + 61] - in[inPos + 60]) << 54;
    out[outPos + 44] = (in[inPos + 61] - in[inPos + 60]) >>> 10 | (in[inPos + 62] - in[inPos + 61]) << 36;
    out[outPos + 45] = (in[inPos + 62] - in[inPos + 61]) >>> 28 | (in[inPos + 63] - in[inPos + 62]) << 18;
  }

  private static void unpack46(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 70368744177663L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 46 | (in[inPos + 1] & 268435455) << 18) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 28 | (in[inPos + 2] & 1023) << 36) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 10 & 70368744177663L) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 56 | (in[inPos + 3] & 274877906943L) << 8) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 38 | (in[inPos + 4] & 1048575) << 26) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 20 | (in[inPos + 5] & 3) << 44) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 2 & 70368744177663L) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 48 | (in[inPos + 6] & 1073741823) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 30 | (in[inPos + 7] & 4095) << 34) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 12 & 70368744177663L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 58 | (in[inPos + 8] & 1099511627775L) << 6) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 8] >>> 40 | (in[inPos + 9] & 4194303) << 24) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 9] >>> 22 | (in[inPos + 10] & 15) << 42) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 10] >>> 4 & 70368744177663L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 10] >>> 50 | (in[inPos + 11] & 4294967295L) << 14) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 11] >>> 32 | (in[inPos + 12] & 16383) << 32) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 12] >>> 14 & 70368744177663L) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 12] >>> 60 | (in[inPos + 13] & 4398046511103L) << 4) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 13] >>> 42 | (in[inPos + 14] & 16777215) << 22) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 14] >>> 24 | (in[inPos + 15] & 63) << 40) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 15] >>> 6 & 70368744177663L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 15] >>> 52 | (in[inPos + 16] & 17179869183L) << 12) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 16] >>> 34 | (in[inPos + 17] & 65535) << 30) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 17] >>> 16 & 70368744177663L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 17] >>> 62 | (in[inPos + 18] & 17592186044415L) << 2) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 18] >>> 44 | (in[inPos + 19] & 67108863) << 20) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 19] >>> 26 | (in[inPos + 20] & 255) << 38) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 20] >>> 8 & 70368744177663L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 20] >>> 54 | (in[inPos + 21] & 68719476735L) << 10) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 21] >>> 36 | (in[inPos + 22] & 262143) << 28) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 22] >>> 18) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 23] & 70368744177663L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 23] >>> 46 | (in[inPos + 24] & 268435455) << 18) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 24] >>> 28 | (in[inPos + 25] & 1023) << 36) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 25] >>> 10 & 70368744177663L) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 25] >>> 56 | (in[inPos + 26] & 274877906943L) << 8) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 26] >>> 38 | (in[inPos + 27] & 1048575) << 26) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 27] >>> 20 | (in[inPos + 28] & 3) << 44) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 28] >>> 2 & 70368744177663L) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 28] >>> 48 | (in[inPos + 29] & 1073741823) << 16) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 29] >>> 30 | (in[inPos + 30] & 4095) << 34) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 30] >>> 12 & 70368744177663L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 30] >>> 58 | (in[inPos + 31] & 1099511627775L) << 6) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 31] >>> 40 | (in[inPos + 32] & 4194303) << 24) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 32] >>> 22 | (in[inPos + 33] & 15) << 42) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 33] >>> 4 & 70368744177663L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 33] >>> 50 | (in[inPos + 34] & 4294967295L) << 14) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 34] >>> 32 | (in[inPos + 35] & 16383) << 32) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 35] >>> 14 & 70368744177663L) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 35] >>> 60 | (in[inPos + 36] & 4398046511103L) << 4) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 36] >>> 42 | (in[inPos + 37] & 16777215) << 22) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 37] >>> 24 | (in[inPos + 38] & 63) << 40) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 38] >>> 6 & 70368744177663L) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 38] >>> 52 | (in[inPos + 39] & 17179869183L) << 12) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 39] >>> 34 | (in[inPos + 40] & 65535) << 30) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 40] >>> 16 & 70368744177663L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 40] >>> 62 | (in[inPos + 41] & 17592186044415L) << 2) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 41] >>> 44 | (in[inPos + 42] & 67108863) << 20) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 42] >>> 26 | (in[inPos + 43] & 255) << 38) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 43] >>> 8 & 70368744177663L) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 43] >>> 54 | (in[inPos + 44] & 68719476735L) << 10) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 44] >>> 36 | (in[inPos + 45] & 262143) << 28) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 45] >>> 18) + out[outPos + 62];
  }

  private static void pack47(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 47;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 17 | (in[inPos + 2] - in[inPos + 1]) << 30;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 34 | (in[inPos + 3] - in[inPos + 2]) << 13 | (in[inPos + 4] - in[inPos + 3]) << 60;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 4 | (in[inPos + 5] - in[inPos + 4]) << 43;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 21 | (in[inPos + 6] - in[inPos + 5]) << 26;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 38 | (in[inPos + 7] - in[inPos + 6]) << 9 | (in[inPos + 8] - in[inPos + 7]) << 56;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 39;
    out[outPos + 7] = (in[inPos + 9] - in[inPos + 8]) >>> 25 | (in[inPos + 10] - in[inPos + 9]) << 22;
    out[outPos + 8] = (in[inPos + 10] - in[inPos + 9]) >>> 42 | (in[inPos + 11] - in[inPos + 10]) << 5 | (in[inPos + 12] - in[inPos + 11]) << 52;
    out[outPos + 9] = (in[inPos + 12] - in[inPos + 11]) >>> 12 | (in[inPos + 13] - in[inPos + 12]) << 35;
    out[outPos + 10] = (in[inPos + 13] - in[inPos + 12]) >>> 29 | (in[inPos + 14] - in[inPos + 13]) << 18;
    out[outPos + 11] = (in[inPos + 14] - in[inPos + 13]) >>> 46 | (in[inPos + 15] - in[inPos + 14]) << 1 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 12] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 31;
    out[outPos + 13] = (in[inPos + 17] - in[inPos + 16]) >>> 33 | (in[inPos + 18] - in[inPos + 17]) << 14 | (in[inPos + 19] - in[inPos + 18]) << 61;
    out[outPos + 14] = (in[inPos + 19] - in[inPos + 18]) >>> 3 | (in[inPos + 20] - in[inPos + 19]) << 44;
    out[outPos + 15] = (in[inPos + 20] - in[inPos + 19]) >>> 20 | (in[inPos + 21] - in[inPos + 20]) << 27;
    out[outPos + 16] = (in[inPos + 21] - in[inPos + 20]) >>> 37 | (in[inPos + 22] - in[inPos + 21]) << 10 | (in[inPos + 23] - in[inPos + 22]) << 57;
    out[outPos + 17] = (in[inPos + 23] - in[inPos + 22]) >>> 7 | (in[inPos + 24] - in[inPos + 23]) << 40;
    out[outPos + 18] = (in[inPos + 24] - in[inPos + 23]) >>> 24 | (in[inPos + 25] - in[inPos + 24]) << 23;
    out[outPos + 19] = (in[inPos + 25] - in[inPos + 24]) >>> 41 | (in[inPos + 26] - in[inPos + 25]) << 6 | (in[inPos + 27] - in[inPos + 26]) << 53;
    out[outPos + 20] = (in[inPos + 27] - in[inPos + 26]) >>> 11 | (in[inPos + 28] - in[inPos + 27]) << 36;
    out[outPos + 21] = (in[inPos + 28] - in[inPos + 27]) >>> 28 | (in[inPos + 29] - in[inPos + 28]) << 19;
    out[outPos + 22] = (in[inPos + 29] - in[inPos + 28]) >>> 45 | (in[inPos + 30] - in[inPos + 29]) << 2 | (in[inPos + 31] - in[inPos + 30]) << 49;
    out[outPos + 23] = (in[inPos + 31] - in[inPos + 30]) >>> 15 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 24] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 15 | (in[inPos + 34] - in[inPos + 33]) << 62;
    out[outPos + 25] = (in[inPos + 34] - in[inPos + 33]) >>> 2 | (in[inPos + 35] - in[inPos + 34]) << 45;
    out[outPos + 26] = (in[inPos + 35] - in[inPos + 34]) >>> 19 | (in[inPos + 36] - in[inPos + 35]) << 28;
    out[outPos + 27] = (in[inPos + 36] - in[inPos + 35]) >>> 36 | (in[inPos + 37] - in[inPos + 36]) << 11 | (in[inPos + 38] - in[inPos + 37]) << 58;
    out[outPos + 28] = (in[inPos + 38] - in[inPos + 37]) >>> 6 | (in[inPos + 39] - in[inPos + 38]) << 41;
    out[outPos + 29] = (in[inPos + 39] - in[inPos + 38]) >>> 23 | (in[inPos + 40] - in[inPos + 39]) << 24;
    out[outPos + 30] = (in[inPos + 40] - in[inPos + 39]) >>> 40 | (in[inPos + 41] - in[inPos + 40]) << 7 | (in[inPos + 42] - in[inPos + 41]) << 54;
    out[outPos + 31] = (in[inPos + 42] - in[inPos + 41]) >>> 10 | (in[inPos + 43] - in[inPos + 42]) << 37;
    out[outPos + 32] = (in[inPos + 43] - in[inPos + 42]) >>> 27 | (in[inPos + 44] - in[inPos + 43]) << 20;
    out[outPos + 33] = (in[inPos + 44] - in[inPos + 43]) >>> 44 | (in[inPos + 45] - in[inPos + 44]) << 3 | (in[inPos + 46] - in[inPos + 45]) << 50;
    out[outPos + 34] = (in[inPos + 46] - in[inPos + 45]) >>> 14 | (in[inPos + 47] - in[inPos + 46]) << 33;
    out[outPos + 35] = (in[inPos + 47] - in[inPos + 46]) >>> 31 | (in[inPos + 48] - in[inPos + 47]) << 16 | (in[inPos + 49] - in[inPos + 48]) << 63;
    out[outPos + 36] = (in[inPos + 49] - in[inPos + 48]) >>> 1 | (in[inPos + 50] - in[inPos + 49]) << 46;
    out[outPos + 37] = (in[inPos + 50] - in[inPos + 49]) >>> 18 | (in[inPos + 51] - in[inPos + 50]) << 29;
    out[outPos + 38] = (in[inPos + 51] - in[inPos + 50]) >>> 35 | (in[inPos + 52] - in[inPos + 51]) << 12 | (in[inPos + 53] - in[inPos + 52]) << 59;
    out[outPos + 39] = (in[inPos + 53] - in[inPos + 52]) >>> 5 | (in[inPos + 54] - in[inPos + 53]) << 42;
    out[outPos + 40] = (in[inPos + 54] - in[inPos + 53]) >>> 22 | (in[inPos + 55] - in[inPos + 54]) << 25;
    out[outPos + 41] = (in[inPos + 55] - in[inPos + 54]) >>> 39 | (in[inPos + 56] - in[inPos + 55]) << 8 | (in[inPos + 57] - in[inPos + 56]) << 55;
    out[outPos + 42] = (in[inPos + 57] - in[inPos + 56]) >>> 9 | (in[inPos + 58] - in[inPos + 57]) << 38;
    out[outPos + 43] = (in[inPos + 58] - in[inPos + 57]) >>> 26 | (in[inPos + 59] - in[inPos + 58]) << 21;
    out[outPos + 44] = (in[inPos + 59] - in[inPos + 58]) >>> 43 | (in[inPos + 60] - in[inPos + 59]) << 4 | (in[inPos + 61] - in[inPos + 60]) << 51;
    out[outPos + 45] = (in[inPos + 61] - in[inPos + 60]) >>> 13 | (in[inPos + 62] - in[inPos + 61]) << 34;
    out[outPos + 46] = (in[inPos + 62] - in[inPos + 61]) >>> 30 | (in[inPos + 63] - in[inPos + 62]) << 17;
  }

  private static void unpack47(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 140737488355327L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 47 | (in[inPos + 1] & 1073741823) << 17) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 30 | (in[inPos + 2] & 8191) << 34) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 13 & 140737488355327L) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 60 | (in[inPos + 3] & 8796093022207L) << 4) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 43 | (in[inPos + 4] & 67108863) << 21) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 26 | (in[inPos + 5] & 511) << 38) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 9 & 140737488355327L) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 56 | (in[inPos + 6] & 549755813887L) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 39 | (in[inPos + 7] & 4194303) << 25) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 22 | (in[inPos + 8] & 31) << 42) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 5 & 140737488355327L) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 8] >>> 52 | (in[inPos + 9] & 34359738367L) << 12) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 9] >>> 35 | (in[inPos + 10] & 262143) << 29) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 10] >>> 18 | (in[inPos + 11] & 1) << 46) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 11] >>> 1 & 140737488355327L) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 11] >>> 48 | (in[inPos + 12] & 2147483647) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 12] >>> 31 | (in[inPos + 13] & 16383) << 33) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 13] >>> 14 & 140737488355327L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 13] >>> 61 | (in[inPos + 14] & 17592186044415L) << 3) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 14] >>> 44 | (in[inPos + 15] & 134217727) << 20) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 15] >>> 27 | (in[inPos + 16] & 1023) << 37) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 16] >>> 10 & 140737488355327L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 16] >>> 57 | (in[inPos + 17] & 1099511627775L) << 7) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 17] >>> 40 | (in[inPos + 18] & 8388607) << 24) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 18] >>> 23 | (in[inPos + 19] & 63) << 41) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 19] >>> 6 & 140737488355327L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 19] >>> 53 | (in[inPos + 20] & 68719476735L) << 11) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 20] >>> 36 | (in[inPos + 21] & 524287) << 28) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 21] >>> 19 | (in[inPos + 22] & 3) << 45) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 22] >>> 2 & 140737488355327L) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 22] >>> 49 | (in[inPos + 23] & 4294967295L) << 15) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 23] >>> 32 | (in[inPos + 24] & 32767) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 24] >>> 15 & 140737488355327L) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 24] >>> 62 | (in[inPos + 25] & 35184372088831L) << 2) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 25] >>> 45 | (in[inPos + 26] & 268435455) << 19) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 26] >>> 28 | (in[inPos + 27] & 2047) << 36) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 27] >>> 11 & 140737488355327L) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 27] >>> 58 | (in[inPos + 28] & 2199023255551L) << 6) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 28] >>> 41 | (in[inPos + 29] & 16777215) << 23) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 29] >>> 24 | (in[inPos + 30] & 127) << 40) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 30] >>> 7 & 140737488355327L) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 30] >>> 54 | (in[inPos + 31] & 137438953471L) << 10) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 31] >>> 37 | (in[inPos + 32] & 1048575) << 27) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 32] >>> 20 | (in[inPos + 33] & 7) << 44) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 33] >>> 3 & 140737488355327L) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 33] >>> 50 | (in[inPos + 34] & 8589934591L) << 14) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 34] >>> 33 | (in[inPos + 35] & 65535) << 31) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 35] >>> 16 & 140737488355327L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 35] >>> 63 | (in[inPos + 36] & 70368744177663L) << 1) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 36] >>> 46 | (in[inPos + 37] & 536870911) << 18) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 37] >>> 29 | (in[inPos + 38] & 4095) << 35) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 38] >>> 12 & 140737488355327L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 38] >>> 59 | (in[inPos + 39] & 4398046511103L) << 5) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 39] >>> 42 | (in[inPos + 40] & 33554431) << 22) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 40] >>> 25 | (in[inPos + 41] & 255) << 39) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 41] >>> 8 & 140737488355327L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 41] >>> 55 | (in[inPos + 42] & 274877906943L) << 9) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 42] >>> 38 | (in[inPos + 43] & 2097151) << 26) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 43] >>> 21 | (in[inPos + 44] & 15) << 43) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 44] >>> 4 & 140737488355327L) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 44] >>> 51 | (in[inPos + 45] & 17179869183L) << 13) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 45] >>> 34 | (in[inPos + 46] & 131071) << 30) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 46] >>> 17) + out[outPos + 62];
  }

  private static void pack48(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 48;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 16 | (in[inPos + 2] - in[inPos + 1]) << 32;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 32 | (in[inPos + 3] - in[inPos + 2]) << 16;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) | (in[inPos + 5] - in[inPos + 4]) << 48;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 16 | (in[inPos + 6] - in[inPos + 5]) << 32;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 32 | (in[inPos + 7] - in[inPos + 6]) << 16;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 48;
    out[outPos + 7] = (in[inPos + 9] - in[inPos + 8]) >>> 16 | (in[inPos + 10] - in[inPos + 9]) << 32;
    out[outPos + 8] = (in[inPos + 10] - in[inPos + 9]) >>> 32 | (in[inPos + 11] - in[inPos + 10]) << 16;
    out[outPos + 9] = (in[inPos + 12] - in[inPos + 11]) | (in[inPos + 13] - in[inPos + 12]) << 48;
    out[outPos + 10] = (in[inPos + 13] - in[inPos + 12]) >>> 16 | (in[inPos + 14] - in[inPos + 13]) << 32;
    out[outPos + 11] = (in[inPos + 14] - in[inPos + 13]) >>> 32 | (in[inPos + 15] - in[inPos + 14]) << 16;
    out[outPos + 12] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 48;
    out[outPos + 13] = (in[inPos + 17] - in[inPos + 16]) >>> 16 | (in[inPos + 18] - in[inPos + 17]) << 32;
    out[outPos + 14] = (in[inPos + 18] - in[inPos + 17]) >>> 32 | (in[inPos + 19] - in[inPos + 18]) << 16;
    out[outPos + 15] = (in[inPos + 20] - in[inPos + 19]) | (in[inPos + 21] - in[inPos + 20]) << 48;
    out[outPos + 16] = (in[inPos + 21] - in[inPos + 20]) >>> 16 | (in[inPos + 22] - in[inPos + 21]) << 32;
    out[outPos + 17] = (in[inPos + 22] - in[inPos + 21]) >>> 32 | (in[inPos + 23] - in[inPos + 22]) << 16;
    out[outPos + 18] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 48;
    out[outPos + 19] = (in[inPos + 25] - in[inPos + 24]) >>> 16 | (in[inPos + 26] - in[inPos + 25]) << 32;
    out[outPos + 20] = (in[inPos + 26] - in[inPos + 25]) >>> 32 | (in[inPos + 27] - in[inPos + 26]) << 16;
    out[outPos + 21] = (in[inPos + 28] - in[inPos + 27]) | (in[inPos + 29] - in[inPos + 28]) << 48;
    out[outPos + 22] = (in[inPos + 29] - in[inPos + 28]) >>> 16 | (in[inPos + 30] - in[inPos + 29]) << 32;
    out[outPos + 23] = (in[inPos + 30] - in[inPos + 29]) >>> 32 | (in[inPos + 31] - in[inPos + 30]) << 16;
    out[outPos + 24] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 48;
    out[outPos + 25] = (in[inPos + 33] - in[inPos + 32]) >>> 16 | (in[inPos + 34] - in[inPos + 33]) << 32;
    out[outPos + 26] = (in[inPos + 34] - in[inPos + 33]) >>> 32 | (in[inPos + 35] - in[inPos + 34]) << 16;
    out[outPos + 27] = (in[inPos + 36] - in[inPos + 35]) | (in[inPos + 37] - in[inPos + 36]) << 48;
    out[outPos + 28] = (in[inPos + 37] - in[inPos + 36]) >>> 16 | (in[inPos + 38] - in[inPos + 37]) << 32;
    out[outPos + 29] = (in[inPos + 38] - in[inPos + 37]) >>> 32 | (in[inPos + 39] - in[inPos + 38]) << 16;
    out[outPos + 30] = (in[inPos + 40] - in[inPos + 39]) | (in[inPos + 41] - in[inPos + 40]) << 48;
    out[outPos + 31] = (in[inPos + 41] - in[inPos + 40]) >>> 16 | (in[inPos + 42] - in[inPos + 41]) << 32;
    out[outPos + 32] = (in[inPos + 42] - in[inPos + 41]) >>> 32 | (in[inPos + 43] - in[inPos + 42]) << 16;
    out[outPos + 33] = (in[inPos + 44] - in[inPos + 43]) | (in[inPos + 45] - in[inPos + 44]) << 48;
    out[outPos + 34] = (in[inPos + 45] - in[inPos + 44]) >>> 16 | (in[inPos + 46] - in[inPos + 45]) << 32;
    out[outPos + 35] = (in[inPos + 46] - in[inPos + 45]) >>> 32 | (in[inPos + 47] - in[inPos + 46]) << 16;
    out[outPos + 36] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 48;
    out[outPos + 37] = (in[inPos + 49] - in[inPos + 48]) >>> 16 | (in[inPos + 50] - in[inPos + 49]) << 32;
    out[outPos + 38] = (in[inPos + 50] - in[inPos + 49]) >>> 32 | (in[inPos + 51] - in[inPos + 50]) << 16;
    out[outPos + 39] = (in[inPos + 52] - in[inPos + 51]) | (in[inPos + 53] - in[inPos + 52]) << 48;
    out[outPos + 40] = (in[inPos + 53] - in[inPos + 52]) >>> 16 | (in[inPos + 54] - in[inPos + 53]) << 32;
    out[outPos + 41] = (in[inPos + 54] - in[inPos + 53]) >>> 32 | (in[inPos + 55] - in[inPos + 54]) << 16;
    out[outPos + 42] = (in[inPos + 56] - in[inPos + 55]) | (in[inPos + 57] - in[inPos + 56]) << 48;
    out[outPos + 43] = (in[inPos + 57] - in[inPos + 56]) >>> 16 | (in[inPos + 58] - in[inPos + 57]) << 32;
    out[outPos + 44] = (in[inPos + 58] - in[inPos + 57]) >>> 32 | (in[inPos + 59] - in[inPos + 58]) << 16;
    out[outPos + 45] = (in[inPos + 60] - in[inPos + 59]) | (in[inPos + 61] - in[inPos + 60]) << 48;
    out[outPos + 46] = (in[inPos + 61] - in[inPos + 60]) >>> 16 | (in[inPos + 62] - in[inPos + 61]) << 32;
    out[outPos + 47] = (in[inPos + 62] - in[inPos + 61]) >>> 32 | (in[inPos + 63] - in[inPos + 62]) << 16;
  }

  private static void unpack48(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 281474976710655L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 48 | (in[inPos + 1] & 4294967295L) << 16) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 32 | (in[inPos + 2] & 65535) << 32) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 16) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] & 281474976710655L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 48 | (in[inPos + 4] & 4294967295L) << 16) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 32 | (in[inPos + 5] & 65535) << 32) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 16) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] & 281474976710655L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 48 | (in[inPos + 7] & 4294967295L) << 16) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 32 | (in[inPos + 8] & 65535) << 32) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 16) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] & 281474976710655L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 9] >>> 48 | (in[inPos + 10] & 4294967295L) << 16) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 10] >>> 32 | (in[inPos + 11] & 65535) << 32) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 11] >>> 16) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 12] & 281474976710655L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 12] >>> 48 | (in[inPos + 13] & 4294967295L) << 16) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 13] >>> 32 | (in[inPos + 14] & 65535) << 32) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 14] >>> 16) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 15] & 281474976710655L) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 4294967295L) << 16) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 16] >>> 32 | (in[inPos + 17] & 65535) << 32) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 17] >>> 16) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 18] & 281474976710655L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 18] >>> 48 | (in[inPos + 19] & 4294967295L) << 16) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 19] >>> 32 | (in[inPos + 20] & 65535) << 32) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 20] >>> 16) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 21] & 281474976710655L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 21] >>> 48 | (in[inPos + 22] & 4294967295L) << 16) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 22] >>> 32 | (in[inPos + 23] & 65535) << 32) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 23] >>> 16) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 24] & 281474976710655L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 24] >>> 48 | (in[inPos + 25] & 4294967295L) << 16) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 25] >>> 32 | (in[inPos + 26] & 65535) << 32) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 26] >>> 16) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 27] & 281474976710655L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 27] >>> 48 | (in[inPos + 28] & 4294967295L) << 16) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 28] >>> 32 | (in[inPos + 29] & 65535) << 32) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 29] >>> 16) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 30] & 281474976710655L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 30] >>> 48 | (in[inPos + 31] & 4294967295L) << 16) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 31] >>> 32 | (in[inPos + 32] & 65535) << 32) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 32] >>> 16) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 33] & 281474976710655L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 33] >>> 48 | (in[inPos + 34] & 4294967295L) << 16) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 34] >>> 32 | (in[inPos + 35] & 65535) << 32) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 35] >>> 16) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 36] & 281474976710655L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 36] >>> 48 | (in[inPos + 37] & 4294967295L) << 16) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 37] >>> 32 | (in[inPos + 38] & 65535) << 32) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 38] >>> 16) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 39] & 281474976710655L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 39] >>> 48 | (in[inPos + 40] & 4294967295L) << 16) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 40] >>> 32 | (in[inPos + 41] & 65535) << 32) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 41] >>> 16) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 42] & 281474976710655L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 42] >>> 48 | (in[inPos + 43] & 4294967295L) << 16) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 43] >>> 32 | (in[inPos + 44] & 65535) << 32) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 44] >>> 16) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 45] & 281474976710655L) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 45] >>> 48 | (in[inPos + 46] & 4294967295L) << 16) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 46] >>> 32 | (in[inPos + 47] & 65535) << 32) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 47] >>> 16) + out[outPos + 62];
  }

  private static void pack49(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 49;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 15 | (in[inPos + 2] - in[inPos + 1]) << 34;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 30 | (in[inPos + 3] - in[inPos + 2]) << 19;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 45 | (in[inPos + 4] - in[inPos + 3]) << 4 | (in[inPos + 5] - in[inPos + 4]) << 53;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 11 | (in[inPos + 6] - in[inPos + 5]) << 38;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 26 | (in[inPos + 7] - in[inPos + 6]) << 23;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 41 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 57;
    out[outPos + 7] = (in[inPos + 9] - in[inPos + 8]) >>> 7 | (in[inPos + 10] - in[inPos + 9]) << 42;
    out[outPos + 8] = (in[inPos + 10] - in[inPos + 9]) >>> 22 | (in[inPos + 11] - in[inPos + 10]) << 27;
    out[outPos + 9] = (in[inPos + 11] - in[inPos + 10]) >>> 37 | (in[inPos + 12] - in[inPos + 11]) << 12 | (in[inPos + 13] - in[inPos + 12]) << 61;
    out[outPos + 10] = (in[inPos + 13] - in[inPos + 12]) >>> 3 | (in[inPos + 14] - in[inPos + 13]) << 46;
    out[outPos + 11] = (in[inPos + 14] - in[inPos + 13]) >>> 18 | (in[inPos + 15] - in[inPos + 14]) << 31;
    out[outPos + 12] = (in[inPos + 15] - in[inPos + 14]) >>> 33 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 13] = (in[inPos + 16] - in[inPos + 15]) >>> 48 | (in[inPos + 17] - in[inPos + 16]) << 1 | (in[inPos + 18] - in[inPos + 17]) << 50;
    out[outPos + 14] = (in[inPos + 18] - in[inPos + 17]) >>> 14 | (in[inPos + 19] - in[inPos + 18]) << 35;
    out[outPos + 15] = (in[inPos + 19] - in[inPos + 18]) >>> 29 | (in[inPos + 20] - in[inPos + 19]) << 20;
    out[outPos + 16] = (in[inPos + 20] - in[inPos + 19]) >>> 44 | (in[inPos + 21] - in[inPos + 20]) << 5 | (in[inPos + 22] - in[inPos + 21]) << 54;
    out[outPos + 17] = (in[inPos + 22] - in[inPos + 21]) >>> 10 | (in[inPos + 23] - in[inPos + 22]) << 39;
    out[outPos + 18] = (in[inPos + 23] - in[inPos + 22]) >>> 25 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 19] = (in[inPos + 24] - in[inPos + 23]) >>> 40 | (in[inPos + 25] - in[inPos + 24]) << 9 | (in[inPos + 26] - in[inPos + 25]) << 58;
    out[outPos + 20] = (in[inPos + 26] - in[inPos + 25]) >>> 6 | (in[inPos + 27] - in[inPos + 26]) << 43;
    out[outPos + 21] = (in[inPos + 27] - in[inPos + 26]) >>> 21 | (in[inPos + 28] - in[inPos + 27]) << 28;
    out[outPos + 22] = (in[inPos + 28] - in[inPos + 27]) >>> 36 | (in[inPos + 29] - in[inPos + 28]) << 13 | (in[inPos + 30] - in[inPos + 29]) << 62;
    out[outPos + 23] = (in[inPos + 30] - in[inPos + 29]) >>> 2 | (in[inPos + 31] - in[inPos + 30]) << 47;
    out[outPos + 24] = (in[inPos + 31] - in[inPos + 30]) >>> 17 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 25] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 17;
    out[outPos + 26] = (in[inPos + 33] - in[inPos + 32]) >>> 47 | (in[inPos + 34] - in[inPos + 33]) << 2 | (in[inPos + 35] - in[inPos + 34]) << 51;
    out[outPos + 27] = (in[inPos + 35] - in[inPos + 34]) >>> 13 | (in[inPos + 36] - in[inPos + 35]) << 36;
    out[outPos + 28] = (in[inPos + 36] - in[inPos + 35]) >>> 28 | (in[inPos + 37] - in[inPos + 36]) << 21;
    out[outPos + 29] = (in[inPos + 37] - in[inPos + 36]) >>> 43 | (in[inPos + 38] - in[inPos + 37]) << 6 | (in[inPos + 39] - in[inPos + 38]) << 55;
    out[outPos + 30] = (in[inPos + 39] - in[inPos + 38]) >>> 9 | (in[inPos + 40] - in[inPos + 39]) << 40;
    out[outPos + 31] = (in[inPos + 40] - in[inPos + 39]) >>> 24 | (in[inPos + 41] - in[inPos + 40]) << 25;
    out[outPos + 32] = (in[inPos + 41] - in[inPos + 40]) >>> 39 | (in[inPos + 42] - in[inPos + 41]) << 10 | (in[inPos + 43] - in[inPos + 42]) << 59;
    out[outPos + 33] = (in[inPos + 43] - in[inPos + 42]) >>> 5 | (in[inPos + 44] - in[inPos + 43]) << 44;
    out[outPos + 34] = (in[inPos + 44] - in[inPos + 43]) >>> 20 | (in[inPos + 45] - in[inPos + 44]) << 29;
    out[outPos + 35] = (in[inPos + 45] - in[inPos + 44]) >>> 35 | (in[inPos + 46] - in[inPos + 45]) << 14 | (in[inPos + 47] - in[inPos + 46]) << 63;
    out[outPos + 36] = (in[inPos + 47] - in[inPos + 46]) >>> 1 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 37] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 33;
    out[outPos + 38] = (in[inPos + 49] - in[inPos + 48]) >>> 31 | (in[inPos + 50] - in[inPos + 49]) << 18;
    out[outPos + 39] = (in[inPos + 50] - in[inPos + 49]) >>> 46 | (in[inPos + 51] - in[inPos + 50]) << 3 | (in[inPos + 52] - in[inPos + 51]) << 52;
    out[outPos + 40] = (in[inPos + 52] - in[inPos + 51]) >>> 12 | (in[inPos + 53] - in[inPos + 52]) << 37;
    out[outPos + 41] = (in[inPos + 53] - in[inPos + 52]) >>> 27 | (in[inPos + 54] - in[inPos + 53]) << 22;
    out[outPos + 42] = (in[inPos + 54] - in[inPos + 53]) >>> 42 | (in[inPos + 55] - in[inPos + 54]) << 7 | (in[inPos + 56] - in[inPos + 55]) << 56;
    out[outPos + 43] = (in[inPos + 56] - in[inPos + 55]) >>> 8 | (in[inPos + 57] - in[inPos + 56]) << 41;
    out[outPos + 44] = (in[inPos + 57] - in[inPos + 56]) >>> 23 | (in[inPos + 58] - in[inPos + 57]) << 26;
    out[outPos + 45] = (in[inPos + 58] - in[inPos + 57]) >>> 38 | (in[inPos + 59] - in[inPos + 58]) << 11 | (in[inPos + 60] - in[inPos + 59]) << 60;
    out[outPos + 46] = (in[inPos + 60] - in[inPos + 59]) >>> 4 | (in[inPos + 61] - in[inPos + 60]) << 45;
    out[outPos + 47] = (in[inPos + 61] - in[inPos + 60]) >>> 19 | (in[inPos + 62] - in[inPos + 61]) << 30;
    out[outPos + 48] = (in[inPos + 62] - in[inPos + 61]) >>> 34 | (in[inPos + 63] - in[inPos + 62]) << 15;
  }

  private static void unpack49(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 562949953421311L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 49 | (in[inPos + 1] & 17179869183L) << 15) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 34 | (in[inPos + 2] & 524287) << 30) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 19 | (in[inPos + 3] & 15) << 45) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 4 & 562949953421311L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 53 | (in[inPos + 4] & 274877906943L) << 11) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 38 | (in[inPos + 5] & 8388607) << 26) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 23 | (in[inPos + 6] & 255) << 41) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 8 & 562949953421311L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 57 | (in[inPos + 7] & 4398046511103L) << 7) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 42 | (in[inPos + 8] & 134217727) << 22) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 27 | (in[inPos + 9] & 4095) << 37) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] >>> 12 & 562949953421311L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 9] >>> 61 | (in[inPos + 10] & 70368744177663L) << 3) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 10] >>> 46 | (in[inPos + 11] & 2147483647) << 18) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 11] >>> 31 | (in[inPos + 12] & 65535) << 33) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 12] >>> 16 | (in[inPos + 13] & 1) << 48) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 13] >>> 1 & 562949953421311L) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 13] >>> 50 | (in[inPos + 14] & 34359738367L) << 14) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 14] >>> 35 | (in[inPos + 15] & 1048575) << 29) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 15] >>> 20 | (in[inPos + 16] & 31) << 44) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 16] >>> 5 & 562949953421311L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 16] >>> 54 | (in[inPos + 17] & 549755813887L) << 10) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 17] >>> 39 | (in[inPos + 18] & 16777215) << 25) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 18] >>> 24 | (in[inPos + 19] & 511) << 40) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 19] >>> 9 & 562949953421311L) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 19] >>> 58 | (in[inPos + 20] & 8796093022207L) << 6) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 20] >>> 43 | (in[inPos + 21] & 268435455) << 21) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 21] >>> 28 | (in[inPos + 22] & 8191) << 36) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 22] >>> 13 & 562949953421311L) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 22] >>> 62 | (in[inPos + 23] & 140737488355327L) << 2) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 23] >>> 47 | (in[inPos + 24] & 4294967295L) << 17) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 24] >>> 32 | (in[inPos + 25] & 131071) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 25] >>> 17 | (in[inPos + 26] & 3) << 47) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 26] >>> 2 & 562949953421311L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 26] >>> 51 | (in[inPos + 27] & 68719476735L) << 13) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 27] >>> 36 | (in[inPos + 28] & 2097151) << 28) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 28] >>> 21 | (in[inPos + 29] & 63) << 43) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 29] >>> 6 & 562949953421311L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 29] >>> 55 | (in[inPos + 30] & 1099511627775L) << 9) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 30] >>> 40 | (in[inPos + 31] & 33554431) << 24) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 31] >>> 25 | (in[inPos + 32] & 1023) << 39) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 32] >>> 10 & 562949953421311L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 32] >>> 59 | (in[inPos + 33] & 17592186044415L) << 5) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 33] >>> 44 | (in[inPos + 34] & 536870911) << 20) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 34] >>> 29 | (in[inPos + 35] & 16383) << 35) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 35] >>> 14 & 562949953421311L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 35] >>> 63 | (in[inPos + 36] & 281474976710655L) << 1) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 36] >>> 48 | (in[inPos + 37] & 8589934591L) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 37] >>> 33 | (in[inPos + 38] & 262143) << 31) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 38] >>> 18 | (in[inPos + 39] & 7) << 46) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 39] >>> 3 & 562949953421311L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 39] >>> 52 | (in[inPos + 40] & 137438953471L) << 12) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 40] >>> 37 | (in[inPos + 41] & 4194303) << 27) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 41] >>> 22 | (in[inPos + 42] & 127) << 42) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 42] >>> 7 & 562949953421311L) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 42] >>> 56 | (in[inPos + 43] & 2199023255551L) << 8) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 43] >>> 41 | (in[inPos + 44] & 67108863) << 23) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 44] >>> 26 | (in[inPos + 45] & 2047) << 38) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 45] >>> 11 & 562949953421311L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 45] >>> 60 | (in[inPos + 46] & 35184372088831L) << 4) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 46] >>> 45 | (in[inPos + 47] & 1073741823) << 19) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 47] >>> 30 | (in[inPos + 48] & 32767) << 34) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 48] >>> 15) + out[outPos + 62];
  }

  private static void pack50(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 50;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 14 | (in[inPos + 2] - in[inPos + 1]) << 36;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 28 | (in[inPos + 3] - in[inPos + 2]) << 22;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 42 | (in[inPos + 4] - in[inPos + 3]) << 8 | (in[inPos + 5] - in[inPos + 4]) << 58;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 6 | (in[inPos + 6] - in[inPos + 5]) << 44;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 20 | (in[inPos + 7] - in[inPos + 6]) << 30;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 34 | (in[inPos + 8] - in[inPos + 7]) << 16;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 48 | (in[inPos + 9] - in[inPos + 8]) << 2 | (in[inPos + 10] - in[inPos + 9]) << 52;
    out[outPos + 8] = (in[inPos + 10] - in[inPos + 9]) >>> 12 | (in[inPos + 11] - in[inPos + 10]) << 38;
    out[outPos + 9] = (in[inPos + 11] - in[inPos + 10]) >>> 26 | (in[inPos + 12] - in[inPos + 11]) << 24;
    out[outPos + 10] = (in[inPos + 12] - in[inPos + 11]) >>> 40 | (in[inPos + 13] - in[inPos + 12]) << 10 | (in[inPos + 14] - in[inPos + 13]) << 60;
    out[outPos + 11] = (in[inPos + 14] - in[inPos + 13]) >>> 4 | (in[inPos + 15] - in[inPos + 14]) << 46;
    out[outPos + 12] = (in[inPos + 15] - in[inPos + 14]) >>> 18 | (in[inPos + 16] - in[inPos + 15]) << 32;
    out[outPos + 13] = (in[inPos + 16] - in[inPos + 15]) >>> 32 | (in[inPos + 17] - in[inPos + 16]) << 18;
    out[outPos + 14] = (in[inPos + 17] - in[inPos + 16]) >>> 46 | (in[inPos + 18] - in[inPos + 17]) << 4 | (in[inPos + 19] - in[inPos + 18]) << 54;
    out[outPos + 15] = (in[inPos + 19] - in[inPos + 18]) >>> 10 | (in[inPos + 20] - in[inPos + 19]) << 40;
    out[outPos + 16] = (in[inPos + 20] - in[inPos + 19]) >>> 24 | (in[inPos + 21] - in[inPos + 20]) << 26;
    out[outPos + 17] = (in[inPos + 21] - in[inPos + 20]) >>> 38 | (in[inPos + 22] - in[inPos + 21]) << 12 | (in[inPos + 23] - in[inPos + 22]) << 62;
    out[outPos + 18] = (in[inPos + 23] - in[inPos + 22]) >>> 2 | (in[inPos + 24] - in[inPos + 23]) << 48;
    out[outPos + 19] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 34;
    out[outPos + 20] = (in[inPos + 25] - in[inPos + 24]) >>> 30 | (in[inPos + 26] - in[inPos + 25]) << 20;
    out[outPos + 21] = (in[inPos + 26] - in[inPos + 25]) >>> 44 | (in[inPos + 27] - in[inPos + 26]) << 6 | (in[inPos + 28] - in[inPos + 27]) << 56;
    out[outPos + 22] = (in[inPos + 28] - in[inPos + 27]) >>> 8 | (in[inPos + 29] - in[inPos + 28]) << 42;
    out[outPos + 23] = (in[inPos + 29] - in[inPos + 28]) >>> 22 | (in[inPos + 30] - in[inPos + 29]) << 28;
    out[outPos + 24] = (in[inPos + 30] - in[inPos + 29]) >>> 36 | (in[inPos + 31] - in[inPos + 30]) << 14;
    out[outPos + 25] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 50;
    out[outPos + 26] = (in[inPos + 33] - in[inPos + 32]) >>> 14 | (in[inPos + 34] - in[inPos + 33]) << 36;
    out[outPos + 27] = (in[inPos + 34] - in[inPos + 33]) >>> 28 | (in[inPos + 35] - in[inPos + 34]) << 22;
    out[outPos + 28] = (in[inPos + 35] - in[inPos + 34]) >>> 42 | (in[inPos + 36] - in[inPos + 35]) << 8 | (in[inPos + 37] - in[inPos + 36]) << 58;
    out[outPos + 29] = (in[inPos + 37] - in[inPos + 36]) >>> 6 | (in[inPos + 38] - in[inPos + 37]) << 44;
    out[outPos + 30] = (in[inPos + 38] - in[inPos + 37]) >>> 20 | (in[inPos + 39] - in[inPos + 38]) << 30;
    out[outPos + 31] = (in[inPos + 39] - in[inPos + 38]) >>> 34 | (in[inPos + 40] - in[inPos + 39]) << 16;
    out[outPos + 32] = (in[inPos + 40] - in[inPos + 39]) >>> 48 | (in[inPos + 41] - in[inPos + 40]) << 2 | (in[inPos + 42] - in[inPos + 41]) << 52;
    out[outPos + 33] = (in[inPos + 42] - in[inPos + 41]) >>> 12 | (in[inPos + 43] - in[inPos + 42]) << 38;
    out[outPos + 34] = (in[inPos + 43] - in[inPos + 42]) >>> 26 | (in[inPos + 44] - in[inPos + 43]) << 24;
    out[outPos + 35] = (in[inPos + 44] - in[inPos + 43]) >>> 40 | (in[inPos + 45] - in[inPos + 44]) << 10 | (in[inPos + 46] - in[inPos + 45]) << 60;
    out[outPos + 36] = (in[inPos + 46] - in[inPos + 45]) >>> 4 | (in[inPos + 47] - in[inPos + 46]) << 46;
    out[outPos + 37] = (in[inPos + 47] - in[inPos + 46]) >>> 18 | (in[inPos + 48] - in[inPos + 47]) << 32;
    out[outPos + 38] = (in[inPos + 48] - in[inPos + 47]) >>> 32 | (in[inPos + 49] - in[inPos + 48]) << 18;
    out[outPos + 39] = (in[inPos + 49] - in[inPos + 48]) >>> 46 | (in[inPos + 50] - in[inPos + 49]) << 4 | (in[inPos + 51] - in[inPos + 50]) << 54;
    out[outPos + 40] = (in[inPos + 51] - in[inPos + 50]) >>> 10 | (in[inPos + 52] - in[inPos + 51]) << 40;
    out[outPos + 41] = (in[inPos + 52] - in[inPos + 51]) >>> 24 | (in[inPos + 53] - in[inPos + 52]) << 26;
    out[outPos + 42] = (in[inPos + 53] - in[inPos + 52]) >>> 38 | (in[inPos + 54] - in[inPos + 53]) << 12 | (in[inPos + 55] - in[inPos + 54]) << 62;
    out[outPos + 43] = (in[inPos + 55] - in[inPos + 54]) >>> 2 | (in[inPos + 56] - in[inPos + 55]) << 48;
    out[outPos + 44] = (in[inPos + 56] - in[inPos + 55]) >>> 16 | (in[inPos + 57] - in[inPos + 56]) << 34;
    out[outPos + 45] = (in[inPos + 57] - in[inPos + 56]) >>> 30 | (in[inPos + 58] - in[inPos + 57]) << 20;
    out[outPos + 46] = (in[inPos + 58] - in[inPos + 57]) >>> 44 | (in[inPos + 59] - in[inPos + 58]) << 6 | (in[inPos + 60] - in[inPos + 59]) << 56;
    out[outPos + 47] = (in[inPos + 60] - in[inPos + 59]) >>> 8 | (in[inPos + 61] - in[inPos + 60]) << 42;
    out[outPos + 48] = (in[inPos + 61] - in[inPos + 60]) >>> 22 | (in[inPos + 62] - in[inPos + 61]) << 28;
    out[outPos + 49] = (in[inPos + 62] - in[inPos + 61]) >>> 36 | (in[inPos + 63] - in[inPos + 62]) << 14;
  }

  private static void unpack50(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1125899906842623L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 50 | (in[inPos + 1] & 68719476735L) << 14) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 36 | (in[inPos + 2] & 4194303) << 28) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 22 | (in[inPos + 3] & 255) << 42) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 8 & 1125899906842623L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 58 | (in[inPos + 4] & 17592186044415L) << 6) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 44 | (in[inPos + 5] & 1073741823) << 20) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 30 | (in[inPos + 6] & 65535) << 34) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 16 | (in[inPos + 7] & 3) << 48) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 2 & 1125899906842623L) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 52 | (in[inPos + 8] & 274877906943L) << 12) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 38 | (in[inPos + 9] & 16777215) << 26) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] >>> 24 | (in[inPos + 10] & 1023) << 40) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 10] >>> 10 & 1125899906842623L) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 10] >>> 60 | (in[inPos + 11] & 70368744177663L) << 4) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 11] >>> 46 | (in[inPos + 12] & 4294967295L) << 18) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 12] >>> 32 | (in[inPos + 13] & 262143) << 32) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 13] >>> 18 | (in[inPos + 14] & 15) << 46) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 14] >>> 4 & 1125899906842623L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 14] >>> 54 | (in[inPos + 15] & 1099511627775L) << 10) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 15] >>> 40 | (in[inPos + 16] & 67108863) << 24) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 16] >>> 26 | (in[inPos + 17] & 4095) << 38) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 17] >>> 12 & 1125899906842623L) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 17] >>> 62 | (in[inPos + 18] & 281474976710655L) << 2) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 18] >>> 48 | (in[inPos + 19] & 17179869183L) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 19] >>> 34 | (in[inPos + 20] & 1048575) << 30) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 20] >>> 20 | (in[inPos + 21] & 63) << 44) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 21] >>> 6 & 1125899906842623L) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 21] >>> 56 | (in[inPos + 22] & 4398046511103L) << 8) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 22] >>> 42 | (in[inPos + 23] & 268435455) << 22) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 23] >>> 28 | (in[inPos + 24] & 16383) << 36) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 24] >>> 14) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 25] & 1125899906842623L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 25] >>> 50 | (in[inPos + 26] & 68719476735L) << 14) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 26] >>> 36 | (in[inPos + 27] & 4194303) << 28) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 27] >>> 22 | (in[inPos + 28] & 255) << 42) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 28] >>> 8 & 1125899906842623L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 28] >>> 58 | (in[inPos + 29] & 17592186044415L) << 6) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 29] >>> 44 | (in[inPos + 30] & 1073741823) << 20) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 30] >>> 30 | (in[inPos + 31] & 65535) << 34) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 31] >>> 16 | (in[inPos + 32] & 3) << 48) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 32] >>> 2 & 1125899906842623L) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 32] >>> 52 | (in[inPos + 33] & 274877906943L) << 12) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 33] >>> 38 | (in[inPos + 34] & 16777215) << 26) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 34] >>> 24 | (in[inPos + 35] & 1023) << 40) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 35] >>> 10 & 1125899906842623L) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 35] >>> 60 | (in[inPos + 36] & 70368744177663L) << 4) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 36] >>> 46 | (in[inPos + 37] & 4294967295L) << 18) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 37] >>> 32 | (in[inPos + 38] & 262143) << 32) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 38] >>> 18 | (in[inPos + 39] & 15) << 46) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 39] >>> 4 & 1125899906842623L) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 39] >>> 54 | (in[inPos + 40] & 1099511627775L) << 10) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 40] >>> 40 | (in[inPos + 41] & 67108863) << 24) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 41] >>> 26 | (in[inPos + 42] & 4095) << 38) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 42] >>> 12 & 1125899906842623L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 42] >>> 62 | (in[inPos + 43] & 281474976710655L) << 2) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 43] >>> 48 | (in[inPos + 44] & 17179869183L) << 16) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 44] >>> 34 | (in[inPos + 45] & 1048575) << 30) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 45] >>> 20 | (in[inPos + 46] & 63) << 44) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 46] >>> 6 & 1125899906842623L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 46] >>> 56 | (in[inPos + 47] & 4398046511103L) << 8) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 47] >>> 42 | (in[inPos + 48] & 268435455) << 22) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 48] >>> 28 | (in[inPos + 49] & 16383) << 36) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 49] >>> 14) + out[outPos + 62];
  }

  private static void pack51(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 51;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 13 | (in[inPos + 2] - in[inPos + 1]) << 38;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 26 | (in[inPos + 3] - in[inPos + 2]) << 25;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 39 | (in[inPos + 4] - in[inPos + 3]) << 12 | (in[inPos + 5] - in[inPos + 4]) << 63;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 1 | (in[inPos + 6] - in[inPos + 5]) << 50;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 14 | (in[inPos + 7] - in[inPos + 6]) << 37;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 27 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 40 | (in[inPos + 9] - in[inPos + 8]) << 11 | (in[inPos + 10] - in[inPos + 9]) << 62;
    out[outPos + 8] = (in[inPos + 10] - in[inPos + 9]) >>> 2 | (in[inPos + 11] - in[inPos + 10]) << 49;
    out[outPos + 9] = (in[inPos + 11] - in[inPos + 10]) >>> 15 | (in[inPos + 12] - in[inPos + 11]) << 36;
    out[outPos + 10] = (in[inPos + 12] - in[inPos + 11]) >>> 28 | (in[inPos + 13] - in[inPos + 12]) << 23;
    out[outPos + 11] = (in[inPos + 13] - in[inPos + 12]) >>> 41 | (in[inPos + 14] - in[inPos + 13]) << 10 | (in[inPos + 15] - in[inPos + 14]) << 61;
    out[outPos + 12] = (in[inPos + 15] - in[inPos + 14]) >>> 3 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 13] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 35;
    out[outPos + 14] = (in[inPos + 17] - in[inPos + 16]) >>> 29 | (in[inPos + 18] - in[inPos + 17]) << 22;
    out[outPos + 15] = (in[inPos + 18] - in[inPos + 17]) >>> 42 | (in[inPos + 19] - in[inPos + 18]) << 9 | (in[inPos + 20] - in[inPos + 19]) << 60;
    out[outPos + 16] = (in[inPos + 20] - in[inPos + 19]) >>> 4 | (in[inPos + 21] - in[inPos + 20]) << 47;
    out[outPos + 17] = (in[inPos + 21] - in[inPos + 20]) >>> 17 | (in[inPos + 22] - in[inPos + 21]) << 34;
    out[outPos + 18] = (in[inPos + 22] - in[inPos + 21]) >>> 30 | (in[inPos + 23] - in[inPos + 22]) << 21;
    out[outPos + 19] = (in[inPos + 23] - in[inPos + 22]) >>> 43 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 59;
    out[outPos + 20] = (in[inPos + 25] - in[inPos + 24]) >>> 5 | (in[inPos + 26] - in[inPos + 25]) << 46;
    out[outPos + 21] = (in[inPos + 26] - in[inPos + 25]) >>> 18 | (in[inPos + 27] - in[inPos + 26]) << 33;
    out[outPos + 22] = (in[inPos + 27] - in[inPos + 26]) >>> 31 | (in[inPos + 28] - in[inPos + 27]) << 20;
    out[outPos + 23] = (in[inPos + 28] - in[inPos + 27]) >>> 44 | (in[inPos + 29] - in[inPos + 28]) << 7 | (in[inPos + 30] - in[inPos + 29]) << 58;
    out[outPos + 24] = (in[inPos + 30] - in[inPos + 29]) >>> 6 | (in[inPos + 31] - in[inPos + 30]) << 45;
    out[outPos + 25] = (in[inPos + 31] - in[inPos + 30]) >>> 19 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 26] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 19;
    out[outPos + 27] = (in[inPos + 33] - in[inPos + 32]) >>> 45 | (in[inPos + 34] - in[inPos + 33]) << 6 | (in[inPos + 35] - in[inPos + 34]) << 57;
    out[outPos + 28] = (in[inPos + 35] - in[inPos + 34]) >>> 7 | (in[inPos + 36] - in[inPos + 35]) << 44;
    out[outPos + 29] = (in[inPos + 36] - in[inPos + 35]) >>> 20 | (in[inPos + 37] - in[inPos + 36]) << 31;
    out[outPos + 30] = (in[inPos + 37] - in[inPos + 36]) >>> 33 | (in[inPos + 38] - in[inPos + 37]) << 18;
    out[outPos + 31] = (in[inPos + 38] - in[inPos + 37]) >>> 46 | (in[inPos + 39] - in[inPos + 38]) << 5 | (in[inPos + 40] - in[inPos + 39]) << 56;
    out[outPos + 32] = (in[inPos + 40] - in[inPos + 39]) >>> 8 | (in[inPos + 41] - in[inPos + 40]) << 43;
    out[outPos + 33] = (in[inPos + 41] - in[inPos + 40]) >>> 21 | (in[inPos + 42] - in[inPos + 41]) << 30;
    out[outPos + 34] = (in[inPos + 42] - in[inPos + 41]) >>> 34 | (in[inPos + 43] - in[inPos + 42]) << 17;
    out[outPos + 35] = (in[inPos + 43] - in[inPos + 42]) >>> 47 | (in[inPos + 44] - in[inPos + 43]) << 4 | (in[inPos + 45] - in[inPos + 44]) << 55;
    out[outPos + 36] = (in[inPos + 45] - in[inPos + 44]) >>> 9 | (in[inPos + 46] - in[inPos + 45]) << 42;
    out[outPos + 37] = (in[inPos + 46] - in[inPos + 45]) >>> 22 | (in[inPos + 47] - in[inPos + 46]) << 29;
    out[outPos + 38] = (in[inPos + 47] - in[inPos + 46]) >>> 35 | (in[inPos + 48] - in[inPos + 47]) << 16;
    out[outPos + 39] = (in[inPos + 48] - in[inPos + 47]) >>> 48 | (in[inPos + 49] - in[inPos + 48]) << 3 | (in[inPos + 50] - in[inPos + 49]) << 54;
    out[outPos + 40] = (in[inPos + 50] - in[inPos + 49]) >>> 10 | (in[inPos + 51] - in[inPos + 50]) << 41;
    out[outPos + 41] = (in[inPos + 51] - in[inPos + 50]) >>> 23 | (in[inPos + 52] - in[inPos + 51]) << 28;
    out[outPos + 42] = (in[inPos + 52] - in[inPos + 51]) >>> 36 | (in[inPos + 53] - in[inPos + 52]) << 15;
    out[outPos + 43] = (in[inPos + 53] - in[inPos + 52]) >>> 49 | (in[inPos + 54] - in[inPos + 53]) << 2 | (in[inPos + 55] - in[inPos + 54]) << 53;
    out[outPos + 44] = (in[inPos + 55] - in[inPos + 54]) >>> 11 | (in[inPos + 56] - in[inPos + 55]) << 40;
    out[outPos + 45] = (in[inPos + 56] - in[inPos + 55]) >>> 24 | (in[inPos + 57] - in[inPos + 56]) << 27;
    out[outPos + 46] = (in[inPos + 57] - in[inPos + 56]) >>> 37 | (in[inPos + 58] - in[inPos + 57]) << 14;
    out[outPos + 47] = (in[inPos + 58] - in[inPos + 57]) >>> 50 | (in[inPos + 59] - in[inPos + 58]) << 1 | (in[inPos + 60] - in[inPos + 59]) << 52;
    out[outPos + 48] = (in[inPos + 60] - in[inPos + 59]) >>> 12 | (in[inPos + 61] - in[inPos + 60]) << 39;
    out[outPos + 49] = (in[inPos + 61] - in[inPos + 60]) >>> 25 | (in[inPos + 62] - in[inPos + 61]) << 26;
    out[outPos + 50] = (in[inPos + 62] - in[inPos + 61]) >>> 38 | (in[inPos + 63] - in[inPos + 62]) << 13;
  }

  private static void unpack51(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2251799813685247L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 51 | (in[inPos + 1] & 274877906943L) << 13) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 38 | (in[inPos + 2] & 33554431) << 26) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 25 | (in[inPos + 3] & 4095) << 39) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 12 & 2251799813685247L) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 63 | (in[inPos + 4] & 1125899906842623L) << 1) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 50 | (in[inPos + 5] & 137438953471L) << 14) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 37 | (in[inPos + 6] & 16777215) << 27) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 24 | (in[inPos + 7] & 2047) << 40) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 11 & 2251799813685247L) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 62 | (in[inPos + 8] & 562949953421311L) << 2) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 49 | (in[inPos + 9] & 68719476735L) << 15) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] >>> 36 | (in[inPos + 10] & 8388607) << 28) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 10] >>> 23 | (in[inPos + 11] & 1023) << 41) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 11] >>> 10 & 2251799813685247L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 11] >>> 61 | (in[inPos + 12] & 281474976710655L) << 3) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 12] >>> 48 | (in[inPos + 13] & 34359738367L) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 13] >>> 35 | (in[inPos + 14] & 4194303) << 29) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 14] >>> 22 | (in[inPos + 15] & 511) << 42) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 15] >>> 9 & 2251799813685247L) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 15] >>> 60 | (in[inPos + 16] & 140737488355327L) << 4) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 16] >>> 47 | (in[inPos + 17] & 17179869183L) << 17) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 17] >>> 34 | (in[inPos + 18] & 2097151) << 30) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 18] >>> 21 | (in[inPos + 19] & 255) << 43) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 19] >>> 8 & 2251799813685247L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 19] >>> 59 | (in[inPos + 20] & 70368744177663L) << 5) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 20] >>> 46 | (in[inPos + 21] & 8589934591L) << 18) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 21] >>> 33 | (in[inPos + 22] & 1048575) << 31) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 22] >>> 20 | (in[inPos + 23] & 127) << 44) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 23] >>> 7 & 2251799813685247L) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 23] >>> 58 | (in[inPos + 24] & 35184372088831L) << 6) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 24] >>> 45 | (in[inPos + 25] & 4294967295L) << 19) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 25] >>> 32 | (in[inPos + 26] & 524287) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 26] >>> 19 | (in[inPos + 27] & 63) << 45) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 27] >>> 6 & 2251799813685247L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 27] >>> 57 | (in[inPos + 28] & 17592186044415L) << 7) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 28] >>> 44 | (in[inPos + 29] & 2147483647) << 20) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 29] >>> 31 | (in[inPos + 30] & 262143) << 33) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 30] >>> 18 | (in[inPos + 31] & 31) << 46) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 31] >>> 5 & 2251799813685247L) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 31] >>> 56 | (in[inPos + 32] & 8796093022207L) << 8) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 32] >>> 43 | (in[inPos + 33] & 1073741823) << 21) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 33] >>> 30 | (in[inPos + 34] & 131071) << 34) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 34] >>> 17 | (in[inPos + 35] & 15) << 47) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 35] >>> 4 & 2251799813685247L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 35] >>> 55 | (in[inPos + 36] & 4398046511103L) << 9) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 36] >>> 42 | (in[inPos + 37] & 536870911) << 22) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 37] >>> 29 | (in[inPos + 38] & 65535) << 35) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 38] >>> 16 | (in[inPos + 39] & 7) << 48) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 39] >>> 3 & 2251799813685247L) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 39] >>> 54 | (in[inPos + 40] & 2199023255551L) << 10) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 40] >>> 41 | (in[inPos + 41] & 268435455) << 23) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 41] >>> 28 | (in[inPos + 42] & 32767) << 36) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 42] >>> 15 | (in[inPos + 43] & 3) << 49) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 43] >>> 2 & 2251799813685247L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 43] >>> 53 | (in[inPos + 44] & 1099511627775L) << 11) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 44] >>> 40 | (in[inPos + 45] & 134217727) << 24) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 45] >>> 27 | (in[inPos + 46] & 16383) << 37) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 46] >>> 14 | (in[inPos + 47] & 1) << 50) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 47] >>> 1 & 2251799813685247L) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 47] >>> 52 | (in[inPos + 48] & 549755813887L) << 12) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 48] >>> 39 | (in[inPos + 49] & 67108863) << 25) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 49] >>> 26 | (in[inPos + 50] & 8191) << 38) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 50] >>> 13) + out[outPos + 62];
  }

  private static void pack52(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 52;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 12 | (in[inPos + 2] - in[inPos + 1]) << 40;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 24 | (in[inPos + 3] - in[inPos + 2]) << 28;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 36 | (in[inPos + 4] - in[inPos + 3]) << 16;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 48 | (in[inPos + 5] - in[inPos + 4]) << 4 | (in[inPos + 6] - in[inPos + 5]) << 56;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 8 | (in[inPos + 7] - in[inPos + 6]) << 44;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 20 | (in[inPos + 8] - in[inPos + 7]) << 32;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 32 | (in[inPos + 9] - in[inPos + 8]) << 20;
    out[outPos + 8] = (in[inPos + 9] - in[inPos + 8]) >>> 44 | (in[inPos + 10] - in[inPos + 9]) << 8 | (in[inPos + 11] - in[inPos + 10]) << 60;
    out[outPos + 9] = (in[inPos + 11] - in[inPos + 10]) >>> 4 | (in[inPos + 12] - in[inPos + 11]) << 48;
    out[outPos + 10] = (in[inPos + 12] - in[inPos + 11]) >>> 16 | (in[inPos + 13] - in[inPos + 12]) << 36;
    out[outPos + 11] = (in[inPos + 13] - in[inPos + 12]) >>> 28 | (in[inPos + 14] - in[inPos + 13]) << 24;
    out[outPos + 12] = (in[inPos + 14] - in[inPos + 13]) >>> 40 | (in[inPos + 15] - in[inPos + 14]) << 12;
    out[outPos + 13] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 52;
    out[outPos + 14] = (in[inPos + 17] - in[inPos + 16]) >>> 12 | (in[inPos + 18] - in[inPos + 17]) << 40;
    out[outPos + 15] = (in[inPos + 18] - in[inPos + 17]) >>> 24 | (in[inPos + 19] - in[inPos + 18]) << 28;
    out[outPos + 16] = (in[inPos + 19] - in[inPos + 18]) >>> 36 | (in[inPos + 20] - in[inPos + 19]) << 16;
    out[outPos + 17] = (in[inPos + 20] - in[inPos + 19]) >>> 48 | (in[inPos + 21] - in[inPos + 20]) << 4 | (in[inPos + 22] - in[inPos + 21]) << 56;
    out[outPos + 18] = (in[inPos + 22] - in[inPos + 21]) >>> 8 | (in[inPos + 23] - in[inPos + 22]) << 44;
    out[outPos + 19] = (in[inPos + 23] - in[inPos + 22]) >>> 20 | (in[inPos + 24] - in[inPos + 23]) << 32;
    out[outPos + 20] = (in[inPos + 24] - in[inPos + 23]) >>> 32 | (in[inPos + 25] - in[inPos + 24]) << 20;
    out[outPos + 21] = (in[inPos + 25] - in[inPos + 24]) >>> 44 | (in[inPos + 26] - in[inPos + 25]) << 8 | (in[inPos + 27] - in[inPos + 26]) << 60;
    out[outPos + 22] = (in[inPos + 27] - in[inPos + 26]) >>> 4 | (in[inPos + 28] - in[inPos + 27]) << 48;
    out[outPos + 23] = (in[inPos + 28] - in[inPos + 27]) >>> 16 | (in[inPos + 29] - in[inPos + 28]) << 36;
    out[outPos + 24] = (in[inPos + 29] - in[inPos + 28]) >>> 28 | (in[inPos + 30] - in[inPos + 29]) << 24;
    out[outPos + 25] = (in[inPos + 30] - in[inPos + 29]) >>> 40 | (in[inPos + 31] - in[inPos + 30]) << 12;
    out[outPos + 26] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 52;
    out[outPos + 27] = (in[inPos + 33] - in[inPos + 32]) >>> 12 | (in[inPos + 34] - in[inPos + 33]) << 40;
    out[outPos + 28] = (in[inPos + 34] - in[inPos + 33]) >>> 24 | (in[inPos + 35] - in[inPos + 34]) << 28;
    out[outPos + 29] = (in[inPos + 35] - in[inPos + 34]) >>> 36 | (in[inPos + 36] - in[inPos + 35]) << 16;
    out[outPos + 30] = (in[inPos + 36] - in[inPos + 35]) >>> 48 | (in[inPos + 37] - in[inPos + 36]) << 4 | (in[inPos + 38] - in[inPos + 37]) << 56;
    out[outPos + 31] = (in[inPos + 38] - in[inPos + 37]) >>> 8 | (in[inPos + 39] - in[inPos + 38]) << 44;
    out[outPos + 32] = (in[inPos + 39] - in[inPos + 38]) >>> 20 | (in[inPos + 40] - in[inPos + 39]) << 32;
    out[outPos + 33] = (in[inPos + 40] - in[inPos + 39]) >>> 32 | (in[inPos + 41] - in[inPos + 40]) << 20;
    out[outPos + 34] = (in[inPos + 41] - in[inPos + 40]) >>> 44 | (in[inPos + 42] - in[inPos + 41]) << 8 | (in[inPos + 43] - in[inPos + 42]) << 60;
    out[outPos + 35] = (in[inPos + 43] - in[inPos + 42]) >>> 4 | (in[inPos + 44] - in[inPos + 43]) << 48;
    out[outPos + 36] = (in[inPos + 44] - in[inPos + 43]) >>> 16 | (in[inPos + 45] - in[inPos + 44]) << 36;
    out[outPos + 37] = (in[inPos + 45] - in[inPos + 44]) >>> 28 | (in[inPos + 46] - in[inPos + 45]) << 24;
    out[outPos + 38] = (in[inPos + 46] - in[inPos + 45]) >>> 40 | (in[inPos + 47] - in[inPos + 46]) << 12;
    out[outPos + 39] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 52;
    out[outPos + 40] = (in[inPos + 49] - in[inPos + 48]) >>> 12 | (in[inPos + 50] - in[inPos + 49]) << 40;
    out[outPos + 41] = (in[inPos + 50] - in[inPos + 49]) >>> 24 | (in[inPos + 51] - in[inPos + 50]) << 28;
    out[outPos + 42] = (in[inPos + 51] - in[inPos + 50]) >>> 36 | (in[inPos + 52] - in[inPos + 51]) << 16;
    out[outPos + 43] = (in[inPos + 52] - in[inPos + 51]) >>> 48 | (in[inPos + 53] - in[inPos + 52]) << 4 | (in[inPos + 54] - in[inPos + 53]) << 56;
    out[outPos + 44] = (in[inPos + 54] - in[inPos + 53]) >>> 8 | (in[inPos + 55] - in[inPos + 54]) << 44;
    out[outPos + 45] = (in[inPos + 55] - in[inPos + 54]) >>> 20 | (in[inPos + 56] - in[inPos + 55]) << 32;
    out[outPos + 46] = (in[inPos + 56] - in[inPos + 55]) >>> 32 | (in[inPos + 57] - in[inPos + 56]) << 20;
    out[outPos + 47] = (in[inPos + 57] - in[inPos + 56]) >>> 44 | (in[inPos + 58] - in[inPos + 57]) << 8 | (in[inPos + 59] - in[inPos + 58]) << 60;
    out[outPos + 48] = (in[inPos + 59] - in[inPos + 58]) >>> 4 | (in[inPos + 60] - in[inPos + 59]) << 48;
    out[outPos + 49] = (in[inPos + 60] - in[inPos + 59]) >>> 16 | (in[inPos + 61] - in[inPos + 60]) << 36;
    out[outPos + 50] = (in[inPos + 61] - in[inPos + 60]) >>> 28 | (in[inPos + 62] - in[inPos + 61]) << 24;
    out[outPos + 51] = (in[inPos + 62] - in[inPos + 61]) >>> 40 | (in[inPos + 63] - in[inPos + 62]) << 12;
  }

  private static void unpack52(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 4503599627370495L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 52 | (in[inPos + 1] & 1099511627775L) << 12) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 40 | (in[inPos + 2] & 268435455) << 24) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 28 | (in[inPos + 3] & 65535) << 36) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 16 | (in[inPos + 4] & 15) << 48) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 4 & 4503599627370495L) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 56 | (in[inPos + 5] & 17592186044415L) << 8) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 44 | (in[inPos + 6] & 4294967295L) << 20) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 32 | (in[inPos + 7] & 1048575) << 32) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 20 | (in[inPos + 8] & 255) << 44) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 8 & 4503599627370495L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 60 | (in[inPos + 9] & 281474976710655L) << 4) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] >>> 48 | (in[inPos + 10] & 68719476735L) << 16) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 10] >>> 36 | (in[inPos + 11] & 16777215) << 28) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 11] >>> 24 | (in[inPos + 12] & 4095) << 40) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 12] >>> 12) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 13] & 4503599627370495L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 13] >>> 52 | (in[inPos + 14] & 1099511627775L) << 12) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 14] >>> 40 | (in[inPos + 15] & 268435455) << 24) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 15] >>> 28 | (in[inPos + 16] & 65535) << 36) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 16] >>> 16 | (in[inPos + 17] & 15) << 48) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 17] >>> 4 & 4503599627370495L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 17] >>> 56 | (in[inPos + 18] & 17592186044415L) << 8) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 18] >>> 44 | (in[inPos + 19] & 4294967295L) << 20) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 19] >>> 32 | (in[inPos + 20] & 1048575) << 32) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 20] >>> 20 | (in[inPos + 21] & 255) << 44) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 21] >>> 8 & 4503599627370495L) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 21] >>> 60 | (in[inPos + 22] & 281474976710655L) << 4) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 22] >>> 48 | (in[inPos + 23] & 68719476735L) << 16) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 23] >>> 36 | (in[inPos + 24] & 16777215) << 28) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 24] >>> 24 | (in[inPos + 25] & 4095) << 40) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 25] >>> 12) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 26] & 4503599627370495L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 26] >>> 52 | (in[inPos + 27] & 1099511627775L) << 12) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 27] >>> 40 | (in[inPos + 28] & 268435455) << 24) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 28] >>> 28 | (in[inPos + 29] & 65535) << 36) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 29] >>> 16 | (in[inPos + 30] & 15) << 48) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 30] >>> 4 & 4503599627370495L) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 30] >>> 56 | (in[inPos + 31] & 17592186044415L) << 8) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 31] >>> 44 | (in[inPos + 32] & 4294967295L) << 20) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 32] >>> 32 | (in[inPos + 33] & 1048575) << 32) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 33] >>> 20 | (in[inPos + 34] & 255) << 44) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 34] >>> 8 & 4503599627370495L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 34] >>> 60 | (in[inPos + 35] & 281474976710655L) << 4) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 35] >>> 48 | (in[inPos + 36] & 68719476735L) << 16) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 36] >>> 36 | (in[inPos + 37] & 16777215) << 28) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 37] >>> 24 | (in[inPos + 38] & 4095) << 40) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 38] >>> 12) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 39] & 4503599627370495L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 39] >>> 52 | (in[inPos + 40] & 1099511627775L) << 12) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 40] >>> 40 | (in[inPos + 41] & 268435455) << 24) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 41] >>> 28 | (in[inPos + 42] & 65535) << 36) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 42] >>> 16 | (in[inPos + 43] & 15) << 48) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 43] >>> 4 & 4503599627370495L) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 43] >>> 56 | (in[inPos + 44] & 17592186044415L) << 8) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 44] >>> 44 | (in[inPos + 45] & 4294967295L) << 20) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 45] >>> 32 | (in[inPos + 46] & 1048575) << 32) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 46] >>> 20 | (in[inPos + 47] & 255) << 44) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 47] >>> 8 & 4503599627370495L) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 47] >>> 60 | (in[inPos + 48] & 281474976710655L) << 4) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 48] >>> 48 | (in[inPos + 49] & 68719476735L) << 16) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 49] >>> 36 | (in[inPos + 50] & 16777215) << 28) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 50] >>> 24 | (in[inPos + 51] & 4095) << 40) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 51] >>> 12) + out[outPos + 62];
  }

  private static void pack53(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 53;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 11 | (in[inPos + 2] - in[inPos + 1]) << 42;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 22 | (in[inPos + 3] - in[inPos + 2]) << 31;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 33 | (in[inPos + 4] - in[inPos + 3]) << 20;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 44 | (in[inPos + 5] - in[inPos + 4]) << 9 | (in[inPos + 6] - in[inPos + 5]) << 62;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 2 | (in[inPos + 7] - in[inPos + 6]) << 51;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 13 | (in[inPos + 8] - in[inPos + 7]) << 40;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 24 | (in[inPos + 9] - in[inPos + 8]) << 29;
    out[outPos + 8] = (in[inPos + 9] - in[inPos + 8]) >>> 35 | (in[inPos + 10] - in[inPos + 9]) << 18;
    out[outPos + 9] = (in[inPos + 10] - in[inPos + 9]) >>> 46 | (in[inPos + 11] - in[inPos + 10]) << 7 | (in[inPos + 12] - in[inPos + 11]) << 60;
    out[outPos + 10] = (in[inPos + 12] - in[inPos + 11]) >>> 4 | (in[inPos + 13] - in[inPos + 12]) << 49;
    out[outPos + 11] = (in[inPos + 13] - in[inPos + 12]) >>> 15 | (in[inPos + 14] - in[inPos + 13]) << 38;
    out[outPos + 12] = (in[inPos + 14] - in[inPos + 13]) >>> 26 | (in[inPos + 15] - in[inPos + 14]) << 27;
    out[outPos + 13] = (in[inPos + 15] - in[inPos + 14]) >>> 37 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 14] = (in[inPos + 16] - in[inPos + 15]) >>> 48 | (in[inPos + 17] - in[inPos + 16]) << 5 | (in[inPos + 18] - in[inPos + 17]) << 58;
    out[outPos + 15] = (in[inPos + 18] - in[inPos + 17]) >>> 6 | (in[inPos + 19] - in[inPos + 18]) << 47;
    out[outPos + 16] = (in[inPos + 19] - in[inPos + 18]) >>> 17 | (in[inPos + 20] - in[inPos + 19]) << 36;
    out[outPos + 17] = (in[inPos + 20] - in[inPos + 19]) >>> 28 | (in[inPos + 21] - in[inPos + 20]) << 25;
    out[outPos + 18] = (in[inPos + 21] - in[inPos + 20]) >>> 39 | (in[inPos + 22] - in[inPos + 21]) << 14;
    out[outPos + 19] = (in[inPos + 22] - in[inPos + 21]) >>> 50 | (in[inPos + 23] - in[inPos + 22]) << 3 | (in[inPos + 24] - in[inPos + 23]) << 56;
    out[outPos + 20] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 45;
    out[outPos + 21] = (in[inPos + 25] - in[inPos + 24]) >>> 19 | (in[inPos + 26] - in[inPos + 25]) << 34;
    out[outPos + 22] = (in[inPos + 26] - in[inPos + 25]) >>> 30 | (in[inPos + 27] - in[inPos + 26]) << 23;
    out[outPos + 23] = (in[inPos + 27] - in[inPos + 26]) >>> 41 | (in[inPos + 28] - in[inPos + 27]) << 12;
    out[outPos + 24] = (in[inPos + 28] - in[inPos + 27]) >>> 52 | (in[inPos + 29] - in[inPos + 28]) << 1 | (in[inPos + 30] - in[inPos + 29]) << 54;
    out[outPos + 25] = (in[inPos + 30] - in[inPos + 29]) >>> 10 | (in[inPos + 31] - in[inPos + 30]) << 43;
    out[outPos + 26] = (in[inPos + 31] - in[inPos + 30]) >>> 21 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 27] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 21;
    out[outPos + 28] = (in[inPos + 33] - in[inPos + 32]) >>> 43 | (in[inPos + 34] - in[inPos + 33]) << 10 | (in[inPos + 35] - in[inPos + 34]) << 63;
    out[outPos + 29] = (in[inPos + 35] - in[inPos + 34]) >>> 1 | (in[inPos + 36] - in[inPos + 35]) << 52;
    out[outPos + 30] = (in[inPos + 36] - in[inPos + 35]) >>> 12 | (in[inPos + 37] - in[inPos + 36]) << 41;
    out[outPos + 31] = (in[inPos + 37] - in[inPos + 36]) >>> 23 | (in[inPos + 38] - in[inPos + 37]) << 30;
    out[outPos + 32] = (in[inPos + 38] - in[inPos + 37]) >>> 34 | (in[inPos + 39] - in[inPos + 38]) << 19;
    out[outPos + 33] = (in[inPos + 39] - in[inPos + 38]) >>> 45 | (in[inPos + 40] - in[inPos + 39]) << 8 | (in[inPos + 41] - in[inPos + 40]) << 61;
    out[outPos + 34] = (in[inPos + 41] - in[inPos + 40]) >>> 3 | (in[inPos + 42] - in[inPos + 41]) << 50;
    out[outPos + 35] = (in[inPos + 42] - in[inPos + 41]) >>> 14 | (in[inPos + 43] - in[inPos + 42]) << 39;
    out[outPos + 36] = (in[inPos + 43] - in[inPos + 42]) >>> 25 | (in[inPos + 44] - in[inPos + 43]) << 28;
    out[outPos + 37] = (in[inPos + 44] - in[inPos + 43]) >>> 36 | (in[inPos + 45] - in[inPos + 44]) << 17;
    out[outPos + 38] = (in[inPos + 45] - in[inPos + 44]) >>> 47 | (in[inPos + 46] - in[inPos + 45]) << 6 | (in[inPos + 47] - in[inPos + 46]) << 59;
    out[outPos + 39] = (in[inPos + 47] - in[inPos + 46]) >>> 5 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 40] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 37;
    out[outPos + 41] = (in[inPos + 49] - in[inPos + 48]) >>> 27 | (in[inPos + 50] - in[inPos + 49]) << 26;
    out[outPos + 42] = (in[inPos + 50] - in[inPos + 49]) >>> 38 | (in[inPos + 51] - in[inPos + 50]) << 15;
    out[outPos + 43] = (in[inPos + 51] - in[inPos + 50]) >>> 49 | (in[inPos + 52] - in[inPos + 51]) << 4 | (in[inPos + 53] - in[inPos + 52]) << 57;
    out[outPos + 44] = (in[inPos + 53] - in[inPos + 52]) >>> 7 | (in[inPos + 54] - in[inPos + 53]) << 46;
    out[outPos + 45] = (in[inPos + 54] - in[inPos + 53]) >>> 18 | (in[inPos + 55] - in[inPos + 54]) << 35;
    out[outPos + 46] = (in[inPos + 55] - in[inPos + 54]) >>> 29 | (in[inPos + 56] - in[inPos + 55]) << 24;
    out[outPos + 47] = (in[inPos + 56] - in[inPos + 55]) >>> 40 | (in[inPos + 57] - in[inPos + 56]) << 13;
    out[outPos + 48] = (in[inPos + 57] - in[inPos + 56]) >>> 51 | (in[inPos + 58] - in[inPos + 57]) << 2 | (in[inPos + 59] - in[inPos + 58]) << 55;
    out[outPos + 49] = (in[inPos + 59] - in[inPos + 58]) >>> 9 | (in[inPos + 60] - in[inPos + 59]) << 44;
    out[outPos + 50] = (in[inPos + 60] - in[inPos + 59]) >>> 20 | (in[inPos + 61] - in[inPos + 60]) << 33;
    out[outPos + 51] = (in[inPos + 61] - in[inPos + 60]) >>> 31 | (in[inPos + 62] - in[inPos + 61]) << 22;
    out[outPos + 52] = (in[inPos + 62] - in[inPos + 61]) >>> 42 | (in[inPos + 63] - in[inPos + 62]) << 11;
  }

  private static void unpack53(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 9007199254740991L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 53 | (in[inPos + 1] & 4398046511103L) << 11) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 42 | (in[inPos + 2] & 2147483647) << 22) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 31 | (in[inPos + 3] & 1048575) << 33) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 20 | (in[inPos + 4] & 511) << 44) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 9 & 9007199254740991L) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 62 | (in[inPos + 5] & 2251799813685247L) << 2) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 51 | (in[inPos + 6] & 1099511627775L) << 13) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 40 | (in[inPos + 7] & 536870911) << 24) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 29 | (in[inPos + 8] & 262143) << 35) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 18 | (in[inPos + 9] & 127) << 46) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 7 & 9007199254740991L) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] >>> 60 | (in[inPos + 10] & 562949953421311L) << 4) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 10] >>> 49 | (in[inPos + 11] & 274877906943L) << 15) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 11] >>> 38 | (in[inPos + 12] & 134217727) << 26) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 12] >>> 27 | (in[inPos + 13] & 65535) << 37) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 13] >>> 16 | (in[inPos + 14] & 31) << 48) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 14] >>> 5 & 9007199254740991L) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 14] >>> 58 | (in[inPos + 15] & 140737488355327L) << 6) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 15] >>> 47 | (in[inPos + 16] & 68719476735L) << 17) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 16] >>> 36 | (in[inPos + 17] & 33554431) << 28) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 17] >>> 25 | (in[inPos + 18] & 16383) << 39) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 18] >>> 14 | (in[inPos + 19] & 7) << 50) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 19] >>> 3 & 9007199254740991L) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 19] >>> 56 | (in[inPos + 20] & 35184372088831L) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 20] >>> 45 | (in[inPos + 21] & 17179869183L) << 19) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 21] >>> 34 | (in[inPos + 22] & 8388607) << 30) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 22] >>> 23 | (in[inPos + 23] & 4095) << 41) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 23] >>> 12 | (in[inPos + 24] & 1) << 52) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 24] >>> 1 & 9007199254740991L) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 24] >>> 54 | (in[inPos + 25] & 8796093022207L) << 10) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 25] >>> 43 | (in[inPos + 26] & 4294967295L) << 21) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 26] >>> 32 | (in[inPos + 27] & 2097151) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 27] >>> 21 | (in[inPos + 28] & 1023) << 43) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 28] >>> 10 & 9007199254740991L) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 28] >>> 63 | (in[inPos + 29] & 4503599627370495L) << 1) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 29] >>> 52 | (in[inPos + 30] & 2199023255551L) << 12) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 30] >>> 41 | (in[inPos + 31] & 1073741823) << 23) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 31] >>> 30 | (in[inPos + 32] & 524287) << 34) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 32] >>> 19 | (in[inPos + 33] & 255) << 45) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 33] >>> 8 & 9007199254740991L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 33] >>> 61 | (in[inPos + 34] & 1125899906842623L) << 3) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 34] >>> 50 | (in[inPos + 35] & 549755813887L) << 14) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 35] >>> 39 | (in[inPos + 36] & 268435455) << 25) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 36] >>> 28 | (in[inPos + 37] & 131071) << 36) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 37] >>> 17 | (in[inPos + 38] & 63) << 47) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 38] >>> 6 & 9007199254740991L) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 38] >>> 59 | (in[inPos + 39] & 281474976710655L) << 5) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 39] >>> 48 | (in[inPos + 40] & 137438953471L) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 40] >>> 37 | (in[inPos + 41] & 67108863) << 27) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 41] >>> 26 | (in[inPos + 42] & 32767) << 38) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 42] >>> 15 | (in[inPos + 43] & 15) << 49) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 43] >>> 4 & 9007199254740991L) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 43] >>> 57 | (in[inPos + 44] & 70368744177663L) << 7) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 44] >>> 46 | (in[inPos + 45] & 34359738367L) << 18) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 45] >>> 35 | (in[inPos + 46] & 16777215) << 29) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 46] >>> 24 | (in[inPos + 47] & 8191) << 40) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 47] >>> 13 | (in[inPos + 48] & 3) << 51) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 48] >>> 2 & 9007199254740991L) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 48] >>> 55 | (in[inPos + 49] & 17592186044415L) << 9) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 49] >>> 44 | (in[inPos + 50] & 8589934591L) << 20) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 50] >>> 33 | (in[inPos + 51] & 4194303) << 31) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 51] >>> 22 | (in[inPos + 52] & 2047) << 42) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 52] >>> 11) + out[outPos + 62];
  }

  private static void pack54(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 54;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 10 | (in[inPos + 2] - in[inPos + 1]) << 44;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 20 | (in[inPos + 3] - in[inPos + 2]) << 34;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 30 | (in[inPos + 4] - in[inPos + 3]) << 24;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 40 | (in[inPos + 5] - in[inPos + 4]) << 14;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 50 | (in[inPos + 6] - in[inPos + 5]) << 4 | (in[inPos + 7] - in[inPos + 6]) << 58;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 6 | (in[inPos + 8] - in[inPos + 7]) << 48;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 38;
    out[outPos + 8] = (in[inPos + 9] - in[inPos + 8]) >>> 26 | (in[inPos + 10] - in[inPos + 9]) << 28;
    out[outPos + 9] = (in[inPos + 10] - in[inPos + 9]) >>> 36 | (in[inPos + 11] - in[inPos + 10]) << 18;
    out[outPos + 10] = (in[inPos + 11] - in[inPos + 10]) >>> 46 | (in[inPos + 12] - in[inPos + 11]) << 8 | (in[inPos + 13] - in[inPos + 12]) << 62;
    out[outPos + 11] = (in[inPos + 13] - in[inPos + 12]) >>> 2 | (in[inPos + 14] - in[inPos + 13]) << 52;
    out[outPos + 12] = (in[inPos + 14] - in[inPos + 13]) >>> 12 | (in[inPos + 15] - in[inPos + 14]) << 42;
    out[outPos + 13] = (in[inPos + 15] - in[inPos + 14]) >>> 22 | (in[inPos + 16] - in[inPos + 15]) << 32;
    out[outPos + 14] = (in[inPos + 16] - in[inPos + 15]) >>> 32 | (in[inPos + 17] - in[inPos + 16]) << 22;
    out[outPos + 15] = (in[inPos + 17] - in[inPos + 16]) >>> 42 | (in[inPos + 18] - in[inPos + 17]) << 12;
    out[outPos + 16] = (in[inPos + 18] - in[inPos + 17]) >>> 52 | (in[inPos + 19] - in[inPos + 18]) << 2 | (in[inPos + 20] - in[inPos + 19]) << 56;
    out[outPos + 17] = (in[inPos + 20] - in[inPos + 19]) >>> 8 | (in[inPos + 21] - in[inPos + 20]) << 46;
    out[outPos + 18] = (in[inPos + 21] - in[inPos + 20]) >>> 18 | (in[inPos + 22] - in[inPos + 21]) << 36;
    out[outPos + 19] = (in[inPos + 22] - in[inPos + 21]) >>> 28 | (in[inPos + 23] - in[inPos + 22]) << 26;
    out[outPos + 20] = (in[inPos + 23] - in[inPos + 22]) >>> 38 | (in[inPos + 24] - in[inPos + 23]) << 16;
    out[outPos + 21] = (in[inPos + 24] - in[inPos + 23]) >>> 48 | (in[inPos + 25] - in[inPos + 24]) << 6 | (in[inPos + 26] - in[inPos + 25]) << 60;
    out[outPos + 22] = (in[inPos + 26] - in[inPos + 25]) >>> 4 | (in[inPos + 27] - in[inPos + 26]) << 50;
    out[outPos + 23] = (in[inPos + 27] - in[inPos + 26]) >>> 14 | (in[inPos + 28] - in[inPos + 27]) << 40;
    out[outPos + 24] = (in[inPos + 28] - in[inPos + 27]) >>> 24 | (in[inPos + 29] - in[inPos + 28]) << 30;
    out[outPos + 25] = (in[inPos + 29] - in[inPos + 28]) >>> 34 | (in[inPos + 30] - in[inPos + 29]) << 20;
    out[outPos + 26] = (in[inPos + 30] - in[inPos + 29]) >>> 44 | (in[inPos + 31] - in[inPos + 30]) << 10;
    out[outPos + 27] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 54;
    out[outPos + 28] = (in[inPos + 33] - in[inPos + 32]) >>> 10 | (in[inPos + 34] - in[inPos + 33]) << 44;
    out[outPos + 29] = (in[inPos + 34] - in[inPos + 33]) >>> 20 | (in[inPos + 35] - in[inPos + 34]) << 34;
    out[outPos + 30] = (in[inPos + 35] - in[inPos + 34]) >>> 30 | (in[inPos + 36] - in[inPos + 35]) << 24;
    out[outPos + 31] = (in[inPos + 36] - in[inPos + 35]) >>> 40 | (in[inPos + 37] - in[inPos + 36]) << 14;
    out[outPos + 32] = (in[inPos + 37] - in[inPos + 36]) >>> 50 | (in[inPos + 38] - in[inPos + 37]) << 4 | (in[inPos + 39] - in[inPos + 38]) << 58;
    out[outPos + 33] = (in[inPos + 39] - in[inPos + 38]) >>> 6 | (in[inPos + 40] - in[inPos + 39]) << 48;
    out[outPos + 34] = (in[inPos + 40] - in[inPos + 39]) >>> 16 | (in[inPos + 41] - in[inPos + 40]) << 38;
    out[outPos + 35] = (in[inPos + 41] - in[inPos + 40]) >>> 26 | (in[inPos + 42] - in[inPos + 41]) << 28;
    out[outPos + 36] = (in[inPos + 42] - in[inPos + 41]) >>> 36 | (in[inPos + 43] - in[inPos + 42]) << 18;
    out[outPos + 37] = (in[inPos + 43] - in[inPos + 42]) >>> 46 | (in[inPos + 44] - in[inPos + 43]) << 8 | (in[inPos + 45] - in[inPos + 44]) << 62;
    out[outPos + 38] = (in[inPos + 45] - in[inPos + 44]) >>> 2 | (in[inPos + 46] - in[inPos + 45]) << 52;
    out[outPos + 39] = (in[inPos + 46] - in[inPos + 45]) >>> 12 | (in[inPos + 47] - in[inPos + 46]) << 42;
    out[outPos + 40] = (in[inPos + 47] - in[inPos + 46]) >>> 22 | (in[inPos + 48] - in[inPos + 47]) << 32;
    out[outPos + 41] = (in[inPos + 48] - in[inPos + 47]) >>> 32 | (in[inPos + 49] - in[inPos + 48]) << 22;
    out[outPos + 42] = (in[inPos + 49] - in[inPos + 48]) >>> 42 | (in[inPos + 50] - in[inPos + 49]) << 12;
    out[outPos + 43] = (in[inPos + 50] - in[inPos + 49]) >>> 52 | (in[inPos + 51] - in[inPos + 50]) << 2 | (in[inPos + 52] - in[inPos + 51]) << 56;
    out[outPos + 44] = (in[inPos + 52] - in[inPos + 51]) >>> 8 | (in[inPos + 53] - in[inPos + 52]) << 46;
    out[outPos + 45] = (in[inPos + 53] - in[inPos + 52]) >>> 18 | (in[inPos + 54] - in[inPos + 53]) << 36;
    out[outPos + 46] = (in[inPos + 54] - in[inPos + 53]) >>> 28 | (in[inPos + 55] - in[inPos + 54]) << 26;
    out[outPos + 47] = (in[inPos + 55] - in[inPos + 54]) >>> 38 | (in[inPos + 56] - in[inPos + 55]) << 16;
    out[outPos + 48] = (in[inPos + 56] - in[inPos + 55]) >>> 48 | (in[inPos + 57] - in[inPos + 56]) << 6 | (in[inPos + 58] - in[inPos + 57]) << 60;
    out[outPos + 49] = (in[inPos + 58] - in[inPos + 57]) >>> 4 | (in[inPos + 59] - in[inPos + 58]) << 50;
    out[outPos + 50] = (in[inPos + 59] - in[inPos + 58]) >>> 14 | (in[inPos + 60] - in[inPos + 59]) << 40;
    out[outPos + 51] = (in[inPos + 60] - in[inPos + 59]) >>> 24 | (in[inPos + 61] - in[inPos + 60]) << 30;
    out[outPos + 52] = (in[inPos + 61] - in[inPos + 60]) >>> 34 | (in[inPos + 62] - in[inPos + 61]) << 20;
    out[outPos + 53] = (in[inPos + 62] - in[inPos + 61]) >>> 44 | (in[inPos + 63] - in[inPos + 62]) << 10;
  }

  private static void unpack54(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 18014398509481983L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 54 | (in[inPos + 1] & 17592186044415L) << 10) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 44 | (in[inPos + 2] & 17179869183L) << 20) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 34 | (in[inPos + 3] & 16777215) << 30) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 24 | (in[inPos + 4] & 16383) << 40) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 14 | (in[inPos + 5] & 15) << 50) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 4 & 18014398509481983L) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 281474976710655L) << 6) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 48 | (in[inPos + 7] & 274877906943L) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 38 | (in[inPos + 8] & 268435455) << 26) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 28 | (in[inPos + 9] & 262143) << 36) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 18 | (in[inPos + 10] & 255) << 46) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 10] >>> 8 & 18014398509481983L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 10] >>> 62 | (in[inPos + 11] & 4503599627370495L) << 2) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 4398046511103L) << 12) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 12] >>> 42 | (in[inPos + 13] & 4294967295L) << 22) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 13] >>> 32 | (in[inPos + 14] & 4194303) << 32) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 14] >>> 22 | (in[inPos + 15] & 4095) << 42) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 15] >>> 12 | (in[inPos + 16] & 3) << 52) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 16] >>> 2 & 18014398509481983L) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 70368744177663L) << 8) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 17] >>> 46 | (in[inPos + 18] & 68719476735L) << 18) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 18] >>> 36 | (in[inPos + 19] & 67108863) << 28) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 19] >>> 26 | (in[inPos + 20] & 65535) << 38) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 20] >>> 16 | (in[inPos + 21] & 63) << 48) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 21] >>> 6 & 18014398509481983L) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 21] >>> 60 | (in[inPos + 22] & 1125899906842623L) << 4) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 22] >>> 50 | (in[inPos + 23] & 1099511627775L) << 14) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 23] >>> 40 | (in[inPos + 24] & 1073741823) << 24) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 24] >>> 30 | (in[inPos + 25] & 1048575) << 34) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 25] >>> 20 | (in[inPos + 26] & 1023) << 44) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 26] >>> 10) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 27] & 18014398509481983L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 27] >>> 54 | (in[inPos + 28] & 17592186044415L) << 10) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 28] >>> 44 | (in[inPos + 29] & 17179869183L) << 20) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 29] >>> 34 | (in[inPos + 30] & 16777215) << 30) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 30] >>> 24 | (in[inPos + 31] & 16383) << 40) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 31] >>> 14 | (in[inPos + 32] & 15) << 50) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 32] >>> 4 & 18014398509481983L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 32] >>> 58 | (in[inPos + 33] & 281474976710655L) << 6) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 33] >>> 48 | (in[inPos + 34] & 274877906943L) << 16) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 34] >>> 38 | (in[inPos + 35] & 268435455) << 26) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 35] >>> 28 | (in[inPos + 36] & 262143) << 36) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 36] >>> 18 | (in[inPos + 37] & 255) << 46) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 37] >>> 8 & 18014398509481983L) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 37] >>> 62 | (in[inPos + 38] & 4503599627370495L) << 2) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 38] >>> 52 | (in[inPos + 39] & 4398046511103L) << 12) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 39] >>> 42 | (in[inPos + 40] & 4294967295L) << 22) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 40] >>> 32 | (in[inPos + 41] & 4194303) << 32) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 41] >>> 22 | (in[inPos + 42] & 4095) << 42) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 42] >>> 12 | (in[inPos + 43] & 3) << 52) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 43] >>> 2 & 18014398509481983L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 43] >>> 56 | (in[inPos + 44] & 70368744177663L) << 8) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 44] >>> 46 | (in[inPos + 45] & 68719476735L) << 18) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 45] >>> 36 | (in[inPos + 46] & 67108863) << 28) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 46] >>> 26 | (in[inPos + 47] & 65535) << 38) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 47] >>> 16 | (in[inPos + 48] & 63) << 48) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 48] >>> 6 & 18014398509481983L) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 48] >>> 60 | (in[inPos + 49] & 1125899906842623L) << 4) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 49] >>> 50 | (in[inPos + 50] & 1099511627775L) << 14) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 50] >>> 40 | (in[inPos + 51] & 1073741823) << 24) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 51] >>> 30 | (in[inPos + 52] & 1048575) << 34) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 52] >>> 20 | (in[inPos + 53] & 1023) << 44) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 53] >>> 10) + out[outPos + 62];
  }

  private static void pack55(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 55;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 9 | (in[inPos + 2] - in[inPos + 1]) << 46;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 18 | (in[inPos + 3] - in[inPos + 2]) << 37;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 27 | (in[inPos + 4] - in[inPos + 3]) << 28;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 36 | (in[inPos + 5] - in[inPos + 4]) << 19;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 45 | (in[inPos + 6] - in[inPos + 5]) << 10;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 54 | (in[inPos + 7] - in[inPos + 6]) << 1 | (in[inPos + 8] - in[inPos + 7]) << 56;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 47;
    out[outPos + 8] = (in[inPos + 9] - in[inPos + 8]) >>> 17 | (in[inPos + 10] - in[inPos + 9]) << 38;
    out[outPos + 9] = (in[inPos + 10] - in[inPos + 9]) >>> 26 | (in[inPos + 11] - in[inPos + 10]) << 29;
    out[outPos + 10] = (in[inPos + 11] - in[inPos + 10]) >>> 35 | (in[inPos + 12] - in[inPos + 11]) << 20;
    out[outPos + 11] = (in[inPos + 12] - in[inPos + 11]) >>> 44 | (in[inPos + 13] - in[inPos + 12]) << 11;
    out[outPos + 12] = (in[inPos + 13] - in[inPos + 12]) >>> 53 | (in[inPos + 14] - in[inPos + 13]) << 2 | (in[inPos + 15] - in[inPos + 14]) << 57;
    out[outPos + 13] = (in[inPos + 15] - in[inPos + 14]) >>> 7 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 14] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 39;
    out[outPos + 15] = (in[inPos + 17] - in[inPos + 16]) >>> 25 | (in[inPos + 18] - in[inPos + 17]) << 30;
    out[outPos + 16] = (in[inPos + 18] - in[inPos + 17]) >>> 34 | (in[inPos + 19] - in[inPos + 18]) << 21;
    out[outPos + 17] = (in[inPos + 19] - in[inPos + 18]) >>> 43 | (in[inPos + 20] - in[inPos + 19]) << 12;
    out[outPos + 18] = (in[inPos + 20] - in[inPos + 19]) >>> 52 | (in[inPos + 21] - in[inPos + 20]) << 3 | (in[inPos + 22] - in[inPos + 21]) << 58;
    out[outPos + 19] = (in[inPos + 22] - in[inPos + 21]) >>> 6 | (in[inPos + 23] - in[inPos + 22]) << 49;
    out[outPos + 20] = (in[inPos + 23] - in[inPos + 22]) >>> 15 | (in[inPos + 24] - in[inPos + 23]) << 40;
    out[outPos + 21] = (in[inPos + 24] - in[inPos + 23]) >>> 24 | (in[inPos + 25] - in[inPos + 24]) << 31;
    out[outPos + 22] = (in[inPos + 25] - in[inPos + 24]) >>> 33 | (in[inPos + 26] - in[inPos + 25]) << 22;
    out[outPos + 23] = (in[inPos + 26] - in[inPos + 25]) >>> 42 | (in[inPos + 27] - in[inPos + 26]) << 13;
    out[outPos + 24] = (in[inPos + 27] - in[inPos + 26]) >>> 51 | (in[inPos + 28] - in[inPos + 27]) << 4 | (in[inPos + 29] - in[inPos + 28]) << 59;
    out[outPos + 25] = (in[inPos + 29] - in[inPos + 28]) >>> 5 | (in[inPos + 30] - in[inPos + 29]) << 50;
    out[outPos + 26] = (in[inPos + 30] - in[inPos + 29]) >>> 14 | (in[inPos + 31] - in[inPos + 30]) << 41;
    out[outPos + 27] = (in[inPos + 31] - in[inPos + 30]) >>> 23 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 28] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 23;
    out[outPos + 29] = (in[inPos + 33] - in[inPos + 32]) >>> 41 | (in[inPos + 34] - in[inPos + 33]) << 14;
    out[outPos + 30] = (in[inPos + 34] - in[inPos + 33]) >>> 50 | (in[inPos + 35] - in[inPos + 34]) << 5 | (in[inPos + 36] - in[inPos + 35]) << 60;
    out[outPos + 31] = (in[inPos + 36] - in[inPos + 35]) >>> 4 | (in[inPos + 37] - in[inPos + 36]) << 51;
    out[outPos + 32] = (in[inPos + 37] - in[inPos + 36]) >>> 13 | (in[inPos + 38] - in[inPos + 37]) << 42;
    out[outPos + 33] = (in[inPos + 38] - in[inPos + 37]) >>> 22 | (in[inPos + 39] - in[inPos + 38]) << 33;
    out[outPos + 34] = (in[inPos + 39] - in[inPos + 38]) >>> 31 | (in[inPos + 40] - in[inPos + 39]) << 24;
    out[outPos + 35] = (in[inPos + 40] - in[inPos + 39]) >>> 40 | (in[inPos + 41] - in[inPos + 40]) << 15;
    out[outPos + 36] = (in[inPos + 41] - in[inPos + 40]) >>> 49 | (in[inPos + 42] - in[inPos + 41]) << 6 | (in[inPos + 43] - in[inPos + 42]) << 61;
    out[outPos + 37] = (in[inPos + 43] - in[inPos + 42]) >>> 3 | (in[inPos + 44] - in[inPos + 43]) << 52;
    out[outPos + 38] = (in[inPos + 44] - in[inPos + 43]) >>> 12 | (in[inPos + 45] - in[inPos + 44]) << 43;
    out[outPos + 39] = (in[inPos + 45] - in[inPos + 44]) >>> 21 | (in[inPos + 46] - in[inPos + 45]) << 34;
    out[outPos + 40] = (in[inPos + 46] - in[inPos + 45]) >>> 30 | (in[inPos + 47] - in[inPos + 46]) << 25;
    out[outPos + 41] = (in[inPos + 47] - in[inPos + 46]) >>> 39 | (in[inPos + 48] - in[inPos + 47]) << 16;
    out[outPos + 42] = (in[inPos + 48] - in[inPos + 47]) >>> 48 | (in[inPos + 49] - in[inPos + 48]) << 7 | (in[inPos + 50] - in[inPos + 49]) << 62;
    out[outPos + 43] = (in[inPos + 50] - in[inPos + 49]) >>> 2 | (in[inPos + 51] - in[inPos + 50]) << 53;
    out[outPos + 44] = (in[inPos + 51] - in[inPos + 50]) >>> 11 | (in[inPos + 52] - in[inPos + 51]) << 44;
    out[outPos + 45] = (in[inPos + 52] - in[inPos + 51]) >>> 20 | (in[inPos + 53] - in[inPos + 52]) << 35;
    out[outPos + 46] = (in[inPos + 53] - in[inPos + 52]) >>> 29 | (in[inPos + 54] - in[inPos + 53]) << 26;
    out[outPos + 47] = (in[inPos + 54] - in[inPos + 53]) >>> 38 | (in[inPos + 55] - in[inPos + 54]) << 17;
    out[outPos + 48] = (in[inPos + 55] - in[inPos + 54]) >>> 47 | (in[inPos + 56] - in[inPos + 55]) << 8 | (in[inPos + 57] - in[inPos + 56]) << 63;
    out[outPos + 49] = (in[inPos + 57] - in[inPos + 56]) >>> 1 | (in[inPos + 58] - in[inPos + 57]) << 54;
    out[outPos + 50] = (in[inPos + 58] - in[inPos + 57]) >>> 10 | (in[inPos + 59] - in[inPos + 58]) << 45;
    out[outPos + 51] = (in[inPos + 59] - in[inPos + 58]) >>> 19 | (in[inPos + 60] - in[inPos + 59]) << 36;
    out[outPos + 52] = (in[inPos + 60] - in[inPos + 59]) >>> 28 | (in[inPos + 61] - in[inPos + 60]) << 27;
    out[outPos + 53] = (in[inPos + 61] - in[inPos + 60]) >>> 37 | (in[inPos + 62] - in[inPos + 61]) << 18;
    out[outPos + 54] = (in[inPos + 62] - in[inPos + 61]) >>> 46 | (in[inPos + 63] - in[inPos + 62]) << 9;
  }

  private static void unpack55(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 36028797018963967L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 55 | (in[inPos + 1] & 70368744177663L) << 9) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 46 | (in[inPos + 2] & 137438953471L) << 18) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 37 | (in[inPos + 3] & 268435455) << 27) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 28 | (in[inPos + 4] & 524287) << 36) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 19 | (in[inPos + 5] & 1023) << 45) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 10 | (in[inPos + 6] & 1) << 54) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 1 & 36028797018963967L) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 56 | (in[inPos + 7] & 140737488355327L) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 47 | (in[inPos + 8] & 274877906943L) << 17) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 38 | (in[inPos + 9] & 536870911) << 26) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 29 | (in[inPos + 10] & 1048575) << 35) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 10] >>> 20 | (in[inPos + 11] & 2047) << 44) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 11] >>> 11 | (in[inPos + 12] & 3) << 53) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 12] >>> 2 & 36028797018963967L) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 12] >>> 57 | (in[inPos + 13] & 281474976710655L) << 7) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 13] >>> 48 | (in[inPos + 14] & 549755813887L) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 14] >>> 39 | (in[inPos + 15] & 1073741823) << 25) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 15] >>> 30 | (in[inPos + 16] & 2097151) << 34) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 16] >>> 21 | (in[inPos + 17] & 4095) << 43) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 17] >>> 12 | (in[inPos + 18] & 7) << 52) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 18] >>> 3 & 36028797018963967L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 18] >>> 58 | (in[inPos + 19] & 562949953421311L) << 6) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 19] >>> 49 | (in[inPos + 20] & 1099511627775L) << 15) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 20] >>> 40 | (in[inPos + 21] & 2147483647) << 24) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 21] >>> 31 | (in[inPos + 22] & 4194303) << 33) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 22] >>> 22 | (in[inPos + 23] & 8191) << 42) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 23] >>> 13 | (in[inPos + 24] & 15) << 51) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 24] >>> 4 & 36028797018963967L) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 24] >>> 59 | (in[inPos + 25] & 1125899906842623L) << 5) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 25] >>> 50 | (in[inPos + 26] & 2199023255551L) << 14) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 26] >>> 41 | (in[inPos + 27] & 4294967295L) << 23) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 27] >>> 32 | (in[inPos + 28] & 8388607) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 28] >>> 23 | (in[inPos + 29] & 16383) << 41) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 29] >>> 14 | (in[inPos + 30] & 31) << 50) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 30] >>> 5 & 36028797018963967L) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 30] >>> 60 | (in[inPos + 31] & 2251799813685247L) << 4) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 31] >>> 51 | (in[inPos + 32] & 4398046511103L) << 13) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 32] >>> 42 | (in[inPos + 33] & 8589934591L) << 22) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 33] >>> 33 | (in[inPos + 34] & 16777215) << 31) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 34] >>> 24 | (in[inPos + 35] & 32767) << 40) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 35] >>> 15 | (in[inPos + 36] & 63) << 49) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 36] >>> 6 & 36028797018963967L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 36] >>> 61 | (in[inPos + 37] & 4503599627370495L) << 3) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 37] >>> 52 | (in[inPos + 38] & 8796093022207L) << 12) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 38] >>> 43 | (in[inPos + 39] & 17179869183L) << 21) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 39] >>> 34 | (in[inPos + 40] & 33554431) << 30) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 40] >>> 25 | (in[inPos + 41] & 65535) << 39) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 41] >>> 16 | (in[inPos + 42] & 127) << 48) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 42] >>> 7 & 36028797018963967L) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 42] >>> 62 | (in[inPos + 43] & 9007199254740991L) << 2) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 43] >>> 53 | (in[inPos + 44] & 17592186044415L) << 11) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 44] >>> 44 | (in[inPos + 45] & 34359738367L) << 20) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 45] >>> 35 | (in[inPos + 46] & 67108863) << 29) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 46] >>> 26 | (in[inPos + 47] & 131071) << 38) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 47] >>> 17 | (in[inPos + 48] & 255) << 47) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 48] >>> 8 & 36028797018963967L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 48] >>> 63 | (in[inPos + 49] & 18014398509481983L) << 1) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 49] >>> 54 | (in[inPos + 50] & 35184372088831L) << 10) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 50] >>> 45 | (in[inPos + 51] & 68719476735L) << 19) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 51] >>> 36 | (in[inPos + 52] & 134217727) << 28) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 52] >>> 27 | (in[inPos + 53] & 262143) << 37) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 53] >>> 18 | (in[inPos + 54] & 511) << 46) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 54] >>> 9) + out[outPos + 62];
  }

  private static void pack56(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 56;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 8 | (in[inPos + 2] - in[inPos + 1]) << 48;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 16 | (in[inPos + 3] - in[inPos + 2]) << 40;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 24 | (in[inPos + 4] - in[inPos + 3]) << 32;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 32 | (in[inPos + 5] - in[inPos + 4]) << 24;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 40 | (in[inPos + 6] - in[inPos + 5]) << 16;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 48 | (in[inPos + 7] - in[inPos + 6]) << 8;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 56;
    out[outPos + 8] = (in[inPos + 9] - in[inPos + 8]) >>> 8 | (in[inPos + 10] - in[inPos + 9]) << 48;
    out[outPos + 9] = (in[inPos + 10] - in[inPos + 9]) >>> 16 | (in[inPos + 11] - in[inPos + 10]) << 40;
    out[outPos + 10] = (in[inPos + 11] - in[inPos + 10]) >>> 24 | (in[inPos + 12] - in[inPos + 11]) << 32;
    out[outPos + 11] = (in[inPos + 12] - in[inPos + 11]) >>> 32 | (in[inPos + 13] - in[inPos + 12]) << 24;
    out[outPos + 12] = (in[inPos + 13] - in[inPos + 12]) >>> 40 | (in[inPos + 14] - in[inPos + 13]) << 16;
    out[outPos + 13] = (in[inPos + 14] - in[inPos + 13]) >>> 48 | (in[inPos + 15] - in[inPos + 14]) << 8;
    out[outPos + 14] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 56;
    out[outPos + 15] = (in[inPos + 17] - in[inPos + 16]) >>> 8 | (in[inPos + 18] - in[inPos + 17]) << 48;
    out[outPos + 16] = (in[inPos + 18] - in[inPos + 17]) >>> 16 | (in[inPos + 19] - in[inPos + 18]) << 40;
    out[outPos + 17] = (in[inPos + 19] - in[inPos + 18]) >>> 24 | (in[inPos + 20] - in[inPos + 19]) << 32;
    out[outPos + 18] = (in[inPos + 20] - in[inPos + 19]) >>> 32 | (in[inPos + 21] - in[inPos + 20]) << 24;
    out[outPos + 19] = (in[inPos + 21] - in[inPos + 20]) >>> 40 | (in[inPos + 22] - in[inPos + 21]) << 16;
    out[outPos + 20] = (in[inPos + 22] - in[inPos + 21]) >>> 48 | (in[inPos + 23] - in[inPos + 22]) << 8;
    out[outPos + 21] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 56;
    out[outPos + 22] = (in[inPos + 25] - in[inPos + 24]) >>> 8 | (in[inPos + 26] - in[inPos + 25]) << 48;
    out[outPos + 23] = (in[inPos + 26] - in[inPos + 25]) >>> 16 | (in[inPos + 27] - in[inPos + 26]) << 40;
    out[outPos + 24] = (in[inPos + 27] - in[inPos + 26]) >>> 24 | (in[inPos + 28] - in[inPos + 27]) << 32;
    out[outPos + 25] = (in[inPos + 28] - in[inPos + 27]) >>> 32 | (in[inPos + 29] - in[inPos + 28]) << 24;
    out[outPos + 26] = (in[inPos + 29] - in[inPos + 28]) >>> 40 | (in[inPos + 30] - in[inPos + 29]) << 16;
    out[outPos + 27] = (in[inPos + 30] - in[inPos + 29]) >>> 48 | (in[inPos + 31] - in[inPos + 30]) << 8;
    out[outPos + 28] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 56;
    out[outPos + 29] = (in[inPos + 33] - in[inPos + 32]) >>> 8 | (in[inPos + 34] - in[inPos + 33]) << 48;
    out[outPos + 30] = (in[inPos + 34] - in[inPos + 33]) >>> 16 | (in[inPos + 35] - in[inPos + 34]) << 40;
    out[outPos + 31] = (in[inPos + 35] - in[inPos + 34]) >>> 24 | (in[inPos + 36] - in[inPos + 35]) << 32;
    out[outPos + 32] = (in[inPos + 36] - in[inPos + 35]) >>> 32 | (in[inPos + 37] - in[inPos + 36]) << 24;
    out[outPos + 33] = (in[inPos + 37] - in[inPos + 36]) >>> 40 | (in[inPos + 38] - in[inPos + 37]) << 16;
    out[outPos + 34] = (in[inPos + 38] - in[inPos + 37]) >>> 48 | (in[inPos + 39] - in[inPos + 38]) << 8;
    out[outPos + 35] = (in[inPos + 40] - in[inPos + 39]) | (in[inPos + 41] - in[inPos + 40]) << 56;
    out[outPos + 36] = (in[inPos + 41] - in[inPos + 40]) >>> 8 | (in[inPos + 42] - in[inPos + 41]) << 48;
    out[outPos + 37] = (in[inPos + 42] - in[inPos + 41]) >>> 16 | (in[inPos + 43] - in[inPos + 42]) << 40;
    out[outPos + 38] = (in[inPos + 43] - in[inPos + 42]) >>> 24 | (in[inPos + 44] - in[inPos + 43]) << 32;
    out[outPos + 39] = (in[inPos + 44] - in[inPos + 43]) >>> 32 | (in[inPos + 45] - in[inPos + 44]) << 24;
    out[outPos + 40] = (in[inPos + 45] - in[inPos + 44]) >>> 40 | (in[inPos + 46] - in[inPos + 45]) << 16;
    out[outPos + 41] = (in[inPos + 46] - in[inPos + 45]) >>> 48 | (in[inPos + 47] - in[inPos + 46]) << 8;
    out[outPos + 42] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 56;
    out[outPos + 43] = (in[inPos + 49] - in[inPos + 48]) >>> 8 | (in[inPos + 50] - in[inPos + 49]) << 48;
    out[outPos + 44] = (in[inPos + 50] - in[inPos + 49]) >>> 16 | (in[inPos + 51] - in[inPos + 50]) << 40;
    out[outPos + 45] = (in[inPos + 51] - in[inPos + 50]) >>> 24 | (in[inPos + 52] - in[inPos + 51]) << 32;
    out[outPos + 46] = (in[inPos + 52] - in[inPos + 51]) >>> 32 | (in[inPos + 53] - in[inPos + 52]) << 24;
    out[outPos + 47] = (in[inPos + 53] - in[inPos + 52]) >>> 40 | (in[inPos + 54] - in[inPos + 53]) << 16;
    out[outPos + 48] = (in[inPos + 54] - in[inPos + 53]) >>> 48 | (in[inPos + 55] - in[inPos + 54]) << 8;
    out[outPos + 49] = (in[inPos + 56] - in[inPos + 55]) | (in[inPos + 57] - in[inPos + 56]) << 56;
    out[outPos + 50] = (in[inPos + 57] - in[inPos + 56]) >>> 8 | (in[inPos + 58] - in[inPos + 57]) << 48;
    out[outPos + 51] = (in[inPos + 58] - in[inPos + 57]) >>> 16 | (in[inPos + 59] - in[inPos + 58]) << 40;
    out[outPos + 52] = (in[inPos + 59] - in[inPos + 58]) >>> 24 | (in[inPos + 60] - in[inPos + 59]) << 32;
    out[outPos + 53] = (in[inPos + 60] - in[inPos + 59]) >>> 32 | (in[inPos + 61] - in[inPos + 60]) << 24;
    out[outPos + 54] = (in[inPos + 61] - in[inPos + 60]) >>> 40 | (in[inPos + 62] - in[inPos + 61]) << 16;
    out[outPos + 55] = (in[inPos + 62] - in[inPos + 61]) >>> 48 | (in[inPos + 63] - in[inPos + 62]) << 8;
  }

  private static void unpack56(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 72057594037927935L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 56 | (in[inPos + 1] & 281474976710655L) << 8) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 48 | (in[inPos + 2] & 1099511627775L) << 16) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 40 | (in[inPos + 3] & 4294967295L) << 24) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 32 | (in[inPos + 4] & 16777215) << 32) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 24 | (in[inPos + 5] & 65535) << 40) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 16 | (in[inPos + 6] & 255) << 48) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 8) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] & 72057594037927935L) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 281474976710655L) << 8) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 48 | (in[inPos + 9] & 1099511627775L) << 16) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 40 | (in[inPos + 10] & 4294967295L) << 24) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 10] >>> 32 | (in[inPos + 11] & 16777215) << 32) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 11] >>> 24 | (in[inPos + 12] & 65535) << 40) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 12] >>> 16 | (in[inPos + 13] & 255) << 48) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 13] >>> 8) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 14] & 72057594037927935L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 14] >>> 56 | (in[inPos + 15] & 281474976710655L) << 8) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 1099511627775L) << 16) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 16] >>> 40 | (in[inPos + 17] & 4294967295L) << 24) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 17] >>> 32 | (in[inPos + 18] & 16777215) << 32) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 18] >>> 24 | (in[inPos + 19] & 65535) << 40) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 19] >>> 16 | (in[inPos + 20] & 255) << 48) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 20] >>> 8) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 21] & 72057594037927935L) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 21] >>> 56 | (in[inPos + 22] & 281474976710655L) << 8) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 22] >>> 48 | (in[inPos + 23] & 1099511627775L) << 16) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 23] >>> 40 | (in[inPos + 24] & 4294967295L) << 24) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 24] >>> 32 | (in[inPos + 25] & 16777215) << 32) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 25] >>> 24 | (in[inPos + 26] & 65535) << 40) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 26] >>> 16 | (in[inPos + 27] & 255) << 48) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 27] >>> 8) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 28] & 72057594037927935L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 28] >>> 56 | (in[inPos + 29] & 281474976710655L) << 8) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 29] >>> 48 | (in[inPos + 30] & 1099511627775L) << 16) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 30] >>> 40 | (in[inPos + 31] & 4294967295L) << 24) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 31] >>> 32 | (in[inPos + 32] & 16777215) << 32) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 32] >>> 24 | (in[inPos + 33] & 65535) << 40) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 33] >>> 16 | (in[inPos + 34] & 255) << 48) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 34] >>> 8) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 35] & 72057594037927935L) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 35] >>> 56 | (in[inPos + 36] & 281474976710655L) << 8) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 36] >>> 48 | (in[inPos + 37] & 1099511627775L) << 16) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 37] >>> 40 | (in[inPos + 38] & 4294967295L) << 24) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 38] >>> 32 | (in[inPos + 39] & 16777215) << 32) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 39] >>> 24 | (in[inPos + 40] & 65535) << 40) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 40] >>> 16 | (in[inPos + 41] & 255) << 48) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 41] >>> 8) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 42] & 72057594037927935L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 42] >>> 56 | (in[inPos + 43] & 281474976710655L) << 8) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 43] >>> 48 | (in[inPos + 44] & 1099511627775L) << 16) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 44] >>> 40 | (in[inPos + 45] & 4294967295L) << 24) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 45] >>> 32 | (in[inPos + 46] & 16777215) << 32) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 46] >>> 24 | (in[inPos + 47] & 65535) << 40) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 47] >>> 16 | (in[inPos + 48] & 255) << 48) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 48] >>> 8) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 49] & 72057594037927935L) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 49] >>> 56 | (in[inPos + 50] & 281474976710655L) << 8) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 50] >>> 48 | (in[inPos + 51] & 1099511627775L) << 16) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 51] >>> 40 | (in[inPos + 52] & 4294967295L) << 24) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 52] >>> 32 | (in[inPos + 53] & 16777215) << 32) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 53] >>> 24 | (in[inPos + 54] & 65535) << 40) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 54] >>> 16 | (in[inPos + 55] & 255) << 48) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 55] >>> 8) + out[outPos + 62];
  }

  private static void pack57(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 57;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 7 | (in[inPos + 2] - in[inPos + 1]) << 50;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 14 | (in[inPos + 3] - in[inPos + 2]) << 43;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 21 | (in[inPos + 4] - in[inPos + 3]) << 36;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 28 | (in[inPos + 5] - in[inPos + 4]) << 29;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 35 | (in[inPos + 6] - in[inPos + 5]) << 22;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 42 | (in[inPos + 7] - in[inPos + 6]) << 15;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 49 | (in[inPos + 8] - in[inPos + 7]) << 8;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 56 | (in[inPos + 9] - in[inPos + 8]) << 1 | (in[inPos + 10] - in[inPos + 9]) << 58;
    out[outPos + 9] = (in[inPos + 10] - in[inPos + 9]) >>> 6 | (in[inPos + 11] - in[inPos + 10]) << 51;
    out[outPos + 10] = (in[inPos + 11] - in[inPos + 10]) >>> 13 | (in[inPos + 12] - in[inPos + 11]) << 44;
    out[outPos + 11] = (in[inPos + 12] - in[inPos + 11]) >>> 20 | (in[inPos + 13] - in[inPos + 12]) << 37;
    out[outPos + 12] = (in[inPos + 13] - in[inPos + 12]) >>> 27 | (in[inPos + 14] - in[inPos + 13]) << 30;
    out[outPos + 13] = (in[inPos + 14] - in[inPos + 13]) >>> 34 | (in[inPos + 15] - in[inPos + 14]) << 23;
    out[outPos + 14] = (in[inPos + 15] - in[inPos + 14]) >>> 41 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 15] = (in[inPos + 16] - in[inPos + 15]) >>> 48 | (in[inPos + 17] - in[inPos + 16]) << 9;
    out[outPos + 16] = (in[inPos + 17] - in[inPos + 16]) >>> 55 | (in[inPos + 18] - in[inPos + 17]) << 2 | (in[inPos + 19] - in[inPos + 18]) << 59;
    out[outPos + 17] = (in[inPos + 19] - in[inPos + 18]) >>> 5 | (in[inPos + 20] - in[inPos + 19]) << 52;
    out[outPos + 18] = (in[inPos + 20] - in[inPos + 19]) >>> 12 | (in[inPos + 21] - in[inPos + 20]) << 45;
    out[outPos + 19] = (in[inPos + 21] - in[inPos + 20]) >>> 19 | (in[inPos + 22] - in[inPos + 21]) << 38;
    out[outPos + 20] = (in[inPos + 22] - in[inPos + 21]) >>> 26 | (in[inPos + 23] - in[inPos + 22]) << 31;
    out[outPos + 21] = (in[inPos + 23] - in[inPos + 22]) >>> 33 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 22] = (in[inPos + 24] - in[inPos + 23]) >>> 40 | (in[inPos + 25] - in[inPos + 24]) << 17;
    out[outPos + 23] = (in[inPos + 25] - in[inPos + 24]) >>> 47 | (in[inPos + 26] - in[inPos + 25]) << 10;
    out[outPos + 24] = (in[inPos + 26] - in[inPos + 25]) >>> 54 | (in[inPos + 27] - in[inPos + 26]) << 3 | (in[inPos + 28] - in[inPos + 27]) << 60;
    out[outPos + 25] = (in[inPos + 28] - in[inPos + 27]) >>> 4 | (in[inPos + 29] - in[inPos + 28]) << 53;
    out[outPos + 26] = (in[inPos + 29] - in[inPos + 28]) >>> 11 | (in[inPos + 30] - in[inPos + 29]) << 46;
    out[outPos + 27] = (in[inPos + 30] - in[inPos + 29]) >>> 18 | (in[inPos + 31] - in[inPos + 30]) << 39;
    out[outPos + 28] = (in[inPos + 31] - in[inPos + 30]) >>> 25 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 29] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 25;
    out[outPos + 30] = (in[inPos + 33] - in[inPos + 32]) >>> 39 | (in[inPos + 34] - in[inPos + 33]) << 18;
    out[outPos + 31] = (in[inPos + 34] - in[inPos + 33]) >>> 46 | (in[inPos + 35] - in[inPos + 34]) << 11;
    out[outPos + 32] = (in[inPos + 35] - in[inPos + 34]) >>> 53 | (in[inPos + 36] - in[inPos + 35]) << 4 | (in[inPos + 37] - in[inPos + 36]) << 61;
    out[outPos + 33] = (in[inPos + 37] - in[inPos + 36]) >>> 3 | (in[inPos + 38] - in[inPos + 37]) << 54;
    out[outPos + 34] = (in[inPos + 38] - in[inPos + 37]) >>> 10 | (in[inPos + 39] - in[inPos + 38]) << 47;
    out[outPos + 35] = (in[inPos + 39] - in[inPos + 38]) >>> 17 | (in[inPos + 40] - in[inPos + 39]) << 40;
    out[outPos + 36] = (in[inPos + 40] - in[inPos + 39]) >>> 24 | (in[inPos + 41] - in[inPos + 40]) << 33;
    out[outPos + 37] = (in[inPos + 41] - in[inPos + 40]) >>> 31 | (in[inPos + 42] - in[inPos + 41]) << 26;
    out[outPos + 38] = (in[inPos + 42] - in[inPos + 41]) >>> 38 | (in[inPos + 43] - in[inPos + 42]) << 19;
    out[outPos + 39] = (in[inPos + 43] - in[inPos + 42]) >>> 45 | (in[inPos + 44] - in[inPos + 43]) << 12;
    out[outPos + 40] = (in[inPos + 44] - in[inPos + 43]) >>> 52 | (in[inPos + 45] - in[inPos + 44]) << 5 | (in[inPos + 46] - in[inPos + 45]) << 62;
    out[outPos + 41] = (in[inPos + 46] - in[inPos + 45]) >>> 2 | (in[inPos + 47] - in[inPos + 46]) << 55;
    out[outPos + 42] = (in[inPos + 47] - in[inPos + 46]) >>> 9 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 43] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 41;
    out[outPos + 44] = (in[inPos + 49] - in[inPos + 48]) >>> 23 | (in[inPos + 50] - in[inPos + 49]) << 34;
    out[outPos + 45] = (in[inPos + 50] - in[inPos + 49]) >>> 30 | (in[inPos + 51] - in[inPos + 50]) << 27;
    out[outPos + 46] = (in[inPos + 51] - in[inPos + 50]) >>> 37 | (in[inPos + 52] - in[inPos + 51]) << 20;
    out[outPos + 47] = (in[inPos + 52] - in[inPos + 51]) >>> 44 | (in[inPos + 53] - in[inPos + 52]) << 13;
    out[outPos + 48] = (in[inPos + 53] - in[inPos + 52]) >>> 51 | (in[inPos + 54] - in[inPos + 53]) << 6 | (in[inPos + 55] - in[inPos + 54]) << 63;
    out[outPos + 49] = (in[inPos + 55] - in[inPos + 54]) >>> 1 | (in[inPos + 56] - in[inPos + 55]) << 56;
    out[outPos + 50] = (in[inPos + 56] - in[inPos + 55]) >>> 8 | (in[inPos + 57] - in[inPos + 56]) << 49;
    out[outPos + 51] = (in[inPos + 57] - in[inPos + 56]) >>> 15 | (in[inPos + 58] - in[inPos + 57]) << 42;
    out[outPos + 52] = (in[inPos + 58] - in[inPos + 57]) >>> 22 | (in[inPos + 59] - in[inPos + 58]) << 35;
    out[outPos + 53] = (in[inPos + 59] - in[inPos + 58]) >>> 29 | (in[inPos + 60] - in[inPos + 59]) << 28;
    out[outPos + 54] = (in[inPos + 60] - in[inPos + 59]) >>> 36 | (in[inPos + 61] - in[inPos + 60]) << 21;
    out[outPos + 55] = (in[inPos + 61] - in[inPos + 60]) >>> 43 | (in[inPos + 62] - in[inPos + 61]) << 14;
    out[outPos + 56] = (in[inPos + 62] - in[inPos + 61]) >>> 50 | (in[inPos + 63] - in[inPos + 62]) << 7;
  }

  private static void unpack57(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 144115188075855871L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 57 | (in[inPos + 1] & 1125899906842623L) << 7) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 50 | (in[inPos + 2] & 8796093022207L) << 14) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 43 | (in[inPos + 3] & 68719476735L) << 21) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 36 | (in[inPos + 4] & 536870911) << 28) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 29 | (in[inPos + 5] & 4194303) << 35) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 22 | (in[inPos + 6] & 32767) << 42) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 15 | (in[inPos + 7] & 255) << 49) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 8 | (in[inPos + 8] & 1) << 56) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 1 & 144115188075855871L) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 58 | (in[inPos + 9] & 2251799813685247L) << 6) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 51 | (in[inPos + 10] & 17592186044415L) << 13) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 10] >>> 44 | (in[inPos + 11] & 137438953471L) << 20) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 11] >>> 37 | (in[inPos + 12] & 1073741823) << 27) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 12] >>> 30 | (in[inPos + 13] & 8388607) << 34) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 13] >>> 23 | (in[inPos + 14] & 65535) << 41) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 14] >>> 16 | (in[inPos + 15] & 511) << 48) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 15] >>> 9 | (in[inPos + 16] & 3) << 55) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 16] >>> 2 & 144115188075855871L) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 16] >>> 59 | (in[inPos + 17] & 4503599627370495L) << 5) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 17] >>> 52 | (in[inPos + 18] & 35184372088831L) << 12) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 18] >>> 45 | (in[inPos + 19] & 274877906943L) << 19) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 19] >>> 38 | (in[inPos + 20] & 2147483647) << 26) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 20] >>> 31 | (in[inPos + 21] & 16777215) << 33) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 21] >>> 24 | (in[inPos + 22] & 131071) << 40) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 22] >>> 17 | (in[inPos + 23] & 1023) << 47) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 23] >>> 10 | (in[inPos + 24] & 7) << 54) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 24] >>> 3 & 144115188075855871L) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 24] >>> 60 | (in[inPos + 25] & 9007199254740991L) << 4) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 25] >>> 53 | (in[inPos + 26] & 70368744177663L) << 11) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 26] >>> 46 | (in[inPos + 27] & 549755813887L) << 18) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 27] >>> 39 | (in[inPos + 28] & 4294967295L) << 25) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 28] >>> 32 | (in[inPos + 29] & 33554431) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 29] >>> 25 | (in[inPos + 30] & 262143) << 39) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 30] >>> 18 | (in[inPos + 31] & 2047) << 46) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 31] >>> 11 | (in[inPos + 32] & 15) << 53) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 32] >>> 4 & 144115188075855871L) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 32] >>> 61 | (in[inPos + 33] & 18014398509481983L) << 3) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 33] >>> 54 | (in[inPos + 34] & 140737488355327L) << 10) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 34] >>> 47 | (in[inPos + 35] & 1099511627775L) << 17) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 35] >>> 40 | (in[inPos + 36] & 8589934591L) << 24) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 36] >>> 33 | (in[inPos + 37] & 67108863) << 31) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 37] >>> 26 | (in[inPos + 38] & 524287) << 38) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 38] >>> 19 | (in[inPos + 39] & 4095) << 45) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 39] >>> 12 | (in[inPos + 40] & 31) << 52) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 40] >>> 5 & 144115188075855871L) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 40] >>> 62 | (in[inPos + 41] & 36028797018963967L) << 2) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 41] >>> 55 | (in[inPos + 42] & 281474976710655L) << 9) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 42] >>> 48 | (in[inPos + 43] & 2199023255551L) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 43] >>> 41 | (in[inPos + 44] & 17179869183L) << 23) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 44] >>> 34 | (in[inPos + 45] & 134217727) << 30) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 45] >>> 27 | (in[inPos + 46] & 1048575) << 37) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 46] >>> 20 | (in[inPos + 47] & 8191) << 44) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 47] >>> 13 | (in[inPos + 48] & 63) << 51) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 48] >>> 6 & 144115188075855871L) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 48] >>> 63 | (in[inPos + 49] & 72057594037927935L) << 1) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 49] >>> 56 | (in[inPos + 50] & 562949953421311L) << 8) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 50] >>> 49 | (in[inPos + 51] & 4398046511103L) << 15) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 51] >>> 42 | (in[inPos + 52] & 34359738367L) << 22) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 52] >>> 35 | (in[inPos + 53] & 268435455) << 29) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 53] >>> 28 | (in[inPos + 54] & 2097151) << 36) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 54] >>> 21 | (in[inPos + 55] & 16383) << 43) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 55] >>> 14 | (in[inPos + 56] & 127) << 50) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 56] >>> 7) + out[outPos + 62];
  }

  private static void pack58(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 58;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 6 | (in[inPos + 2] - in[inPos + 1]) << 52;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 12 | (in[inPos + 3] - in[inPos + 2]) << 46;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 18 | (in[inPos + 4] - in[inPos + 3]) << 40;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 24 | (in[inPos + 5] - in[inPos + 4]) << 34;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 30 | (in[inPos + 6] - in[inPos + 5]) << 28;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 36 | (in[inPos + 7] - in[inPos + 6]) << 22;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 42 | (in[inPos + 8] - in[inPos + 7]) << 16;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 48 | (in[inPos + 9] - in[inPos + 8]) << 10;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 54 | (in[inPos + 10] - in[inPos + 9]) << 4 | (in[inPos + 11] - in[inPos + 10]) << 62;
    out[outPos + 10] = (in[inPos + 11] - in[inPos + 10]) >>> 2 | (in[inPos + 12] - in[inPos + 11]) << 56;
    out[outPos + 11] = (in[inPos + 12] - in[inPos + 11]) >>> 8 | (in[inPos + 13] - in[inPos + 12]) << 50;
    out[outPos + 12] = (in[inPos + 13] - in[inPos + 12]) >>> 14 | (in[inPos + 14] - in[inPos + 13]) << 44;
    out[outPos + 13] = (in[inPos + 14] - in[inPos + 13]) >>> 20 | (in[inPos + 15] - in[inPos + 14]) << 38;
    out[outPos + 14] = (in[inPos + 15] - in[inPos + 14]) >>> 26 | (in[inPos + 16] - in[inPos + 15]) << 32;
    out[outPos + 15] = (in[inPos + 16] - in[inPos + 15]) >>> 32 | (in[inPos + 17] - in[inPos + 16]) << 26;
    out[outPos + 16] = (in[inPos + 17] - in[inPos + 16]) >>> 38 | (in[inPos + 18] - in[inPos + 17]) << 20;
    out[outPos + 17] = (in[inPos + 18] - in[inPos + 17]) >>> 44 | (in[inPos + 19] - in[inPos + 18]) << 14;
    out[outPos + 18] = (in[inPos + 19] - in[inPos + 18]) >>> 50 | (in[inPos + 20] - in[inPos + 19]) << 8;
    out[outPos + 19] = (in[inPos + 20] - in[inPos + 19]) >>> 56 | (in[inPos + 21] - in[inPos + 20]) << 2 | (in[inPos + 22] - in[inPos + 21]) << 60;
    out[outPos + 20] = (in[inPos + 22] - in[inPos + 21]) >>> 4 | (in[inPos + 23] - in[inPos + 22]) << 54;
    out[outPos + 21] = (in[inPos + 23] - in[inPos + 22]) >>> 10 | (in[inPos + 24] - in[inPos + 23]) << 48;
    out[outPos + 22] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 42;
    out[outPos + 23] = (in[inPos + 25] - in[inPos + 24]) >>> 22 | (in[inPos + 26] - in[inPos + 25]) << 36;
    out[outPos + 24] = (in[inPos + 26] - in[inPos + 25]) >>> 28 | (in[inPos + 27] - in[inPos + 26]) << 30;
    out[outPos + 25] = (in[inPos + 27] - in[inPos + 26]) >>> 34 | (in[inPos + 28] - in[inPos + 27]) << 24;
    out[outPos + 26] = (in[inPos + 28] - in[inPos + 27]) >>> 40 | (in[inPos + 29] - in[inPos + 28]) << 18;
    out[outPos + 27] = (in[inPos + 29] - in[inPos + 28]) >>> 46 | (in[inPos + 30] - in[inPos + 29]) << 12;
    out[outPos + 28] = (in[inPos + 30] - in[inPos + 29]) >>> 52 | (in[inPos + 31] - in[inPos + 30]) << 6;
    out[outPos + 29] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 58;
    out[outPos + 30] = (in[inPos + 33] - in[inPos + 32]) >>> 6 | (in[inPos + 34] - in[inPos + 33]) << 52;
    out[outPos + 31] = (in[inPos + 34] - in[inPos + 33]) >>> 12 | (in[inPos + 35] - in[inPos + 34]) << 46;
    out[outPos + 32] = (in[inPos + 35] - in[inPos + 34]) >>> 18 | (in[inPos + 36] - in[inPos + 35]) << 40;
    out[outPos + 33] = (in[inPos + 36] - in[inPos + 35]) >>> 24 | (in[inPos + 37] - in[inPos + 36]) << 34;
    out[outPos + 34] = (in[inPos + 37] - in[inPos + 36]) >>> 30 | (in[inPos + 38] - in[inPos + 37]) << 28;
    out[outPos + 35] = (in[inPos + 38] - in[inPos + 37]) >>> 36 | (in[inPos + 39] - in[inPos + 38]) << 22;
    out[outPos + 36] = (in[inPos + 39] - in[inPos + 38]) >>> 42 | (in[inPos + 40] - in[inPos + 39]) << 16;
    out[outPos + 37] = (in[inPos + 40] - in[inPos + 39]) >>> 48 | (in[inPos + 41] - in[inPos + 40]) << 10;
    out[outPos + 38] = (in[inPos + 41] - in[inPos + 40]) >>> 54 | (in[inPos + 42] - in[inPos + 41]) << 4 | (in[inPos + 43] - in[inPos + 42]) << 62;
    out[outPos + 39] = (in[inPos + 43] - in[inPos + 42]) >>> 2 | (in[inPos + 44] - in[inPos + 43]) << 56;
    out[outPos + 40] = (in[inPos + 44] - in[inPos + 43]) >>> 8 | (in[inPos + 45] - in[inPos + 44]) << 50;
    out[outPos + 41] = (in[inPos + 45] - in[inPos + 44]) >>> 14 | (in[inPos + 46] - in[inPos + 45]) << 44;
    out[outPos + 42] = (in[inPos + 46] - in[inPos + 45]) >>> 20 | (in[inPos + 47] - in[inPos + 46]) << 38;
    out[outPos + 43] = (in[inPos + 47] - in[inPos + 46]) >>> 26 | (in[inPos + 48] - in[inPos + 47]) << 32;
    out[outPos + 44] = (in[inPos + 48] - in[inPos + 47]) >>> 32 | (in[inPos + 49] - in[inPos + 48]) << 26;
    out[outPos + 45] = (in[inPos + 49] - in[inPos + 48]) >>> 38 | (in[inPos + 50] - in[inPos + 49]) << 20;
    out[outPos + 46] = (in[inPos + 50] - in[inPos + 49]) >>> 44 | (in[inPos + 51] - in[inPos + 50]) << 14;
    out[outPos + 47] = (in[inPos + 51] - in[inPos + 50]) >>> 50 | (in[inPos + 52] - in[inPos + 51]) << 8;
    out[outPos + 48] = (in[inPos + 52] - in[inPos + 51]) >>> 56 | (in[inPos + 53] - in[inPos + 52]) << 2 | (in[inPos + 54] - in[inPos + 53]) << 60;
    out[outPos + 49] = (in[inPos + 54] - in[inPos + 53]) >>> 4 | (in[inPos + 55] - in[inPos + 54]) << 54;
    out[outPos + 50] = (in[inPos + 55] - in[inPos + 54]) >>> 10 | (in[inPos + 56] - in[inPos + 55]) << 48;
    out[outPos + 51] = (in[inPos + 56] - in[inPos + 55]) >>> 16 | (in[inPos + 57] - in[inPos + 56]) << 42;
    out[outPos + 52] = (in[inPos + 57] - in[inPos + 56]) >>> 22 | (in[inPos + 58] - in[inPos + 57]) << 36;
    out[outPos + 53] = (in[inPos + 58] - in[inPos + 57]) >>> 28 | (in[inPos + 59] - in[inPos + 58]) << 30;
    out[outPos + 54] = (in[inPos + 59] - in[inPos + 58]) >>> 34 | (in[inPos + 60] - in[inPos + 59]) << 24;
    out[outPos + 55] = (in[inPos + 60] - in[inPos + 59]) >>> 40 | (in[inPos + 61] - in[inPos + 60]) << 18;
    out[outPos + 56] = (in[inPos + 61] - in[inPos + 60]) >>> 46 | (in[inPos + 62] - in[inPos + 61]) << 12;
    out[outPos + 57] = (in[inPos + 62] - in[inPos + 61]) >>> 52 | (in[inPos + 63] - in[inPos + 62]) << 6;
  }

  private static void unpack58(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 288230376151711743L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 58 | (in[inPos + 1] & 4503599627370495L) << 6) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 52 | (in[inPos + 2] & 70368744177663L) << 12) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 46 | (in[inPos + 3] & 1099511627775L) << 18) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 40 | (in[inPos + 4] & 17179869183L) << 24) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 34 | (in[inPos + 5] & 268435455) << 30) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 28 | (in[inPos + 6] & 4194303) << 36) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 22 | (in[inPos + 7] & 65535) << 42) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 16 | (in[inPos + 8] & 1023) << 48) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 10 | (in[inPos + 9] & 15) << 54) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 4 & 288230376151711743L) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 62 | (in[inPos + 10] & 72057594037927935L) << 2) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 10] >>> 56 | (in[inPos + 11] & 1125899906842623L) << 8) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 11] >>> 50 | (in[inPos + 12] & 17592186044415L) << 14) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 12] >>> 44 | (in[inPos + 13] & 274877906943L) << 20) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 13] >>> 38 | (in[inPos + 14] & 4294967295L) << 26) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 14] >>> 32 | (in[inPos + 15] & 67108863) << 32) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 15] >>> 26 | (in[inPos + 16] & 1048575) << 38) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 16] >>> 20 | (in[inPos + 17] & 16383) << 44) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 17] >>> 14 | (in[inPos + 18] & 255) << 50) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 18] >>> 8 | (in[inPos + 19] & 3) << 56) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 19] >>> 2 & 288230376151711743L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 19] >>> 60 | (in[inPos + 20] & 18014398509481983L) << 4) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 20] >>> 54 | (in[inPos + 21] & 281474976710655L) << 10) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 21] >>> 48 | (in[inPos + 22] & 4398046511103L) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 22] >>> 42 | (in[inPos + 23] & 68719476735L) << 22) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 23] >>> 36 | (in[inPos + 24] & 1073741823) << 28) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 24] >>> 30 | (in[inPos + 25] & 16777215) << 34) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 25] >>> 24 | (in[inPos + 26] & 262143) << 40) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 26] >>> 18 | (in[inPos + 27] & 4095) << 46) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 27] >>> 12 | (in[inPos + 28] & 63) << 52) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 28] >>> 6) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 29] & 288230376151711743L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 29] >>> 58 | (in[inPos + 30] & 4503599627370495L) << 6) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 30] >>> 52 | (in[inPos + 31] & 70368744177663L) << 12) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 31] >>> 46 | (in[inPos + 32] & 1099511627775L) << 18) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 32] >>> 40 | (in[inPos + 33] & 17179869183L) << 24) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 33] >>> 34 | (in[inPos + 34] & 268435455) << 30) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 34] >>> 28 | (in[inPos + 35] & 4194303) << 36) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 35] >>> 22 | (in[inPos + 36] & 65535) << 42) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 36] >>> 16 | (in[inPos + 37] & 1023) << 48) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 37] >>> 10 | (in[inPos + 38] & 15) << 54) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 38] >>> 4 & 288230376151711743L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 38] >>> 62 | (in[inPos + 39] & 72057594037927935L) << 2) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 39] >>> 56 | (in[inPos + 40] & 1125899906842623L) << 8) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 40] >>> 50 | (in[inPos + 41] & 17592186044415L) << 14) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 41] >>> 44 | (in[inPos + 42] & 274877906943L) << 20) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 42] >>> 38 | (in[inPos + 43] & 4294967295L) << 26) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 43] >>> 32 | (in[inPos + 44] & 67108863) << 32) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 44] >>> 26 | (in[inPos + 45] & 1048575) << 38) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 45] >>> 20 | (in[inPos + 46] & 16383) << 44) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 46] >>> 14 | (in[inPos + 47] & 255) << 50) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 47] >>> 8 | (in[inPos + 48] & 3) << 56) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 48] >>> 2 & 288230376151711743L) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 48] >>> 60 | (in[inPos + 49] & 18014398509481983L) << 4) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 49] >>> 54 | (in[inPos + 50] & 281474976710655L) << 10) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 50] >>> 48 | (in[inPos + 51] & 4398046511103L) << 16) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 51] >>> 42 | (in[inPos + 52] & 68719476735L) << 22) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 52] >>> 36 | (in[inPos + 53] & 1073741823) << 28) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 53] >>> 30 | (in[inPos + 54] & 16777215) << 34) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 54] >>> 24 | (in[inPos + 55] & 262143) << 40) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 55] >>> 18 | (in[inPos + 56] & 4095) << 46) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 56] >>> 12 | (in[inPos + 57] & 63) << 52) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 57] >>> 6) + out[outPos + 62];
  }

  private static void pack59(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 59;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 5 | (in[inPos + 2] - in[inPos + 1]) << 54;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 10 | (in[inPos + 3] - in[inPos + 2]) << 49;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 15 | (in[inPos + 4] - in[inPos + 3]) << 44;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 20 | (in[inPos + 5] - in[inPos + 4]) << 39;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 25 | (in[inPos + 6] - in[inPos + 5]) << 34;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 30 | (in[inPos + 7] - in[inPos + 6]) << 29;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 35 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 40 | (in[inPos + 9] - in[inPos + 8]) << 19;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 45 | (in[inPos + 10] - in[inPos + 9]) << 14;
    out[outPos + 10] = (in[inPos + 10] - in[inPos + 9]) >>> 50 | (in[inPos + 11] - in[inPos + 10]) << 9;
    out[outPos + 11] = (in[inPos + 11] - in[inPos + 10]) >>> 55 | (in[inPos + 12] - in[inPos + 11]) << 4 | (in[inPos + 13] - in[inPos + 12]) << 63;
    out[outPos + 12] = (in[inPos + 13] - in[inPos + 12]) >>> 1 | (in[inPos + 14] - in[inPos + 13]) << 58;
    out[outPos + 13] = (in[inPos + 14] - in[inPos + 13]) >>> 6 | (in[inPos + 15] - in[inPos + 14]) << 53;
    out[outPos + 14] = (in[inPos + 15] - in[inPos + 14]) >>> 11 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 15] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 43;
    out[outPos + 16] = (in[inPos + 17] - in[inPos + 16]) >>> 21 | (in[inPos + 18] - in[inPos + 17]) << 38;
    out[outPos + 17] = (in[inPos + 18] - in[inPos + 17]) >>> 26 | (in[inPos + 19] - in[inPos + 18]) << 33;
    out[outPos + 18] = (in[inPos + 19] - in[inPos + 18]) >>> 31 | (in[inPos + 20] - in[inPos + 19]) << 28;
    out[outPos + 19] = (in[inPos + 20] - in[inPos + 19]) >>> 36 | (in[inPos + 21] - in[inPos + 20]) << 23;
    out[outPos + 20] = (in[inPos + 21] - in[inPos + 20]) >>> 41 | (in[inPos + 22] - in[inPos + 21]) << 18;
    out[outPos + 21] = (in[inPos + 22] - in[inPos + 21]) >>> 46 | (in[inPos + 23] - in[inPos + 22]) << 13;
    out[outPos + 22] = (in[inPos + 23] - in[inPos + 22]) >>> 51 | (in[inPos + 24] - in[inPos + 23]) << 8;
    out[outPos + 23] = (in[inPos + 24] - in[inPos + 23]) >>> 56 | (in[inPos + 25] - in[inPos + 24]) << 3 | (in[inPos + 26] - in[inPos + 25]) << 62;
    out[outPos + 24] = (in[inPos + 26] - in[inPos + 25]) >>> 2 | (in[inPos + 27] - in[inPos + 26]) << 57;
    out[outPos + 25] = (in[inPos + 27] - in[inPos + 26]) >>> 7 | (in[inPos + 28] - in[inPos + 27]) << 52;
    out[outPos + 26] = (in[inPos + 28] - in[inPos + 27]) >>> 12 | (in[inPos + 29] - in[inPos + 28]) << 47;
    out[outPos + 27] = (in[inPos + 29] - in[inPos + 28]) >>> 17 | (in[inPos + 30] - in[inPos + 29]) << 42;
    out[outPos + 28] = (in[inPos + 30] - in[inPos + 29]) >>> 22 | (in[inPos + 31] - in[inPos + 30]) << 37;
    out[outPos + 29] = (in[inPos + 31] - in[inPos + 30]) >>> 27 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 30] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 27;
    out[outPos + 31] = (in[inPos + 33] - in[inPos + 32]) >>> 37 | (in[inPos + 34] - in[inPos + 33]) << 22;
    out[outPos + 32] = (in[inPos + 34] - in[inPos + 33]) >>> 42 | (in[inPos + 35] - in[inPos + 34]) << 17;
    out[outPos + 33] = (in[inPos + 35] - in[inPos + 34]) >>> 47 | (in[inPos + 36] - in[inPos + 35]) << 12;
    out[outPos + 34] = (in[inPos + 36] - in[inPos + 35]) >>> 52 | (in[inPos + 37] - in[inPos + 36]) << 7;
    out[outPos + 35] = (in[inPos + 37] - in[inPos + 36]) >>> 57 | (in[inPos + 38] - in[inPos + 37]) << 2 | (in[inPos + 39] - in[inPos + 38]) << 61;
    out[outPos + 36] = (in[inPos + 39] - in[inPos + 38]) >>> 3 | (in[inPos + 40] - in[inPos + 39]) << 56;
    out[outPos + 37] = (in[inPos + 40] - in[inPos + 39]) >>> 8 | (in[inPos + 41] - in[inPos + 40]) << 51;
    out[outPos + 38] = (in[inPos + 41] - in[inPos + 40]) >>> 13 | (in[inPos + 42] - in[inPos + 41]) << 46;
    out[outPos + 39] = (in[inPos + 42] - in[inPos + 41]) >>> 18 | (in[inPos + 43] - in[inPos + 42]) << 41;
    out[outPos + 40] = (in[inPos + 43] - in[inPos + 42]) >>> 23 | (in[inPos + 44] - in[inPos + 43]) << 36;
    out[outPos + 41] = (in[inPos + 44] - in[inPos + 43]) >>> 28 | (in[inPos + 45] - in[inPos + 44]) << 31;
    out[outPos + 42] = (in[inPos + 45] - in[inPos + 44]) >>> 33 | (in[inPos + 46] - in[inPos + 45]) << 26;
    out[outPos + 43] = (in[inPos + 46] - in[inPos + 45]) >>> 38 | (in[inPos + 47] - in[inPos + 46]) << 21;
    out[outPos + 44] = (in[inPos + 47] - in[inPos + 46]) >>> 43 | (in[inPos + 48] - in[inPos + 47]) << 16;
    out[outPos + 45] = (in[inPos + 48] - in[inPos + 47]) >>> 48 | (in[inPos + 49] - in[inPos + 48]) << 11;
    out[outPos + 46] = (in[inPos + 49] - in[inPos + 48]) >>> 53 | (in[inPos + 50] - in[inPos + 49]) << 6;
    out[outPos + 47] = (in[inPos + 50] - in[inPos + 49]) >>> 58 | (in[inPos + 51] - in[inPos + 50]) << 1 | (in[inPos + 52] - in[inPos + 51]) << 60;
    out[outPos + 48] = (in[inPos + 52] - in[inPos + 51]) >>> 4 | (in[inPos + 53] - in[inPos + 52]) << 55;
    out[outPos + 49] = (in[inPos + 53] - in[inPos + 52]) >>> 9 | (in[inPos + 54] - in[inPos + 53]) << 50;
    out[outPos + 50] = (in[inPos + 54] - in[inPos + 53]) >>> 14 | (in[inPos + 55] - in[inPos + 54]) << 45;
    out[outPos + 51] = (in[inPos + 55] - in[inPos + 54]) >>> 19 | (in[inPos + 56] - in[inPos + 55]) << 40;
    out[outPos + 52] = (in[inPos + 56] - in[inPos + 55]) >>> 24 | (in[inPos + 57] - in[inPos + 56]) << 35;
    out[outPos + 53] = (in[inPos + 57] - in[inPos + 56]) >>> 29 | (in[inPos + 58] - in[inPos + 57]) << 30;
    out[outPos + 54] = (in[inPos + 58] - in[inPos + 57]) >>> 34 | (in[inPos + 59] - in[inPos + 58]) << 25;
    out[outPos + 55] = (in[inPos + 59] - in[inPos + 58]) >>> 39 | (in[inPos + 60] - in[inPos + 59]) << 20;
    out[outPos + 56] = (in[inPos + 60] - in[inPos + 59]) >>> 44 | (in[inPos + 61] - in[inPos + 60]) << 15;
    out[outPos + 57] = (in[inPos + 61] - in[inPos + 60]) >>> 49 | (in[inPos + 62] - in[inPos + 61]) << 10;
    out[outPos + 58] = (in[inPos + 62] - in[inPos + 61]) >>> 54 | (in[inPos + 63] - in[inPos + 62]) << 5;
  }

  private static void unpack59(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 576460752303423487L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 59 | (in[inPos + 1] & 18014398509481983L) << 5) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 54 | (in[inPos + 2] & 562949953421311L) << 10) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 49 | (in[inPos + 3] & 17592186044415L) << 15) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 44 | (in[inPos + 4] & 549755813887L) << 20) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 39 | (in[inPos + 5] & 17179869183L) << 25) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 34 | (in[inPos + 6] & 536870911) << 30) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 29 | (in[inPos + 7] & 16777215) << 35) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 24 | (in[inPos + 8] & 524287) << 40) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 19 | (in[inPos + 9] & 16383) << 45) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 14 | (in[inPos + 10] & 511) << 50) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 10] >>> 9 | (in[inPos + 11] & 15) << 55) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 11] >>> 4 & 576460752303423487L) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 11] >>> 63 | (in[inPos + 12] & 288230376151711743L) << 1) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 12] >>> 58 | (in[inPos + 13] & 9007199254740991L) << 6) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 13] >>> 53 | (in[inPos + 14] & 281474976710655L) << 11) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 14] >>> 48 | (in[inPos + 15] & 8796093022207L) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 15] >>> 43 | (in[inPos + 16] & 274877906943L) << 21) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 16] >>> 38 | (in[inPos + 17] & 8589934591L) << 26) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 17] >>> 33 | (in[inPos + 18] & 268435455) << 31) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 18] >>> 28 | (in[inPos + 19] & 8388607) << 36) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 19] >>> 23 | (in[inPos + 20] & 262143) << 41) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 20] >>> 18 | (in[inPos + 21] & 8191) << 46) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 21] >>> 13 | (in[inPos + 22] & 255) << 51) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 22] >>> 8 | (in[inPos + 23] & 7) << 56) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 23] >>> 3 & 576460752303423487L) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 23] >>> 62 | (in[inPos + 24] & 144115188075855871L) << 2) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 24] >>> 57 | (in[inPos + 25] & 4503599627370495L) << 7) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 25] >>> 52 | (in[inPos + 26] & 140737488355327L) << 12) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 26] >>> 47 | (in[inPos + 27] & 4398046511103L) << 17) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 27] >>> 42 | (in[inPos + 28] & 137438953471L) << 22) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 28] >>> 37 | (in[inPos + 29] & 4294967295L) << 27) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 29] >>> 32 | (in[inPos + 30] & 134217727) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 30] >>> 27 | (in[inPos + 31] & 4194303) << 37) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 31] >>> 22 | (in[inPos + 32] & 131071) << 42) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 32] >>> 17 | (in[inPos + 33] & 4095) << 47) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 33] >>> 12 | (in[inPos + 34] & 127) << 52) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 34] >>> 7 | (in[inPos + 35] & 3) << 57) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 35] >>> 2 & 576460752303423487L) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 35] >>> 61 | (in[inPos + 36] & 72057594037927935L) << 3) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 36] >>> 56 | (in[inPos + 37] & 2251799813685247L) << 8) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 37] >>> 51 | (in[inPos + 38] & 70368744177663L) << 13) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 38] >>> 46 | (in[inPos + 39] & 2199023255551L) << 18) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 39] >>> 41 | (in[inPos + 40] & 68719476735L) << 23) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 40] >>> 36 | (in[inPos + 41] & 2147483647) << 28) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 41] >>> 31 | (in[inPos + 42] & 67108863) << 33) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 42] >>> 26 | (in[inPos + 43] & 2097151) << 38) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 43] >>> 21 | (in[inPos + 44] & 65535) << 43) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 44] >>> 16 | (in[inPos + 45] & 2047) << 48) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 45] >>> 11 | (in[inPos + 46] & 63) << 53) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 46] >>> 6 | (in[inPos + 47] & 1) << 58) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 47] >>> 1 & 576460752303423487L) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 47] >>> 60 | (in[inPos + 48] & 36028797018963967L) << 4) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 48] >>> 55 | (in[inPos + 49] & 1125899906842623L) << 9) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 49] >>> 50 | (in[inPos + 50] & 35184372088831L) << 14) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 50] >>> 45 | (in[inPos + 51] & 1099511627775L) << 19) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 51] >>> 40 | (in[inPos + 52] & 34359738367L) << 24) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 52] >>> 35 | (in[inPos + 53] & 1073741823) << 29) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 53] >>> 30 | (in[inPos + 54] & 33554431) << 34) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 54] >>> 25 | (in[inPos + 55] & 1048575) << 39) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 55] >>> 20 | (in[inPos + 56] & 32767) << 44) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 56] >>> 15 | (in[inPos + 57] & 1023) << 49) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 57] >>> 10 | (in[inPos + 58] & 31) << 54) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 58] >>> 5) + out[outPos + 62];
  }

  private static void pack60(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 60;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 4 | (in[inPos + 2] - in[inPos + 1]) << 56;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 8 | (in[inPos + 3] - in[inPos + 2]) << 52;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 12 | (in[inPos + 4] - in[inPos + 3]) << 48;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 16 | (in[inPos + 5] - in[inPos + 4]) << 44;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 20 | (in[inPos + 6] - in[inPos + 5]) << 40;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 24 | (in[inPos + 7] - in[inPos + 6]) << 36;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 28 | (in[inPos + 8] - in[inPos + 7]) << 32;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 32 | (in[inPos + 9] - in[inPos + 8]) << 28;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 36 | (in[inPos + 10] - in[inPos + 9]) << 24;
    out[outPos + 10] = (in[inPos + 10] - in[inPos + 9]) >>> 40 | (in[inPos + 11] - in[inPos + 10]) << 20;
    out[outPos + 11] = (in[inPos + 11] - in[inPos + 10]) >>> 44 | (in[inPos + 12] - in[inPos + 11]) << 16;
    out[outPos + 12] = (in[inPos + 12] - in[inPos + 11]) >>> 48 | (in[inPos + 13] - in[inPos + 12]) << 12;
    out[outPos + 13] = (in[inPos + 13] - in[inPos + 12]) >>> 52 | (in[inPos + 14] - in[inPos + 13]) << 8;
    out[outPos + 14] = (in[inPos + 14] - in[inPos + 13]) >>> 56 | (in[inPos + 15] - in[inPos + 14]) << 4;
    out[outPos + 15] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 60;
    out[outPos + 16] = (in[inPos + 17] - in[inPos + 16]) >>> 4 | (in[inPos + 18] - in[inPos + 17]) << 56;
    out[outPos + 17] = (in[inPos + 18] - in[inPos + 17]) >>> 8 | (in[inPos + 19] - in[inPos + 18]) << 52;
    out[outPos + 18] = (in[inPos + 19] - in[inPos + 18]) >>> 12 | (in[inPos + 20] - in[inPos + 19]) << 48;
    out[outPos + 19] = (in[inPos + 20] - in[inPos + 19]) >>> 16 | (in[inPos + 21] - in[inPos + 20]) << 44;
    out[outPos + 20] = (in[inPos + 21] - in[inPos + 20]) >>> 20 | (in[inPos + 22] - in[inPos + 21]) << 40;
    out[outPos + 21] = (in[inPos + 22] - in[inPos + 21]) >>> 24 | (in[inPos + 23] - in[inPos + 22]) << 36;
    out[outPos + 22] = (in[inPos + 23] - in[inPos + 22]) >>> 28 | (in[inPos + 24] - in[inPos + 23]) << 32;
    out[outPos + 23] = (in[inPos + 24] - in[inPos + 23]) >>> 32 | (in[inPos + 25] - in[inPos + 24]) << 28;
    out[outPos + 24] = (in[inPos + 25] - in[inPos + 24]) >>> 36 | (in[inPos + 26] - in[inPos + 25]) << 24;
    out[outPos + 25] = (in[inPos + 26] - in[inPos + 25]) >>> 40 | (in[inPos + 27] - in[inPos + 26]) << 20;
    out[outPos + 26] = (in[inPos + 27] - in[inPos + 26]) >>> 44 | (in[inPos + 28] - in[inPos + 27]) << 16;
    out[outPos + 27] = (in[inPos + 28] - in[inPos + 27]) >>> 48 | (in[inPos + 29] - in[inPos + 28]) << 12;
    out[outPos + 28] = (in[inPos + 29] - in[inPos + 28]) >>> 52 | (in[inPos + 30] - in[inPos + 29]) << 8;
    out[outPos + 29] = (in[inPos + 30] - in[inPos + 29]) >>> 56 | (in[inPos + 31] - in[inPos + 30]) << 4;
    out[outPos + 30] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 60;
    out[outPos + 31] = (in[inPos + 33] - in[inPos + 32]) >>> 4 | (in[inPos + 34] - in[inPos + 33]) << 56;
    out[outPos + 32] = (in[inPos + 34] - in[inPos + 33]) >>> 8 | (in[inPos + 35] - in[inPos + 34]) << 52;
    out[outPos + 33] = (in[inPos + 35] - in[inPos + 34]) >>> 12 | (in[inPos + 36] - in[inPos + 35]) << 48;
    out[outPos + 34] = (in[inPos + 36] - in[inPos + 35]) >>> 16 | (in[inPos + 37] - in[inPos + 36]) << 44;
    out[outPos + 35] = (in[inPos + 37] - in[inPos + 36]) >>> 20 | (in[inPos + 38] - in[inPos + 37]) << 40;
    out[outPos + 36] = (in[inPos + 38] - in[inPos + 37]) >>> 24 | (in[inPos + 39] - in[inPos + 38]) << 36;
    out[outPos + 37] = (in[inPos + 39] - in[inPos + 38]) >>> 28 | (in[inPos + 40] - in[inPos + 39]) << 32;
    out[outPos + 38] = (in[inPos + 40] - in[inPos + 39]) >>> 32 | (in[inPos + 41] - in[inPos + 40]) << 28;
    out[outPos + 39] = (in[inPos + 41] - in[inPos + 40]) >>> 36 | (in[inPos + 42] - in[inPos + 41]) << 24;
    out[outPos + 40] = (in[inPos + 42] - in[inPos + 41]) >>> 40 | (in[inPos + 43] - in[inPos + 42]) << 20;
    out[outPos + 41] = (in[inPos + 43] - in[inPos + 42]) >>> 44 | (in[inPos + 44] - in[inPos + 43]) << 16;
    out[outPos + 42] = (in[inPos + 44] - in[inPos + 43]) >>> 48 | (in[inPos + 45] - in[inPos + 44]) << 12;
    out[outPos + 43] = (in[inPos + 45] - in[inPos + 44]) >>> 52 | (in[inPos + 46] - in[inPos + 45]) << 8;
    out[outPos + 44] = (in[inPos + 46] - in[inPos + 45]) >>> 56 | (in[inPos + 47] - in[inPos + 46]) << 4;
    out[outPos + 45] = (in[inPos + 48] - in[inPos + 47]) | (in[inPos + 49] - in[inPos + 48]) << 60;
    out[outPos + 46] = (in[inPos + 49] - in[inPos + 48]) >>> 4 | (in[inPos + 50] - in[inPos + 49]) << 56;
    out[outPos + 47] = (in[inPos + 50] - in[inPos + 49]) >>> 8 | (in[inPos + 51] - in[inPos + 50]) << 52;
    out[outPos + 48] = (in[inPos + 51] - in[inPos + 50]) >>> 12 | (in[inPos + 52] - in[inPos + 51]) << 48;
    out[outPos + 49] = (in[inPos + 52] - in[inPos + 51]) >>> 16 | (in[inPos + 53] - in[inPos + 52]) << 44;
    out[outPos + 50] = (in[inPos + 53] - in[inPos + 52]) >>> 20 | (in[inPos + 54] - in[inPos + 53]) << 40;
    out[outPos + 51] = (in[inPos + 54] - in[inPos + 53]) >>> 24 | (in[inPos + 55] - in[inPos + 54]) << 36;
    out[outPos + 52] = (in[inPos + 55] - in[inPos + 54]) >>> 28 | (in[inPos + 56] - in[inPos + 55]) << 32;
    out[outPos + 53] = (in[inPos + 56] - in[inPos + 55]) >>> 32 | (in[inPos + 57] - in[inPos + 56]) << 28;
    out[outPos + 54] = (in[inPos + 57] - in[inPos + 56]) >>> 36 | (in[inPos + 58] - in[inPos + 57]) << 24;
    out[outPos + 55] = (in[inPos + 58] - in[inPos + 57]) >>> 40 | (in[inPos + 59] - in[inPos + 58]) << 20;
    out[outPos + 56] = (in[inPos + 59] - in[inPos + 58]) >>> 44 | (in[inPos + 60] - in[inPos + 59]) << 16;
    out[outPos + 57] = (in[inPos + 60] - in[inPos + 59]) >>> 48 | (in[inPos + 61] - in[inPos + 60]) << 12;
    out[outPos + 58] = (in[inPos + 61] - in[inPos + 60]) >>> 52 | (in[inPos + 62] - in[inPos + 61]) << 8;
    out[outPos + 59] = (in[inPos + 62] - in[inPos + 61]) >>> 56 | (in[inPos + 63] - in[inPos + 62]) << 4;
  }

  private static void unpack60(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1152921504606846975L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 60 | (in[inPos + 1] & 72057594037927935L) << 4) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 56 | (in[inPos + 2] & 4503599627370495L) << 8) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 52 | (in[inPos + 3] & 281474976710655L) << 12) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 48 | (in[inPos + 4] & 17592186044415L) << 16) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 44 | (in[inPos + 5] & 1099511627775L) << 20) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 40 | (in[inPos + 6] & 68719476735L) << 24) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 36 | (in[inPos + 7] & 4294967295L) << 28) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 32 | (in[inPos + 8] & 268435455) << 32) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 28 | (in[inPos + 9] & 16777215) << 36) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 24 | (in[inPos + 10] & 1048575) << 40) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 10] >>> 20 | (in[inPos + 11] & 65535) << 44) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 11] >>> 16 | (in[inPos + 12] & 4095) << 48) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 12] >>> 12 | (in[inPos + 13] & 255) << 52) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 13] >>> 8 | (in[inPos + 14] & 15) << 56) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 14] >>> 4) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 15] & 1152921504606846975L) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 15] >>> 60 | (in[inPos + 16] & 72057594037927935L) << 4) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 16] >>> 56 | (in[inPos + 17] & 4503599627370495L) << 8) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 17] >>> 52 | (in[inPos + 18] & 281474976710655L) << 12) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 18] >>> 48 | (in[inPos + 19] & 17592186044415L) << 16) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 1099511627775L) << 20) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 20] >>> 40 | (in[inPos + 21] & 68719476735L) << 24) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 21] >>> 36 | (in[inPos + 22] & 4294967295L) << 28) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 22] >>> 32 | (in[inPos + 23] & 268435455) << 32) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 23] >>> 28 | (in[inPos + 24] & 16777215) << 36) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 24] >>> 24 | (in[inPos + 25] & 1048575) << 40) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 25] >>> 20 | (in[inPos + 26] & 65535) << 44) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 26] >>> 16 | (in[inPos + 27] & 4095) << 48) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 27] >>> 12 | (in[inPos + 28] & 255) << 52) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 28] >>> 8 | (in[inPos + 29] & 15) << 56) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 29] >>> 4) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 30] & 1152921504606846975L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 30] >>> 60 | (in[inPos + 31] & 72057594037927935L) << 4) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 31] >>> 56 | (in[inPos + 32] & 4503599627370495L) << 8) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 32] >>> 52 | (in[inPos + 33] & 281474976710655L) << 12) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 33] >>> 48 | (in[inPos + 34] & 17592186044415L) << 16) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 34] >>> 44 | (in[inPos + 35] & 1099511627775L) << 20) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 35] >>> 40 | (in[inPos + 36] & 68719476735L) << 24) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 36] >>> 36 | (in[inPos + 37] & 4294967295L) << 28) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 37] >>> 32 | (in[inPos + 38] & 268435455) << 32) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 38] >>> 28 | (in[inPos + 39] & 16777215) << 36) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 39] >>> 24 | (in[inPos + 40] & 1048575) << 40) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 40] >>> 20 | (in[inPos + 41] & 65535) << 44) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 41] >>> 16 | (in[inPos + 42] & 4095) << 48) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 42] >>> 12 | (in[inPos + 43] & 255) << 52) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 43] >>> 8 | (in[inPos + 44] & 15) << 56) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 44] >>> 4) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 45] & 1152921504606846975L) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 45] >>> 60 | (in[inPos + 46] & 72057594037927935L) << 4) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 46] >>> 56 | (in[inPos + 47] & 4503599627370495L) << 8) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 47] >>> 52 | (in[inPos + 48] & 281474976710655L) << 12) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 48] >>> 48 | (in[inPos + 49] & 17592186044415L) << 16) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 49] >>> 44 | (in[inPos + 50] & 1099511627775L) << 20) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 50] >>> 40 | (in[inPos + 51] & 68719476735L) << 24) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 51] >>> 36 | (in[inPos + 52] & 4294967295L) << 28) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 52] >>> 32 | (in[inPos + 53] & 268435455) << 32) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 53] >>> 28 | (in[inPos + 54] & 16777215) << 36) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 54] >>> 24 | (in[inPos + 55] & 1048575) << 40) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 55] >>> 20 | (in[inPos + 56] & 65535) << 44) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 56] >>> 16 | (in[inPos + 57] & 4095) << 48) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 57] >>> 12 | (in[inPos + 58] & 255) << 52) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 58] >>> 8 | (in[inPos + 59] & 15) << 56) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 59] >>> 4) + out[outPos + 62];
  }

  private static void pack61(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 61;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 3 | (in[inPos + 2] - in[inPos + 1]) << 58;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 6 | (in[inPos + 3] - in[inPos + 2]) << 55;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 9 | (in[inPos + 4] - in[inPos + 3]) << 52;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 12 | (in[inPos + 5] - in[inPos + 4]) << 49;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 15 | (in[inPos + 6] - in[inPos + 5]) << 46;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 18 | (in[inPos + 7] - in[inPos + 6]) << 43;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 21 | (in[inPos + 8] - in[inPos + 7]) << 40;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 24 | (in[inPos + 9] - in[inPos + 8]) << 37;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 27 | (in[inPos + 10] - in[inPos + 9]) << 34;
    out[outPos + 10] = (in[inPos + 10] - in[inPos + 9]) >>> 30 | (in[inPos + 11] - in[inPos + 10]) << 31;
    out[outPos + 11] = (in[inPos + 11] - in[inPos + 10]) >>> 33 | (in[inPos + 12] - in[inPos + 11]) << 28;
    out[outPos + 12] = (in[inPos + 12] - in[inPos + 11]) >>> 36 | (in[inPos + 13] - in[inPos + 12]) << 25;
    out[outPos + 13] = (in[inPos + 13] - in[inPos + 12]) >>> 39 | (in[inPos + 14] - in[inPos + 13]) << 22;
    out[outPos + 14] = (in[inPos + 14] - in[inPos + 13]) >>> 42 | (in[inPos + 15] - in[inPos + 14]) << 19;
    out[outPos + 15] = (in[inPos + 15] - in[inPos + 14]) >>> 45 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 16] = (in[inPos + 16] - in[inPos + 15]) >>> 48 | (in[inPos + 17] - in[inPos + 16]) << 13;
    out[outPos + 17] = (in[inPos + 17] - in[inPos + 16]) >>> 51 | (in[inPos + 18] - in[inPos + 17]) << 10;
    out[outPos + 18] = (in[inPos + 18] - in[inPos + 17]) >>> 54 | (in[inPos + 19] - in[inPos + 18]) << 7;
    out[outPos + 19] = (in[inPos + 19] - in[inPos + 18]) >>> 57 | (in[inPos + 20] - in[inPos + 19]) << 4;
    out[outPos + 20] = (in[inPos + 20] - in[inPos + 19]) >>> 60 | (in[inPos + 21] - in[inPos + 20]) << 1 | (in[inPos + 22] - in[inPos + 21]) << 62;
    out[outPos + 21] = (in[inPos + 22] - in[inPos + 21]) >>> 2 | (in[inPos + 23] - in[inPos + 22]) << 59;
    out[outPos + 22] = (in[inPos + 23] - in[inPos + 22]) >>> 5 | (in[inPos + 24] - in[inPos + 23]) << 56;
    out[outPos + 23] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 53;
    out[outPos + 24] = (in[inPos + 25] - in[inPos + 24]) >>> 11 | (in[inPos + 26] - in[inPos + 25]) << 50;
    out[outPos + 25] = (in[inPos + 26] - in[inPos + 25]) >>> 14 | (in[inPos + 27] - in[inPos + 26]) << 47;
    out[outPos + 26] = (in[inPos + 27] - in[inPos + 26]) >>> 17 | (in[inPos + 28] - in[inPos + 27]) << 44;
    out[outPos + 27] = (in[inPos + 28] - in[inPos + 27]) >>> 20 | (in[inPos + 29] - in[inPos + 28]) << 41;
    out[outPos + 28] = (in[inPos + 29] - in[inPos + 28]) >>> 23 | (in[inPos + 30] - in[inPos + 29]) << 38;
    out[outPos + 29] = (in[inPos + 30] - in[inPos + 29]) >>> 26 | (in[inPos + 31] - in[inPos + 30]) << 35;
    out[outPos + 30] = (in[inPos + 31] - in[inPos + 30]) >>> 29 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 31] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 29;
    out[outPos + 32] = (in[inPos + 33] - in[inPos + 32]) >>> 35 | (in[inPos + 34] - in[inPos + 33]) << 26;
    out[outPos + 33] = (in[inPos + 34] - in[inPos + 33]) >>> 38 | (in[inPos + 35] - in[inPos + 34]) << 23;
    out[outPos + 34] = (in[inPos + 35] - in[inPos + 34]) >>> 41 | (in[inPos + 36] - in[inPos + 35]) << 20;
    out[outPos + 35] = (in[inPos + 36] - in[inPos + 35]) >>> 44 | (in[inPos + 37] - in[inPos + 36]) << 17;
    out[outPos + 36] = (in[inPos + 37] - in[inPos + 36]) >>> 47 | (in[inPos + 38] - in[inPos + 37]) << 14;
    out[outPos + 37] = (in[inPos + 38] - in[inPos + 37]) >>> 50 | (in[inPos + 39] - in[inPos + 38]) << 11;
    out[outPos + 38] = (in[inPos + 39] - in[inPos + 38]) >>> 53 | (in[inPos + 40] - in[inPos + 39]) << 8;
    out[outPos + 39] = (in[inPos + 40] - in[inPos + 39]) >>> 56 | (in[inPos + 41] - in[inPos + 40]) << 5;
    out[outPos + 40] = (in[inPos + 41] - in[inPos + 40]) >>> 59 | (in[inPos + 42] - in[inPos + 41]) << 2 | (in[inPos + 43] - in[inPos + 42]) << 63;
    out[outPos + 41] = (in[inPos + 43] - in[inPos + 42]) >>> 1 | (in[inPos + 44] - in[inPos + 43]) << 60;
    out[outPos + 42] = (in[inPos + 44] - in[inPos + 43]) >>> 4 | (in[inPos + 45] - in[inPos + 44]) << 57;
    out[outPos + 43] = (in[inPos + 45] - in[inPos + 44]) >>> 7 | (in[inPos + 46] - in[inPos + 45]) << 54;
    out[outPos + 44] = (in[inPos + 46] - in[inPos + 45]) >>> 10 | (in[inPos + 47] - in[inPos + 46]) << 51;
    out[outPos + 45] = (in[inPos + 47] - in[inPos + 46]) >>> 13 | (in[inPos + 48] - in[inPos + 47]) << 48;
    out[outPos + 46] = (in[inPos + 48] - in[inPos + 47]) >>> 16 | (in[inPos + 49] - in[inPos + 48]) << 45;
    out[outPos + 47] = (in[inPos + 49] - in[inPos + 48]) >>> 19 | (in[inPos + 50] - in[inPos + 49]) << 42;
    out[outPos + 48] = (in[inPos + 50] - in[inPos + 49]) >>> 22 | (in[inPos + 51] - in[inPos + 50]) << 39;
    out[outPos + 49] = (in[inPos + 51] - in[inPos + 50]) >>> 25 | (in[inPos + 52] - in[inPos + 51]) << 36;
    out[outPos + 50] = (in[inPos + 52] - in[inPos + 51]) >>> 28 | (in[inPos + 53] - in[inPos + 52]) << 33;
    out[outPos + 51] = (in[inPos + 53] - in[inPos + 52]) >>> 31 | (in[inPos + 54] - in[inPos + 53]) << 30;
    out[outPos + 52] = (in[inPos + 54] - in[inPos + 53]) >>> 34 | (in[inPos + 55] - in[inPos + 54]) << 27;
    out[outPos + 53] = (in[inPos + 55] - in[inPos + 54]) >>> 37 | (in[inPos + 56] - in[inPos + 55]) << 24;
    out[outPos + 54] = (in[inPos + 56] - in[inPos + 55]) >>> 40 | (in[inPos + 57] - in[inPos + 56]) << 21;
    out[outPos + 55] = (in[inPos + 57] - in[inPos + 56]) >>> 43 | (in[inPos + 58] - in[inPos + 57]) << 18;
    out[outPos + 56] = (in[inPos + 58] - in[inPos + 57]) >>> 46 | (in[inPos + 59] - in[inPos + 58]) << 15;
    out[outPos + 57] = (in[inPos + 59] - in[inPos + 58]) >>> 49 | (in[inPos + 60] - in[inPos + 59]) << 12;
    out[outPos + 58] = (in[inPos + 60] - in[inPos + 59]) >>> 52 | (in[inPos + 61] - in[inPos + 60]) << 9;
    out[outPos + 59] = (in[inPos + 61] - in[inPos + 60]) >>> 55 | (in[inPos + 62] - in[inPos + 61]) << 6;
    out[outPos + 60] = (in[inPos + 62] - in[inPos + 61]) >>> 58 | (in[inPos + 63] - in[inPos + 62]) << 3;
  }

  private static void unpack61(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2305843009213693951L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 61 | (in[inPos + 1] & 288230376151711743L) << 3) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 58 | (in[inPos + 2] & 36028797018963967L) << 6) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 55 | (in[inPos + 3] & 4503599627370495L) << 9) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 52 | (in[inPos + 4] & 562949953421311L) << 12) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 49 | (in[inPos + 5] & 70368744177663L) << 15) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 46 | (in[inPos + 6] & 8796093022207L) << 18) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 43 | (in[inPos + 7] & 1099511627775L) << 21) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 40 | (in[inPos + 8] & 137438953471L) << 24) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 37 | (in[inPos + 9] & 17179869183L) << 27) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 34 | (in[inPos + 10] & 2147483647) << 30) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 10] >>> 31 | (in[inPos + 11] & 268435455) << 33) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 11] >>> 28 | (in[inPos + 12] & 33554431) << 36) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 12] >>> 25 | (in[inPos + 13] & 4194303) << 39) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 13] >>> 22 | (in[inPos + 14] & 524287) << 42) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 14] >>> 19 | (in[inPos + 15] & 65535) << 45) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 15] >>> 16 | (in[inPos + 16] & 8191) << 48) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 16] >>> 13 | (in[inPos + 17] & 1023) << 51) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 17] >>> 10 | (in[inPos + 18] & 127) << 54) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 18] >>> 7 | (in[inPos + 19] & 15) << 57) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 19] >>> 4 | (in[inPos + 20] & 1) << 60) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 20] >>> 1 & 2305843009213693951L) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 20] >>> 62 | (in[inPos + 21] & 576460752303423487L) << 2) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 21] >>> 59 | (in[inPos + 22] & 72057594037927935L) << 5) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 22] >>> 56 | (in[inPos + 23] & 9007199254740991L) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 23] >>> 53 | (in[inPos + 24] & 1125899906842623L) << 11) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 24] >>> 50 | (in[inPos + 25] & 140737488355327L) << 14) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 25] >>> 47 | (in[inPos + 26] & 17592186044415L) << 17) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 26] >>> 44 | (in[inPos + 27] & 2199023255551L) << 20) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 27] >>> 41 | (in[inPos + 28] & 274877906943L) << 23) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 28] >>> 38 | (in[inPos + 29] & 34359738367L) << 26) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 29] >>> 35 | (in[inPos + 30] & 4294967295L) << 29) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 30] >>> 32 | (in[inPos + 31] & 536870911) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 31] >>> 29 | (in[inPos + 32] & 67108863) << 35) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 32] >>> 26 | (in[inPos + 33] & 8388607) << 38) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 33] >>> 23 | (in[inPos + 34] & 1048575) << 41) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 34] >>> 20 | (in[inPos + 35] & 131071) << 44) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 35] >>> 17 | (in[inPos + 36] & 16383) << 47) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 36] >>> 14 | (in[inPos + 37] & 2047) << 50) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 37] >>> 11 | (in[inPos + 38] & 255) << 53) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 38] >>> 8 | (in[inPos + 39] & 31) << 56) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 39] >>> 5 | (in[inPos + 40] & 3) << 59) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 40] >>> 2 & 2305843009213693951L) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 40] >>> 63 | (in[inPos + 41] & 1152921504606846975L) << 1) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 41] >>> 60 | (in[inPos + 42] & 144115188075855871L) << 4) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 42] >>> 57 | (in[inPos + 43] & 18014398509481983L) << 7) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 43] >>> 54 | (in[inPos + 44] & 2251799813685247L) << 10) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 44] >>> 51 | (in[inPos + 45] & 281474976710655L) << 13) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 45] >>> 48 | (in[inPos + 46] & 35184372088831L) << 16) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 46] >>> 45 | (in[inPos + 47] & 4398046511103L) << 19) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 47] >>> 42 | (in[inPos + 48] & 549755813887L) << 22) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 48] >>> 39 | (in[inPos + 49] & 68719476735L) << 25) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 49] >>> 36 | (in[inPos + 50] & 8589934591L) << 28) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 50] >>> 33 | (in[inPos + 51] & 1073741823) << 31) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 51] >>> 30 | (in[inPos + 52] & 134217727) << 34) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 52] >>> 27 | (in[inPos + 53] & 16777215) << 37) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 53] >>> 24 | (in[inPos + 54] & 2097151) << 40) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 54] >>> 21 | (in[inPos + 55] & 262143) << 43) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 55] >>> 18 | (in[inPos + 56] & 32767) << 46) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 56] >>> 15 | (in[inPos + 57] & 4095) << 49) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 57] >>> 12 | (in[inPos + 58] & 511) << 52) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 58] >>> 9 | (in[inPos + 59] & 63) << 55) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 59] >>> 6 | (in[inPos + 60] & 7) << 58) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 60] >>> 3) + out[outPos + 62];
  }

  private static void pack62(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 62;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 2 | (in[inPos + 2] - in[inPos + 1]) << 60;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 4 | (in[inPos + 3] - in[inPos + 2]) << 58;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 6 | (in[inPos + 4] - in[inPos + 3]) << 56;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 8 | (in[inPos + 5] - in[inPos + 4]) << 54;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 10 | (in[inPos + 6] - in[inPos + 5]) << 52;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 12 | (in[inPos + 7] - in[inPos + 6]) << 50;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 14 | (in[inPos + 8] - in[inPos + 7]) << 48;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 46;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 18 | (in[inPos + 10] - in[inPos + 9]) << 44;
    out[outPos + 10] = (in[inPos + 10] - in[inPos + 9]) >>> 20 | (in[inPos + 11] - in[inPos + 10]) << 42;
    out[outPos + 11] = (in[inPos + 11] - in[inPos + 10]) >>> 22 | (in[inPos + 12] - in[inPos + 11]) << 40;
    out[outPos + 12] = (in[inPos + 12] - in[inPos + 11]) >>> 24 | (in[inPos + 13] - in[inPos + 12]) << 38;
    out[outPos + 13] = (in[inPos + 13] - in[inPos + 12]) >>> 26 | (in[inPos + 14] - in[inPos + 13]) << 36;
    out[outPos + 14] = (in[inPos + 14] - in[inPos + 13]) >>> 28 | (in[inPos + 15] - in[inPos + 14]) << 34;
    out[outPos + 15] = (in[inPos + 15] - in[inPos + 14]) >>> 30 | (in[inPos + 16] - in[inPos + 15]) << 32;
    out[outPos + 16] = (in[inPos + 16] - in[inPos + 15]) >>> 32 | (in[inPos + 17] - in[inPos + 16]) << 30;
    out[outPos + 17] = (in[inPos + 17] - in[inPos + 16]) >>> 34 | (in[inPos + 18] - in[inPos + 17]) << 28;
    out[outPos + 18] = (in[inPos + 18] - in[inPos + 17]) >>> 36 | (in[inPos + 19] - in[inPos + 18]) << 26;
    out[outPos + 19] = (in[inPos + 19] - in[inPos + 18]) >>> 38 | (in[inPos + 20] - in[inPos + 19]) << 24;
    out[outPos + 20] = (in[inPos + 20] - in[inPos + 19]) >>> 40 | (in[inPos + 21] - in[inPos + 20]) << 22;
    out[outPos + 21] = (in[inPos + 21] - in[inPos + 20]) >>> 42 | (in[inPos + 22] - in[inPos + 21]) << 20;
    out[outPos + 22] = (in[inPos + 22] - in[inPos + 21]) >>> 44 | (in[inPos + 23] - in[inPos + 22]) << 18;
    out[outPos + 23] = (in[inPos + 23] - in[inPos + 22]) >>> 46 | (in[inPos + 24] - in[inPos + 23]) << 16;
    out[outPos + 24] = (in[inPos + 24] - in[inPos + 23]) >>> 48 | (in[inPos + 25] - in[inPos + 24]) << 14;
    out[outPos + 25] = (in[inPos + 25] - in[inPos + 24]) >>> 50 | (in[inPos + 26] - in[inPos + 25]) << 12;
    out[outPos + 26] = (in[inPos + 26] - in[inPos + 25]) >>> 52 | (in[inPos + 27] - in[inPos + 26]) << 10;
    out[outPos + 27] = (in[inPos + 27] - in[inPos + 26]) >>> 54 | (in[inPos + 28] - in[inPos + 27]) << 8;
    out[outPos + 28] = (in[inPos + 28] - in[inPos + 27]) >>> 56 | (in[inPos + 29] - in[inPos + 28]) << 6;
    out[outPos + 29] = (in[inPos + 29] - in[inPos + 28]) >>> 58 | (in[inPos + 30] - in[inPos + 29]) << 4;
    out[outPos + 30] = (in[inPos + 30] - in[inPos + 29]) >>> 60 | (in[inPos + 31] - in[inPos + 30]) << 2;
    out[outPos + 31] = (in[inPos + 32] - in[inPos + 31]) | (in[inPos + 33] - in[inPos + 32]) << 62;
    out[outPos + 32] = (in[inPos + 33] - in[inPos + 32]) >>> 2 | (in[inPos + 34] - in[inPos + 33]) << 60;
    out[outPos + 33] = (in[inPos + 34] - in[inPos + 33]) >>> 4 | (in[inPos + 35] - in[inPos + 34]) << 58;
    out[outPos + 34] = (in[inPos + 35] - in[inPos + 34]) >>> 6 | (in[inPos + 36] - in[inPos + 35]) << 56;
    out[outPos + 35] = (in[inPos + 36] - in[inPos + 35]) >>> 8 | (in[inPos + 37] - in[inPos + 36]) << 54;
    out[outPos + 36] = (in[inPos + 37] - in[inPos + 36]) >>> 10 | (in[inPos + 38] - in[inPos + 37]) << 52;
    out[outPos + 37] = (in[inPos + 38] - in[inPos + 37]) >>> 12 | (in[inPos + 39] - in[inPos + 38]) << 50;
    out[outPos + 38] = (in[inPos + 39] - in[inPos + 38]) >>> 14 | (in[inPos + 40] - in[inPos + 39]) << 48;
    out[outPos + 39] = (in[inPos + 40] - in[inPos + 39]) >>> 16 | (in[inPos + 41] - in[inPos + 40]) << 46;
    out[outPos + 40] = (in[inPos + 41] - in[inPos + 40]) >>> 18 | (in[inPos + 42] - in[inPos + 41]) << 44;
    out[outPos + 41] = (in[inPos + 42] - in[inPos + 41]) >>> 20 | (in[inPos + 43] - in[inPos + 42]) << 42;
    out[outPos + 42] = (in[inPos + 43] - in[inPos + 42]) >>> 22 | (in[inPos + 44] - in[inPos + 43]) << 40;
    out[outPos + 43] = (in[inPos + 44] - in[inPos + 43]) >>> 24 | (in[inPos + 45] - in[inPos + 44]) << 38;
    out[outPos + 44] = (in[inPos + 45] - in[inPos + 44]) >>> 26 | (in[inPos + 46] - in[inPos + 45]) << 36;
    out[outPos + 45] = (in[inPos + 46] - in[inPos + 45]) >>> 28 | (in[inPos + 47] - in[inPos + 46]) << 34;
    out[outPos + 46] = (in[inPos + 47] - in[inPos + 46]) >>> 30 | (in[inPos + 48] - in[inPos + 47]) << 32;
    out[outPos + 47] = (in[inPos + 48] - in[inPos + 47]) >>> 32 | (in[inPos + 49] - in[inPos + 48]) << 30;
    out[outPos + 48] = (in[inPos + 49] - in[inPos + 48]) >>> 34 | (in[inPos + 50] - in[inPos + 49]) << 28;
    out[outPos + 49] = (in[inPos + 50] - in[inPos + 49]) >>> 36 | (in[inPos + 51] - in[inPos + 50]) << 26;
    out[outPos + 50] = (in[inPos + 51] - in[inPos + 50]) >>> 38 | (in[inPos + 52] - in[inPos + 51]) << 24;
    out[outPos + 51] = (in[inPos + 52] - in[inPos + 51]) >>> 40 | (in[inPos + 53] - in[inPos + 52]) << 22;
    out[outPos + 52] = (in[inPos + 53] - in[inPos + 52]) >>> 42 | (in[inPos + 54] - in[inPos + 53]) << 20;
    out[outPos + 53] = (in[inPos + 54] - in[inPos + 53]) >>> 44 | (in[inPos + 55] - in[inPos + 54]) << 18;
    out[outPos + 54] = (in[inPos + 55] - in[inPos + 54]) >>> 46 | (in[inPos + 56] - in[inPos + 55]) << 16;
    out[outPos + 55] = (in[inPos + 56] - in[inPos + 55]) >>> 48 | (in[inPos + 57] - in[inPos + 56]) << 14;
    out[outPos + 56] = (in[inPos + 57] - in[inPos + 56]) >>> 50 | (in[inPos + 58] - in[inPos + 57]) << 12;
    out[outPos + 57] = (in[inPos + 58] - in[inPos + 57]) >>> 52 | (in[inPos + 59] - in[inPos + 58]) << 10;
    out[outPos + 58] = (in[inPos + 59] - in[inPos + 58]) >>> 54 | (in[inPos + 60] - in[inPos + 59]) << 8;
    out[outPos + 59] = (in[inPos + 60] - in[inPos + 59]) >>> 56 | (in[inPos + 61] - in[inPos + 60]) << 6;
    out[outPos + 60] = (in[inPos + 61] - in[inPos + 60]) >>> 58 | (in[inPos + 62] - in[inPos + 61]) << 4;
    out[outPos + 61] = (in[inPos + 62] - in[inPos + 61]) >>> 60 | (in[inPos + 63] - in[inPos + 62]) << 2;
  }

  private static void unpack62(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 4611686018427387903L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 62 | (in[inPos + 1] & 1152921504606846975L) << 2) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 60 | (in[inPos + 2] & 288230376151711743L) << 4) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 58 | (in[inPos + 3] & 72057594037927935L) << 6) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 56 | (in[inPos + 4] & 18014398509481983L) << 8) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 54 | (in[inPos + 5] & 4503599627370495L) << 10) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 52 | (in[inPos + 6] & 1125899906842623L) << 12) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 50 | (in[inPos + 7] & 281474976710655L) << 14) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 48 | (in[inPos + 8] & 70368744177663L) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 46 | (in[inPos + 9] & 17592186044415L) << 18) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 44 | (in[inPos + 10] & 4398046511103L) << 20) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 10] >>> 42 | (in[inPos + 11] & 1099511627775L) << 22) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 11] >>> 40 | (in[inPos + 12] & 274877906943L) << 24) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 12] >>> 38 | (in[inPos + 13] & 68719476735L) << 26) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 13] >>> 36 | (in[inPos + 14] & 17179869183L) << 28) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 14] >>> 34 | (in[inPos + 15] & 4294967295L) << 30) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 15] >>> 32 | (in[inPos + 16] & 1073741823) << 32) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 16] >>> 30 | (in[inPos + 17] & 268435455) << 34) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 17] >>> 28 | (in[inPos + 18] & 67108863) << 36) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 18] >>> 26 | (in[inPos + 19] & 16777215) << 38) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 19] >>> 24 | (in[inPos + 20] & 4194303) << 40) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 20] >>> 22 | (in[inPos + 21] & 1048575) << 42) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 21] >>> 20 | (in[inPos + 22] & 262143) << 44) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 22] >>> 18 | (in[inPos + 23] & 65535) << 46) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 23] >>> 16 | (in[inPos + 24] & 16383) << 48) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 24] >>> 14 | (in[inPos + 25] & 4095) << 50) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 25] >>> 12 | (in[inPos + 26] & 1023) << 52) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 26] >>> 10 | (in[inPos + 27] & 255) << 54) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 27] >>> 8 | (in[inPos + 28] & 63) << 56) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 28] >>> 6 | (in[inPos + 29] & 15) << 58) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 29] >>> 4 | (in[inPos + 30] & 3) << 60) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 30] >>> 2) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 31] & 4611686018427387903L) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 31] >>> 62 | (in[inPos + 32] & 1152921504606846975L) << 2) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 32] >>> 60 | (in[inPos + 33] & 288230376151711743L) << 4) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 33] >>> 58 | (in[inPos + 34] & 72057594037927935L) << 6) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 34] >>> 56 | (in[inPos + 35] & 18014398509481983L) << 8) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 35] >>> 54 | (in[inPos + 36] & 4503599627370495L) << 10) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 36] >>> 52 | (in[inPos + 37] & 1125899906842623L) << 12) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 37] >>> 50 | (in[inPos + 38] & 281474976710655L) << 14) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 38] >>> 48 | (in[inPos + 39] & 70368744177663L) << 16) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 39] >>> 46 | (in[inPos + 40] & 17592186044415L) << 18) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 40] >>> 44 | (in[inPos + 41] & 4398046511103L) << 20) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 41] >>> 42 | (in[inPos + 42] & 1099511627775L) << 22) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 42] >>> 40 | (in[inPos + 43] & 274877906943L) << 24) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 43] >>> 38 | (in[inPos + 44] & 68719476735L) << 26) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 44] >>> 36 | (in[inPos + 45] & 17179869183L) << 28) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 45] >>> 34 | (in[inPos + 46] & 4294967295L) << 30) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 46] >>> 32 | (in[inPos + 47] & 1073741823) << 32) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 47] >>> 30 | (in[inPos + 48] & 268435455) << 34) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 48] >>> 28 | (in[inPos + 49] & 67108863) << 36) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 49] >>> 26 | (in[inPos + 50] & 16777215) << 38) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 50] >>> 24 | (in[inPos + 51] & 4194303) << 40) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 51] >>> 22 | (in[inPos + 52] & 1048575) << 42) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 52] >>> 20 | (in[inPos + 53] & 262143) << 44) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 53] >>> 18 | (in[inPos + 54] & 65535) << 46) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 54] >>> 16 | (in[inPos + 55] & 16383) << 48) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 55] >>> 14 | (in[inPos + 56] & 4095) << 50) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 56] >>> 12 | (in[inPos + 57] & 1023) << 52) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 57] >>> 10 | (in[inPos + 58] & 255) << 54) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 58] >>> 8 | (in[inPos + 59] & 63) << 56) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 59] >>> 6 | (in[inPos + 60] & 15) << 58) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 60] >>> 4 | (in[inPos + 61] & 3) << 60) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 61] >>> 2) + out[outPos + 62];
  }

  private static void pack63(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 63;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 1 | (in[inPos + 2] - in[inPos + 1]) << 62;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 2 | (in[inPos + 3] - in[inPos + 2]) << 61;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 3 | (in[inPos + 4] - in[inPos + 3]) << 60;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 4 | (in[inPos + 5] - in[inPos + 4]) << 59;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 5 | (in[inPos + 6] - in[inPos + 5]) << 58;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 6 | (in[inPos + 7] - in[inPos + 6]) << 57;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 7 | (in[inPos + 8] - in[inPos + 7]) << 56;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 55;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 9 | (in[inPos + 10] - in[inPos + 9]) << 54;
    out[outPos + 10] = (in[inPos + 10] - in[inPos + 9]) >>> 10 | (in[inPos + 11] - in[inPos + 10]) << 53;
    out[outPos + 11] = (in[inPos + 11] - in[inPos + 10]) >>> 11 | (in[inPos + 12] - in[inPos + 11]) << 52;
    out[outPos + 12] = (in[inPos + 12] - in[inPos + 11]) >>> 12 | (in[inPos + 13] - in[inPos + 12]) << 51;
    out[outPos + 13] = (in[inPos + 13] - in[inPos + 12]) >>> 13 | (in[inPos + 14] - in[inPos + 13]) << 50;
    out[outPos + 14] = (in[inPos + 14] - in[inPos + 13]) >>> 14 | (in[inPos + 15] - in[inPos + 14]) << 49;
    out[outPos + 15] = (in[inPos + 15] - in[inPos + 14]) >>> 15 | (in[inPos + 16] - in[inPos + 15]) << 48;
    out[outPos + 16] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 47;
    out[outPos + 17] = (in[inPos + 17] - in[inPos + 16]) >>> 17 | (in[inPos + 18] - in[inPos + 17]) << 46;
    out[outPos + 18] = (in[inPos + 18] - in[inPos + 17]) >>> 18 | (in[inPos + 19] - in[inPos + 18]) << 45;
    out[outPos + 19] = (in[inPos + 19] - in[inPos + 18]) >>> 19 | (in[inPos + 20] - in[inPos + 19]) << 44;
    out[outPos + 20] = (in[inPos + 20] - in[inPos + 19]) >>> 20 | (in[inPos + 21] - in[inPos + 20]) << 43;
    out[outPos + 21] = (in[inPos + 21] - in[inPos + 20]) >>> 21 | (in[inPos + 22] - in[inPos + 21]) << 42;
    out[outPos + 22] = (in[inPos + 22] - in[inPos + 21]) >>> 22 | (in[inPos + 23] - in[inPos + 22]) << 41;
    out[outPos + 23] = (in[inPos + 23] - in[inPos + 22]) >>> 23 | (in[inPos + 24] - in[inPos + 23]) << 40;
    out[outPos + 24] = (in[inPos + 24] - in[inPos + 23]) >>> 24 | (in[inPos + 25] - in[inPos + 24]) << 39;
    out[outPos + 25] = (in[inPos + 25] - in[inPos + 24]) >>> 25 | (in[inPos + 26] - in[inPos + 25]) << 38;
    out[outPos + 26] = (in[inPos + 26] - in[inPos + 25]) >>> 26 | (in[inPos + 27] - in[inPos + 26]) << 37;
    out[outPos + 27] = (in[inPos + 27] - in[inPos + 26]) >>> 27 | (in[inPos + 28] - in[inPos + 27]) << 36;
    out[outPos + 28] = (in[inPos + 28] - in[inPos + 27]) >>> 28 | (in[inPos + 29] - in[inPos + 28]) << 35;
    out[outPos + 29] = (in[inPos + 29] - in[inPos + 28]) >>> 29 | (in[inPos + 30] - in[inPos + 29]) << 34;
    out[outPos + 30] = (in[inPos + 30] - in[inPos + 29]) >>> 30 | (in[inPos + 31] - in[inPos + 30]) << 33;
    out[outPos + 31] = (in[inPos + 31] - in[inPos + 30]) >>> 31 | (in[inPos + 32] - in[inPos + 31]) << 32;
    out[outPos + 32] = (in[inPos + 32] - in[inPos + 31]) >>> 32 | (in[inPos + 33] - in[inPos + 32]) << 31;
    out[outPos + 33] = (in[inPos + 33] - in[inPos + 32]) >>> 33 | (in[inPos + 34] - in[inPos + 33]) << 30;
    out[outPos + 34] = (in[inPos + 34] - in[inPos + 33]) >>> 34 | (in[inPos + 35] - in[inPos + 34]) << 29;
    out[outPos + 35] = (in[inPos + 35] - in[inPos + 34]) >>> 35 | (in[inPos + 36] - in[inPos + 35]) << 28;
    out[outPos + 36] = (in[inPos + 36] - in[inPos + 35]) >>> 36 | (in[inPos + 37] - in[inPos + 36]) << 27;
    out[outPos + 37] = (in[inPos + 37] - in[inPos + 36]) >>> 37 | (in[inPos + 38] - in[inPos + 37]) << 26;
    out[outPos + 38] = (in[inPos + 38] - in[inPos + 37]) >>> 38 | (in[inPos + 39] - in[inPos + 38]) << 25;
    out[outPos + 39] = (in[inPos + 39] - in[inPos + 38]) >>> 39 | (in[inPos + 40] - in[inPos + 39]) << 24;
    out[outPos + 40] = (in[inPos + 40] - in[inPos + 39]) >>> 40 | (in[inPos + 41] - in[inPos + 40]) << 23;
    out[outPos + 41] = (in[inPos + 41] - in[inPos + 40]) >>> 41 | (in[inPos + 42] - in[inPos + 41]) << 22;
    out[outPos + 42] = (in[inPos + 42] - in[inPos + 41]) >>> 42 | (in[inPos + 43] - in[inPos + 42]) << 21;
    out[outPos + 43] = (in[inPos + 43] - in[inPos + 42]) >>> 43 | (in[inPos + 44] - in[inPos + 43]) << 20;
    out[outPos + 44] = (in[inPos + 44] - in[inPos + 43]) >>> 44 | (in[inPos + 45] - in[inPos + 44]) << 19;
    out[outPos + 45] = (in[inPos + 45] - in[inPos + 44]) >>> 45 | (in[inPos + 46] - in[inPos + 45]) << 18;
    out[outPos + 46] = (in[inPos + 46] - in[inPos + 45]) >>> 46 | (in[inPos + 47] - in[inPos + 46]) << 17;
    out[outPos + 47] = (in[inPos + 47] - in[inPos + 46]) >>> 47 | (in[inPos + 48] - in[inPos + 47]) << 16;
    out[outPos + 48] = (in[inPos + 48] - in[inPos + 47]) >>> 48 | (in[inPos + 49] - in[inPos + 48]) << 15;
    out[outPos + 49] = (in[inPos + 49] - in[inPos + 48]) >>> 49 | (in[inPos + 50] - in[inPos + 49]) << 14;
    out[outPos + 50] = (in[inPos + 50] - in[inPos + 49]) >>> 50 | (in[inPos + 51] - in[inPos + 50]) << 13;
    out[outPos + 51] = (in[inPos + 51] - in[inPos + 50]) >>> 51 | (in[inPos + 52] - in[inPos + 51]) << 12;
    out[outPos + 52] = (in[inPos + 52] - in[inPos + 51]) >>> 52 | (in[inPos + 53] - in[inPos + 52]) << 11;
    out[outPos + 53] = (in[inPos + 53] - in[inPos + 52]) >>> 53 | (in[inPos + 54] - in[inPos + 53]) << 10;
    out[outPos + 54] = (in[inPos + 54] - in[inPos + 53]) >>> 54 | (in[inPos + 55] - in[inPos + 54]) << 9;
    out[outPos + 55] = (in[inPos + 55] - in[inPos + 54]) >>> 55 | (in[inPos + 56] - in[inPos + 55]) << 8;
    out[outPos + 56] = (in[inPos + 56] - in[inPos + 55]) >>> 56 | (in[inPos + 57] - in[inPos + 56]) << 7;
    out[outPos + 57] = (in[inPos + 57] - in[inPos + 56]) >>> 57 | (in[inPos + 58] - in[inPos + 57]) << 6;
    out[outPos + 58] = (in[inPos + 58] - in[inPos + 57]) >>> 58 | (in[inPos + 59] - in[inPos + 58]) << 5;
    out[outPos + 59] = (in[inPos + 59] - in[inPos + 58]) >>> 59 | (in[inPos + 60] - in[inPos + 59]) << 4;
    out[outPos + 60] = (in[inPos + 60] - in[inPos + 59]) >>> 60 | (in[inPos + 61] - in[inPos + 60]) << 3;
    out[outPos + 61] = (in[inPos + 61] - in[inPos + 60]) >>> 61 | (in[inPos + 62] - in[inPos + 61]) << 2;
    out[outPos + 62] = (in[inPos + 62] - in[inPos + 61]) >>> 62 | (in[inPos + 63] - in[inPos + 62]) << 1;
  }

  private static void unpack63(final long initValue, final long[] in, final int inPos,
      final long[] out, final int outPos) {
    out[outPos] = (in[inPos] & 9223372036854775807L) + initValue;
    out[outPos + 1] = (in[inPos] >>> 63 | (in[inPos + 1] & 4611686018427387903L) << 1) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 62 | (in[inPos + 2] & 2305843009213693951L) << 2) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 61 | (in[inPos + 3] & 1152921504606846975L) << 3) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 60 | (in[inPos + 4] & 576460752303423487L) << 4) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 59 | (in[inPos + 5] & 288230376151711743L) << 5) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 58 | (in[inPos + 6] & 144115188075855871L) << 6) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 57 | (in[inPos + 7] & 72057594037927935L) << 7) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 56 | (in[inPos + 8] & 36028797018963967L) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 55 | (in[inPos + 9] & 18014398509481983L) << 9) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 54 | (in[inPos + 10] & 9007199254740991L) << 10) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 10] >>> 53 | (in[inPos + 11] & 4503599627370495L) << 11) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 11] >>> 52 | (in[inPos + 12] & 2251799813685247L) << 12) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 12] >>> 51 | (in[inPos + 13] & 1125899906842623L) << 13) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 13] >>> 50 | (in[inPos + 14] & 562949953421311L) << 14) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 14] >>> 49 | (in[inPos + 15] & 281474976710655L) << 15) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 15] >>> 48 | (in[inPos + 16] & 140737488355327L) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 16] >>> 47 | (in[inPos + 17] & 70368744177663L) << 17) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 17] >>> 46 | (in[inPos + 18] & 35184372088831L) << 18) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 18] >>> 45 | (in[inPos + 19] & 17592186044415L) << 19) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 19] >>> 44 | (in[inPos + 20] & 8796093022207L) << 20) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 20] >>> 43 | (in[inPos + 21] & 4398046511103L) << 21) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 21] >>> 42 | (in[inPos + 22] & 2199023255551L) << 22) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 22] >>> 41 | (in[inPos + 23] & 1099511627775L) << 23) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 23] >>> 40 | (in[inPos + 24] & 549755813887L) << 24) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 24] >>> 39 | (in[inPos + 25] & 274877906943L) << 25) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 25] >>> 38 | (in[inPos + 26] & 137438953471L) << 26) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 26] >>> 37 | (in[inPos + 27] & 68719476735L) << 27) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 27] >>> 36 | (in[inPos + 28] & 34359738367L) << 28) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 28] >>> 35 | (in[inPos + 29] & 17179869183L) << 29) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 29] >>> 34 | (in[inPos + 30] & 8589934591L) << 30) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 30] >>> 33 | (in[inPos + 31] & 4294967295L) << 31) + out[outPos + 30];
    out[outPos + 32] = (in[inPos + 31] >>> 32 | (in[inPos + 32] & 2147483647) << 32) + out[outPos + 31];
    out[outPos + 33] = (in[inPos + 32] >>> 31 | (in[inPos + 33] & 1073741823) << 33) + out[outPos + 32];
    out[outPos + 34] = (in[inPos + 33] >>> 30 | (in[inPos + 34] & 536870911) << 34) + out[outPos + 33];
    out[outPos + 35] = (in[inPos + 34] >>> 29 | (in[inPos + 35] & 268435455) << 35) + out[outPos + 34];
    out[outPos + 36] = (in[inPos + 35] >>> 28 | (in[inPos + 36] & 134217727) << 36) + out[outPos + 35];
    out[outPos + 37] = (in[inPos + 36] >>> 27 | (in[inPos + 37] & 67108863) << 37) + out[outPos + 36];
    out[outPos + 38] = (in[inPos + 37] >>> 26 | (in[inPos + 38] & 33554431) << 38) + out[outPos + 37];
    out[outPos + 39] = (in[inPos + 38] >>> 25 | (in[inPos + 39] & 16777215) << 39) + out[outPos + 38];
    out[outPos + 40] = (in[inPos + 39] >>> 24 | (in[inPos + 40] & 8388607) << 40) + out[outPos + 39];
    out[outPos + 41] = (in[inPos + 40] >>> 23 | (in[inPos + 41] & 4194303) << 41) + out[outPos + 40];
    out[outPos + 42] = (in[inPos + 41] >>> 22 | (in[inPos + 42] & 2097151) << 42) + out[outPos + 41];
    out[outPos + 43] = (in[inPos + 42] >>> 21 | (in[inPos + 43] & 1048575) << 43) + out[outPos + 42];
    out[outPos + 44] = (in[inPos + 43] >>> 20 | (in[inPos + 44] & 524287) << 44) + out[outPos + 43];
    out[outPos + 45] = (in[inPos + 44] >>> 19 | (in[inPos + 45] & 262143) << 45) + out[outPos + 44];
    out[outPos + 46] = (in[inPos + 45] >>> 18 | (in[inPos + 46] & 131071) << 46) + out[outPos + 45];
    out[outPos + 47] = (in[inPos + 46] >>> 17 | (in[inPos + 47] & 65535) << 47) + out[outPos + 46];
    out[outPos + 48] = (in[inPos + 47] >>> 16 | (in[inPos + 48] & 32767) << 48) + out[outPos + 47];
    out[outPos + 49] = (in[inPos + 48] >>> 15 | (in[inPos + 49] & 16383) << 49) + out[outPos + 48];
    out[outPos + 50] = (in[inPos + 49] >>> 14 | (in[inPos + 50] & 8191) << 50) + out[outPos + 49];
    out[outPos + 51] = (in[inPos + 50] >>> 13 | (in[inPos + 51] & 4095) << 51) + out[outPos + 50];
    out[outPos + 52] = (in[inPos + 51] >>> 12 | (in[inPos + 52] & 2047) << 52) + out[outPos + 51];
    out[outPos + 53] = (in[inPos + 52] >>> 11 | (in[inPos + 53] & 1023) << 53) + out[outPos + 52];
    out[outPos + 54] = (in[inPos + 53] >>> 10 | (in[inPos + 54] & 511) << 54) + out[outPos + 53];
    out[outPos + 55] = (in[inPos + 54] >>> 9 | (in[inPos + 55] & 255) << 55) + out[outPos + 54];
    out[outPos + 56] = (in[inPos + 55] >>> 8 | (in[inPos + 56] & 127) << 56) + out[outPos + 55];
    out[outPos + 57] = (in[inPos + 56] >>> 7 | (in[inPos + 57] & 63) << 57) + out[outPos + 56];
    out[outPos + 58] = (in[inPos + 57] >>> 6 | (in[inPos + 58] & 31) << 58) + out[outPos + 57];
    out[outPos + 59] = (in[inPos + 58] >>> 5 | (in[inPos + 59] & 15) << 59) + out[outPos + 58];
    out[outPos + 60] = (in[inPos + 59] >>> 4 | (in[inPos + 60] & 7) << 60) + out[outPos + 59];
    out[outPos + 61] = (in[inPos + 60] >>> 3 | (in[inPos + 61] & 3) << 61) + out[outPos + 60];
    out[outPos + 62] = (in[inPos + 61] >>> 2 | (in[inPos + 62] & 1) << 62) + out[outPos + 61];
    out[outPos + 63] = (in[inPos + 62] >>> 1) + out[outPos + 62];
  }
}
