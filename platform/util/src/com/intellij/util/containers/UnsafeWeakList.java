/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.Condition;
import com.intellij.reference.SoftReference;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Implementation of the {@link Collection} interface which:
 * <ul>
 *  <li>Stores elements using weak semantics (see {@link WeakReference})</li>
 *  <li>Automatically reclaims storage for garbage collected elements</li>
 *  <li>Is NOT thread safe</li>
 *  <li>Is NOT RandomAccess, because garbage collector can remove element at any time</li>
 *  <li>Does NOT support null elements</li>
 * </ul>
 */
public class UnsafeWeakList<T> extends AbstractCollection<T> {
  protected final List<MyReference<T>> myList;
  private final ReferenceQueue<T> myQueue = new ReferenceQueue<T>();
  private int myAlive;
  private int modCount;

  private static class MyReference<T> extends WeakReference<T> {
    private final int index;

    private MyReference(int index, T referent, ReferenceQueue<? super T> queue) {
      super(referent, queue);
      this.index = index;
    }
  }

  public UnsafeWeakList() {
    myList = new ArrayList<MyReference<T>>();
  }

  public UnsafeWeakList(int capacity) {
    myList = new ArrayList<MyReference<T>>(capacity);
  }

  boolean processQueue() {
    boolean processed = false;
    MyReference<T> reference;
    while ((reference = (MyReference<T>)myQueue.poll()) != null) {
      int index = reference.index;

      if (index < myList.size() && reference == myList.get(index)) { // list may have changed while the reference was dangling in queue
        nullizeAt(index);
      }
      processed = true;
    }
    if (myAlive < myList.size() / 2) {
      reduceCapacity();
    }
    return processed;
  }

  private void nullizeAt(int index) {
    myList.set(index, null);
    myAlive--;
    // do not incr modCount here because every iterator().remove() usages will throw
  }

  private void reduceCapacity() {
    int toSaveAlive = 0;
    for (int i=0; i<myList.size();i++) {
      MyReference<T> reference = myList.get(i);
      if (reference == null) continue;
      T t = reference.get();
      if (t == null) {
        myAlive--;
        continue;
      }
      if (toSaveAlive != i) {
        myList.set(toSaveAlive, new MyReference<T>(toSaveAlive, t, myQueue));
      }
      toSaveAlive++;
    }
    if (toSaveAlive != myList.size()) {
      myList.subList(toSaveAlive, myList.size()).clear();
      modCount++;
    }
    myAlive = toSaveAlive;
  }

  private void append(@NotNull T element) {
    myList.add(new MyReference<T>(myList.size(), element, myQueue));
    myAlive++;
    modCount++;
  }

  @Override
  public boolean add(@NotNull T element) {
    processQueue();
    append(element);
    return true;
  }

  public boolean addIfAbsent(@NotNull T element) {
    processQueue();
    if (contains(element)) return false;
    append(element);
    return true;
  }

  @Override
  public void clear() {
    processQueue();
    myList.clear();
    myAlive = 0;
    modCount++;
  }

  @TestOnly
  int listSize() {
    return myList.size();
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return new MyIterator();
  }
  private class MyIterator implements Iterator<T> {
    private final int startModCount;
    private int curIndex;
    private T curElement;

    private int nextIndex = -1;
    private T nextElement;
    private boolean modified; // set this flag on modification and update modCount in the very end of iteration to avoid CME on each remove()

    private MyIterator() {
      startModCount = modCount;
      findNext();
    }

    private void findNext() {
      if (modCount != startModCount) throw new ConcurrentModificationException();
      curIndex = nextIndex;
      curElement = nextElement;
      nextElement = null;
      nextIndex = -1;
      for (int i= curIndex +1; i<myList.size();i++) {
        T t = SoftReference.dereference(myList.get(i));
        if (t != null) {
          nextElement = t;
          nextIndex = i;
          break;
        }
      }
      if (nextIndex == -1 && modified) {
        modCount++;
      }
    }

    @Override
    public boolean hasNext() {
      return nextElement != null;
    }

    @Override
    public T next() {
      if (!hasNext()) throw new NoSuchElementException();
      findNext();
      return curElement;
    }

    @Override
    public void remove() {
      if (curElement == null) throw new NoSuchElementException();
      int index = curIndex;
      nullizeAt(index);
      modified = true;
    }
  }

  @Override
  public boolean remove(@NotNull Object o) {
    processQueue();
    for (int i = 0; i < myList.size(); i++) {
      T t = SoftReference.dereference(myList.get(i));
      if (t != null && t.equals(o)) {
        nullizeAt(i);
        modCount++;
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends T> c) {
    processQueue();
    return super.addAll(c);
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    processQueue();
    return super.removeAll(c);
  }

  private static final Function<MyReference<Object>, Object> DEREF = new Function<MyReference<Object>, Object>() {
    @Override
    public Object fun(MyReference<Object> reference) {
      return SoftReference.dereference(reference);
    }
  };
  private static <X> Function<MyReference<X>, X> deref() {
    //noinspection unchecked
    return (Function)DEREF;
  }
  @NotNull
  public List<T> toStrongList() {
    return ContainerUtil.mapNotNull(myList, UnsafeWeakList.<T>deref());
  }

  @Override
  @Deprecated
  public int size() {
    throwNotAllowedException();
    return -1;
  }

  private static void throwNotAllowedException() {
    throw new IncorrectOperationException("index/size-based operations in UnsafeWeakList are not supported because they don't make sense in the presence of weak references. Use .iterator() (which retains its elements to avoid sudden GC) instead.");
  }

  @Override
  public boolean isEmpty() {
    if (myList.isEmpty()) return true;
    Condition<MyReference<T>> notNull = notNull();
    return ContainerUtil.find(myList, notNull) == null;
  }

  private static <T> Condition<MyReference<T>> notNull() {
    //noinspection unchecked
    return (Condition)NOT_NULL;
  }
  private static final Condition<MyReference<Object>> NOT_NULL = new Condition<MyReference<Object>>() {
    @Override
    public boolean value(MyReference<Object> reference) {
      return SoftReference.dereference(reference) != null;
    }
  };

  // (*@#ing plugins
  @Deprecated
  public T get(int index) {
    throwNotAllowedException();
    return null;
  }
}
