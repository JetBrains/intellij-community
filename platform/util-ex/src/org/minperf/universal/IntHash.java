// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.minperf.universal;

import com.intellij.util.io.Murmur3_32Hash;

/**
 * A sample hash implementation for long keys.
 */
public final class IntHash implements UniversalHash<Integer> {
  public static int universalHash(int x, long index) {
    return Murmur3_32Hash.hashInt(x, (int)index);
  }

  @Override
  public long universalHash(Integer key, long index) {
    return universalHash((int)key, index);
  }

  @Override
  public String toString() {
    return "IntHash (Murmur3)";
  }
}