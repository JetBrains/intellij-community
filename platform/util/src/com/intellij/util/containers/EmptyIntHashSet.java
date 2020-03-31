// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtilRt;
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
    return ArrayUtilRt.EMPTY_INT_ARRAY;
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
