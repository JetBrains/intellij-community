// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import org.jetbrains.annotations.NotNull;

public class Enumerator<T> {
  private static final Logger LOG = Logger.getInstance(Enumerator.class);
  private final TObjectIntHashMap<T> myNumbers;
  private int myNextNumber = 1;

  public Enumerator(int expectNumber, @NotNull TObjectHashingStrategy<T> strategy) {
    myNumbers = new TObjectIntHashMap<>(expectNumber, strategy);
  }

  public void clear() {
    myNumbers.clear();
    myNextNumber = 1;
  }

  public int @NotNull [] enumerate(T @NotNull [] objects) {
    return enumerate(objects, 0, 0);
  }

  public int @NotNull [] enumerate(T @NotNull [] objects, final int startShift, final int endCut) {
    int[] idx = ArrayUtil.newIntArray(objects.length - startShift - endCut);
    for (int i = startShift; i < objects.length - endCut; i++) {
      final T object = objects[i];
      final int number = enumerate(object);
      idx[i - startShift] = number;
    }
    return idx;
  }

  public int enumerate(T object) {
    final int res = enumerateImpl(object);
    return Math.max(res, -res);
  }

  public boolean add(T object) {
    final int res = enumerateImpl(object);
    return res < 0;
  }

  public int enumerateImpl(T object) {
    if( object == null ) return 0;

    int number = myNumbers.get(object);
    if (number == 0) {
      number = myNextNumber++;
      myNumbers.put(object, number);
      return -number;
    }
    return number;
  }

  public boolean contains(@NotNull T object) {
    return myNumbers.get(object) != 0;
  }

  public int get(T object) {
    if (object == null) return 0;
    final int res = myNumbers.get(object);

    if (res == 0)
      LOG.error( "Object "+ object + " must be already added to enumerator!" );

    return res;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    for (TObjectIntIterator<T> iter = myNumbers.iterator(); iter.hasNext(); ) {
      iter.advance();
      buffer.append(iter.value()).append(": ").append(iter.key()).append("\n");
    }
    return buffer.toString();
  }
}
