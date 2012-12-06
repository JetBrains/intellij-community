/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

/**
 * @author dyoma
 */
public class Enumerator<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.containers.Enumerator");
  private final TObjectIntHashMap<T> myNumbers;
  private int myNextNumber = 1;

  public Enumerator(int expectNumber, TObjectHashingStrategy<T> strategy) {
    myNumbers = new TObjectIntHashMap<T>(expectNumber, strategy);
  }

  public void clear() {
    myNumbers.clear();
    myNextNumber = 1;
  }

  public int[] enumerate(T[] objects) {
    return enumerate(objects, 0, 0);
  }

  public int[] enumerate(T[] objects, final int startShift, final int endCut) {
    int[] idx = ArrayUtil.newIntArray(objects.length - startShift - endCut);
    for (int i = startShift; i < (objects.length - endCut); i++) {
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

  public int get(T object) {
    if (object == null) return 0;
    final int res = myNumbers.get(object);

    if (res == 0)
      LOG.error( "Object "+ object + " must be already added to enumerator!" );

    return res;
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();
    for( TObjectIntIterator<T> iter = myNumbers.iterator(); iter.hasNext(); ) {
      iter.advance();
      buffer.append(Integer.toString(iter.value()) + ": " + iter.key().toString() + "\n");
    }
    return buffer.toString();
  }
}
