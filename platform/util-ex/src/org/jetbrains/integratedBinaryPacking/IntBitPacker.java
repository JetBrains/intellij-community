// Copyright Daniel Lemire, http://lemire.me/en/ Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.integratedBinaryPacking;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;

public final class IntBitPacker {
  public static int compressIntegrated(final int[] in, int startIndex, final int endIndex,
      final int[] out, int initValue) {
    int tmpOutPos = 0;
    for (; startIndex + 96 < endIndex; startIndex += 128) {
      final int mBits1 = maxDiffBits(initValue, in, startIndex);
      final int initOffset2 = in[startIndex + 31];
      final int mBits2 = maxDiffBits(initOffset2, in, startIndex + 32);
      final int initOffset3 = in[startIndex + 63];
      final int mBits3 = maxDiffBits(initOffset3, in, startIndex + 64);
      final int initOffset4 = in[startIndex + 95];
      final int mBits4 = maxDiffBits(initOffset4, in, startIndex + 96);
      out[tmpOutPos++] = mBits1 << 24 | mBits2 << 16 | mBits3 << 8 | mBits4;
      pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
      tmpOutPos += mBits1;
      pack(initOffset2, in, startIndex + 32, out, tmpOutPos, mBits2);
      tmpOutPos += mBits2;
      pack(initOffset3, in, startIndex + 64, out, tmpOutPos, mBits3);
      tmpOutPos += mBits3;
      pack(initOffset4, in, startIndex + 96, out, tmpOutPos, mBits4);
      tmpOutPos += mBits4;
      initValue = in[startIndex + 127];
    }
    switch (endIndex - startIndex) {
      case 32: {
        final int mBits1 = maxDiffBits(initValue, in, startIndex);
        out[tmpOutPos++] = mBits1;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        return tmpOutPos + mBits1;
      }
      case 64: {
        final int mBits1 = maxDiffBits(initValue, in, startIndex);
        final int initOffset2 = in[startIndex + 31];
        final int mBits2 = maxDiffBits(initOffset2, in, startIndex + 32);
        out[tmpOutPos++] = mBits1 << 8 | mBits2;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        tmpOutPos += mBits1;
        pack(initOffset2, in, startIndex + 32, out, tmpOutPos, mBits2);
        return tmpOutPos + mBits2;
      }
      case 96: {
        final int mBits1 = maxDiffBits(initValue, in, startIndex);
        final int initOffset2 = in[startIndex + 31];
        final int mBits2 = maxDiffBits(initOffset2, in, startIndex + 32);
        final int initOffset3 = in[startIndex + 63];
        final int mBits3 = maxDiffBits(initOffset3, in, startIndex + 64);
        out[tmpOutPos++] = mBits1 << 16 | mBits2 << 8 | mBits3;
        pack(initValue, in, startIndex, out, tmpOutPos, mBits1);
        tmpOutPos += mBits1;
        pack(initOffset2, in, startIndex + 32, out, tmpOutPos, mBits2);
        tmpOutPos += mBits2;
        pack(initOffset3, in, startIndex + 64, out, tmpOutPos, mBits3);
        return tmpOutPos + mBits3;
      }
      case 0: {
        return tmpOutPos;
      }
      default: {
        throw new IllegalStateException();
      }
    }
  }

  public static void decompressIntegrated(final int[] in, int startIndex, final int[] out,
      final int outPosition, final int outEndIndex, int initValue) {
    assert outEndIndex != 0;
    int index = startIndex;
    int s = outPosition;
    for (; s + 127 < outEndIndex; s += 128) {
      final int mBits1 = in[index] >>> 24;
      final int mBits2 = in[index] >>> 16 & 0xff;
      final int mBits3 = in[index] >>> 8 & 0xff;
      final int mBits4 = in[index] & 0xff;
      index++;
      unpack(initValue, in, index, out, s, mBits1);
      index += mBits1;
      initValue = out[s + 31];
      unpack(initValue, in, index, out, s + 32, mBits2);
      index += mBits2;
      initValue = out[s + 63];
      unpack(initValue, in, index, out, s + 64, mBits3);
      index += mBits3;
      initValue = out[s + 95];
      unpack(initValue, in, index, out, s + 96, mBits4);
      index += mBits4;
      initValue = out[s + 127];
    }
    switch (outEndIndex - s) {
      case 32: {
        final int mBits1 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
      }
      break;
      case 64: {
        final int mBits1 = in[index] >>> 8;
        final int mBits2 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
        index += mBits1;
        initValue = out[s + 31];
        unpack(initValue, in, index, out, s + 32, mBits2);
      }
      break;
      case 96: {
        final int mBits1 = in[index] >>> 16;
        final int mBits2 = in[index] >>> 8 & 0xff;
        final int mBits3 = in[index] & 0xff;
        index++;
        unpack(initValue, in, index, out, s, mBits1);
        index += mBits1;
        initValue = out[s + 31];
        unpack(initValue, in, index, out, s + 32, mBits2);
        index += mBits2;
        initValue = out[s + 63];
        unpack(initValue, in, index, out, s + 64, mBits3);
      }
      break;
    }
  }

