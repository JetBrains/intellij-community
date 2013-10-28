/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.List;

public class WeakReferenceArray<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.containers.WeakReferenceArray");

  static final int MINIMUM_CAPACITY = 5;
  private final ReferenceQueue<T> myQueue = new TReferenceQueue<T>();
  private MyWeakReference[] myReferences;
  private int mySize = 0;
  private int myCorpseCounter = 0;

  public WeakReferenceArray() {
    this(MINIMUM_CAPACITY);
  }

  public WeakReferenceArray(int size) {
    myReferences = new MyWeakReference[size];
  }

  public T remove(int index) {
    checkRange(index);
    T result = getImpl(index);
    removeReference(index);
    return result;
  }

  private void checkRange(int index) {
    if (index >= mySize) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }
  }

  public int getCorpseCount() {
    flushQueue();
    return myCorpseCounter;
  }

  private void flushQueue() {
    Reference nextRef;
    while ((nextRef = myQueue.poll()) != null) {
      if (!(nextRef instanceof MyWeakReference)) continue; // With 1.4 sometimes queue contains other references ?!?
      MyWeakReference reference = (MyWeakReference)nextRef;
      reference.setNull(myReferences);
      myCorpseCounter++;
    }
  }

  public void add(T object) {
    ensureCapacity(mySize + 1);
    MyWeakReference.createAt(myReferences, mySize, object, myQueue);
    mySize++;
  }

  public void add(int index, T element) {
    ensureCapacity(mySize + 1);
    if (index < 0 || index > mySize) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }
    for (int i = mySize - 1; i >= index; i--) {
      MyWeakReference aliveReference = MyWeakReference.getFrom(myReferences, i);
      if (aliveReference != null) {
        aliveReference.putTo(myReferences, i + 1);
      }
    }
    MyWeakReference.createAt(myReferences, index, element, myQueue);
    mySize++;
  }

  private void ensureCapacity(int size) {
    if (size != 0 && myReferences.length < MINIMUM_CAPACITY) {
      growTo(Math.max(MINIMUM_CAPACITY, size));
      return;
    }
    if (size <= myReferences.length) return;
    if (size - myReferences.length <= getCorpseCount()) {
      compress(-1);
      if (mySize < myReferences.length - 1) return;
    }
    int newCapacity = 2 * myReferences.length;
    growTo(newCapacity);
  }

  private void growTo(int newCapacity) {
    MyWeakReference[] references = new MyWeakReference[newCapacity];
    System.arraycopy(myReferences, 0, references, 0, myReferences.length);
    myReferences = references;
  }

  public int size() {
    return mySize;
  }

  public int compress(int trackIndex) {
    if (getCorpseCount() == 0) return trackIndex;
    return doCompress(myReferences, trackIndex);
  }

  private int doCompress(MyWeakReference[] newReferences, int trackIndex) {
    myCorpseCounter = 0;
    int validIndex = 0;
    int newIndex = -1;
    boolean trackingDone = false;
    for (int i = nextValid(-1); i < size(); i = nextValid(i)) {
      if (!trackingDone) {
        if (i == trackIndex) {
          newIndex = validIndex;
          trackingDone = true;
        }
        if (i > trackIndex) {
          newIndex = -validIndex - 1;
          trackingDone = true;
        }
      }
      MyWeakReference aliveReference = MyWeakReference.getFrom(myReferences, i);
      if (validIndex < i) {
        performRemoveAt(validIndex);
        //myReferences[i] = null;
      }
      else {
        LOG.assertTrue(validIndex == i);
      }
      aliveReference.moveTo(myReferences, newReferences, validIndex);
      validIndex++;
    }

    if (newIndex == -1) newIndex = -validIndex - 1;
    for (int i = validIndex; i < mySize; i++) {
      performRemoveAt(i);
    }
    for (int i = validIndex; i < myReferences.length; i++) {
      LOG.assertTrue(myReferences[i] == null);
    }

    flushQueue();
    mySize = validIndex;

    return newIndex;
  }

  private void performRemoveAt(int index) {
    if (removeReference(index)) {
      myCorpseCounter--;
      flushQueue();
      if (myCorpseCounter < 0) LOG.error(String.valueOf(myCorpseCounter));
    }
  }

  int nextValid(int index) {
    index++;
    while (index < size()) {
      if (getImpl(index) != null) return index;
      index++;
    }
    return size();
  }

  private T getImpl(int index) {
    final MyWeakReference<T> reference = MyWeakReference.getFrom(myReferences, index);
    return reference == null ? null : reference.get();
  }

  public int getCapacity() {
    return myReferences.length;
  }

  public T get(int index) {
    checkRange(index);
    return getImpl(index);
  }

  public int reduceCapacity(int trackIndex) {
    int aliveSize = getNotBuriedCount();
    if (myReferences.length / 4 >= aliveSize) {
      MyWeakReference[] references = new MyWeakReference[aliveSize * 2];
      int newIndex = doCompress(references, trackIndex);
      myReferences = references;
      return newIndex;
    }
    return trackIndex;
  }

  private int getNotBuriedCount() {
    flushQueue();
    int counter = 0;
    for (MyWeakReference myReference : myReferences) {
      if (myReference != null) counter++;
    }
    return counter;
  }

  public int getAliveCount() {
    return size() - getCorpseCount();
  }

  // For testing only
  WeakReference[] getReferences() {
    return myReferences;
  }

  boolean removeReference(int index) {
    MyWeakReference reference = MyWeakReference.getFrom(myReferences, index);
    return reference != null && reference.removeFrom(myReferences);
  }

  public void toStrongCollection(final List<T> result) {
    for (MyWeakReference reference : myReferences) {
      final T deref = reference != null ? (T)reference.get() : null;
      if (deref != null) {
        result.add(deref);
      }
    }
  }

  private static class MyWeakReference<E> extends WeakReference<E> {
    private int myIndex = -1;

    private MyWeakReference(E e, ReferenceQueue<E> referenceQueue) {
      super(e, referenceQueue);
    }

    public static <E> void createAt(MyWeakReference[] array, int index, E element, ReferenceQueue<E> queue) {
      new MyWeakReference<E>(element, queue).putTo(array, index);
    }

    public static <E> MyWeakReference<E> getFrom(MyWeakReference[] array, int index) {
      MyWeakReference<E> reference = array[index];
      if (reference == null) {
        return null;
      }
      LOG.assertTrue(index == reference.myIndex);
      return reference;
    }

    private void putTo(MyWeakReference[] array, int index) {
      array[index] = this;
      myIndex = index;
    }

    public boolean removeFrom(MyWeakReference[] array) {
      LOG.assertTrue(array[myIndex] == this);
      clear();
      array[myIndex] = null;
      myIndex = -1;
      return enqueue();
    }

    public void moveTo(MyWeakReference[] fromArray, MyWeakReference[] toArray, int newIndex) {
      LOG.assertTrue(fromArray[myIndex] == this);
      fromArray[myIndex] = null;
      LOG.assertTrue(toArray[newIndex] == null);
      toArray[newIndex] = this;
      myIndex = newIndex;
    }

    public void setNull(MyWeakReference[] array) {
      LOG.assertTrue(get() == null);
      if (myIndex == -1) return;
      LOG.assertTrue(array[myIndex] == this);
      array[myIndex] = null;
    }
  }

  private static class TReferenceQueue<T> extends ReferenceQueue<T> {
    @Override
    public Reference<? extends T> poll() {
      Reference<? extends T> reference = super.poll();
      return reference;
    }
  }
}
