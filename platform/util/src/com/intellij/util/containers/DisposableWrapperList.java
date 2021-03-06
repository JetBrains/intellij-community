/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtilRt;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Similar to a list returned by {@link ContainerUtil#createLockFreeCopyOnWriteList} with additional methods that allow
 * some elements of the list to be associated with {@link Disposable} objects. When these disposable objects are disposed,
 * the associated elements are removed from the list.
 *
 * @param <E> the type of elements held in this list
 */
public final class DisposableWrapperList<E> extends AbstractList<E> {
  @NotNull private final List<DisposableWrapper> myWrappedList = ContainerUtil.createLockFreeCopyOnWriteList();

  public DisposableWrapperList() {
  }

  @Override
  public boolean add(@NotNull E element) {
    return myWrappedList.add(new DisposableWrapper(element));
  }

  @Override
  public void add(int index, @NotNull E element) {
    myWrappedList.add(index, new DisposableWrapper(element));
  }

  /**
   * Appends the specified element to the end of the list with automatic removal controlled by a given {@link Disposable}.
   *
   * @param element element to be added to the list
   * @param parentDisposable triggers removal of the element from the list when disposed
   * @return the disposable object representing the added element. Disposal of this object removes the element from
   *     the list. Conversely, removal of the element from the list triggers disposal of its disposable object.
   */
  @NotNull
  public Disposable add(@NotNull E element, @NotNull Disposable parentDisposable) {
    DisposableWrapper disposableWrapper = createDisposableWrapper(element, parentDisposable);
    myWrappedList.add(disposableWrapper);
    return disposableWrapper;
  }

  /**
   * Inserts the specified element at the specified position in the list with automatic removal controlled by a given {@link Disposable}.
   *
   * @param index index at which the specified element is to be inserted
   * @param element element to be inserted to the list
   * @param parentDisposable triggers removal of the element from the list when disposed
   * @return the disposable object representing the added element. Disposal of this object removes the element from
   *     the list. Conversely, removal of the element from the list triggers disposal of its disposable object.
   */
  @NotNull
  public Disposable add(int index, @NotNull E element, @NotNull Disposable parentDisposable) {
    DisposableWrapper disposableWrapper = createDisposableWrapper(element, parentDisposable);
    myWrappedList.add(index, disposableWrapper);
    return disposableWrapper;
  }


  @Override
  public boolean addAll(@NotNull Collection<? extends E> collection) {
    Collection<DisposableWrapper> disposableWrappers = wrapAll(collection);
    return myWrappedList.addAll(disposableWrappers);
  }

  @Override
  public boolean addAll(int index, @NotNull Collection<? extends E> collection) {
    Collection<DisposableWrapper> disposableWrappers = wrapAll(collection);
    return myWrappedList.addAll(index, disposableWrappers);
  }

  @Override
  public boolean remove(@Nullable Object obj) {
    List<DisposableWrapper> removedWrappers = new ArrayList<>(1);
    boolean result = myWrappedList.removeIf(disposableWrapper -> {
      if (disposableWrapper.delegate.equals(obj) &&
          (removedWrappers.isEmpty() && disposableWrapper.makeUnique() || removedWrappers.contains(disposableWrapper))) {
        removedWrappers.add(disposableWrapper);
        return true;
      }
      return false;
    });

    for (DisposableWrapper disposableWrapper : removedWrappers) {
      disposableWrapper.disposeWithoutRemoval();
    }
    return result;
  }

  @Override
  @Nullable
  public E remove(int index) {
    DisposableWrapper removedWrapper = myWrappedList.remove(index);
    return unwrapAndDispose(removedWrapper);
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> objects) {
    return removeIf(element -> objects.contains(element));
  }

  @Override
  public boolean removeIf(@NotNull Predicate<? super E> filter) {
    Set<DisposableWrapper> removedWrappers = new ReferenceOpenHashSet<>(myWrappedList.size());
    boolean result = myWrappedList.removeIf(disposableWrapper -> {
      if (filter.test(disposableWrapper.delegate) && (disposableWrapper.makeUnique() || removedWrappers.contains(disposableWrapper))) {
        removedWrappers.add(disposableWrapper);
        return true;
      }
      return false;
    });

    for (DisposableWrapper disposableWrapper : removedWrappers) {
      disposableWrapper.disposeWithoutRemoval();
    }
    return result;
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> objects) {
    return removeIf(element -> !objects.contains(element));
  }

  @Override
  public void clear() {
    removeIf(__ -> true);
  }

  @Override
  public int size() {
    return myWrappedList.size();
  }

  @Override
  public boolean isEmpty() {
    return myWrappedList.isEmpty();
  }

  @Override
  @NotNull
  public Iterator<E> iterator() {
    return new DisposableWrapperListIterator(0);
  }

  @Override
  public Object @NotNull [] toArray() {
    Object[] elements = myWrappedList.toArray();
    if (elements.length == 0) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    for (int i = 0, n = elements.length; i < n; i++) {
      elements[i] = ((DisposableWrapper)elements[i]).delegate;
    }
    return elements;
  }

  @Override
  public <T> T @NotNull [] toArray(T @NotNull [] array) {
    Object[] elements = myWrappedList.toArray();
    int len = elements.length;
    if (array.length < len) {
      //noinspection unchecked
      array = (T[])Array.newInstance(array.getClass().getComponentType(), len);
    }
    else {
      Arrays.fill(array, len, array.length, null);
    }

    for (int i = 0; i < len; i++) {
      //noinspection unchecked
      array[i] = (T)((DisposableWrapper)elements[i]).delegate;
    }
    return array;
  }

  @Override
  public E get(int index) {
    return myWrappedList.get(index).delegate;
  }

  @Override
  public E set(int index, E element) {
    DisposableWrapper replaced = myWrappedList.set(index, new DisposableWrapper(element));
    return unwrapAndDispose(replaced);
  }

  @Override
  public boolean contains(@Nullable Object obj) {
    return obj != null && myWrappedList.contains(new DisposableWrapper((E)obj));
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> collection) {
    Collection<DisposableWrapper> disposableWrappers = wrapAll((Collection<? extends E>)collection);
    return myWrappedList.containsAll(disposableWrappers);
  }

  @Override
  public int indexOf(@Nullable Object obj) {
    return obj == null ? -1 : myWrappedList.indexOf(new DisposableWrapper((E)obj));
  }

  @Override
  public int lastIndexOf(@Nullable Object obj) {
    return obj == null ? -1 : myWrappedList.lastIndexOf(new DisposableWrapper((E)obj));
  }

  @Override
  @NotNull
  public ListIterator<E> listIterator() {
    return new DisposableWrapperListIterator(0);
  }

  @Override
  @NotNull
  public ListIterator<E> listIterator(int index) {
    return new DisposableWrapperListIterator(index);
  }

  @Override
  @NotNull
  public List<E> subList(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  private DisposableWrapper createDisposableWrapper(@NotNull E element, @NotNull Disposable parentDisposable) {
    DisposableWrapper disposableWrapper = new DisposableWrapper(element, true);
    Disposer.register(parentDisposable, disposableWrapper);
    return disposableWrapper;
  }

  @NotNull
  private Collection<DisposableWrapper> wrapAll(@NotNull Collection<? extends E> collection) {
    if (collection.isEmpty()) {
      return Collections.emptyList();
    }
    List<DisposableWrapper> result = new ArrayList<>(collection.size());
    for (E obj : collection) {
      result.add(new DisposableWrapper(obj));
    }
    return result;
  }

  @Nullable
  private E unwrapAndDispose(@Nullable DisposableWrapper disposableWrapper) {
    if (disposableWrapper == null) {
      return null;
    }
    E unwrapped = disposableWrapper.delegate;
    disposableWrapper.disposeWithoutRemoval();
    return unwrapped;
  }

  private class DisposableWrapper extends AtomicBoolean implements Disposable {
    @NotNull
    private final E delegate;
    private boolean removeFromContainer;

    DisposableWrapper(@NotNull E obj) {
      this(obj, false);
    }

    DisposableWrapper(@NotNull E delegate, boolean removeFromContainer) {
      this.delegate = delegate;
      this.removeFromContainer = removeFromContainer;
    }

    @Override
    public void dispose() {
      if (removeFromContainer) {
        makeUnique(); // Make sure that exactly this wrapper is removed.
        myWrappedList.remove(this);
      }
    }

    void disposeWithoutRemoval() {
      if (removeFromContainer) {
        removeFromContainer = false;
        Disposer.dispose(this);
      }
    }

    /**
     * Makes the object equal only to itself.
     *
     * @return true if the object's state changed as a result of the method call
     */
    boolean makeUnique() {
      return compareAndSet( false, true);
    }

    private boolean isUnique() {
      return get();
    }

    @Override
    public int hashCode() {
      return delegate.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || !getClass().equals(obj.getClass())) {
        return false;
      }
      DisposableWrapper other = (DisposableWrapper)obj;
      try {
        return delegate.equals(other.delegate) && !isUnique() && !other.isUnique();
      }
      catch (ClassCastException e) {
        throw new RuntimeException("failed DisposableWrapper.equals(" + classInfo(other.delegate)
                                   + "; this.delegate=" + classInfo(delegate) + ". Whole list=" + myWrappedList, e);
      }
    }

    @NotNull
    private String classInfo(@NotNull E o) {
      try {
        return o + " (" + o.getClass() + "; super interfaces: " + Arrays.toString(o.getClass().getInterfaces()) +")";
      }
      catch (Throwable e) {
        // ignore in case of Proxy object with poorly-implemented toString()
        return e.getMessage();
      }
    }
  }

  private class DisposableWrapperListIterator implements ListIterator<E> {
    @NotNull private final ListIterator<DisposableWrapper> myDelegate;
    @Nullable private DisposableWrapper myLastReturned;

    DisposableWrapperListIterator(int initialCursor) {
      myDelegate = myWrappedList.listIterator(initialCursor);
    }

    @Override
    public boolean hasNext() {
      return myDelegate.hasNext();
    }

    @Override
    public E next() {
      myLastReturned = myDelegate.next();
      return myLastReturned.delegate;
    }

    @Override
    public boolean hasPrevious() {
      return myDelegate.hasPrevious();
    }

    @Override
    public E previous() {
      myLastReturned = myDelegate.previous();
      return myLastReturned.delegate;
    }

    @Override
    public int nextIndex() {
      return myDelegate.nextIndex();
    }

    @Override
    public int previousIndex() {
      return myDelegate.previousIndex();
    }

    @Override
    public void remove() {
      if (myLastReturned == null) {
        throw new NoSuchElementException();
      }

      if (myLastReturned.makeUnique()) {
        myDelegate.remove();
        myLastReturned.disposeWithoutRemoval();
        myLastReturned = null;
      }
    }

    @Override
    public void set(E element) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(E element) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
      return myDelegate.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || !getClass().equals(obj.getClass())) {
        return false;
      }
      return myDelegate.equals(((DisposableWrapperListIterator)obj).myDelegate);
    }
  }
}
