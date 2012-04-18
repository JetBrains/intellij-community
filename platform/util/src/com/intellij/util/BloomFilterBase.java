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
package com.intellij.util;

public class BloomFilterBase {
  private final int myHashFunctionCount;
  private final int myBitsCount;
  private final long[] myElementsSet;
  private static final int BITS_PER_ELEMENT = 6;

  protected BloomFilterBase(int _maxElementCount, double probability) {
    int bitsPerElementFactor = (int)Math.ceil(-Math.log(probability) / (Math.log(2) * Math.log(2)));
    myHashFunctionCount = (int)Math.ceil(bitsPerElementFactor * Math.log(2));

    int bitsCount = _maxElementCount * bitsPerElementFactor;

    if ((bitsCount & 1) == 0) ++bitsCount;
    while(!isPrime(bitsCount))  bitsCount += 2;
    myBitsCount = bitsCount;
    myElementsSet = new long[(bitsCount >> BITS_PER_ELEMENT) + 1];
  }

  private static boolean isPrime(int bits) {
    if ((bits & 1) == 0 || bits % 3 == 0) return false;
    int sqrt = (int)Math.sqrt(bits);
    for(int i = 6; i <= sqrt; i += 6) {
      if (bits % (i - 1) == 0 || bits % (i + 1) == 0) return false;
    }
    return true;
  }

  protected final void addIt(int prime, int prime2) {
    for(int i = 0; i < myHashFunctionCount; ++i) {
      int abs = Math.abs(i * prime + prime2 * (myHashFunctionCount - i)) % myBitsCount;
      myElementsSet[abs >> BITS_PER_ELEMENT] |= (1L << abs);
    }
  }

  protected final boolean maybeContains(int prime, int prime2) {
    for(int i = 0; i < myHashFunctionCount; ++i) {
      int abs = Math.abs(i * prime + prime2 * (myHashFunctionCount - i)) % myBitsCount;
      if ((myElementsSet[abs >> BITS_PER_ELEMENT] & (1L << abs)) == 0) return false;
    }

    return true;
  }
}
