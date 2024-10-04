/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.utils;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

@ApiStatus.Internal
public class UnsignedBitSet implements Cloneable {
  @NotNull private final BitSet myPositiveSet;
  @NotNull private final BitSet myNegativeSet;

  public UnsignedBitSet(@NotNull BitSet positiveSet, @NotNull BitSet negativeSet) {
    myPositiveSet = positiveSet;
    myNegativeSet = negativeSet;
  }

  public UnsignedBitSet() {
    this(new BitSet(), new BitSet());
  }

  private static int toPositive(int bitIndex) {
    return -bitIndex;
  }

  public void set(int bitIndex, boolean value) {
    if (bitIndex >= 0) {
      myPositiveSet.set(bitIndex, value);
    }
    else {
      myNegativeSet.set(toPositive(bitIndex), value);
    }
  }

  // boundaries included
  public void set(int fromIndex, int toIndex, boolean value) {
    if (fromIndex > toIndex) {
      throw new IndexOutOfBoundsException("fromIndex: " + fromIndex +
                                          " > toIndex: " + toIndex);
    }
    if (fromIndex >= 0) {
      myPositiveSet.set(fromIndex, toIndex + 1, value);
    }
    else if (toIndex < 0) {
      myNegativeSet.set(toPositive(toIndex), toPositive(fromIndex) + 1, value);
    }
    else {
      myPositiveSet.set(0, toIndex + 1, value);
      myNegativeSet.set(toPositive(-1), toPositive(fromIndex) + 1, value);
    }
  }

  public boolean get(int bitIndex) {
    if (bitIndex >= 0) {
      return myPositiveSet.get(bitIndex);
    }
    else {
      return myNegativeSet.get(toPositive(bitIndex));
    }
  }

  @Override
  public UnsignedBitSet clone() {
    return new UnsignedBitSet((BitSet)myPositiveSet.clone(), (BitSet)myNegativeSet.clone());
  }
}
