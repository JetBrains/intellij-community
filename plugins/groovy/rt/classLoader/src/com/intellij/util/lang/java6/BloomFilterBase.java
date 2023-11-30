// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang.java6;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

public class BloomFilterBase {
  private final int myHashFunctionCount;
  private final int myBitsCount;
  private final long[] elementSet;
  private static final int BITS_PER_ELEMENT = 6;

  protected BloomFilterBase(int _maxElementCount, double probability) {
    int bitsPerElementFactor = (int)Math.ceil(-Math.log(probability) / (Math.log(2) * Math.log(2)));
    myHashFunctionCount = (int)Math.ceil(bitsPerElementFactor * Math.log(2));

    int bitsCount = _maxElementCount * bitsPerElementFactor;

    if ((bitsCount & 1) == 0) {
      ++bitsCount;
    }
    while (!isPrime(bitsCount)) {
      bitsCount += 2;
    }
    myBitsCount = bitsCount;
    elementSet = new long[(bitsCount >> BITS_PER_ELEMENT) + 1];
  }

  private static boolean isPrime(int bits) {
    if ((bits & 1) == 0 || bits % 3 == 0) {
      return false;
    }
    int sqrt = (int)Math.sqrt(bits);
    for (int i = 6; i <= sqrt; i += 6) {
      if (bits % (i - 1) == 0 || bits % (i + 1) == 0) {
        return false;
      }
    }
    return true;
  }

  protected final void addIt(int prime, int prime2) {
    for (int i = 0; i < myHashFunctionCount; ++i) {
      int abs = Math.abs((i * prime + prime2 * (myHashFunctionCount - i)) % myBitsCount);
      elementSet[abs >> BITS_PER_ELEMENT] |= (1L << abs);
    }
  }

  protected final boolean maybeContains(int prime, int prime2) {
    for(int i = 0; i < myHashFunctionCount; ++i) {
      int abs = Math.abs((i * prime + prime2 * (myHashFunctionCount - i)) % myBitsCount);
      if ((elementSet[abs >> BITS_PER_ELEMENT] & (1L << abs)) == 0) {
        return false;
      }
    }

    return true;
  }

  protected BloomFilterBase(@NotNull DataInput input) throws IOException {
    myHashFunctionCount = DataInputOutputUtilRt.readINT(input);
    myBitsCount = DataInputOutputUtilRt.readINT(input);
    elementSet = new long[(myBitsCount >> BITS_PER_ELEMENT) + 1];

    for (int i = 0; i < elementSet.length; i++) {
      elementSet[i] = input.readLong();
    }
  }

  public void save(@NotNull ByteBuffer buffer) {
    buffer.putInt(myHashFunctionCount);
    buffer.putInt(myBitsCount);
    buffer.asLongBuffer().put(elementSet);
    buffer.position(buffer.position() + elementSet.length * 8);
  }

  protected void save(@NotNull DataOutput output) throws IOException {
    DataInputOutputUtilRt.writeINT(output, myHashFunctionCount);
    DataInputOutputUtilRt.writeINT(output, myBitsCount);
    for (long l : elementSet) {
      output.writeLong(l);
    }
  }
}
