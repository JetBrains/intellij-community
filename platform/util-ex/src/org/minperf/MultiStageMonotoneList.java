// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf;

/**
 * This implementation uses a linear regression, and 3 levels of offsets. It is
 * much simpler and typically faster than an EliasFanoMonotoneList, but space
 * usage is not linear.
 */
@SuppressWarnings("DuplicatedCode")
final class MultiStageMonotoneList implements MonotoneList {
  public static final int SHIFT1 = 6;
  public static final int SHIFT2 = 3;
  public static final int FACTOR1 = 32;
  public static final int FACTOR2 = 16;

  private final BitBuffer buffer;
  private final int startLevel1, startLevel2, startLevel3;
  private final int bitCount1, bitCount2, bitCount3;
  private final long factor;
  private final int add;

  private MultiStageMonotoneList(BitBuffer buffer) {
    this.buffer = buffer;
    int count3 = (int)buffer.readEliasDelta() - 1;
    int diff = (int)buffer.readEliasDelta() - 1;
    this.factor = getScaleFactor(diff, count3);
    this.add = (int)BitBuffer.unfoldSigned(buffer.readEliasDelta() - 1);
    this.bitCount1 = (int)buffer.readEliasDelta() - 1;
    this.bitCount2 = (int)buffer.readEliasDelta() - 1;
    this.bitCount3 = (int)buffer.readEliasDelta() - 1;
    startLevel1 = buffer.position();
    int count2 = (count3 + (1 << SHIFT2) - 1) >> SHIFT2;
    int count1 = (count3 + (1 << SHIFT1) - 1) >> SHIFT1;
    startLevel2 = startLevel1 + count1 * bitCount1;
    startLevel3 = startLevel2 + count2 * bitCount2;
    buffer.seek(startLevel3 + bitCount3 * count3);
  }

  private static long getScaleFactor(int multiply, int divide) {
    return divide == 0 ? 0 : ((long)multiply << 32) / divide + 1;
  }

  public static MultiStageMonotoneList generate(int[] data, BitBuffer buffer) {
    int start = buffer.position();
    int count3 = data.length;
    // verify it is monotone
    for (int i = 1; i < count3; i++) {
      if (data[i - 1] > data[i]) {
        throw new IllegalArgumentException();
      }
    }
    int diff = data[count3 - 1] - data[0];
    long factor = getScaleFactor(diff, count3);
    int add = data[0];
    for (int i = 1; i < count3; i++) {
      int expected = (int)((i * factor) >>> 32);
      int x = data[i];
      add = Math.min(add, x - expected);
    }
    buffer.writeEliasDelta(count3 + 1);
    buffer.writeEliasDelta(diff + 1);
    buffer.writeEliasDelta(BitBuffer.foldSigned(add) + 1);
    int count2 = (count3 + (1 << SHIFT2) - 1) >> SHIFT2;
    int count1 = (count3 + (1 << SHIFT1) - 1) >> SHIFT1;
    int[] group1 = new int[count1];
    int[] group2 = new int[count2];
    int[] group3 = new int[count3];
    for (int i = 0; i < count3; i++) {
      // int expected = (int) (i * max / count3);
      int expected = (int)((i * factor) >>> 32) + add;
      int got = data[i];
      int x = got - expected;
      if (x < 0) {
        throw new AssertionError();
      }
      group3[i] = x;
    }
    int a = Integer.MAX_VALUE;
    for (int i = 0; i < count3; i++) {
      int x = group3[i];
      a = Math.min(a, x);
      if ((i + 1) >> SHIFT2 != i >> SHIFT2 || i == count3 - 1) {
        group2[i >> SHIFT2] = a / FACTOR2;
        a = Integer.MAX_VALUE;
      }
    }
    a = Integer.MAX_VALUE;
    for (int i = 0; i < count3; i++) {
      int d = group2[i >> SHIFT2] * FACTOR2;
      int x = group3[i];
      group3[i] -= d;
      if (group3[i] < 0) {
        throw new AssertionError();
      }
      a = Math.min(a, x);
      if ((i + 1) >> SHIFT1 != i >> SHIFT1 || i == count3 - 1) {
        group1[i >> SHIFT1] = a / FACTOR1;
        a = Integer.MAX_VALUE;
      }
    }
    int last = -1;
    for (int i = 0; i < count3; i++) {
      int i2 = i >> SHIFT2;
      if (i2 == last) {
        continue;
      }
      int d = group1[i >> SHIFT1] * FACTOR1;
      group2[i2] -= d / FACTOR2;
      last = i2;
    }
    int max1 = 0, max2 = 0, max3 = 0;
    for (int value : group3) {
      max3 = Math.max(max3, value);
    }
    for (int k : group2) {
      max2 = Math.max(max2, k);
    }
    for (int j : group1) {
      max1 = Math.max(max1, j);
    }
    int bitCount1 = 32 - Integer.numberOfLeadingZeros(max1);
    int bitCount2 = 32 - Integer.numberOfLeadingZeros(max2);
    int bitCount3 = 32 - Integer.numberOfLeadingZeros(max3);
    buffer.writeEliasDelta(bitCount1 + 1);
    buffer.writeEliasDelta(bitCount2 + 1);
    buffer.writeEliasDelta(bitCount3 + 1);
    for (int x : group1) {
      buffer.writeNumber(x, bitCount1);
    }
    for (int x : group2) {
      buffer.writeNumber(x, bitCount2);
    }
    for (int x : group3) {
      buffer.writeNumber(x, bitCount3);
    }
    buffer.seek(start);
    return new MultiStageMonotoneList(buffer);
  }

