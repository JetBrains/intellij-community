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

package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.Flags;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

public class BitSetFlags implements Flags {

  private final int mySize;

  @NotNull private final BitSet myBitSet;

  // default value is false
  public BitSetFlags(int size) {
    if (size < 0) throw new NegativeArraySizeException("size < 0: " + size);
    mySize = size;
    myBitSet = new BitSet();
  }

  public BitSetFlags(int size, boolean defaultValue) {
    this(size);
    if (defaultValue) setAll(true);
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public boolean get(int index) {
    checkRange(index);
    return myBitSet.get(index);
  }

  @Override
  public void set(int index, boolean value) {
    checkRange(index);
    myBitSet.set(index, value);
  }

  @Override
  public void setAll(boolean value) {
    myBitSet.set(0, mySize, value);
  }

  private void checkRange(int index) {
    if (index < 0) throw new IndexOutOfBoundsException("index is " + index + " which is less then zero");
    if (index >= mySize) throw new IndexOutOfBoundsException("index is " + index + " and set size is " + mySize);
  }


}
