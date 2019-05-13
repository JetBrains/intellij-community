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
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntProcedure;

import java.util.NoSuchElementException;

public class EmptyIntHashSet extends TIntHashSet {
  public static final TIntHashSet INSTANCE = new EmptyIntHashSet();
  public static final TIntIterator EMPTY_INT_ITERATOR = new TIntIterator(INSTANCE) {
    @Override
    public int next() {
      throw new NoSuchElementException();
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public void remove() {
      throw new NoSuchElementException();
    }
  };

  private EmptyIntHashSet() {
    super(0);
  }

  @Override
  public boolean add(int val) {
    throw new IncorrectOperationException();
  }

  @Override
  public int[] toArray() {
    return ArrayUtil.EMPTY_INT_ARRAY;
  }

  @Override
  public TIntIterator iterator() {
    return EMPTY_INT_ITERATOR;
  }

  @Override
  public void clear() {
    throw new IncorrectOperationException();
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean remove(int val) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean containsAll(int[] array) {
    return false;
  }

  @Override
  public boolean addAll(int[] array) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean removeAll(int[] array) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean retainAll(int[] array) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean contains(int val) {
    return false;
  }

  @Override
  public boolean forEach(TIntProcedure procedure) {
    return true;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public void compact() {
    throw new IncorrectOperationException();
  }

  @Override
  public String toString() {
    return "Empty Int Hash Set";
  }
}