  public static int getSize(int[] data) {
    int result = 0;
    int count3 = data.length;
    // verify it is monotone
    for (int i = 1; i < count3; i++) {
      if (data[i - 1] > data[i]) {
        throw new IllegalArgumentException();
      }
    }
    int diff = data[count3 - 1] - data[0];
    long factor = getScaleFactor(diff, count3);
    int add = data[0];
    for (int i = 1; i < count3; i++) {
      int expected = (int)((i * factor) >>> 32);
      int x = data[i];
      add = Math.min(add, x - expected);
    }
    result += BitBuffer.getEliasDeltaSize(count3 + 1);
    result += BitBuffer.getEliasDeltaSize(diff + 1);
    result += BitBuffer.getEliasDeltaSize(BitBuffer.foldSigned(add) + 1);
    int count2 = (count3 + (1 << SHIFT2) - 1) >> SHIFT2;
    int count1 = (count3 + (1 << SHIFT1) - 1) >> SHIFT1;
    int[] group1 = new int[count1];
    int[] group2 = new int[count2];
    int[] group3 = new int[count3];
    for (int i = 0; i < count3; i++) {
      // int expected = (int) (i * max / count3);
      int expected = (int)((i * factor) >>> 32) + add;
      int got = data[i];
      int x = got - expected;
      if (x < 0) {
        throw new AssertionError();
      }
      group3[i] = x;
    }
    int a = Integer.MAX_VALUE;
    for (int i = 0; i < count3; i++) {
      int x = group3[i];
      a = Math.min(a, x);
      if ((i + 1) >> SHIFT2 != i >> SHIFT2 || i == count3 - 1) {
        group2[i >> SHIFT2] = a / FACTOR2;
        a = Integer.MAX_VALUE;
      }
    }
    a = Integer.MAX_VALUE;
    for (int i = 0; i < count3; i++) {
      int d = group2[i >> SHIFT2] * FACTOR2;
      int x = group3[i];
      group3[i] -= d;
      if (group3[i] < 0) {
        throw new AssertionError();
      }
      a = Math.min(a, x);
      if ((i + 1) >> SHIFT1 != i >> SHIFT1 || i == count3 - 1) {
        group1[i >> SHIFT1] = a / FACTOR1;
        a = Integer.MAX_VALUE;
      }
    }
    int last = -1;
    for (int i = 0; i < count3; i++) {
      int i2 = i >> SHIFT2;
      if (i2 == last) {
        continue;
      }
      int d = group1[i >> SHIFT1] * FACTOR1;
      group2[i2] -= d / FACTOR2;
      last = i2;
    }
    int max1 = 0, max2 = 0, max3 = 0;
    for (int value : group3) {
      max3 = Math.max(max3, value);
    }
    for (int k : group2) {
      max2 = Math.max(max2, k);
    }
    for (int j : group1) {
      max1 = Math.max(max1, j);
    }
    int bitCount1 = 32 - Integer.numberOfLeadingZeros(max1);
    int bitCount2 = 32 - Integer.numberOfLeadingZeros(max2);
    int bitCount3 = 32 - Integer.numberOfLeadingZeros(max3);
    result += BitBuffer.getEliasDeltaSize(bitCount1 + 1);
    result += BitBuffer.getEliasDeltaSize(bitCount2 + 1);
    result += BitBuffer.getEliasDeltaSize(bitCount3 + 1);
    result += bitCount1 * group1.length;
    result += bitCount2 * group2.length;
    result += bitCount3 * group3.length;
    return result;
  }

  public static MultiStageMonotoneList load(BitBuffer buffer) {
    return new MultiStageMonotoneList(buffer);
  }

  @Override
  public int get(int i) {
    int expected = (int)((i * factor) >>> 32) + add;
    long a = buffer.readNumber(startLevel1 + (long)(i >>> SHIFT1) * bitCount1, bitCount1);
    long b = buffer.readNumber(startLevel2 + (long)(i >>> SHIFT2) * bitCount2, bitCount2);
    long c = buffer.readNumber(startLevel3 + (long)i * bitCount3, bitCount3);
    return (int)(expected + a * FACTOR1 + b * FACTOR2 + c);
  }

  @Override
  public long getPair(int i) {
    return ((long)get(i) << 32) | (get(i + 1));
  }
}