  public static void writeVar(final ByteBuf buf, final int value) {
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
    } else {
      buf.writeByte((byte)((value & 127) | 128));
      buf.writeByte((byte)(value >>> 7 | 128));
      buf.writeByte((byte)(value >>> 14 | 128));
      buf.writeByte((byte)(value >>> 21 | 128));
      buf.writeByte((byte)(value >>> 28));
    }
  }

  public static int readVar(final ByteBuf buf) {
    byte aByte = buf.readByte();
    int value = aByte & 127;
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
            value |= buf.readByte() << 28;
          }
        }
      }
    }
    return value;
  }

  public static void compressVariable(final int[] in, final int startIndex, final int endIndex,
      final ByteBuf buf) {
    int initValue = 0;
    for (int index = startIndex; index < endIndex; index++) {
      final int value = (in[index] - initValue);
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
      } else {
        buf.writeByte((byte)((value & 127) | 128));
        buf.writeByte((byte)(value >>> 7 | 128));
        buf.writeByte((byte)(value >>> 14 | 128));
        buf.writeByte((byte)(value >>> 21 | 128));
        buf.writeByte((byte)(value >>> 28));
      }
    }
  }

  public static void decompressVariable(final ByteBuf buf, final int[] out, final int endIndex) {
    int initValue = 0;
    int value;
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
              value |= buf.readByte() << 28;
            }
          }
        }
      }
    }
  }

  private static int maxDiffBits(int initValue, int[] in, int position) {
    int mask = in[position] - initValue;
    for (int i = position + 1; i < position + 32; ++i) {
      mask |= in[i] - in[i - 1];
    }
    return 32 - Integer.numberOfLeadingZeros(mask);
  }

  /**
   * Pack 32 32-bit integers as deltas with an initial value.
   *
   * @param initValue initial value (used to compute first delta)
   * @param in         input array
   * @param inPos      initial position in input array
   * @param out        output array
   * @param outPos     initial position in output array
   * @param bitCount        number of bits to use per integer
   */
  private static void pack(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos, final int bitCount) {
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
        System.arraycopy(in, inPos, out, outPos, 32);
        break;
      }
      default: {
        throw new IllegalArgumentException("Unsupported bit width: " + bitCount);
      }
    }
  }

  /**
   * Unpack 32 32-bit integers as deltas with an initial value.
   *
   * @param initValue initial value (used to compute first delta)
   * @param in         input array
   * @param inPos      initial position in input array
   * @param out        output array
   * @param outPos     initial position in output array
   * @param bitCount        number of bits to use per integer
   */
  private static void unpack(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos, final int bitCount) {
    switch ((byte)bitCount) {
      case 0: {
        Arrays.fill(out, outPos, outPos + 32, initValue);
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
        System.arraycopy(in, inPos, out, outPos, 32);
        break;
      }
      default: {
        throw new IllegalArgumentException("Unsupported bit width: " + bitCount);
      }
    }
  }

  private static void pack1(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 1 | (in[inPos + 2] - in[inPos + 1]) << 2 | (in[inPos + 3] - in[inPos + 2]) << 3 | (in[inPos + 4] - in[inPos + 3]) << 4 | (in[inPos + 5] - in[inPos + 4]) << 5 | (in[inPos + 6] - in[inPos + 5]) << 6 | (in[inPos + 7] - in[inPos + 6]) << 7 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 9 | (in[inPos + 10] - in[inPos + 9]) << 10 | (in[inPos + 11] - in[inPos + 10]) << 11 | (in[inPos + 12] - in[inPos + 11]) << 12 | (in[inPos + 13] - in[inPos + 12]) << 13 | (in[inPos + 14] - in[inPos + 13]) << 14 | (in[inPos + 15] - in[inPos + 14]) << 15 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 17 | (in[inPos + 18] - in[inPos + 17]) << 18 | (in[inPos + 19] - in[inPos + 18]) << 19 | (in[inPos + 20] - in[inPos + 19]) << 20 | (in[inPos + 21] - in[inPos + 20]) << 21 | (in[inPos + 22] - in[inPos + 21]) << 22 | (in[inPos + 23] - in[inPos + 22]) << 23 | (in[inPos + 24] - in[inPos + 23]) << 24 | (in[inPos + 25] - in[inPos + 24]) << 25 | (in[inPos + 26] - in[inPos + 25]) << 26 | (in[inPos + 27] - in[inPos + 26]) << 27 | (in[inPos + 28] - in[inPos + 27]) << 28 | (in[inPos + 29] - in[inPos + 28]) << 29 | (in[inPos + 30] - in[inPos + 29]) << 30 | (in[inPos + 31] - in[inPos + 30]) << 31;
  }

  private static void unpack1(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
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
    out[outPos + 31] = (in[inPos] >>> 31) + out[outPos + 30];
  }

  private static void pack2(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 2 | (in[inPos + 2] - in[inPos + 1]) << 4 | (in[inPos + 3] - in[inPos + 2]) << 6 | (in[inPos + 4] - in[inPos + 3]) << 8 | (in[inPos + 5] - in[inPos + 4]) << 10 | (in[inPos + 6] - in[inPos + 5]) << 12 | (in[inPos + 7] - in[inPos + 6]) << 14 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 18 | (in[inPos + 10] - in[inPos + 9]) << 20 | (in[inPos + 11] - in[inPos + 10]) << 22 | (in[inPos + 12] - in[inPos + 11]) << 24 | (in[inPos + 13] - in[inPos + 12]) << 26 | (in[inPos + 14] - in[inPos + 13]) << 28 | (in[inPos + 15] - in[inPos + 14]) << 30;
    out[outPos + 1] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 2 | (in[inPos + 18] - in[inPos + 17]) << 4 | (in[inPos + 19] - in[inPos + 18]) << 6 | (in[inPos + 20] - in[inPos + 19]) << 8 | (in[inPos + 21] - in[inPos + 20]) << 10 | (in[inPos + 22] - in[inPos + 21]) << 12 | (in[inPos + 23] - in[inPos + 22]) << 14 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 18 | (in[inPos + 26] - in[inPos + 25]) << 20 | (in[inPos + 27] - in[inPos + 26]) << 22 | (in[inPos + 28] - in[inPos + 27]) << 24 | (in[inPos + 29] - in[inPos + 28]) << 26 | (in[inPos + 30] - in[inPos + 29]) << 28 | (in[inPos + 31] - in[inPos + 30]) << 30;
  }

  private static void unpack2(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
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
    out[outPos + 15] = (in[inPos] >>> 30) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 1] & 3) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 1] >>> 2 & 3) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 1] >>> 4 & 3) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 1] >>> 6 & 3) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 1] >>> 8 & 3) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 1] >>> 10 & 3) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 1] >>> 12 & 3) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 1] >>> 14 & 3) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 1] >>> 16 & 3) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 1] >>> 18 & 3) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 1] >>> 20 & 3) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 1] >>> 22 & 3) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 1] >>> 24 & 3) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 1] >>> 26 & 3) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 1] >>> 28 & 3) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 1] >>> 30) + out[outPos + 30];
  }

  private static void pack3(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 3 | (in[inPos + 2] - in[inPos + 1]) << 6 | (in[inPos + 3] - in[inPos + 2]) << 9 | (in[inPos + 4] - in[inPos + 3]) << 12 | (in[inPos + 5] - in[inPos + 4]) << 15 | (in[inPos + 6] - in[inPos + 5]) << 18 | (in[inPos + 7] - in[inPos + 6]) << 21 | (in[inPos + 8] - in[inPos + 7]) << 24 | (in[inPos + 9] - in[inPos + 8]) << 27 | (in[inPos + 10] - in[inPos + 9]) << 30;
    out[outPos + 1] = (in[inPos + 10] - in[inPos + 9]) >>> 2 | (in[inPos + 11] - in[inPos + 10]) << 1 | (in[inPos + 12] - in[inPos + 11]) << 4 | (in[inPos + 13] - in[inPos + 12]) << 7 | (in[inPos + 14] - in[inPos + 13]) << 10 | (in[inPos + 15] - in[inPos + 14]) << 13 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 19 | (in[inPos + 18] - in[inPos + 17]) << 22 | (in[inPos + 19] - in[inPos + 18]) << 25 | (in[inPos + 20] - in[inPos + 19]) << 28 | (in[inPos + 21] - in[inPos + 20]) << 31;
    out[outPos + 2] = (in[inPos + 21] - in[inPos + 20]) >>> 1 | (in[inPos + 22] - in[inPos + 21]) << 2 | (in[inPos + 23] - in[inPos + 22]) << 5 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 11 | (in[inPos + 26] - in[inPos + 25]) << 14 | (in[inPos + 27] - in[inPos + 26]) << 17 | (in[inPos + 28] - in[inPos + 27]) << 20 | (in[inPos + 29] - in[inPos + 28]) << 23 | (in[inPos + 30] - in[inPos + 29]) << 26 | (in[inPos + 31] - in[inPos + 30]) << 29;
  }

  private static void unpack3(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
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
    out[outPos + 10] = (in[inPos] >>> 30 | (in[inPos + 1] & 1) << 2) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 1 & 7) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 1] >>> 4 & 7) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 1] >>> 7 & 7) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 1] >>> 10 & 7) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 1] >>> 13 & 7) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 1] >>> 16 & 7) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 1] >>> 19 & 7) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 1] >>> 22 & 7) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 1] >>> 25 & 7) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 1] >>> 28 & 7) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 1] >>> 31 | (in[inPos + 2] & 3) << 1) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 2] >>> 2 & 7) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 2] >>> 5 & 7) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 2] >>> 8 & 7) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 2] >>> 11 & 7) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 2] >>> 14 & 7) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 2] >>> 17 & 7) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 2] >>> 20 & 7) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 2] >>> 23 & 7) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 2] >>> 26 & 7) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 2] >>> 29) + out[outPos + 30];
  }

  private static void pack4(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 4 | (in[inPos + 2] - in[inPos + 1]) << 8 | (in[inPos + 3] - in[inPos + 2]) << 12 | (in[inPos + 4] - in[inPos + 3]) << 16 | (in[inPos + 5] - in[inPos + 4]) << 20 | (in[inPos + 6] - in[inPos + 5]) << 24 | (in[inPos + 7] - in[inPos + 6]) << 28;
    out[outPos + 1] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 4 | (in[inPos + 10] - in[inPos + 9]) << 8 | (in[inPos + 11] - in[inPos + 10]) << 12 | (in[inPos + 12] - in[inPos + 11]) << 16 | (in[inPos + 13] - in[inPos + 12]) << 20 | (in[inPos + 14] - in[inPos + 13]) << 24 | (in[inPos + 15] - in[inPos + 14]) << 28;
    out[outPos + 2] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 4 | (in[inPos + 18] - in[inPos + 17]) << 8 | (in[inPos + 19] - in[inPos + 18]) << 12 | (in[inPos + 20] - in[inPos + 19]) << 16 | (in[inPos + 21] - in[inPos + 20]) << 20 | (in[inPos + 22] - in[inPos + 21]) << 24 | (in[inPos + 23] - in[inPos + 22]) << 28;
    out[outPos + 3] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 4 | (in[inPos + 26] - in[inPos + 25]) << 8 | (in[inPos + 27] - in[inPos + 26]) << 12 | (in[inPos + 28] - in[inPos + 27]) << 16 | (in[inPos + 29] - in[inPos + 28]) << 20 | (in[inPos + 30] - in[inPos + 29]) << 24 | (in[inPos + 31] - in[inPos + 30]) << 28;
  }

  private static void unpack4(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = (in[inPos] & 15) + initValue;
    out[outPos + 1] = (in[inPos] >>> 4 & 15) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 8 & 15) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 12 & 15) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 16 & 15) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 20 & 15) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 24 & 15) + out[outPos + 5];
    out[outPos + 7] = (in[inPos] >>> 28) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] & 15) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 4 & 15) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 8 & 15) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 12 & 15) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 1] >>> 16 & 15) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 1] >>> 20 & 15) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 1] >>> 24 & 15) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 1] >>> 28) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 2] & 15) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 2] >>> 4 & 15) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 2] >>> 8 & 15) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 2] >>> 12 & 15) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 2] >>> 16 & 15) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 2] >>> 20 & 15) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 2] >>> 24 & 15) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 2] >>> 28) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 3] & 15) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 3] >>> 4 & 15) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 3] >>> 8 & 15) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 3] >>> 12 & 15) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 3] >>> 16 & 15) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 3] >>> 20 & 15) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 3] >>> 24 & 15) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 3] >>> 28) + out[outPos + 30];
  }

  private static void pack5(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 5 | (in[inPos + 2] - in[inPos + 1]) << 10 | (in[inPos + 3] - in[inPos + 2]) << 15 | (in[inPos + 4] - in[inPos + 3]) << 20 | (in[inPos + 5] - in[inPos + 4]) << 25 | (in[inPos + 6] - in[inPos + 5]) << 30;
    out[outPos + 1] = (in[inPos + 6] - in[inPos + 5]) >>> 2 | (in[inPos + 7] - in[inPos + 6]) << 3 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 13 | (in[inPos + 10] - in[inPos + 9]) << 18 | (in[inPos + 11] - in[inPos + 10]) << 23 | (in[inPos + 12] - in[inPos + 11]) << 28;
    out[outPos + 2] = (in[inPos + 12] - in[inPos + 11]) >>> 4 | (in[inPos + 13] - in[inPos + 12]) << 1 | (in[inPos + 14] - in[inPos + 13]) << 6 | (in[inPos + 15] - in[inPos + 14]) << 11 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 21 | (in[inPos + 18] - in[inPos + 17]) << 26 | (in[inPos + 19] - in[inPos + 18]) << 31;
    out[outPos + 3] = (in[inPos + 19] - in[inPos + 18]) >>> 1 | (in[inPos + 20] - in[inPos + 19]) << 4 | (in[inPos + 21] - in[inPos + 20]) << 9 | (in[inPos + 22] - in[inPos + 21]) << 14 | (in[inPos + 23] - in[inPos + 22]) << 19 | (in[inPos + 24] - in[inPos + 23]) << 24 | (in[inPos + 25] - in[inPos + 24]) << 29;
    out[outPos + 4] = (in[inPos + 25] - in[inPos + 24]) >>> 3 | (in[inPos + 26] - in[inPos + 25]) << 2 | (in[inPos + 27] - in[inPos + 26]) << 7 | (in[inPos + 28] - in[inPos + 27]) << 12 | (in[inPos + 29] - in[inPos + 28]) << 17 | (in[inPos + 30] - in[inPos + 29]) << 22 | (in[inPos + 31] - in[inPos + 30]) << 27;
  }

  private static void unpack5(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = (in[inPos] & 31) + initValue;
    out[outPos + 1] = (in[inPos] >>> 5 & 31) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 10 & 31) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 15 & 31) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 20 & 31) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 25 & 31) + out[outPos + 4];
    out[outPos + 6] = (in[inPos] >>> 30 | (in[inPos + 1] & 7) << 2) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 3 & 31) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 8 & 31) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 13 & 31) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 18 & 31) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 1] >>> 23 & 31) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 1] >>> 28 | (in[inPos + 2] & 1) << 4) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 2] >>> 1 & 31) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 2] >>> 6 & 31) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 2] >>> 11 & 31) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 2] >>> 16 & 31) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 2] >>> 21 & 31) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 2] >>> 26 & 31) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 2] >>> 31 | (in[inPos + 3] & 15) << 1) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 3] >>> 4 & 31) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 3] >>> 9 & 31) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 3] >>> 14 & 31) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 3] >>> 19 & 31) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 3] >>> 24 & 31) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 3] >>> 29 | (in[inPos + 4] & 3) << 3) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 4] >>> 2 & 31) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 4] >>> 7 & 31) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 4] >>> 12 & 31) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 4] >>> 17 & 31) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 4] >>> 22 & 31) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 4] >>> 27) + out[outPos + 30];
  }

  private static void pack6(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 6 | (in[inPos + 2] - in[inPos + 1]) << 12 | (in[inPos + 3] - in[inPos + 2]) << 18 | (in[inPos + 4] - in[inPos + 3]) << 24 | (in[inPos + 5] - in[inPos + 4]) << 30;
    out[outPos + 1] = (in[inPos + 5] - in[inPos + 4]) >>> 2 | (in[inPos + 6] - in[inPos + 5]) << 4 | (in[inPos + 7] - in[inPos + 6]) << 10 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 22 | (in[inPos + 10] - in[inPos + 9]) << 28;
    out[outPos + 2] = (in[inPos + 10] - in[inPos + 9]) >>> 4 | (in[inPos + 11] - in[inPos + 10]) << 2 | (in[inPos + 12] - in[inPos + 11]) << 8 | (in[inPos + 13] - in[inPos + 12]) << 14 | (in[inPos + 14] - in[inPos + 13]) << 20 | (in[inPos + 15] - in[inPos + 14]) << 26;
    out[outPos + 3] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 6 | (in[inPos + 18] - in[inPos + 17]) << 12 | (in[inPos + 19] - in[inPos + 18]) << 18 | (in[inPos + 20] - in[inPos + 19]) << 24 | (in[inPos + 21] - in[inPos + 20]) << 30;
    out[outPos + 4] = (in[inPos + 21] - in[inPos + 20]) >>> 2 | (in[inPos + 22] - in[inPos + 21]) << 4 | (in[inPos + 23] - in[inPos + 22]) << 10 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 22 | (in[inPos + 26] - in[inPos + 25]) << 28;
    out[outPos + 5] = (in[inPos + 26] - in[inPos + 25]) >>> 4 | (in[inPos + 27] - in[inPos + 26]) << 2 | (in[inPos + 28] - in[inPos + 27]) << 8 | (in[inPos + 29] - in[inPos + 28]) << 14 | (in[inPos + 30] - in[inPos + 29]) << 20 | (in[inPos + 31] - in[inPos + 30]) << 26;
  }

  private static void unpack6(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = (in[inPos] & 63) + initValue;
    out[outPos + 1] = (in[inPos] >>> 6 & 63) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 12 & 63) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 18 & 63) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 24 & 63) + out[outPos + 3];
    out[outPos + 5] = (in[inPos] >>> 30 | (in[inPos + 1] & 15) << 2) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 4 & 63) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 10 & 63) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 16 & 63) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 22 & 63) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 1] >>> 28 | (in[inPos + 2] & 3) << 4) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 2 & 63) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 2] >>> 8 & 63) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 2] >>> 14 & 63) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 2] >>> 20 & 63) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 2] >>> 26) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 3] & 63) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 3] >>> 6 & 63) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 3] >>> 12 & 63) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 3] >>> 18 & 63) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 3] >>> 24 & 63) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 3] >>> 30 | (in[inPos + 4] & 15) << 2) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 4] >>> 4 & 63) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 4] >>> 10 & 63) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 4] >>> 16 & 63) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 4] >>> 22 & 63) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 4] >>> 28 | (in[inPos + 5] & 3) << 4) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 5] >>> 2 & 63) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 5] >>> 8 & 63) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 5] >>> 14 & 63) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 5] >>> 20 & 63) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 5] >>> 26) + out[outPos + 30];
  }

  private static void pack7(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 7 | (in[inPos + 2] - in[inPos + 1]) << 14 | (in[inPos + 3] - in[inPos + 2]) << 21 | (in[inPos + 4] - in[inPos + 3]) << 28;
    out[outPos + 1] = (in[inPos + 4] - in[inPos + 3]) >>> 4 | (in[inPos + 5] - in[inPos + 4]) << 3 | (in[inPos + 6] - in[inPos + 5]) << 10 | (in[inPos + 7] - in[inPos + 6]) << 17 | (in[inPos + 8] - in[inPos + 7]) << 24 | (in[inPos + 9] - in[inPos + 8]) << 31;
    out[outPos + 2] = (in[inPos + 9] - in[inPos + 8]) >>> 1 | (in[inPos + 10] - in[inPos + 9]) << 6 | (in[inPos + 11] - in[inPos + 10]) << 13 | (in[inPos + 12] - in[inPos + 11]) << 20 | (in[inPos + 13] - in[inPos + 12]) << 27;
    out[outPos + 3] = (in[inPos + 13] - in[inPos + 12]) >>> 5 | (in[inPos + 14] - in[inPos + 13]) << 2 | (in[inPos + 15] - in[inPos + 14]) << 9 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 23 | (in[inPos + 18] - in[inPos + 17]) << 30;
    out[outPos + 4] = (in[inPos + 18] - in[inPos + 17]) >>> 2 | (in[inPos + 19] - in[inPos + 18]) << 5 | (in[inPos + 20] - in[inPos + 19]) << 12 | (in[inPos + 21] - in[inPos + 20]) << 19 | (in[inPos + 22] - in[inPos + 21]) << 26;
    out[outPos + 5] = (in[inPos + 22] - in[inPos + 21]) >>> 6 | (in[inPos + 23] - in[inPos + 22]) << 1 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 15 | (in[inPos + 26] - in[inPos + 25]) << 22 | (in[inPos + 27] - in[inPos + 26]) << 29;
    out[outPos + 6] = (in[inPos + 27] - in[inPos + 26]) >>> 3 | (in[inPos + 28] - in[inPos + 27]) << 4 | (in[inPos + 29] - in[inPos + 28]) << 11 | (in[inPos + 30] - in[inPos + 29]) << 18 | (in[inPos + 31] - in[inPos + 30]) << 25;
  }

  private static void unpack7(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = (in[inPos] & 127) + initValue;
    out[outPos + 1] = (in[inPos] >>> 7 & 127) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 14 & 127) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 21 & 127) + out[outPos + 2];
    out[outPos + 4] = (in[inPos] >>> 28 | (in[inPos + 1] & 7) << 4) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 3 & 127) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 10 & 127) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 17 & 127) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 1] >>> 24 & 127) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 1] >>> 31 | (in[inPos + 2] & 63) << 1) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 6 & 127) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 13 & 127) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 2] >>> 20 & 127) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 2] >>> 27 | (in[inPos + 3] & 3) << 5) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 3] >>> 2 & 127) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 3] >>> 9 & 127) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 3] >>> 16 & 127) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 3] >>> 23 & 127) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 3] >>> 30 | (in[inPos + 4] & 31) << 2) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 4] >>> 5 & 127) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 4] >>> 12 & 127) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 4] >>> 19 & 127) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 4] >>> 26 | (in[inPos + 5] & 1) << 6) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 5] >>> 1 & 127) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 5] >>> 8 & 127) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 5] >>> 15 & 127) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 5] >>> 22 & 127) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 5] >>> 29 | (in[inPos + 6] & 15) << 3) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 6] >>> 4 & 127) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 6] >>> 11 & 127) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 6] >>> 18 & 127) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 6] >>> 25) + out[outPos + 30];
  }

  private static void pack8(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 8 | (in[inPos + 2] - in[inPos + 1]) << 16 | (in[inPos + 3] - in[inPos + 2]) << 24;
    out[outPos + 1] = (in[inPos + 4] - in[inPos + 3]) | (in[inPos + 5] - in[inPos + 4]) << 8 | (in[inPos + 6] - in[inPos + 5]) << 16 | (in[inPos + 7] - in[inPos + 6]) << 24;
    out[outPos + 2] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 8 | (in[inPos + 10] - in[inPos + 9]) << 16 | (in[inPos + 11] - in[inPos + 10]) << 24;
    out[outPos + 3] = (in[inPos + 12] - in[inPos + 11]) | (in[inPos + 13] - in[inPos + 12]) << 8 | (in[inPos + 14] - in[inPos + 13]) << 16 | (in[inPos + 15] - in[inPos + 14]) << 24;
    out[outPos + 4] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 8 | (in[inPos + 18] - in[inPos + 17]) << 16 | (in[inPos + 19] - in[inPos + 18]) << 24;
    out[outPos + 5] = (in[inPos + 20] - in[inPos + 19]) | (in[inPos + 21] - in[inPos + 20]) << 8 | (in[inPos + 22] - in[inPos + 21]) << 16 | (in[inPos + 23] - in[inPos + 22]) << 24;
    out[outPos + 6] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 8 | (in[inPos + 26] - in[inPos + 25]) << 16 | (in[inPos + 27] - in[inPos + 26]) << 24;
    out[outPos + 7] = (in[inPos + 28] - in[inPos + 27]) | (in[inPos + 29] - in[inPos + 28]) << 8 | (in[inPos + 30] - in[inPos + 29]) << 16 | (in[inPos + 31] - in[inPos + 30]) << 24;
  }

  private static void unpack8(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = (in[inPos] & 255) + initValue;
    out[outPos + 1] = (in[inPos] >>> 8 & 255) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 16 & 255) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 24) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] & 255) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 8 & 255) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 16 & 255) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 24) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] & 255) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 8 & 255) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 16 & 255) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 2] >>> 24) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] & 255) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 3] >>> 8 & 255) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 3] >>> 16 & 255) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 3] >>> 24) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 4] & 255) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 4] >>> 8 & 255) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 4] >>> 16 & 255) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 4] >>> 24) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 5] & 255) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 5] >>> 8 & 255) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 5] >>> 16 & 255) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 5] >>> 24) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 6] & 255) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 6] >>> 8 & 255) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 6] >>> 16 & 255) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 6] >>> 24) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 7] & 255) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 7] >>> 8 & 255) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 7] >>> 16 & 255) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 7] >>> 24) + out[outPos + 30];
  }

  private static void pack9(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 9 | (in[inPos + 2] - in[inPos + 1]) << 18 | (in[inPos + 3] - in[inPos + 2]) << 27;
    out[outPos + 1] = (in[inPos + 3] - in[inPos + 2]) >>> 5 | (in[inPos + 4] - in[inPos + 3]) << 4 | (in[inPos + 5] - in[inPos + 4]) << 13 | (in[inPos + 6] - in[inPos + 5]) << 22 | (in[inPos + 7] - in[inPos + 6]) << 31;
    out[outPos + 2] = (in[inPos + 7] - in[inPos + 6]) >>> 1 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 17 | (in[inPos + 10] - in[inPos + 9]) << 26;
    out[outPos + 3] = (in[inPos + 10] - in[inPos + 9]) >>> 6 | (in[inPos + 11] - in[inPos + 10]) << 3 | (in[inPos + 12] - in[inPos + 11]) << 12 | (in[inPos + 13] - in[inPos + 12]) << 21 | (in[inPos + 14] - in[inPos + 13]) << 30;
    out[outPos + 4] = (in[inPos + 14] - in[inPos + 13]) >>> 2 | (in[inPos + 15] - in[inPos + 14]) << 7 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 25;
    out[outPos + 5] = (in[inPos + 17] - in[inPos + 16]) >>> 7 | (in[inPos + 18] - in[inPos + 17]) << 2 | (in[inPos + 19] - in[inPos + 18]) << 11 | (in[inPos + 20] - in[inPos + 19]) << 20 | (in[inPos + 21] - in[inPos + 20]) << 29;
    out[outPos + 6] = (in[inPos + 21] - in[inPos + 20]) >>> 3 | (in[inPos + 22] - in[inPos + 21]) << 6 | (in[inPos + 23] - in[inPos + 22]) << 15 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 7] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 1 | (in[inPos + 26] - in[inPos + 25]) << 10 | (in[inPos + 27] - in[inPos + 26]) << 19 | (in[inPos + 28] - in[inPos + 27]) << 28;
    out[outPos + 8] = (in[inPos + 28] - in[inPos + 27]) >>> 4 | (in[inPos + 29] - in[inPos + 28]) << 5 | (in[inPos + 30] - in[inPos + 29]) << 14 | (in[inPos + 31] - in[inPos + 30]) << 23;
  }

  private static void unpack9(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = (in[inPos] & 511) + initValue;
    out[outPos + 1] = (in[inPos] >>> 9 & 511) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 18 & 511) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 27 | (in[inPos + 1] & 15) << 5) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 4 & 511) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 13 & 511) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 22 & 511) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 1] >>> 31 | (in[inPos + 2] & 255) << 1) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 8 & 511) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 17 & 511) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 2] >>> 26 | (in[inPos + 3] & 7) << 6) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 3 & 511) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] >>> 12 & 511) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 3] >>> 21 & 511) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 3] >>> 30 | (in[inPos + 4] & 127) << 2) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 4] >>> 7 & 511) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 4] >>> 16 & 511) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 4] >>> 25 | (in[inPos + 5] & 3) << 7) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 5] >>> 2 & 511) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 5] >>> 11 & 511) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 5] >>> 20 & 511) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 5] >>> 29 | (in[inPos + 6] & 63) << 3) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 6] >>> 6 & 511) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 6] >>> 15 & 511) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 6] >>> 24 | (in[inPos + 7] & 1) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 7] >>> 1 & 511) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 7] >>> 10 & 511) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 7] >>> 19 & 511) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 7] >>> 28 | (in[inPos + 8] & 31) << 4) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 8] >>> 5 & 511) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 8] >>> 14 & 511) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 8] >>> 23) + out[outPos + 30];
  }

  private static void pack10(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 10 | (in[inPos + 2] - in[inPos + 1]) << 20 | (in[inPos + 3] - in[inPos + 2]) << 30;
    out[outPos + 1] = (in[inPos + 3] - in[inPos + 2]) >>> 2 | (in[inPos + 4] - in[inPos + 3]) << 8 | (in[inPos + 5] - in[inPos + 4]) << 18 | (in[inPos + 6] - in[inPos + 5]) << 28;
    out[outPos + 2] = (in[inPos + 6] - in[inPos + 5]) >>> 4 | (in[inPos + 7] - in[inPos + 6]) << 6 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 26;
    out[outPos + 3] = (in[inPos + 9] - in[inPos + 8]) >>> 6 | (in[inPos + 10] - in[inPos + 9]) << 4 | (in[inPos + 11] - in[inPos + 10]) << 14 | (in[inPos + 12] - in[inPos + 11]) << 24;
    out[outPos + 4] = (in[inPos + 12] - in[inPos + 11]) >>> 8 | (in[inPos + 13] - in[inPos + 12]) << 2 | (in[inPos + 14] - in[inPos + 13]) << 12 | (in[inPos + 15] - in[inPos + 14]) << 22;
    out[outPos + 5] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 10 | (in[inPos + 18] - in[inPos + 17]) << 20 | (in[inPos + 19] - in[inPos + 18]) << 30;
    out[outPos + 6] = (in[inPos + 19] - in[inPos + 18]) >>> 2 | (in[inPos + 20] - in[inPos + 19]) << 8 | (in[inPos + 21] - in[inPos + 20]) << 18 | (in[inPos + 22] - in[inPos + 21]) << 28;
    out[outPos + 7] = (in[inPos + 22] - in[inPos + 21]) >>> 4 | (in[inPos + 23] - in[inPos + 22]) << 6 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 26;
    out[outPos + 8] = (in[inPos + 25] - in[inPos + 24]) >>> 6 | (in[inPos + 26] - in[inPos + 25]) << 4 | (in[inPos + 27] - in[inPos + 26]) << 14 | (in[inPos + 28] - in[inPos + 27]) << 24;
    out[outPos + 9] = (in[inPos + 28] - in[inPos + 27]) >>> 8 | (in[inPos + 29] - in[inPos + 28]) << 2 | (in[inPos + 30] - in[inPos + 29]) << 12 | (in[inPos + 31] - in[inPos + 30]) << 22;
  }

  private static void unpack10(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1023) + initValue;
    out[outPos + 1] = (in[inPos] >>> 10 & 1023) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 20 & 1023) + out[outPos + 1];
    out[outPos + 3] = (in[inPos] >>> 30 | (in[inPos + 1] & 255) << 2) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 8 & 1023) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 18 & 1023) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 1] >>> 28 | (in[inPos + 2] & 63) << 4) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 6 & 1023) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 16 & 1023) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 2] >>> 26 | (in[inPos + 3] & 15) << 6) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 4 & 1023) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 14 & 1023) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 3] >>> 24 | (in[inPos + 4] & 3) << 8) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 4] >>> 2 & 1023) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 4] >>> 12 & 1023) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 4] >>> 22) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 5] & 1023) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 5] >>> 10 & 1023) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 5] >>> 20 & 1023) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 5] >>> 30 | (in[inPos + 6] & 255) << 2) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 6] >>> 8 & 1023) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 6] >>> 18 & 1023) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 6] >>> 28 | (in[inPos + 7] & 63) << 4) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 7] >>> 6 & 1023) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 7] >>> 16 & 1023) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 7] >>> 26 | (in[inPos + 8] & 15) << 6) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 8] >>> 4 & 1023) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 8] >>> 14 & 1023) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 8] >>> 24 | (in[inPos + 9] & 3) << 8) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 9] >>> 2 & 1023) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 9] >>> 12 & 1023) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 9] >>> 22) + out[outPos + 30];
  }

  private static void pack11(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 11 | (in[inPos + 2] - in[inPos + 1]) << 22;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 10 | (in[inPos + 3] - in[inPos + 2]) << 1 | (in[inPos + 4] - in[inPos + 3]) << 12 | (in[inPos + 5] - in[inPos + 4]) << 23;
    out[outPos + 2] = (in[inPos + 5] - in[inPos + 4]) >>> 9 | (in[inPos + 6] - in[inPos + 5]) << 2 | (in[inPos + 7] - in[inPos + 6]) << 13 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 3] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 3 | (in[inPos + 10] - in[inPos + 9]) << 14 | (in[inPos + 11] - in[inPos + 10]) << 25;
    out[outPos + 4] = (in[inPos + 11] - in[inPos + 10]) >>> 7 | (in[inPos + 12] - in[inPos + 11]) << 4 | (in[inPos + 13] - in[inPos + 12]) << 15 | (in[inPos + 14] - in[inPos + 13]) << 26;
    out[outPos + 5] = (in[inPos + 14] - in[inPos + 13]) >>> 6 | (in[inPos + 15] - in[inPos + 14]) << 5 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 27;
    out[outPos + 6] = (in[inPos + 17] - in[inPos + 16]) >>> 5 | (in[inPos + 18] - in[inPos + 17]) << 6 | (in[inPos + 19] - in[inPos + 18]) << 17 | (in[inPos + 20] - in[inPos + 19]) << 28;
    out[outPos + 7] = (in[inPos + 20] - in[inPos + 19]) >>> 4 | (in[inPos + 21] - in[inPos + 20]) << 7 | (in[inPos + 22] - in[inPos + 21]) << 18 | (in[inPos + 23] - in[inPos + 22]) << 29;
    out[outPos + 8] = (in[inPos + 23] - in[inPos + 22]) >>> 3 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 19 | (in[inPos + 26] - in[inPos + 25]) << 30;
    out[outPos + 9] = (in[inPos + 26] - in[inPos + 25]) >>> 2 | (in[inPos + 27] - in[inPos + 26]) << 9 | (in[inPos + 28] - in[inPos + 27]) << 20 | (in[inPos + 29] - in[inPos + 28]) << 31;
    out[outPos + 10] = (in[inPos + 29] - in[inPos + 28]) >>> 1 | (in[inPos + 30] - in[inPos + 29]) << 10 | (in[inPos + 31] - in[inPos + 30]) << 21;
  }

  private static void unpack11(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2047) + initValue;
    out[outPos + 1] = (in[inPos] >>> 11 & 2047) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 22 | (in[inPos + 1] & 1) << 10) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 1 & 2047) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 12 & 2047) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 23 | (in[inPos + 2] & 3) << 9) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 2 & 2047) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 13 & 2047) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 2] >>> 24 | (in[inPos + 3] & 7) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 3 & 2047) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 14 & 2047) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 3] >>> 25 | (in[inPos + 4] & 15) << 7) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 4] >>> 4 & 2047) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 4] >>> 15 & 2047) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 4] >>> 26 | (in[inPos + 5] & 31) << 6) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 5] >>> 5 & 2047) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 5] >>> 16 & 2047) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 5] >>> 27 | (in[inPos + 6] & 63) << 5) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 6] >>> 6 & 2047) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 6] >>> 17 & 2047) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 6] >>> 28 | (in[inPos + 7] & 127) << 4) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 7] >>> 7 & 2047) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 7] >>> 18 & 2047) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 7] >>> 29 | (in[inPos + 8] & 255) << 3) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 8] >>> 8 & 2047) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 8] >>> 19 & 2047) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 8] >>> 30 | (in[inPos + 9] & 511) << 2) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 9] >>> 9 & 2047) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 9] >>> 20 & 2047) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 9] >>> 31 | (in[inPos + 10] & 1023) << 1) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 10] >>> 10 & 2047) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 10] >>> 21) + out[outPos + 30];
  }

  private static void pack12(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 12 | (in[inPos + 2] - in[inPos + 1]) << 24;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 8 | (in[inPos + 3] - in[inPos + 2]) << 4 | (in[inPos + 4] - in[inPos + 3]) << 16 | (in[inPos + 5] - in[inPos + 4]) << 28;
    out[outPos + 2] = (in[inPos + 5] - in[inPos + 4]) >>> 4 | (in[inPos + 6] - in[inPos + 5]) << 8 | (in[inPos + 7] - in[inPos + 6]) << 20;
    out[outPos + 3] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 12 | (in[inPos + 10] - in[inPos + 9]) << 24;
    out[outPos + 4] = (in[inPos + 10] - in[inPos + 9]) >>> 8 | (in[inPos + 11] - in[inPos + 10]) << 4 | (in[inPos + 12] - in[inPos + 11]) << 16 | (in[inPos + 13] - in[inPos + 12]) << 28;
    out[outPos + 5] = (in[inPos + 13] - in[inPos + 12]) >>> 4 | (in[inPos + 14] - in[inPos + 13]) << 8 | (in[inPos + 15] - in[inPos + 14]) << 20;
    out[outPos + 6] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 12 | (in[inPos + 18] - in[inPos + 17]) << 24;
    out[outPos + 7] = (in[inPos + 18] - in[inPos + 17]) >>> 8 | (in[inPos + 19] - in[inPos + 18]) << 4 | (in[inPos + 20] - in[inPos + 19]) << 16 | (in[inPos + 21] - in[inPos + 20]) << 28;
    out[outPos + 8] = (in[inPos + 21] - in[inPos + 20]) >>> 4 | (in[inPos + 22] - in[inPos + 21]) << 8 | (in[inPos + 23] - in[inPos + 22]) << 20;
    out[outPos + 9] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 12 | (in[inPos + 26] - in[inPos + 25]) << 24;
    out[outPos + 10] = (in[inPos + 26] - in[inPos + 25]) >>> 8 | (in[inPos + 27] - in[inPos + 26]) << 4 | (in[inPos + 28] - in[inPos + 27]) << 16 | (in[inPos + 29] - in[inPos + 28]) << 28;
    out[outPos + 11] = (in[inPos + 29] - in[inPos + 28]) >>> 4 | (in[inPos + 30] - in[inPos + 29]) << 8 | (in[inPos + 31] - in[inPos + 30]) << 20;
  }

  private static void unpack12(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 4095) + initValue;
    out[outPos + 1] = (in[inPos] >>> 12 & 4095) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 24 | (in[inPos + 1] & 15) << 8) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 4 & 4095) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 16 & 4095) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 1] >>> 28 | (in[inPos + 2] & 255) << 4) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 8 & 4095) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 20) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] & 4095) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 12 & 4095) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 3] >>> 24 | (in[inPos + 4] & 15) << 8) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 4 & 4095) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 4] >>> 16 & 4095) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 4] >>> 28 | (in[inPos + 5] & 255) << 4) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 5] >>> 8 & 4095) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 5] >>> 20) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 6] & 4095) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 6] >>> 12 & 4095) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 6] >>> 24 | (in[inPos + 7] & 15) << 8) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 7] >>> 4 & 4095) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 7] >>> 16 & 4095) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 7] >>> 28 | (in[inPos + 8] & 255) << 4) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 8] >>> 8 & 4095) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 8] >>> 20) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 9] & 4095) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 9] >>> 12 & 4095) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 9] >>> 24 | (in[inPos + 10] & 15) << 8) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 10] >>> 4 & 4095) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 10] >>> 16 & 4095) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 10] >>> 28 | (in[inPos + 11] & 255) << 4) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 11] >>> 8 & 4095) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 11] >>> 20) + out[outPos + 30];
  }

  private static void pack13(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 13 | (in[inPos + 2] - in[inPos + 1]) << 26;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 6 | (in[inPos + 3] - in[inPos + 2]) << 7 | (in[inPos + 4] - in[inPos + 3]) << 20;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 12 | (in[inPos + 5] - in[inPos + 4]) << 1 | (in[inPos + 6] - in[inPos + 5]) << 14 | (in[inPos + 7] - in[inPos + 6]) << 27;
    out[outPos + 3] = (in[inPos + 7] - in[inPos + 6]) >>> 5 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 21;
    out[outPos + 4] = (in[inPos + 9] - in[inPos + 8]) >>> 11 | (in[inPos + 10] - in[inPos + 9]) << 2 | (in[inPos + 11] - in[inPos + 10]) << 15 | (in[inPos + 12] - in[inPos + 11]) << 28;
    out[outPos + 5] = (in[inPos + 12] - in[inPos + 11]) >>> 4 | (in[inPos + 13] - in[inPos + 12]) << 9 | (in[inPos + 14] - in[inPos + 13]) << 22;
    out[outPos + 6] = (in[inPos + 14] - in[inPos + 13]) >>> 10 | (in[inPos + 15] - in[inPos + 14]) << 3 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 29;
    out[outPos + 7] = (in[inPos + 17] - in[inPos + 16]) >>> 3 | (in[inPos + 18] - in[inPos + 17]) << 10 | (in[inPos + 19] - in[inPos + 18]) << 23;
    out[outPos + 8] = (in[inPos + 19] - in[inPos + 18]) >>> 9 | (in[inPos + 20] - in[inPos + 19]) << 4 | (in[inPos + 21] - in[inPos + 20]) << 17 | (in[inPos + 22] - in[inPos + 21]) << 30;
    out[outPos + 9] = (in[inPos + 22] - in[inPos + 21]) >>> 2 | (in[inPos + 23] - in[inPos + 22]) << 11 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 10] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 5 | (in[inPos + 26] - in[inPos + 25]) << 18 | (in[inPos + 27] - in[inPos + 26]) << 31;
    out[outPos + 11] = (in[inPos + 27] - in[inPos + 26]) >>> 1 | (in[inPos + 28] - in[inPos + 27]) << 12 | (in[inPos + 29] - in[inPos + 28]) << 25;
    out[outPos + 12] = (in[inPos + 29] - in[inPos + 28]) >>> 7 | (in[inPos + 30] - in[inPos + 29]) << 6 | (in[inPos + 31] - in[inPos + 30]) << 19;
  }

  private static void unpack13(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 8191) + initValue;
    out[outPos + 1] = (in[inPos] >>> 13 & 8191) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 26 | (in[inPos + 1] & 127) << 6) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 7 & 8191) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 20 | (in[inPos + 2] & 1) << 12) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 1 & 8191) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 14 & 8191) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 2] >>> 27 | (in[inPos + 3] & 255) << 5) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 8 & 8191) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 21 | (in[inPos + 4] & 3) << 11) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 2 & 8191) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 15 & 8191) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 4] >>> 28 | (in[inPos + 5] & 511) << 4) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 5] >>> 9 & 8191) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 5] >>> 22 | (in[inPos + 6] & 7) << 10) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 6] >>> 3 & 8191) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 6] >>> 16 & 8191) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 6] >>> 29 | (in[inPos + 7] & 1023) << 3) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 7] >>> 10 & 8191) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 7] >>> 23 | (in[inPos + 8] & 15) << 9) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 8] >>> 4 & 8191) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 8] >>> 17 & 8191) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 8] >>> 30 | (in[inPos + 9] & 2047) << 2) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 9] >>> 11 & 8191) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 9] >>> 24 | (in[inPos + 10] & 31) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 10] >>> 5 & 8191) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 10] >>> 18 & 8191) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 10] >>> 31 | (in[inPos + 11] & 4095) << 1) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 11] >>> 12 & 8191) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 11] >>> 25 | (in[inPos + 12] & 63) << 7) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 12] >>> 6 & 8191) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 12] >>> 19) + out[outPos + 30];
  }

  private static void pack14(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 14 | (in[inPos + 2] - in[inPos + 1]) << 28;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 4 | (in[inPos + 3] - in[inPos + 2]) << 10 | (in[inPos + 4] - in[inPos + 3]) << 24;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 8 | (in[inPos + 5] - in[inPos + 4]) << 6 | (in[inPos + 6] - in[inPos + 5]) << 20;
    out[outPos + 3] = (in[inPos + 6] - in[inPos + 5]) >>> 12 | (in[inPos + 7] - in[inPos + 6]) << 2 | (in[inPos + 8] - in[inPos + 7]) << 16 | (in[inPos + 9] - in[inPos + 8]) << 30;
    out[outPos + 4] = (in[inPos + 9] - in[inPos + 8]) >>> 2 | (in[inPos + 10] - in[inPos + 9]) << 12 | (in[inPos + 11] - in[inPos + 10]) << 26;
    out[outPos + 5] = (in[inPos + 11] - in[inPos + 10]) >>> 6 | (in[inPos + 12] - in[inPos + 11]) << 8 | (in[inPos + 13] - in[inPos + 12]) << 22;
    out[outPos + 6] = (in[inPos + 13] - in[inPos + 12]) >>> 10 | (in[inPos + 14] - in[inPos + 13]) << 4 | (in[inPos + 15] - in[inPos + 14]) << 18;
    out[outPos + 7] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 14 | (in[inPos + 18] - in[inPos + 17]) << 28;
    out[outPos + 8] = (in[inPos + 18] - in[inPos + 17]) >>> 4 | (in[inPos + 19] - in[inPos + 18]) << 10 | (in[inPos + 20] - in[inPos + 19]) << 24;
    out[outPos + 9] = (in[inPos + 20] - in[inPos + 19]) >>> 8 | (in[inPos + 21] - in[inPos + 20]) << 6 | (in[inPos + 22] - in[inPos + 21]) << 20;
    out[outPos + 10] = (in[inPos + 22] - in[inPos + 21]) >>> 12 | (in[inPos + 23] - in[inPos + 22]) << 2 | (in[inPos + 24] - in[inPos + 23]) << 16 | (in[inPos + 25] - in[inPos + 24]) << 30;
    out[outPos + 11] = (in[inPos + 25] - in[inPos + 24]) >>> 2 | (in[inPos + 26] - in[inPos + 25]) << 12 | (in[inPos + 27] - in[inPos + 26]) << 26;
    out[outPos + 12] = (in[inPos + 27] - in[inPos + 26]) >>> 6 | (in[inPos + 28] - in[inPos + 27]) << 8 | (in[inPos + 29] - in[inPos + 28]) << 22;
    out[outPos + 13] = (in[inPos + 29] - in[inPos + 28]) >>> 10 | (in[inPos + 30] - in[inPos + 29]) << 4 | (in[inPos + 31] - in[inPos + 30]) << 18;
  }

  private static void unpack14(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 16383) + initValue;
    out[outPos + 1] = (in[inPos] >>> 14 & 16383) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 28 | (in[inPos + 1] & 1023) << 4) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 10 & 16383) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 24 | (in[inPos + 2] & 63) << 8) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 6 & 16383) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 20 | (in[inPos + 3] & 3) << 12) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 2 & 16383) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 16 & 16383) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 3] >>> 30 | (in[inPos + 4] & 4095) << 2) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 12 & 16383) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 4] >>> 26 | (in[inPos + 5] & 255) << 6) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 5] >>> 8 & 16383) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 5] >>> 22 | (in[inPos + 6] & 15) << 10) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 6] >>> 4 & 16383) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 6] >>> 18) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 7] & 16383) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 7] >>> 14 & 16383) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 7] >>> 28 | (in[inPos + 8] & 1023) << 4) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 8] >>> 10 & 16383) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 8] >>> 24 | (in[inPos + 9] & 63) << 8) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 9] >>> 6 & 16383) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 9] >>> 20 | (in[inPos + 10] & 3) << 12) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 10] >>> 2 & 16383) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 10] >>> 16 & 16383) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 10] >>> 30 | (in[inPos + 11] & 4095) << 2) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 11] >>> 12 & 16383) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 11] >>> 26 | (in[inPos + 12] & 255) << 6) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 12] >>> 8 & 16383) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 12] >>> 22 | (in[inPos + 13] & 15) << 10) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 13] >>> 4 & 16383) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 13] >>> 18) + out[outPos + 30];
  }

  private static void pack15(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 15 | (in[inPos + 2] - in[inPos + 1]) << 30;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) >>> 2 | (in[inPos + 3] - in[inPos + 2]) << 13 | (in[inPos + 4] - in[inPos + 3]) << 28;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) >>> 4 | (in[inPos + 5] - in[inPos + 4]) << 11 | (in[inPos + 6] - in[inPos + 5]) << 26;
    out[outPos + 3] = (in[inPos + 6] - in[inPos + 5]) >>> 6 | (in[inPos + 7] - in[inPos + 6]) << 9 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 4] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 7 | (in[inPos + 10] - in[inPos + 9]) << 22;
    out[outPos + 5] = (in[inPos + 10] - in[inPos + 9]) >>> 10 | (in[inPos + 11] - in[inPos + 10]) << 5 | (in[inPos + 12] - in[inPos + 11]) << 20;
    out[outPos + 6] = (in[inPos + 12] - in[inPos + 11]) >>> 12 | (in[inPos + 13] - in[inPos + 12]) << 3 | (in[inPos + 14] - in[inPos + 13]) << 18;
    out[outPos + 7] = (in[inPos + 14] - in[inPos + 13]) >>> 14 | (in[inPos + 15] - in[inPos + 14]) << 1 | (in[inPos + 16] - in[inPos + 15]) << 16 | (in[inPos + 17] - in[inPos + 16]) << 31;
    out[outPos + 8] = (in[inPos + 17] - in[inPos + 16]) >>> 1 | (in[inPos + 18] - in[inPos + 17]) << 14 | (in[inPos + 19] - in[inPos + 18]) << 29;
    out[outPos + 9] = (in[inPos + 19] - in[inPos + 18]) >>> 3 | (in[inPos + 20] - in[inPos + 19]) << 12 | (in[inPos + 21] - in[inPos + 20]) << 27;
    out[outPos + 10] = (in[inPos + 21] - in[inPos + 20]) >>> 5 | (in[inPos + 22] - in[inPos + 21]) << 10 | (in[inPos + 23] - in[inPos + 22]) << 25;
    out[outPos + 11] = (in[inPos + 23] - in[inPos + 22]) >>> 7 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 23;
    out[outPos + 12] = (in[inPos + 25] - in[inPos + 24]) >>> 9 | (in[inPos + 26] - in[inPos + 25]) << 6 | (in[inPos + 27] - in[inPos + 26]) << 21;
    out[outPos + 13] = (in[inPos + 27] - in[inPos + 26]) >>> 11 | (in[inPos + 28] - in[inPos + 27]) << 4 | (in[inPos + 29] - in[inPos + 28]) << 19;
    out[outPos + 14] = (in[inPos + 29] - in[inPos + 28]) >>> 13 | (in[inPos + 30] - in[inPos + 29]) << 2 | (in[inPos + 31] - in[inPos + 30]) << 17;
  }

  private static void unpack15(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 32767) + initValue;
    out[outPos + 1] = (in[inPos] >>> 15 & 32767) + out[outPos];
    out[outPos + 2] = (in[inPos] >>> 30 | (in[inPos + 1] & 8191) << 2) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 13 & 32767) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 1] >>> 28 | (in[inPos + 2] & 2047) << 4) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 11 & 32767) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 2] >>> 26 | (in[inPos + 3] & 511) << 6) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 9 & 32767) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 3] >>> 24 | (in[inPos + 4] & 127) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 7 & 32767) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 4] >>> 22 | (in[inPos + 5] & 31) << 10) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 5] >>> 5 & 32767) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 5] >>> 20 | (in[inPos + 6] & 7) << 12) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 6] >>> 3 & 32767) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 6] >>> 18 | (in[inPos + 7] & 1) << 14) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 7] >>> 1 & 32767) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 7] >>> 16 & 32767) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 7] >>> 31 | (in[inPos + 8] & 16383) << 1) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 8] >>> 14 & 32767) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 8] >>> 29 | (in[inPos + 9] & 4095) << 3) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 9] >>> 12 & 32767) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 9] >>> 27 | (in[inPos + 10] & 1023) << 5) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 10] >>> 10 & 32767) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 10] >>> 25 | (in[inPos + 11] & 255) << 7) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 11] >>> 8 & 32767) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 11] >>> 23 | (in[inPos + 12] & 63) << 9) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 12] >>> 6 & 32767) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 12] >>> 21 | (in[inPos + 13] & 15) << 11) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 13] >>> 4 & 32767) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 13] >>> 19 | (in[inPos + 14] & 3) << 13) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 14] >>> 2 & 32767) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 14] >>> 17) + out[outPos + 30];
  }

  private static void pack16(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 16;
    out[outPos + 1] = (in[inPos + 2] - in[inPos + 1]) | (in[inPos + 3] - in[inPos + 2]) << 16;
    out[outPos + 2] = (in[inPos + 4] - in[inPos + 3]) | (in[inPos + 5] - in[inPos + 4]) << 16;
    out[outPos + 3] = (in[inPos + 6] - in[inPos + 5]) | (in[inPos + 7] - in[inPos + 6]) << 16;
    out[outPos + 4] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 16;
    out[outPos + 5] = (in[inPos + 10] - in[inPos + 9]) | (in[inPos + 11] - in[inPos + 10]) << 16;
    out[outPos + 6] = (in[inPos + 12] - in[inPos + 11]) | (in[inPos + 13] - in[inPos + 12]) << 16;
    out[outPos + 7] = (in[inPos + 14] - in[inPos + 13]) | (in[inPos + 15] - in[inPos + 14]) << 16;
    out[outPos + 8] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 16;
    out[outPos + 9] = (in[inPos + 18] - in[inPos + 17]) | (in[inPos + 19] - in[inPos + 18]) << 16;
    out[outPos + 10] = (in[inPos + 20] - in[inPos + 19]) | (in[inPos + 21] - in[inPos + 20]) << 16;
    out[outPos + 11] = (in[inPos + 22] - in[inPos + 21]) | (in[inPos + 23] - in[inPos + 22]) << 16;
    out[outPos + 12] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 16;
    out[outPos + 13] = (in[inPos + 26] - in[inPos + 25]) | (in[inPos + 27] - in[inPos + 26]) << 16;
    out[outPos + 14] = (in[inPos + 28] - in[inPos + 27]) | (in[inPos + 29] - in[inPos + 28]) << 16;
    out[outPos + 15] = (in[inPos + 30] - in[inPos + 29]) | (in[inPos + 31] - in[inPos + 30]) << 16;
  }

  private static void unpack16(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 65535) + initValue;
    out[outPos + 1] = (in[inPos] >>> 16) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] & 65535) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 16) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] & 65535) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 16) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] & 65535) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 16) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] & 65535) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 16) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] & 65535) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 5] >>> 16) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] & 65535) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 6] >>> 16) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 7] & 65535) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 7] >>> 16) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 8] & 65535) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 8] >>> 16) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 9] & 65535) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 9] >>> 16) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 10] & 65535) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 10] >>> 16) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 11] & 65535) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 11] >>> 16) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 12] & 65535) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 12] >>> 16) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 13] & 65535) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 13] >>> 16) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 14] & 65535) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 14] >>> 16) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 15] & 65535) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 15] >>> 16) + out[outPos + 30];
  }

  private static void pack17(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 17;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 15 | (in[inPos + 2] - in[inPos + 1]) << 2 | (in[inPos + 3] - in[inPos + 2]) << 19;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 13 | (in[inPos + 4] - in[inPos + 3]) << 4 | (in[inPos + 5] - in[inPos + 4]) << 21;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 11 | (in[inPos + 6] - in[inPos + 5]) << 6 | (in[inPos + 7] - in[inPos + 6]) << 23;
    out[outPos + 4] = (in[inPos + 7] - in[inPos + 6]) >>> 9 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 25;
    out[outPos + 5] = (in[inPos + 9] - in[inPos + 8]) >>> 7 | (in[inPos + 10] - in[inPos + 9]) << 10 | (in[inPos + 11] - in[inPos + 10]) << 27;
    out[outPos + 6] = (in[inPos + 11] - in[inPos + 10]) >>> 5 | (in[inPos + 12] - in[inPos + 11]) << 12 | (in[inPos + 13] - in[inPos + 12]) << 29;
    out[outPos + 7] = (in[inPos + 13] - in[inPos + 12]) >>> 3 | (in[inPos + 14] - in[inPos + 13]) << 14 | (in[inPos + 15] - in[inPos + 14]) << 31;
    out[outPos + 8] = (in[inPos + 15] - in[inPos + 14]) >>> 1 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 9] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 1 | (in[inPos + 18] - in[inPos + 17]) << 18;
    out[outPos + 10] = (in[inPos + 18] - in[inPos + 17]) >>> 14 | (in[inPos + 19] - in[inPos + 18]) << 3 | (in[inPos + 20] - in[inPos + 19]) << 20;
    out[outPos + 11] = (in[inPos + 20] - in[inPos + 19]) >>> 12 | (in[inPos + 21] - in[inPos + 20]) << 5 | (in[inPos + 22] - in[inPos + 21]) << 22;
    out[outPos + 12] = (in[inPos + 22] - in[inPos + 21]) >>> 10 | (in[inPos + 23] - in[inPos + 22]) << 7 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 13] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 9 | (in[inPos + 26] - in[inPos + 25]) << 26;
    out[outPos + 14] = (in[inPos + 26] - in[inPos + 25]) >>> 6 | (in[inPos + 27] - in[inPos + 26]) << 11 | (in[inPos + 28] - in[inPos + 27]) << 28;
    out[outPos + 15] = (in[inPos + 28] - in[inPos + 27]) >>> 4 | (in[inPos + 29] - in[inPos + 28]) << 13 | (in[inPos + 30] - in[inPos + 29]) << 30;
    out[outPos + 16] = (in[inPos + 30] - in[inPos + 29]) >>> 2 | (in[inPos + 31] - in[inPos + 30]) << 15;
  }

  private static void unpack17(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 131071) + initValue;
    out[outPos + 1] = (in[inPos] >>> 17 | (in[inPos + 1] & 3) << 15) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 2 & 131071) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 19 | (in[inPos + 2] & 15) << 13) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 4 & 131071) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 21 | (in[inPos + 3] & 63) << 11) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 6 & 131071) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 23 | (in[inPos + 4] & 255) << 9) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 8 & 131071) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 4] >>> 25 | (in[inPos + 5] & 1023) << 7) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 10 & 131071) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 5] >>> 27 | (in[inPos + 6] & 4095) << 5) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] >>> 12 & 131071) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 6] >>> 29 | (in[inPos + 7] & 16383) << 3) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 7] >>> 14 & 131071) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 7] >>> 31 | (in[inPos + 8] & 65535) << 1) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 8] >>> 16 | (in[inPos + 9] & 1) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 9] >>> 1 & 131071) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 9] >>> 18 | (in[inPos + 10] & 7) << 14) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 10] >>> 3 & 131071) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 10] >>> 20 | (in[inPos + 11] & 31) << 12) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 11] >>> 5 & 131071) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 11] >>> 22 | (in[inPos + 12] & 127) << 10) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 12] >>> 7 & 131071) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 12] >>> 24 | (in[inPos + 13] & 511) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 13] >>> 9 & 131071) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 13] >>> 26 | (in[inPos + 14] & 2047) << 6) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 14] >>> 11 & 131071) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 14] >>> 28 | (in[inPos + 15] & 8191) << 4) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 15] >>> 13 & 131071) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 15] >>> 30 | (in[inPos + 16] & 32767) << 2) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 16] >>> 15) + out[outPos + 30];
  }

  private static void pack18(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 18;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 14 | (in[inPos + 2] - in[inPos + 1]) << 4 | (in[inPos + 3] - in[inPos + 2]) << 22;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 10 | (in[inPos + 4] - in[inPos + 3]) << 8 | (in[inPos + 5] - in[inPos + 4]) << 26;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 6 | (in[inPos + 6] - in[inPos + 5]) << 12 | (in[inPos + 7] - in[inPos + 6]) << 30;
    out[outPos + 4] = (in[inPos + 7] - in[inPos + 6]) >>> 2 | (in[inPos + 8] - in[inPos + 7]) << 16;
    out[outPos + 5] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 2 | (in[inPos + 10] - in[inPos + 9]) << 20;
    out[outPos + 6] = (in[inPos + 10] - in[inPos + 9]) >>> 12 | (in[inPos + 11] - in[inPos + 10]) << 6 | (in[inPos + 12] - in[inPos + 11]) << 24;
    out[outPos + 7] = (in[inPos + 12] - in[inPos + 11]) >>> 8 | (in[inPos + 13] - in[inPos + 12]) << 10 | (in[inPos + 14] - in[inPos + 13]) << 28;
    out[outPos + 8] = (in[inPos + 14] - in[inPos + 13]) >>> 4 | (in[inPos + 15] - in[inPos + 14]) << 14;
    out[outPos + 9] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 18;
    out[outPos + 10] = (in[inPos + 17] - in[inPos + 16]) >>> 14 | (in[inPos + 18] - in[inPos + 17]) << 4 | (in[inPos + 19] - in[inPos + 18]) << 22;
    out[outPos + 11] = (in[inPos + 19] - in[inPos + 18]) >>> 10 | (in[inPos + 20] - in[inPos + 19]) << 8 | (in[inPos + 21] - in[inPos + 20]) << 26;
    out[outPos + 12] = (in[inPos + 21] - in[inPos + 20]) >>> 6 | (in[inPos + 22] - in[inPos + 21]) << 12 | (in[inPos + 23] - in[inPos + 22]) << 30;
    out[outPos + 13] = (in[inPos + 23] - in[inPos + 22]) >>> 2 | (in[inPos + 24] - in[inPos + 23]) << 16;
    out[outPos + 14] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 2 | (in[inPos + 26] - in[inPos + 25]) << 20;
    out[outPos + 15] = (in[inPos + 26] - in[inPos + 25]) >>> 12 | (in[inPos + 27] - in[inPos + 26]) << 6 | (in[inPos + 28] - in[inPos + 27]) << 24;
    out[outPos + 16] = (in[inPos + 28] - in[inPos + 27]) >>> 8 | (in[inPos + 29] - in[inPos + 28]) << 10 | (in[inPos + 30] - in[inPos + 29]) << 28;
    out[outPos + 17] = (in[inPos + 30] - in[inPos + 29]) >>> 4 | (in[inPos + 31] - in[inPos + 30]) << 14;
  }

  private static void unpack18(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 262143) + initValue;
    out[outPos + 1] = (in[inPos] >>> 18 | (in[inPos + 1] & 15) << 14) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 4 & 262143) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 22 | (in[inPos + 2] & 255) << 10) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 8 & 262143) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 26 | (in[inPos + 3] & 4095) << 6) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 12 & 262143) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 3] >>> 30 | (in[inPos + 4] & 65535) << 2) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 16 | (in[inPos + 5] & 3) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 2 & 262143) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 20 | (in[inPos + 6] & 63) << 12) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 6 & 262143) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 6] >>> 24 | (in[inPos + 7] & 1023) << 8) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 7] >>> 10 & 262143) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 7] >>> 28 | (in[inPos + 8] & 16383) << 4) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 8] >>> 14) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 9] & 262143) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 9] >>> 18 | (in[inPos + 10] & 15) << 14) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 10] >>> 4 & 262143) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 10] >>> 22 | (in[inPos + 11] & 255) << 10) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 11] >>> 8 & 262143) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 11] >>> 26 | (in[inPos + 12] & 4095) << 6) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 12] >>> 12 & 262143) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 12] >>> 30 | (in[inPos + 13] & 65535) << 2) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 13] >>> 16 | (in[inPos + 14] & 3) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 14] >>> 2 & 262143) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 14] >>> 20 | (in[inPos + 15] & 63) << 12) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 15] >>> 6 & 262143) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 15] >>> 24 | (in[inPos + 16] & 1023) << 8) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 16] >>> 10 & 262143) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 16] >>> 28 | (in[inPos + 17] & 16383) << 4) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 17] >>> 14) + out[outPos + 30];
  }

  private static void pack19(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 19;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 13 | (in[inPos + 2] - in[inPos + 1]) << 6 | (in[inPos + 3] - in[inPos + 2]) << 25;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 7 | (in[inPos + 4] - in[inPos + 3]) << 12 | (in[inPos + 5] - in[inPos + 4]) << 31;
    out[outPos + 3] = (in[inPos + 5] - in[inPos + 4]) >>> 1 | (in[inPos + 6] - in[inPos + 5]) << 18;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 14 | (in[inPos + 7] - in[inPos + 6]) << 5 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 5] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 11 | (in[inPos + 10] - in[inPos + 9]) << 30;
    out[outPos + 6] = (in[inPos + 10] - in[inPos + 9]) >>> 2 | (in[inPos + 11] - in[inPos + 10]) << 17;
    out[outPos + 7] = (in[inPos + 11] - in[inPos + 10]) >>> 15 | (in[inPos + 12] - in[inPos + 11]) << 4 | (in[inPos + 13] - in[inPos + 12]) << 23;
    out[outPos + 8] = (in[inPos + 13] - in[inPos + 12]) >>> 9 | (in[inPos + 14] - in[inPos + 13]) << 10 | (in[inPos + 15] - in[inPos + 14]) << 29;
    out[outPos + 9] = (in[inPos + 15] - in[inPos + 14]) >>> 3 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 10] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 3 | (in[inPos + 18] - in[inPos + 17]) << 22;
    out[outPos + 11] = (in[inPos + 18] - in[inPos + 17]) >>> 10 | (in[inPos + 19] - in[inPos + 18]) << 9 | (in[inPos + 20] - in[inPos + 19]) << 28;
    out[outPos + 12] = (in[inPos + 20] - in[inPos + 19]) >>> 4 | (in[inPos + 21] - in[inPos + 20]) << 15;
    out[outPos + 13] = (in[inPos + 21] - in[inPos + 20]) >>> 17 | (in[inPos + 22] - in[inPos + 21]) << 2 | (in[inPos + 23] - in[inPos + 22]) << 21;
    out[outPos + 14] = (in[inPos + 23] - in[inPos + 22]) >>> 11 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 27;
    out[outPos + 15] = (in[inPos + 25] - in[inPos + 24]) >>> 5 | (in[inPos + 26] - in[inPos + 25]) << 14;
    out[outPos + 16] = (in[inPos + 26] - in[inPos + 25]) >>> 18 | (in[inPos + 27] - in[inPos + 26]) << 1 | (in[inPos + 28] - in[inPos + 27]) << 20;
    out[outPos + 17] = (in[inPos + 28] - in[inPos + 27]) >>> 12 | (in[inPos + 29] - in[inPos + 28]) << 7 | (in[inPos + 30] - in[inPos + 29]) << 26;
    out[outPos + 18] = (in[inPos + 30] - in[inPos + 29]) >>> 6 | (in[inPos + 31] - in[inPos + 30]) << 13;
  }

  private static void unpack19(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 524287) + initValue;
    out[outPos + 1] = (in[inPos] >>> 19 | (in[inPos + 1] & 63) << 13) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 6 & 524287) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 25 | (in[inPos + 2] & 4095) << 7) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 12 & 524287) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 2] >>> 31 | (in[inPos + 3] & 262143) << 1) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 18 | (in[inPos + 4] & 31) << 14) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 5 & 524287) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 4] >>> 24 | (in[inPos + 5] & 2047) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 11 & 524287) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 5] >>> 30 | (in[inPos + 6] & 131071) << 2) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 17 | (in[inPos + 7] & 15) << 15) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 7] >>> 4 & 524287) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 7] >>> 23 | (in[inPos + 8] & 1023) << 9) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 8] >>> 10 & 524287) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 8] >>> 29 | (in[inPos + 9] & 65535) << 3) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 9] >>> 16 | (in[inPos + 10] & 7) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 10] >>> 3 & 524287) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 10] >>> 22 | (in[inPos + 11] & 511) << 10) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 11] >>> 9 & 524287) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 11] >>> 28 | (in[inPos + 12] & 32767) << 4) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 12] >>> 15 | (in[inPos + 13] & 3) << 17) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 13] >>> 2 & 524287) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 13] >>> 21 | (in[inPos + 14] & 255) << 11) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 14] >>> 8 & 524287) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 14] >>> 27 | (in[inPos + 15] & 16383) << 5) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 15] >>> 14 | (in[inPos + 16] & 1) << 18) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 16] >>> 1 & 524287) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 16] >>> 20 | (in[inPos + 17] & 127) << 12) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 17] >>> 7 & 524287) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 17] >>> 26 | (in[inPos + 18] & 8191) << 6) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 18] >>> 13) + out[outPos + 30];
  }

  private static void pack20(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 20;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 12 | (in[inPos + 2] - in[inPos + 1]) << 8 | (in[inPos + 3] - in[inPos + 2]) << 28;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 4 | (in[inPos + 4] - in[inPos + 3]) << 16;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 16 | (in[inPos + 5] - in[inPos + 4]) << 4 | (in[inPos + 6] - in[inPos + 5]) << 24;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 8 | (in[inPos + 7] - in[inPos + 6]) << 12;
    out[outPos + 5] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 20;
    out[outPos + 6] = (in[inPos + 9] - in[inPos + 8]) >>> 12 | (in[inPos + 10] - in[inPos + 9]) << 8 | (in[inPos + 11] - in[inPos + 10]) << 28;
    out[outPos + 7] = (in[inPos + 11] - in[inPos + 10]) >>> 4 | (in[inPos + 12] - in[inPos + 11]) << 16;
    out[outPos + 8] = (in[inPos + 12] - in[inPos + 11]) >>> 16 | (in[inPos + 13] - in[inPos + 12]) << 4 | (in[inPos + 14] - in[inPos + 13]) << 24;
    out[outPos + 9] = (in[inPos + 14] - in[inPos + 13]) >>> 8 | (in[inPos + 15] - in[inPos + 14]) << 12;
    out[outPos + 10] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 20;
    out[outPos + 11] = (in[inPos + 17] - in[inPos + 16]) >>> 12 | (in[inPos + 18] - in[inPos + 17]) << 8 | (in[inPos + 19] - in[inPos + 18]) << 28;
    out[outPos + 12] = (in[inPos + 19] - in[inPos + 18]) >>> 4 | (in[inPos + 20] - in[inPos + 19]) << 16;
    out[outPos + 13] = (in[inPos + 20] - in[inPos + 19]) >>> 16 | (in[inPos + 21] - in[inPos + 20]) << 4 | (in[inPos + 22] - in[inPos + 21]) << 24;
    out[outPos + 14] = (in[inPos + 22] - in[inPos + 21]) >>> 8 | (in[inPos + 23] - in[inPos + 22]) << 12;
    out[outPos + 15] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 20;
    out[outPos + 16] = (in[inPos + 25] - in[inPos + 24]) >>> 12 | (in[inPos + 26] - in[inPos + 25]) << 8 | (in[inPos + 27] - in[inPos + 26]) << 28;
    out[outPos + 17] = (in[inPos + 27] - in[inPos + 26]) >>> 4 | (in[inPos + 28] - in[inPos + 27]) << 16;
    out[outPos + 18] = (in[inPos + 28] - in[inPos + 27]) >>> 16 | (in[inPos + 29] - in[inPos + 28]) << 4 | (in[inPos + 30] - in[inPos + 29]) << 24;
    out[outPos + 19] = (in[inPos + 30] - in[inPos + 29]) >>> 8 | (in[inPos + 31] - in[inPos + 30]) << 12;
  }

  private static void unpack20(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1048575) + initValue;
    out[outPos + 1] = (in[inPos] >>> 20 | (in[inPos + 1] & 255) << 12) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 8 & 1048575) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 28 | (in[inPos + 2] & 65535) << 4) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 16 | (in[inPos + 3] & 15) << 16) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 4 & 1048575) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 24 | (in[inPos + 4] & 4095) << 8) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 12) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] & 1048575) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 20 | (in[inPos + 6] & 255) << 12) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 8 & 1048575) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 6] >>> 28 | (in[inPos + 7] & 65535) << 4) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 7] >>> 16 | (in[inPos + 8] & 15) << 16) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 8] >>> 4 & 1048575) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 8] >>> 24 | (in[inPos + 9] & 4095) << 8) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 9] >>> 12) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 10] & 1048575) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 10] >>> 20 | (in[inPos + 11] & 255) << 12) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 11] >>> 8 & 1048575) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 11] >>> 28 | (in[inPos + 12] & 65535) << 4) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 12] >>> 16 | (in[inPos + 13] & 15) << 16) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 13] >>> 4 & 1048575) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 13] >>> 24 | (in[inPos + 14] & 4095) << 8) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 14] >>> 12) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 15] & 1048575) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 15] >>> 20 | (in[inPos + 16] & 255) << 12) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 16] >>> 8 & 1048575) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 16] >>> 28 | (in[inPos + 17] & 65535) << 4) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 17] >>> 16 | (in[inPos + 18] & 15) << 16) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 18] >>> 4 & 1048575) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 18] >>> 24 | (in[inPos + 19] & 4095) << 8) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 19] >>> 12) + out[outPos + 30];
  }

  private static void pack21(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 21;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 11 | (in[inPos + 2] - in[inPos + 1]) << 10 | (in[inPos + 3] - in[inPos + 2]) << 31;
    out[outPos + 2] = (in[inPos + 3] - in[inPos + 2]) >>> 1 | (in[inPos + 4] - in[inPos + 3]) << 20;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 12 | (in[inPos + 5] - in[inPos + 4]) << 9 | (in[inPos + 6] - in[inPos + 5]) << 30;
    out[outPos + 4] = (in[inPos + 6] - in[inPos + 5]) >>> 2 | (in[inPos + 7] - in[inPos + 6]) << 19;
    out[outPos + 5] = (in[inPos + 7] - in[inPos + 6]) >>> 13 | (in[inPos + 8] - in[inPos + 7]) << 8 | (in[inPos + 9] - in[inPos + 8]) << 29;
    out[outPos + 6] = (in[inPos + 9] - in[inPos + 8]) >>> 3 | (in[inPos + 10] - in[inPos + 9]) << 18;
    out[outPos + 7] = (in[inPos + 10] - in[inPos + 9]) >>> 14 | (in[inPos + 11] - in[inPos + 10]) << 7 | (in[inPos + 12] - in[inPos + 11]) << 28;
    out[outPos + 8] = (in[inPos + 12] - in[inPos + 11]) >>> 4 | (in[inPos + 13] - in[inPos + 12]) << 17;
    out[outPos + 9] = (in[inPos + 13] - in[inPos + 12]) >>> 15 | (in[inPos + 14] - in[inPos + 13]) << 6 | (in[inPos + 15] - in[inPos + 14]) << 27;
    out[outPos + 10] = (in[inPos + 15] - in[inPos + 14]) >>> 5 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 11] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 5 | (in[inPos + 18] - in[inPos + 17]) << 26;
    out[outPos + 12] = (in[inPos + 18] - in[inPos + 17]) >>> 6 | (in[inPos + 19] - in[inPos + 18]) << 15;
    out[outPos + 13] = (in[inPos + 19] - in[inPos + 18]) >>> 17 | (in[inPos + 20] - in[inPos + 19]) << 4 | (in[inPos + 21] - in[inPos + 20]) << 25;
    out[outPos + 14] = (in[inPos + 21] - in[inPos + 20]) >>> 7 | (in[inPos + 22] - in[inPos + 21]) << 14;
    out[outPos + 15] = (in[inPos + 22] - in[inPos + 21]) >>> 18 | (in[inPos + 23] - in[inPos + 22]) << 3 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 16] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 13;
    out[outPos + 17] = (in[inPos + 25] - in[inPos + 24]) >>> 19 | (in[inPos + 26] - in[inPos + 25]) << 2 | (in[inPos + 27] - in[inPos + 26]) << 23;
    out[outPos + 18] = (in[inPos + 27] - in[inPos + 26]) >>> 9 | (in[inPos + 28] - in[inPos + 27]) << 12;
    out[outPos + 19] = (in[inPos + 28] - in[inPos + 27]) >>> 20 | (in[inPos + 29] - in[inPos + 28]) << 1 | (in[inPos + 30] - in[inPos + 29]) << 22;
    out[outPos + 20] = (in[inPos + 30] - in[inPos + 29]) >>> 10 | (in[inPos + 31] - in[inPos + 30]) << 11;
  }

  private static void unpack21(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2097151) + initValue;
    out[outPos + 1] = (in[inPos] >>> 21 | (in[inPos + 1] & 1023) << 11) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 10 & 2097151) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 1] >>> 31 | (in[inPos + 2] & 1048575) << 1) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 20 | (in[inPos + 3] & 511) << 12) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 9 & 2097151) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 3] >>> 30 | (in[inPos + 4] & 524287) << 2) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 19 | (in[inPos + 5] & 255) << 13) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 8 & 2097151) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 5] >>> 29 | (in[inPos + 6] & 262143) << 3) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 18 | (in[inPos + 7] & 127) << 14) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 7 & 2097151) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 7] >>> 28 | (in[inPos + 8] & 131071) << 4) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 8] >>> 17 | (in[inPos + 9] & 63) << 15) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 9] >>> 6 & 2097151) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 9] >>> 27 | (in[inPos + 10] & 65535) << 5) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 10] >>> 16 | (in[inPos + 11] & 31) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 11] >>> 5 & 2097151) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 11] >>> 26 | (in[inPos + 12] & 32767) << 6) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 12] >>> 15 | (in[inPos + 13] & 15) << 17) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 13] >>> 4 & 2097151) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 13] >>> 25 | (in[inPos + 14] & 16383) << 7) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 14] >>> 14 | (in[inPos + 15] & 7) << 18) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 15] >>> 3 & 2097151) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 15] >>> 24 | (in[inPos + 16] & 8191) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 16] >>> 13 | (in[inPos + 17] & 3) << 19) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 17] >>> 2 & 2097151) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 17] >>> 23 | (in[inPos + 18] & 4095) << 9) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 18] >>> 12 | (in[inPos + 19] & 1) << 20) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 19] >>> 1 & 2097151) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 19] >>> 22 | (in[inPos + 20] & 2047) << 10) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 20] >>> 11) + out[outPos + 30];
  }

  private static void pack22(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 22;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 10 | (in[inPos + 2] - in[inPos + 1]) << 12;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 20 | (in[inPos + 3] - in[inPos + 2]) << 2 | (in[inPos + 4] - in[inPos + 3]) << 24;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 8 | (in[inPos + 5] - in[inPos + 4]) << 14;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 18 | (in[inPos + 6] - in[inPos + 5]) << 4 | (in[inPos + 7] - in[inPos + 6]) << 26;
    out[outPos + 5] = (in[inPos + 7] - in[inPos + 6]) >>> 6 | (in[inPos + 8] - in[inPos + 7]) << 16;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 6 | (in[inPos + 10] - in[inPos + 9]) << 28;
    out[outPos + 7] = (in[inPos + 10] - in[inPos + 9]) >>> 4 | (in[inPos + 11] - in[inPos + 10]) << 18;
    out[outPos + 8] = (in[inPos + 11] - in[inPos + 10]) >>> 14 | (in[inPos + 12] - in[inPos + 11]) << 8 | (in[inPos + 13] - in[inPos + 12]) << 30;
    out[outPos + 9] = (in[inPos + 13] - in[inPos + 12]) >>> 2 | (in[inPos + 14] - in[inPos + 13]) << 20;
    out[outPos + 10] = (in[inPos + 14] - in[inPos + 13]) >>> 12 | (in[inPos + 15] - in[inPos + 14]) << 10;
    out[outPos + 11] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 22;
    out[outPos + 12] = (in[inPos + 17] - in[inPos + 16]) >>> 10 | (in[inPos + 18] - in[inPos + 17]) << 12;
    out[outPos + 13] = (in[inPos + 18] - in[inPos + 17]) >>> 20 | (in[inPos + 19] - in[inPos + 18]) << 2 | (in[inPos + 20] - in[inPos + 19]) << 24;
    out[outPos + 14] = (in[inPos + 20] - in[inPos + 19]) >>> 8 | (in[inPos + 21] - in[inPos + 20]) << 14;
    out[outPos + 15] = (in[inPos + 21] - in[inPos + 20]) >>> 18 | (in[inPos + 22] - in[inPos + 21]) << 4 | (in[inPos + 23] - in[inPos + 22]) << 26;
    out[outPos + 16] = (in[inPos + 23] - in[inPos + 22]) >>> 6 | (in[inPos + 24] - in[inPos + 23]) << 16;
    out[outPos + 17] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 6 | (in[inPos + 26] - in[inPos + 25]) << 28;
    out[outPos + 18] = (in[inPos + 26] - in[inPos + 25]) >>> 4 | (in[inPos + 27] - in[inPos + 26]) << 18;
    out[outPos + 19] = (in[inPos + 27] - in[inPos + 26]) >>> 14 | (in[inPos + 28] - in[inPos + 27]) << 8 | (in[inPos + 29] - in[inPos + 28]) << 30;
    out[outPos + 20] = (in[inPos + 29] - in[inPos + 28]) >>> 2 | (in[inPos + 30] - in[inPos + 29]) << 20;
    out[outPos + 21] = (in[inPos + 30] - in[inPos + 29]) >>> 12 | (in[inPos + 31] - in[inPos + 30]) << 10;
  }

  private static void unpack22(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 4194303) + initValue;
    out[outPos + 1] = (in[inPos] >>> 22 | (in[inPos + 1] & 4095) << 10) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 12 | (in[inPos + 2] & 3) << 20) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 2 & 4194303) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 24 | (in[inPos + 3] & 16383) << 8) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 14 | (in[inPos + 4] & 15) << 18) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 4 & 4194303) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 4] >>> 26 | (in[inPos + 5] & 65535) << 6) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 16 | (in[inPos + 6] & 63) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 6 & 4194303) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 6] >>> 28 | (in[inPos + 7] & 262143) << 4) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 18 | (in[inPos + 8] & 255) << 14) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 8] >>> 8 & 4194303) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 8] >>> 30 | (in[inPos + 9] & 1048575) << 2) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 9] >>> 20 | (in[inPos + 10] & 1023) << 12) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 10] >>> 10) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 11] & 4194303) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 11] >>> 22 | (in[inPos + 12] & 4095) << 10) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 12] >>> 12 | (in[inPos + 13] & 3) << 20) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 13] >>> 2 & 4194303) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 13] >>> 24 | (in[inPos + 14] & 16383) << 8) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 14] >>> 14 | (in[inPos + 15] & 15) << 18) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 15] >>> 4 & 4194303) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 15] >>> 26 | (in[inPos + 16] & 65535) << 6) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 16] >>> 16 | (in[inPos + 17] & 63) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 17] >>> 6 & 4194303) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 17] >>> 28 | (in[inPos + 18] & 262143) << 4) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 18] >>> 18 | (in[inPos + 19] & 255) << 14) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 19] >>> 8 & 4194303) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 19] >>> 30 | (in[inPos + 20] & 1048575) << 2) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 20] >>> 20 | (in[inPos + 21] & 1023) << 12) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 21] >>> 10) + out[outPos + 30];
  }

  private static void pack23(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 23;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 9 | (in[inPos + 2] - in[inPos + 1]) << 14;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 18 | (in[inPos + 3] - in[inPos + 2]) << 5 | (in[inPos + 4] - in[inPos + 3]) << 28;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) >>> 4 | (in[inPos + 5] - in[inPos + 4]) << 19;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 13 | (in[inPos + 6] - in[inPos + 5]) << 10;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 22 | (in[inPos + 7] - in[inPos + 6]) << 1 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 15;
    out[outPos + 7] = (in[inPos + 9] - in[inPos + 8]) >>> 17 | (in[inPos + 10] - in[inPos + 9]) << 6 | (in[inPos + 11] - in[inPos + 10]) << 29;
    out[outPos + 8] = (in[inPos + 11] - in[inPos + 10]) >>> 3 | (in[inPos + 12] - in[inPos + 11]) << 20;
    out[outPos + 9] = (in[inPos + 12] - in[inPos + 11]) >>> 12 | (in[inPos + 13] - in[inPos + 12]) << 11;
    out[outPos + 10] = (in[inPos + 13] - in[inPos + 12]) >>> 21 | (in[inPos + 14] - in[inPos + 13]) << 2 | (in[inPos + 15] - in[inPos + 14]) << 25;
    out[outPos + 11] = (in[inPos + 15] - in[inPos + 14]) >>> 7 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 12] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 7 | (in[inPos + 18] - in[inPos + 17]) << 30;
    out[outPos + 13] = (in[inPos + 18] - in[inPos + 17]) >>> 2 | (in[inPos + 19] - in[inPos + 18]) << 21;
    out[outPos + 14] = (in[inPos + 19] - in[inPos + 18]) >>> 11 | (in[inPos + 20] - in[inPos + 19]) << 12;
    out[outPos + 15] = (in[inPos + 20] - in[inPos + 19]) >>> 20 | (in[inPos + 21] - in[inPos + 20]) << 3 | (in[inPos + 22] - in[inPos + 21]) << 26;
    out[outPos + 16] = (in[inPos + 22] - in[inPos + 21]) >>> 6 | (in[inPos + 23] - in[inPos + 22]) << 17;
    out[outPos + 17] = (in[inPos + 23] - in[inPos + 22]) >>> 15 | (in[inPos + 24] - in[inPos + 23]) << 8 | (in[inPos + 25] - in[inPos + 24]) << 31;
    out[outPos + 18] = (in[inPos + 25] - in[inPos + 24]) >>> 1 | (in[inPos + 26] - in[inPos + 25]) << 22;
    out[outPos + 19] = (in[inPos + 26] - in[inPos + 25]) >>> 10 | (in[inPos + 27] - in[inPos + 26]) << 13;
    out[outPos + 20] = (in[inPos + 27] - in[inPos + 26]) >>> 19 | (in[inPos + 28] - in[inPos + 27]) << 4 | (in[inPos + 29] - in[inPos + 28]) << 27;
    out[outPos + 21] = (in[inPos + 29] - in[inPos + 28]) >>> 5 | (in[inPos + 30] - in[inPos + 29]) << 18;
    out[outPos + 22] = (in[inPos + 30] - in[inPos + 29]) >>> 14 | (in[inPos + 31] - in[inPos + 30]) << 9;
  }

  private static void unpack23(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 8388607) + initValue;
    out[outPos + 1] = (in[inPos] >>> 23 | (in[inPos + 1] & 16383) << 9) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 14 | (in[inPos + 2] & 31) << 18) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 5 & 8388607) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 2] >>> 28 | (in[inPos + 3] & 524287) << 4) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 19 | (in[inPos + 4] & 1023) << 13) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 10 | (in[inPos + 5] & 1) << 22) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 1 & 8388607) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 5] >>> 24 | (in[inPos + 6] & 32767) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 15 | (in[inPos + 7] & 63) << 17) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 6 & 8388607) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 7] >>> 29 | (in[inPos + 8] & 1048575) << 3) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 8] >>> 20 | (in[inPos + 9] & 2047) << 12) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 9] >>> 11 | (in[inPos + 10] & 3) << 21) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 10] >>> 2 & 8388607) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 10] >>> 25 | (in[inPos + 11] & 65535) << 7) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 11] >>> 16 | (in[inPos + 12] & 127) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 12] >>> 7 & 8388607) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 12] >>> 30 | (in[inPos + 13] & 2097151) << 2) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 13] >>> 21 | (in[inPos + 14] & 4095) << 11) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 14] >>> 12 | (in[inPos + 15] & 7) << 20) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 15] >>> 3 & 8388607) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 15] >>> 26 | (in[inPos + 16] & 131071) << 6) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 16] >>> 17 | (in[inPos + 17] & 255) << 15) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 17] >>> 8 & 8388607) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 17] >>> 31 | (in[inPos + 18] & 4194303) << 1) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 18] >>> 22 | (in[inPos + 19] & 8191) << 10) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 19] >>> 13 | (in[inPos + 20] & 15) << 19) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 20] >>> 4 & 8388607) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 20] >>> 27 | (in[inPos + 21] & 262143) << 5) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 21] >>> 18 | (in[inPos + 22] & 511) << 14) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 22] >>> 9) + out[outPos + 30];
  }

  private static void pack24(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 24;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 8 | (in[inPos + 2] - in[inPos + 1]) << 16;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 16 | (in[inPos + 3] - in[inPos + 2]) << 8;
    out[outPos + 3] = (in[inPos + 4] - in[inPos + 3]) | (in[inPos + 5] - in[inPos + 4]) << 24;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 8 | (in[inPos + 6] - in[inPos + 5]) << 16;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 16 | (in[inPos + 7] - in[inPos + 6]) << 8;
    out[outPos + 6] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 24;
    out[outPos + 7] = (in[inPos + 9] - in[inPos + 8]) >>> 8 | (in[inPos + 10] - in[inPos + 9]) << 16;
    out[outPos + 8] = (in[inPos + 10] - in[inPos + 9]) >>> 16 | (in[inPos + 11] - in[inPos + 10]) << 8;
    out[outPos + 9] = (in[inPos + 12] - in[inPos + 11]) | (in[inPos + 13] - in[inPos + 12]) << 24;
    out[outPos + 10] = (in[inPos + 13] - in[inPos + 12]) >>> 8 | (in[inPos + 14] - in[inPos + 13]) << 16;
    out[outPos + 11] = (in[inPos + 14] - in[inPos + 13]) >>> 16 | (in[inPos + 15] - in[inPos + 14]) << 8;
    out[outPos + 12] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 24;
    out[outPos + 13] = (in[inPos + 17] - in[inPos + 16]) >>> 8 | (in[inPos + 18] - in[inPos + 17]) << 16;
    out[outPos + 14] = (in[inPos + 18] - in[inPos + 17]) >>> 16 | (in[inPos + 19] - in[inPos + 18]) << 8;
    out[outPos + 15] = (in[inPos + 20] - in[inPos + 19]) | (in[inPos + 21] - in[inPos + 20]) << 24;
    out[outPos + 16] = (in[inPos + 21] - in[inPos + 20]) >>> 8 | (in[inPos + 22] - in[inPos + 21]) << 16;
    out[outPos + 17] = (in[inPos + 22] - in[inPos + 21]) >>> 16 | (in[inPos + 23] - in[inPos + 22]) << 8;
    out[outPos + 18] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 24;
    out[outPos + 19] = (in[inPos + 25] - in[inPos + 24]) >>> 8 | (in[inPos + 26] - in[inPos + 25]) << 16;
    out[outPos + 20] = (in[inPos + 26] - in[inPos + 25]) >>> 16 | (in[inPos + 27] - in[inPos + 26]) << 8;
    out[outPos + 21] = (in[inPos + 28] - in[inPos + 27]) | (in[inPos + 29] - in[inPos + 28]) << 24;
    out[outPos + 22] = (in[inPos + 29] - in[inPos + 28]) >>> 8 | (in[inPos + 30] - in[inPos + 29]) << 16;
    out[outPos + 23] = (in[inPos + 30] - in[inPos + 29]) >>> 16 | (in[inPos + 31] - in[inPos + 30]) << 8;
  }

  private static void unpack24(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 16777215) + initValue;
    out[outPos + 1] = (in[inPos] >>> 24 | (in[inPos + 1] & 65535) << 8) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 16 | (in[inPos + 2] & 255) << 16) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 8) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] & 16777215) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 24 | (in[inPos + 4] & 65535) << 8) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 16 | (in[inPos + 5] & 255) << 16) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 8) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] & 16777215) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 6] >>> 24 | (in[inPos + 7] & 65535) << 8) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 16 | (in[inPos + 8] & 255) << 16) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 8) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] & 16777215) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 9] >>> 24 | (in[inPos + 10] & 65535) << 8) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 10] >>> 16 | (in[inPos + 11] & 255) << 16) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 11] >>> 8) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 12] & 16777215) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 12] >>> 24 | (in[inPos + 13] & 65535) << 8) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 13] >>> 16 | (in[inPos + 14] & 255) << 16) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 14] >>> 8) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 15] & 16777215) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 15] >>> 24 | (in[inPos + 16] & 65535) << 8) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 16] >>> 16 | (in[inPos + 17] & 255) << 16) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 17] >>> 8) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 18] & 16777215) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 18] >>> 24 | (in[inPos + 19] & 65535) << 8) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 19] >>> 16 | (in[inPos + 20] & 255) << 16) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 20] >>> 8) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 21] & 16777215) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 21] >>> 24 | (in[inPos + 22] & 65535) << 8) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 22] >>> 16 | (in[inPos + 23] & 255) << 16) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 23] >>> 8) + out[outPos + 30];
  }

  private static void pack25(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 25;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 7 | (in[inPos + 2] - in[inPos + 1]) << 18;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 14 | (in[inPos + 3] - in[inPos + 2]) << 11;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 21 | (in[inPos + 4] - in[inPos + 3]) << 4 | (in[inPos + 5] - in[inPos + 4]) << 29;
    out[outPos + 4] = (in[inPos + 5] - in[inPos + 4]) >>> 3 | (in[inPos + 6] - in[inPos + 5]) << 22;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 10 | (in[inPos + 7] - in[inPos + 6]) << 15;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 17 | (in[inPos + 8] - in[inPos + 7]) << 8;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 24 | (in[inPos + 9] - in[inPos + 8]) << 1 | (in[inPos + 10] - in[inPos + 9]) << 26;
    out[outPos + 8] = (in[inPos + 10] - in[inPos + 9]) >>> 6 | (in[inPos + 11] - in[inPos + 10]) << 19;
    out[outPos + 9] = (in[inPos + 11] - in[inPos + 10]) >>> 13 | (in[inPos + 12] - in[inPos + 11]) << 12;
    out[outPos + 10] = (in[inPos + 12] - in[inPos + 11]) >>> 20 | (in[inPos + 13] - in[inPos + 12]) << 5 | (in[inPos + 14] - in[inPos + 13]) << 30;
    out[outPos + 11] = (in[inPos + 14] - in[inPos + 13]) >>> 2 | (in[inPos + 15] - in[inPos + 14]) << 23;
    out[outPos + 12] = (in[inPos + 15] - in[inPos + 14]) >>> 9 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 13] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 9;
    out[outPos + 14] = (in[inPos + 17] - in[inPos + 16]) >>> 23 | (in[inPos + 18] - in[inPos + 17]) << 2 | (in[inPos + 19] - in[inPos + 18]) << 27;
    out[outPos + 15] = (in[inPos + 19] - in[inPos + 18]) >>> 5 | (in[inPos + 20] - in[inPos + 19]) << 20;
    out[outPos + 16] = (in[inPos + 20] - in[inPos + 19]) >>> 12 | (in[inPos + 21] - in[inPos + 20]) << 13;
    out[outPos + 17] = (in[inPos + 21] - in[inPos + 20]) >>> 19 | (in[inPos + 22] - in[inPos + 21]) << 6 | (in[inPos + 23] - in[inPos + 22]) << 31;
    out[outPos + 18] = (in[inPos + 23] - in[inPos + 22]) >>> 1 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 19] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 17;
    out[outPos + 20] = (in[inPos + 25] - in[inPos + 24]) >>> 15 | (in[inPos + 26] - in[inPos + 25]) << 10;
    out[outPos + 21] = (in[inPos + 26] - in[inPos + 25]) >>> 22 | (in[inPos + 27] - in[inPos + 26]) << 3 | (in[inPos + 28] - in[inPos + 27]) << 28;
    out[outPos + 22] = (in[inPos + 28] - in[inPos + 27]) >>> 4 | (in[inPos + 29] - in[inPos + 28]) << 21;
    out[outPos + 23] = (in[inPos + 29] - in[inPos + 28]) >>> 11 | (in[inPos + 30] - in[inPos + 29]) << 14;
    out[outPos + 24] = (in[inPos + 30] - in[inPos + 29]) >>> 18 | (in[inPos + 31] - in[inPos + 30]) << 7;
  }

  private static void unpack25(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 33554431) + initValue;
    out[outPos + 1] = (in[inPos] >>> 25 | (in[inPos + 1] & 262143) << 7) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 18 | (in[inPos + 2] & 2047) << 14) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 11 | (in[inPos + 3] & 15) << 21) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 4 & 33554431) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 3] >>> 29 | (in[inPos + 4] & 4194303) << 3) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 22 | (in[inPos + 5] & 32767) << 10) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 15 | (in[inPos + 6] & 255) << 17) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 8 | (in[inPos + 7] & 1) << 24) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 1 & 33554431) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 7] >>> 26 | (in[inPos + 8] & 524287) << 6) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 19 | (in[inPos + 9] & 4095) << 13) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] >>> 12 | (in[inPos + 10] & 31) << 20) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 10] >>> 5 & 33554431) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 10] >>> 30 | (in[inPos + 11] & 8388607) << 2) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 11] >>> 23 | (in[inPos + 12] & 65535) << 9) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 12] >>> 16 | (in[inPos + 13] & 511) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 13] >>> 9 | (in[inPos + 14] & 3) << 23) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 14] >>> 2 & 33554431) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 14] >>> 27 | (in[inPos + 15] & 1048575) << 5) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 15] >>> 20 | (in[inPos + 16] & 8191) << 12) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 16] >>> 13 | (in[inPos + 17] & 63) << 19) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 17] >>> 6 & 33554431) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 17] >>> 31 | (in[inPos + 18] & 16777215) << 1) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 18] >>> 24 | (in[inPos + 19] & 131071) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 19] >>> 17 | (in[inPos + 20] & 1023) << 15) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 20] >>> 10 | (in[inPos + 21] & 7) << 22) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 21] >>> 3 & 33554431) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 21] >>> 28 | (in[inPos + 22] & 2097151) << 4) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 22] >>> 21 | (in[inPos + 23] & 16383) << 11) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 23] >>> 14 | (in[inPos + 24] & 127) << 18) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 24] >>> 7) + out[outPos + 30];
  }

  private static void pack26(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 26;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 6 | (in[inPos + 2] - in[inPos + 1]) << 20;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 12 | (in[inPos + 3] - in[inPos + 2]) << 14;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 18 | (in[inPos + 4] - in[inPos + 3]) << 8;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 24 | (in[inPos + 5] - in[inPos + 4]) << 2 | (in[inPos + 6] - in[inPos + 5]) << 28;
    out[outPos + 5] = (in[inPos + 6] - in[inPos + 5]) >>> 4 | (in[inPos + 7] - in[inPos + 6]) << 22;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 10 | (in[inPos + 8] - in[inPos + 7]) << 16;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 10;
    out[outPos + 8] = (in[inPos + 9] - in[inPos + 8]) >>> 22 | (in[inPos + 10] - in[inPos + 9]) << 4 | (in[inPos + 11] - in[inPos + 10]) << 30;
    out[outPos + 9] = (in[inPos + 11] - in[inPos + 10]) >>> 2 | (in[inPos + 12] - in[inPos + 11]) << 24;
    out[outPos + 10] = (in[inPos + 12] - in[inPos + 11]) >>> 8 | (in[inPos + 13] - in[inPos + 12]) << 18;
    out[outPos + 11] = (in[inPos + 13] - in[inPos + 12]) >>> 14 | (in[inPos + 14] - in[inPos + 13]) << 12;
    out[outPos + 12] = (in[inPos + 14] - in[inPos + 13]) >>> 20 | (in[inPos + 15] - in[inPos + 14]) << 6;
    out[outPos + 13] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 26;
    out[outPos + 14] = (in[inPos + 17] - in[inPos + 16]) >>> 6 | (in[inPos + 18] - in[inPos + 17]) << 20;
    out[outPos + 15] = (in[inPos + 18] - in[inPos + 17]) >>> 12 | (in[inPos + 19] - in[inPos + 18]) << 14;
    out[outPos + 16] = (in[inPos + 19] - in[inPos + 18]) >>> 18 | (in[inPos + 20] - in[inPos + 19]) << 8;
    out[outPos + 17] = (in[inPos + 20] - in[inPos + 19]) >>> 24 | (in[inPos + 21] - in[inPos + 20]) << 2 | (in[inPos + 22] - in[inPos + 21]) << 28;
    out[outPos + 18] = (in[inPos + 22] - in[inPos + 21]) >>> 4 | (in[inPos + 23] - in[inPos + 22]) << 22;
    out[outPos + 19] = (in[inPos + 23] - in[inPos + 22]) >>> 10 | (in[inPos + 24] - in[inPos + 23]) << 16;
    out[outPos + 20] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 10;
    out[outPos + 21] = (in[inPos + 25] - in[inPos + 24]) >>> 22 | (in[inPos + 26] - in[inPos + 25]) << 4 | (in[inPos + 27] - in[inPos + 26]) << 30;
    out[outPos + 22] = (in[inPos + 27] - in[inPos + 26]) >>> 2 | (in[inPos + 28] - in[inPos + 27]) << 24;
    out[outPos + 23] = (in[inPos + 28] - in[inPos + 27]) >>> 8 | (in[inPos + 29] - in[inPos + 28]) << 18;
    out[outPos + 24] = (in[inPos + 29] - in[inPos + 28]) >>> 14 | (in[inPos + 30] - in[inPos + 29]) << 12;
    out[outPos + 25] = (in[inPos + 30] - in[inPos + 29]) >>> 20 | (in[inPos + 31] - in[inPos + 30]) << 6;
  }

  private static void unpack26(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 67108863) + initValue;
    out[outPos + 1] = (in[inPos] >>> 26 | (in[inPos + 1] & 1048575) << 6) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 20 | (in[inPos + 2] & 16383) << 12) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 14 | (in[inPos + 3] & 255) << 18) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 8 | (in[inPos + 4] & 3) << 24) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 2 & 67108863) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 4] >>> 28 | (in[inPos + 5] & 4194303) << 4) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 22 | (in[inPos + 6] & 65535) << 10) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 16 | (in[inPos + 7] & 1023) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 10 | (in[inPos + 8] & 15) << 22) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 4 & 67108863) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 8] >>> 30 | (in[inPos + 9] & 16777215) << 2) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 9] >>> 24 | (in[inPos + 10] & 262143) << 8) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 10] >>> 18 | (in[inPos + 11] & 4095) << 14) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 11] >>> 12 | (in[inPos + 12] & 63) << 20) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 12] >>> 6) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 13] & 67108863) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 13] >>> 26 | (in[inPos + 14] & 1048575) << 6) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 14] >>> 20 | (in[inPos + 15] & 16383) << 12) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 15] >>> 14 | (in[inPos + 16] & 255) << 18) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 16] >>> 8 | (in[inPos + 17] & 3) << 24) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 17] >>> 2 & 67108863) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 17] >>> 28 | (in[inPos + 18] & 4194303) << 4) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 18] >>> 22 | (in[inPos + 19] & 65535) << 10) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 19] >>> 16 | (in[inPos + 20] & 1023) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 20] >>> 10 | (in[inPos + 21] & 15) << 22) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 21] >>> 4 & 67108863) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 21] >>> 30 | (in[inPos + 22] & 16777215) << 2) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 22] >>> 24 | (in[inPos + 23] & 262143) << 8) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 23] >>> 18 | (in[inPos + 24] & 4095) << 14) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 24] >>> 12 | (in[inPos + 25] & 63) << 20) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 25] >>> 6) + out[outPos + 30];
  }

  private static void pack27(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 27;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 5 | (in[inPos + 2] - in[inPos + 1]) << 22;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 10 | (in[inPos + 3] - in[inPos + 2]) << 17;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 15 | (in[inPos + 4] - in[inPos + 3]) << 12;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 20 | (in[inPos + 5] - in[inPos + 4]) << 7;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 25 | (in[inPos + 6] - in[inPos + 5]) << 2 | (in[inPos + 7] - in[inPos + 6]) << 29;
    out[outPos + 6] = (in[inPos + 7] - in[inPos + 6]) >>> 3 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 19;
    out[outPos + 8] = (in[inPos + 9] - in[inPos + 8]) >>> 13 | (in[inPos + 10] - in[inPos + 9]) << 14;
    out[outPos + 9] = (in[inPos + 10] - in[inPos + 9]) >>> 18 | (in[inPos + 11] - in[inPos + 10]) << 9;
    out[outPos + 10] = (in[inPos + 11] - in[inPos + 10]) >>> 23 | (in[inPos + 12] - in[inPos + 11]) << 4 | (in[inPos + 13] - in[inPos + 12]) << 31;
    out[outPos + 11] = (in[inPos + 13] - in[inPos + 12]) >>> 1 | (in[inPos + 14] - in[inPos + 13]) << 26;
    out[outPos + 12] = (in[inPos + 14] - in[inPos + 13]) >>> 6 | (in[inPos + 15] - in[inPos + 14]) << 21;
    out[outPos + 13] = (in[inPos + 15] - in[inPos + 14]) >>> 11 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 14] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 11;
    out[outPos + 15] = (in[inPos + 17] - in[inPos + 16]) >>> 21 | (in[inPos + 18] - in[inPos + 17]) << 6;
    out[outPos + 16] = (in[inPos + 18] - in[inPos + 17]) >>> 26 | (in[inPos + 19] - in[inPos + 18]) << 1 | (in[inPos + 20] - in[inPos + 19]) << 28;
    out[outPos + 17] = (in[inPos + 20] - in[inPos + 19]) >>> 4 | (in[inPos + 21] - in[inPos + 20]) << 23;
    out[outPos + 18] = (in[inPos + 21] - in[inPos + 20]) >>> 9 | (in[inPos + 22] - in[inPos + 21]) << 18;
    out[outPos + 19] = (in[inPos + 22] - in[inPos + 21]) >>> 14 | (in[inPos + 23] - in[inPos + 22]) << 13;
    out[outPos + 20] = (in[inPos + 23] - in[inPos + 22]) >>> 19 | (in[inPos + 24] - in[inPos + 23]) << 8;
    out[outPos + 21] = (in[inPos + 24] - in[inPos + 23]) >>> 24 | (in[inPos + 25] - in[inPos + 24]) << 3 | (in[inPos + 26] - in[inPos + 25]) << 30;
    out[outPos + 22] = (in[inPos + 26] - in[inPos + 25]) >>> 2 | (in[inPos + 27] - in[inPos + 26]) << 25;
    out[outPos + 23] = (in[inPos + 27] - in[inPos + 26]) >>> 7 | (in[inPos + 28] - in[inPos + 27]) << 20;
    out[outPos + 24] = (in[inPos + 28] - in[inPos + 27]) >>> 12 | (in[inPos + 29] - in[inPos + 28]) << 15;
    out[outPos + 25] = (in[inPos + 29] - in[inPos + 28]) >>> 17 | (in[inPos + 30] - in[inPos + 29]) << 10;
    out[outPos + 26] = (in[inPos + 30] - in[inPos + 29]) >>> 22 | (in[inPos + 31] - in[inPos + 30]) << 5;
  }

  private static void unpack27(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 134217727) + initValue;
    out[outPos + 1] = (in[inPos] >>> 27 | (in[inPos + 1] & 4194303) << 5) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 22 | (in[inPos + 2] & 131071) << 10) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 17 | (in[inPos + 3] & 4095) << 15) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 12 | (in[inPos + 4] & 127) << 20) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 7 | (in[inPos + 5] & 3) << 25) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 2 & 134217727) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 5] >>> 29 | (in[inPos + 6] & 16777215) << 3) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 6] >>> 24 | (in[inPos + 7] & 524287) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 19 | (in[inPos + 8] & 16383) << 13) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 14 | (in[inPos + 9] & 511) << 18) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 9 | (in[inPos + 10] & 15) << 23) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 10] >>> 4 & 134217727) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 10] >>> 31 | (in[inPos + 11] & 67108863) << 1) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 11] >>> 26 | (in[inPos + 12] & 2097151) << 6) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 12] >>> 21 | (in[inPos + 13] & 65535) << 11) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 13] >>> 16 | (in[inPos + 14] & 2047) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 14] >>> 11 | (in[inPos + 15] & 63) << 21) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 15] >>> 6 | (in[inPos + 16] & 1) << 26) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 16] >>> 1 & 134217727) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 16] >>> 28 | (in[inPos + 17] & 8388607) << 4) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 17] >>> 23 | (in[inPos + 18] & 262143) << 9) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 18] >>> 18 | (in[inPos + 19] & 8191) << 14) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 19] >>> 13 | (in[inPos + 20] & 255) << 19) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 20] >>> 8 | (in[inPos + 21] & 7) << 24) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 21] >>> 3 & 134217727) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 21] >>> 30 | (in[inPos + 22] & 33554431) << 2) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 22] >>> 25 | (in[inPos + 23] & 1048575) << 7) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 23] >>> 20 | (in[inPos + 24] & 32767) << 12) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 24] >>> 15 | (in[inPos + 25] & 1023) << 17) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 25] >>> 10 | (in[inPos + 26] & 31) << 22) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 26] >>> 5) + out[outPos + 30];
  }

  private static void pack28(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 28;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 4 | (in[inPos + 2] - in[inPos + 1]) << 24;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 8 | (in[inPos + 3] - in[inPos + 2]) << 20;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 12 | (in[inPos + 4] - in[inPos + 3]) << 16;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 16 | (in[inPos + 5] - in[inPos + 4]) << 12;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 20 | (in[inPos + 6] - in[inPos + 5]) << 8;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 24 | (in[inPos + 7] - in[inPos + 6]) << 4;
    out[outPos + 7] = (in[inPos + 8] - in[inPos + 7]) | (in[inPos + 9] - in[inPos + 8]) << 28;
    out[outPos + 8] = (in[inPos + 9] - in[inPos + 8]) >>> 4 | (in[inPos + 10] - in[inPos + 9]) << 24;
    out[outPos + 9] = (in[inPos + 10] - in[inPos + 9]) >>> 8 | (in[inPos + 11] - in[inPos + 10]) << 20;
    out[outPos + 10] = (in[inPos + 11] - in[inPos + 10]) >>> 12 | (in[inPos + 12] - in[inPos + 11]) << 16;
    out[outPos + 11] = (in[inPos + 12] - in[inPos + 11]) >>> 16 | (in[inPos + 13] - in[inPos + 12]) << 12;
    out[outPos + 12] = (in[inPos + 13] - in[inPos + 12]) >>> 20 | (in[inPos + 14] - in[inPos + 13]) << 8;
    out[outPos + 13] = (in[inPos + 14] - in[inPos + 13]) >>> 24 | (in[inPos + 15] - in[inPos + 14]) << 4;
    out[outPos + 14] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 28;
    out[outPos + 15] = (in[inPos + 17] - in[inPos + 16]) >>> 4 | (in[inPos + 18] - in[inPos + 17]) << 24;
    out[outPos + 16] = (in[inPos + 18] - in[inPos + 17]) >>> 8 | (in[inPos + 19] - in[inPos + 18]) << 20;
    out[outPos + 17] = (in[inPos + 19] - in[inPos + 18]) >>> 12 | (in[inPos + 20] - in[inPos + 19]) << 16;
    out[outPos + 18] = (in[inPos + 20] - in[inPos + 19]) >>> 16 | (in[inPos + 21] - in[inPos + 20]) << 12;
    out[outPos + 19] = (in[inPos + 21] - in[inPos + 20]) >>> 20 | (in[inPos + 22] - in[inPos + 21]) << 8;
    out[outPos + 20] = (in[inPos + 22] - in[inPos + 21]) >>> 24 | (in[inPos + 23] - in[inPos + 22]) << 4;
    out[outPos + 21] = (in[inPos + 24] - in[inPos + 23]) | (in[inPos + 25] - in[inPos + 24]) << 28;
    out[outPos + 22] = (in[inPos + 25] - in[inPos + 24]) >>> 4 | (in[inPos + 26] - in[inPos + 25]) << 24;
    out[outPos + 23] = (in[inPos + 26] - in[inPos + 25]) >>> 8 | (in[inPos + 27] - in[inPos + 26]) << 20;
    out[outPos + 24] = (in[inPos + 27] - in[inPos + 26]) >>> 12 | (in[inPos + 28] - in[inPos + 27]) << 16;
    out[outPos + 25] = (in[inPos + 28] - in[inPos + 27]) >>> 16 | (in[inPos + 29] - in[inPos + 28]) << 12;
    out[outPos + 26] = (in[inPos + 29] - in[inPos + 28]) >>> 20 | (in[inPos + 30] - in[inPos + 29]) << 8;
    out[outPos + 27] = (in[inPos + 30] - in[inPos + 29]) >>> 24 | (in[inPos + 31] - in[inPos + 30]) << 4;
  }

  private static void unpack28(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 268435455) + initValue;
    out[outPos + 1] = (in[inPos] >>> 28 | (in[inPos + 1] & 16777215) << 4) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 24 | (in[inPos + 2] & 1048575) << 8) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 20 | (in[inPos + 3] & 65535) << 12) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 16 | (in[inPos + 4] & 4095) << 16) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 12 | (in[inPos + 5] & 255) << 20) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 8 | (in[inPos + 6] & 15) << 24) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 4) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] & 268435455) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 7] >>> 28 | (in[inPos + 8] & 16777215) << 4) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 8] >>> 24 | (in[inPos + 9] & 1048575) << 8) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 20 | (in[inPos + 10] & 65535) << 12) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 10] >>> 16 | (in[inPos + 11] & 4095) << 16) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 11] >>> 12 | (in[inPos + 12] & 255) << 20) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 12] >>> 8 | (in[inPos + 13] & 15) << 24) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 13] >>> 4) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 14] & 268435455) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 14] >>> 28 | (in[inPos + 15] & 16777215) << 4) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 15] >>> 24 | (in[inPos + 16] & 1048575) << 8) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 16] >>> 20 | (in[inPos + 17] & 65535) << 12) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 17] >>> 16 | (in[inPos + 18] & 4095) << 16) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 18] >>> 12 | (in[inPos + 19] & 255) << 20) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 19] >>> 8 | (in[inPos + 20] & 15) << 24) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 20] >>> 4) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 21] & 268435455) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 21] >>> 28 | (in[inPos + 22] & 16777215) << 4) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 22] >>> 24 | (in[inPos + 23] & 1048575) << 8) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 23] >>> 20 | (in[inPos + 24] & 65535) << 12) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 24] >>> 16 | (in[inPos + 25] & 4095) << 16) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 25] >>> 12 | (in[inPos + 26] & 255) << 20) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 26] >>> 8 | (in[inPos + 27] & 15) << 24) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 27] >>> 4) + out[outPos + 30];
  }

  private static void pack29(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 29;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 3 | (in[inPos + 2] - in[inPos + 1]) << 26;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 6 | (in[inPos + 3] - in[inPos + 2]) << 23;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 9 | (in[inPos + 4] - in[inPos + 3]) << 20;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 12 | (in[inPos + 5] - in[inPos + 4]) << 17;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 15 | (in[inPos + 6] - in[inPos + 5]) << 14;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 18 | (in[inPos + 7] - in[inPos + 6]) << 11;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 21 | (in[inPos + 8] - in[inPos + 7]) << 8;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 24 | (in[inPos + 9] - in[inPos + 8]) << 5;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 27 | (in[inPos + 10] - in[inPos + 9]) << 2 | (in[inPos + 11] - in[inPos + 10]) << 31;
    out[outPos + 10] = (in[inPos + 11] - in[inPos + 10]) >>> 1 | (in[inPos + 12] - in[inPos + 11]) << 28;
    out[outPos + 11] = (in[inPos + 12] - in[inPos + 11]) >>> 4 | (in[inPos + 13] - in[inPos + 12]) << 25;
    out[outPos + 12] = (in[inPos + 13] - in[inPos + 12]) >>> 7 | (in[inPos + 14] - in[inPos + 13]) << 22;
    out[outPos + 13] = (in[inPos + 14] - in[inPos + 13]) >>> 10 | (in[inPos + 15] - in[inPos + 14]) << 19;
    out[outPos + 14] = (in[inPos + 15] - in[inPos + 14]) >>> 13 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 15] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 13;
    out[outPos + 16] = (in[inPos + 17] - in[inPos + 16]) >>> 19 | (in[inPos + 18] - in[inPos + 17]) << 10;
    out[outPos + 17] = (in[inPos + 18] - in[inPos + 17]) >>> 22 | (in[inPos + 19] - in[inPos + 18]) << 7;
    out[outPos + 18] = (in[inPos + 19] - in[inPos + 18]) >>> 25 | (in[inPos + 20] - in[inPos + 19]) << 4;
    out[outPos + 19] = (in[inPos + 20] - in[inPos + 19]) >>> 28 | (in[inPos + 21] - in[inPos + 20]) << 1 | (in[inPos + 22] - in[inPos + 21]) << 30;
    out[outPos + 20] = (in[inPos + 22] - in[inPos + 21]) >>> 2 | (in[inPos + 23] - in[inPos + 22]) << 27;
    out[outPos + 21] = (in[inPos + 23] - in[inPos + 22]) >>> 5 | (in[inPos + 24] - in[inPos + 23]) << 24;
    out[outPos + 22] = (in[inPos + 24] - in[inPos + 23]) >>> 8 | (in[inPos + 25] - in[inPos + 24]) << 21;
    out[outPos + 23] = (in[inPos + 25] - in[inPos + 24]) >>> 11 | (in[inPos + 26] - in[inPos + 25]) << 18;
    out[outPos + 24] = (in[inPos + 26] - in[inPos + 25]) >>> 14 | (in[inPos + 27] - in[inPos + 26]) << 15;
    out[outPos + 25] = (in[inPos + 27] - in[inPos + 26]) >>> 17 | (in[inPos + 28] - in[inPos + 27]) << 12;
    out[outPos + 26] = (in[inPos + 28] - in[inPos + 27]) >>> 20 | (in[inPos + 29] - in[inPos + 28]) << 9;
    out[outPos + 27] = (in[inPos + 29] - in[inPos + 28]) >>> 23 | (in[inPos + 30] - in[inPos + 29]) << 6;
    out[outPos + 28] = (in[inPos + 30] - in[inPos + 29]) >>> 26 | (in[inPos + 31] - in[inPos + 30]) << 3;
  }

  private static void unpack29(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 536870911) + initValue;
    out[outPos + 1] = (in[inPos] >>> 29 | (in[inPos + 1] & 67108863) << 3) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 26 | (in[inPos + 2] & 8388607) << 6) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 23 | (in[inPos + 3] & 1048575) << 9) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 20 | (in[inPos + 4] & 131071) << 12) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 17 | (in[inPos + 5] & 16383) << 15) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 14 | (in[inPos + 6] & 2047) << 18) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 11 | (in[inPos + 7] & 255) << 21) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 8 | (in[inPos + 8] & 31) << 24) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 5 | (in[inPos + 9] & 3) << 27) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 2 & 536870911) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 9] >>> 31 | (in[inPos + 10] & 268435455) << 1) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 10] >>> 28 | (in[inPos + 11] & 33554431) << 4) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 11] >>> 25 | (in[inPos + 12] & 4194303) << 7) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 12] >>> 22 | (in[inPos + 13] & 524287) << 10) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 13] >>> 19 | (in[inPos + 14] & 65535) << 13) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 14] >>> 16 | (in[inPos + 15] & 8191) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 15] >>> 13 | (in[inPos + 16] & 1023) << 19) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 16] >>> 10 | (in[inPos + 17] & 127) << 22) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 17] >>> 7 | (in[inPos + 18] & 15) << 25) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 18] >>> 4 | (in[inPos + 19] & 1) << 28) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 19] >>> 1 & 536870911) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 19] >>> 30 | (in[inPos + 20] & 134217727) << 2) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 20] >>> 27 | (in[inPos + 21] & 16777215) << 5) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 21] >>> 24 | (in[inPos + 22] & 2097151) << 8) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 22] >>> 21 | (in[inPos + 23] & 262143) << 11) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 23] >>> 18 | (in[inPos + 24] & 32767) << 14) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 24] >>> 15 | (in[inPos + 25] & 4095) << 17) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 25] >>> 12 | (in[inPos + 26] & 511) << 20) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 26] >>> 9 | (in[inPos + 27] & 63) << 23) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 27] >>> 6 | (in[inPos + 28] & 7) << 26) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 28] >>> 3) + out[outPos + 30];
  }

  private static void pack30(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 30;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 2 | (in[inPos + 2] - in[inPos + 1]) << 28;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 4 | (in[inPos + 3] - in[inPos + 2]) << 26;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 6 | (in[inPos + 4] - in[inPos + 3]) << 24;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 8 | (in[inPos + 5] - in[inPos + 4]) << 22;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 10 | (in[inPos + 6] - in[inPos + 5]) << 20;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 12 | (in[inPos + 7] - in[inPos + 6]) << 18;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 14 | (in[inPos + 8] - in[inPos + 7]) << 16;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 16 | (in[inPos + 9] - in[inPos + 8]) << 14;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 18 | (in[inPos + 10] - in[inPos + 9]) << 12;
    out[outPos + 10] = (in[inPos + 10] - in[inPos + 9]) >>> 20 | (in[inPos + 11] - in[inPos + 10]) << 10;
    out[outPos + 11] = (in[inPos + 11] - in[inPos + 10]) >>> 22 | (in[inPos + 12] - in[inPos + 11]) << 8;
    out[outPos + 12] = (in[inPos + 12] - in[inPos + 11]) >>> 24 | (in[inPos + 13] - in[inPos + 12]) << 6;
    out[outPos + 13] = (in[inPos + 13] - in[inPos + 12]) >>> 26 | (in[inPos + 14] - in[inPos + 13]) << 4;
    out[outPos + 14] = (in[inPos + 14] - in[inPos + 13]) >>> 28 | (in[inPos + 15] - in[inPos + 14]) << 2;
    out[outPos + 15] = (in[inPos + 16] - in[inPos + 15]) | (in[inPos + 17] - in[inPos + 16]) << 30;
    out[outPos + 16] = (in[inPos + 17] - in[inPos + 16]) >>> 2 | (in[inPos + 18] - in[inPos + 17]) << 28;
    out[outPos + 17] = (in[inPos + 18] - in[inPos + 17]) >>> 4 | (in[inPos + 19] - in[inPos + 18]) << 26;
    out[outPos + 18] = (in[inPos + 19] - in[inPos + 18]) >>> 6 | (in[inPos + 20] - in[inPos + 19]) << 24;
    out[outPos + 19] = (in[inPos + 20] - in[inPos + 19]) >>> 8 | (in[inPos + 21] - in[inPos + 20]) << 22;
    out[outPos + 20] = (in[inPos + 21] - in[inPos + 20]) >>> 10 | (in[inPos + 22] - in[inPos + 21]) << 20;
    out[outPos + 21] = (in[inPos + 22] - in[inPos + 21]) >>> 12 | (in[inPos + 23] - in[inPos + 22]) << 18;
    out[outPos + 22] = (in[inPos + 23] - in[inPos + 22]) >>> 14 | (in[inPos + 24] - in[inPos + 23]) << 16;
    out[outPos + 23] = (in[inPos + 24] - in[inPos + 23]) >>> 16 | (in[inPos + 25] - in[inPos + 24]) << 14;
    out[outPos + 24] = (in[inPos + 25] - in[inPos + 24]) >>> 18 | (in[inPos + 26] - in[inPos + 25]) << 12;
    out[outPos + 25] = (in[inPos + 26] - in[inPos + 25]) >>> 20 | (in[inPos + 27] - in[inPos + 26]) << 10;
    out[outPos + 26] = (in[inPos + 27] - in[inPos + 26]) >>> 22 | (in[inPos + 28] - in[inPos + 27]) << 8;
    out[outPos + 27] = (in[inPos + 28] - in[inPos + 27]) >>> 24 | (in[inPos + 29] - in[inPos + 28]) << 6;
    out[outPos + 28] = (in[inPos + 29] - in[inPos + 28]) >>> 26 | (in[inPos + 30] - in[inPos + 29]) << 4;
    out[outPos + 29] = (in[inPos + 30] - in[inPos + 29]) >>> 28 | (in[inPos + 31] - in[inPos + 30]) << 2;
  }

  private static void unpack30(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 1073741823) + initValue;
    out[outPos + 1] = (in[inPos] >>> 30 | (in[inPos + 1] & 268435455) << 2) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 28 | (in[inPos + 2] & 67108863) << 4) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 26 | (in[inPos + 3] & 16777215) << 6) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 24 | (in[inPos + 4] & 4194303) << 8) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 22 | (in[inPos + 5] & 1048575) << 10) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 20 | (in[inPos + 6] & 262143) << 12) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 18 | (in[inPos + 7] & 65535) << 14) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 16 | (in[inPos + 8] & 16383) << 16) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 14 | (in[inPos + 9] & 4095) << 18) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 12 | (in[inPos + 10] & 1023) << 20) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 10] >>> 10 | (in[inPos + 11] & 255) << 22) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 11] >>> 8 | (in[inPos + 12] & 63) << 24) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 12] >>> 6 | (in[inPos + 13] & 15) << 26) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 13] >>> 4 | (in[inPos + 14] & 3) << 28) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 14] >>> 2) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 15] & 1073741823) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 15] >>> 30 | (in[inPos + 16] & 268435455) << 2) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 16] >>> 28 | (in[inPos + 17] & 67108863) << 4) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 17] >>> 26 | (in[inPos + 18] & 16777215) << 6) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 18] >>> 24 | (in[inPos + 19] & 4194303) << 8) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 19] >>> 22 | (in[inPos + 20] & 1048575) << 10) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 20] >>> 20 | (in[inPos + 21] & 262143) << 12) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 21] >>> 18 | (in[inPos + 22] & 65535) << 14) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 22] >>> 16 | (in[inPos + 23] & 16383) << 16) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 23] >>> 14 | (in[inPos + 24] & 4095) << 18) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 24] >>> 12 | (in[inPos + 25] & 1023) << 20) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 25] >>> 10 | (in[inPos + 26] & 255) << 22) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 26] >>> 8 | (in[inPos + 27] & 63) << 24) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 27] >>> 6 | (in[inPos + 28] & 15) << 26) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 28] >>> 4 | (in[inPos + 29] & 3) << 28) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 29] >>> 2) + out[outPos + 30];
  }

  private static void pack31(final int initValue, final int[] in, final int inPos, final int[] out,
      final int outPos) {
    out[outPos] = in[inPos] - initValue | (in[inPos + 1] - in[inPos]) << 31;
    out[outPos + 1] = (in[inPos + 1] - in[inPos]) >>> 1 | (in[inPos + 2] - in[inPos + 1]) << 30;
    out[outPos + 2] = (in[inPos + 2] - in[inPos + 1]) >>> 2 | (in[inPos + 3] - in[inPos + 2]) << 29;
    out[outPos + 3] = (in[inPos + 3] - in[inPos + 2]) >>> 3 | (in[inPos + 4] - in[inPos + 3]) << 28;
    out[outPos + 4] = (in[inPos + 4] - in[inPos + 3]) >>> 4 | (in[inPos + 5] - in[inPos + 4]) << 27;
    out[outPos + 5] = (in[inPos + 5] - in[inPos + 4]) >>> 5 | (in[inPos + 6] - in[inPos + 5]) << 26;
    out[outPos + 6] = (in[inPos + 6] - in[inPos + 5]) >>> 6 | (in[inPos + 7] - in[inPos + 6]) << 25;
    out[outPos + 7] = (in[inPos + 7] - in[inPos + 6]) >>> 7 | (in[inPos + 8] - in[inPos + 7]) << 24;
    out[outPos + 8] = (in[inPos + 8] - in[inPos + 7]) >>> 8 | (in[inPos + 9] - in[inPos + 8]) << 23;
    out[outPos + 9] = (in[inPos + 9] - in[inPos + 8]) >>> 9 | (in[inPos + 10] - in[inPos + 9]) << 22;
    out[outPos + 10] = (in[inPos + 10] - in[inPos + 9]) >>> 10 | (in[inPos + 11] - in[inPos + 10]) << 21;
    out[outPos + 11] = (in[inPos + 11] - in[inPos + 10]) >>> 11 | (in[inPos + 12] - in[inPos + 11]) << 20;
    out[outPos + 12] = (in[inPos + 12] - in[inPos + 11]) >>> 12 | (in[inPos + 13] - in[inPos + 12]) << 19;
    out[outPos + 13] = (in[inPos + 13] - in[inPos + 12]) >>> 13 | (in[inPos + 14] - in[inPos + 13]) << 18;
    out[outPos + 14] = (in[inPos + 14] - in[inPos + 13]) >>> 14 | (in[inPos + 15] - in[inPos + 14]) << 17;
    out[outPos + 15] = (in[inPos + 15] - in[inPos + 14]) >>> 15 | (in[inPos + 16] - in[inPos + 15]) << 16;
    out[outPos + 16] = (in[inPos + 16] - in[inPos + 15]) >>> 16 | (in[inPos + 17] - in[inPos + 16]) << 15;
    out[outPos + 17] = (in[inPos + 17] - in[inPos + 16]) >>> 17 | (in[inPos + 18] - in[inPos + 17]) << 14;
    out[outPos + 18] = (in[inPos + 18] - in[inPos + 17]) >>> 18 | (in[inPos + 19] - in[inPos + 18]) << 13;
    out[outPos + 19] = (in[inPos + 19] - in[inPos + 18]) >>> 19 | (in[inPos + 20] - in[inPos + 19]) << 12;
    out[outPos + 20] = (in[inPos + 20] - in[inPos + 19]) >>> 20 | (in[inPos + 21] - in[inPos + 20]) << 11;
    out[outPos + 21] = (in[inPos + 21] - in[inPos + 20]) >>> 21 | (in[inPos + 22] - in[inPos + 21]) << 10;
    out[outPos + 22] = (in[inPos + 22] - in[inPos + 21]) >>> 22 | (in[inPos + 23] - in[inPos + 22]) << 9;
    out[outPos + 23] = (in[inPos + 23] - in[inPos + 22]) >>> 23 | (in[inPos + 24] - in[inPos + 23]) << 8;
    out[outPos + 24] = (in[inPos + 24] - in[inPos + 23]) >>> 24 | (in[inPos + 25] - in[inPos + 24]) << 7;
    out[outPos + 25] = (in[inPos + 25] - in[inPos + 24]) >>> 25 | (in[inPos + 26] - in[inPos + 25]) << 6;
    out[outPos + 26] = (in[inPos + 26] - in[inPos + 25]) >>> 26 | (in[inPos + 27] - in[inPos + 26]) << 5;
    out[outPos + 27] = (in[inPos + 27] - in[inPos + 26]) >>> 27 | (in[inPos + 28] - in[inPos + 27]) << 4;
    out[outPos + 28] = (in[inPos + 28] - in[inPos + 27]) >>> 28 | (in[inPos + 29] - in[inPos + 28]) << 3;
    out[outPos + 29] = (in[inPos + 29] - in[inPos + 28]) >>> 29 | (in[inPos + 30] - in[inPos + 29]) << 2;
    out[outPos + 30] = (in[inPos + 30] - in[inPos + 29]) >>> 30 | (in[inPos + 31] - in[inPos + 30]) << 1;
  }

  private static void unpack31(final int initValue, final int[] in, final int inPos,
      final int[] out, final int outPos) {
    out[outPos] = (in[inPos] & 2147483647) + initValue;
    out[outPos + 1] = (in[inPos] >>> 31 | (in[inPos + 1] & 1073741823) << 1) + out[outPos];
    out[outPos + 2] = (in[inPos + 1] >>> 30 | (in[inPos + 2] & 536870911) << 2) + out[outPos + 1];
    out[outPos + 3] = (in[inPos + 2] >>> 29 | (in[inPos + 3] & 268435455) << 3) + out[outPos + 2];
    out[outPos + 4] = (in[inPos + 3] >>> 28 | (in[inPos + 4] & 134217727) << 4) + out[outPos + 3];
    out[outPos + 5] = (in[inPos + 4] >>> 27 | (in[inPos + 5] & 67108863) << 5) + out[outPos + 4];
    out[outPos + 6] = (in[inPos + 5] >>> 26 | (in[inPos + 6] & 33554431) << 6) + out[outPos + 5];
    out[outPos + 7] = (in[inPos + 6] >>> 25 | (in[inPos + 7] & 16777215) << 7) + out[outPos + 6];
    out[outPos + 8] = (in[inPos + 7] >>> 24 | (in[inPos + 8] & 8388607) << 8) + out[outPos + 7];
    out[outPos + 9] = (in[inPos + 8] >>> 23 | (in[inPos + 9] & 4194303) << 9) + out[outPos + 8];
    out[outPos + 10] = (in[inPos + 9] >>> 22 | (in[inPos + 10] & 2097151) << 10) + out[outPos + 9];
    out[outPos + 11] = (in[inPos + 10] >>> 21 | (in[inPos + 11] & 1048575) << 11) + out[outPos + 10];
    out[outPos + 12] = (in[inPos + 11] >>> 20 | (in[inPos + 12] & 524287) << 12) + out[outPos + 11];
    out[outPos + 13] = (in[inPos + 12] >>> 19 | (in[inPos + 13] & 262143) << 13) + out[outPos + 12];
    out[outPos + 14] = (in[inPos + 13] >>> 18 | (in[inPos + 14] & 131071) << 14) + out[outPos + 13];
    out[outPos + 15] = (in[inPos + 14] >>> 17 | (in[inPos + 15] & 65535) << 15) + out[outPos + 14];
    out[outPos + 16] = (in[inPos + 15] >>> 16 | (in[inPos + 16] & 32767) << 16) + out[outPos + 15];
    out[outPos + 17] = (in[inPos + 16] >>> 15 | (in[inPos + 17] & 16383) << 17) + out[outPos + 16];
    out[outPos + 18] = (in[inPos + 17] >>> 14 | (in[inPos + 18] & 8191) << 18) + out[outPos + 17];
    out[outPos + 19] = (in[inPos + 18] >>> 13 | (in[inPos + 19] & 4095) << 19) + out[outPos + 18];
    out[outPos + 20] = (in[inPos + 19] >>> 12 | (in[inPos + 20] & 2047) << 20) + out[outPos + 19];
    out[outPos + 21] = (in[inPos + 20] >>> 11 | (in[inPos + 21] & 1023) << 21) + out[outPos + 20];
    out[outPos + 22] = (in[inPos + 21] >>> 10 | (in[inPos + 22] & 511) << 22) + out[outPos + 21];
    out[outPos + 23] = (in[inPos + 22] >>> 9 | (in[inPos + 23] & 255) << 23) + out[outPos + 22];
    out[outPos + 24] = (in[inPos + 23] >>> 8 | (in[inPos + 24] & 127) << 24) + out[outPos + 23];
    out[outPos + 25] = (in[inPos + 24] >>> 7 | (in[inPos + 25] & 63) << 25) + out[outPos + 24];
    out[outPos + 26] = (in[inPos + 25] >>> 6 | (in[inPos + 26] & 31) << 26) + out[outPos + 25];
    out[outPos + 27] = (in[inPos + 26] >>> 5 | (in[inPos + 27] & 15) << 27) + out[outPos + 26];
    out[outPos + 28] = (in[inPos + 27] >>> 4 | (in[inPos + 28] & 7) << 28) + out[outPos + 27];
    out[outPos + 29] = (in[inPos + 28] >>> 3 | (in[inPos + 29] & 3) << 29) + out[outPos + 28];
    out[outPos + 30] = (in[inPos + 29] >>> 2 | (in[inPos + 30] & 1) << 30) + out[outPos + 29];
    out[outPos + 31] = (in[inPos + 30] >>> 1) + out[outPos + 30];
  }
}
