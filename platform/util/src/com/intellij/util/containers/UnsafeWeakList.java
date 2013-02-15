/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Implementation of the {@link java.util.List} interface which:
 * <ul>
 *  <li>Stores elements using weak semantics (see {@link java.lang.ref.WeakReference})</li>
 *  <li>Automatically reclaims storage for garbage collected elements</li>
 *  <li>Is NOT thread safe</li>
 * </ul>
 */
public class UnsafeWeakList<T> extends AbstractList<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.containers.UnsafeWeakList");
  protected final WeakReferenceArray<T> myArray;

  public UnsafeWeakList() {
    this(new WeakReferenceArray<T>());
  }

  // For testing only
  UnsafeWeakList(@NotNull WeakReferenceArray<T> array) {
    myArray = array;
  }

  @Override
  public T get(int index) {
    return myArray.get(index);
  }

  @Override
  public boolean add(T element) {
    tryReduceCapacity(-1);
    myArray.add(element);
    return true;
  }

  @Override
  public boolean contains(Object o) {
    return super.contains(o);
  }

  public boolean addIfAbsent(T element) {
    tryReduceCapacity(-1);
    if (contains(element)) return false;
    myArray.add(element);
    return true;
  }

  @Override
  public void add(int index, T element) {
    tryReduceCapacity(-1);
    myArray.add(index, element);
  }

  @Override
  public T remove(int index) {
    tryReduceCapacity(-1);
    return myArray.remove(index);
  }

  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    for (int i = fromIndex; i < toIndex; i++) {
      myArray.remove(i);
    }
    tryReduceCapacity(-1);
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return new MyIterator();
  }

  @Override
  public int size() {
    return myArray.size();
  }

  public void clear(int index) {
    myArray.removeReference(index);
  }

  public List<T> toStrongList() {
    List<T> result = new ArrayList<T>(myArray.size());
    myArray.toStrongCollection(result);
    return result;
  }

  private int tryReduceCapacity(int trackIndex) {
    modCount++;
    if (canReduceCapacity()) {
      return myArray.reduceCapacity(trackIndex);
    }
    else {
      return probablyCompress(trackIndex);
    }
  }

  private int myCompressCountdown = 10;
  private int probablyCompress(int trackIndex) {
    myCompressCountdown--;
    if (myCompressCountdown > 0) return trackIndex;
    int newIndex = myArray.compress(trackIndex);
    myCompressCountdown = myArray.size() + 10;
    return newIndex;
  }

  private boolean canReduceCapacity() {
    return WeakReferenceArray.MINIMUM_CAPACITY * 2 < myArray.getCapacity() &&
           myArray.getCapacity() > myArray.getAliveCount() * 3;
  }

  protected class MyIterator implements Iterator<T> {
    private int myNextIndex = -1;
    private int myCurrentIndex = -1;
    private T myNextElement = null;
    private int myModCount = modCount;

    public MyIterator() {
      findNext();
    }

    protected void findNext() {
      myNextElement = null;
      while (myNextElement == null) {
        myNextIndex = myArray.nextValid(myNextIndex);
        if (myNextIndex >= myArray.size()) {
          myNextIndex = -1;
          myNextElement = null;
          return;
        }
        myNextElement = myArray.get(myNextIndex);
      }
    }

    @Override
    public boolean hasNext() {
      return myNextElement != null;
    }

    @Override
    public T next() {
      if (modCount != myModCount) throw new ConcurrentModificationException();
      if (myNextElement == null) throw new NoSuchElementException();
      T element = myNextElement;
      myCurrentIndex = myNextIndex;
      findNext();
      return element;
    }

    @Override
    public void remove() {
      if (myCurrentIndex == -1) throw new IllegalStateException();
      myArray.remove(myCurrentIndex);
      final int removedIndex = myCurrentIndex;
      int newIndex = tryReduceCapacity(myNextIndex);
      myCurrentIndex = -1;
      myModCount = modCount;
      if (!hasNext()) return;
      if (newIndex < 0) {
        LOG.error(" was: " + myNextIndex +
                  " got: " + newIndex +
                  " size: " + myArray.size() +
                  " current: " + removedIndex);
      }
      myNextIndex = newIndex;
      LOG.assertTrue(myArray.get(myNextIndex) == myNextElement);
    }
  }
}
