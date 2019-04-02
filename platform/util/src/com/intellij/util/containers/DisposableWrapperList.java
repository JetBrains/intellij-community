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
import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Predicate;

/**
 * Similar to a list returned by {@link ContainerUtil#createLockFreeCopyOnWriteList} with additional methods that allow
 * some elements of the list to be associated with {@link Disposable} objects. When these disposable objects are disposed,
 * the associated elements are removed from the list.
 *
 * @param <E> the type of elements held in this list
 */
public class DisposableWrapperList<E> extends AbstractList<E> {
  @NotNull private final List<DisposableWrapper<E>> myWrappedList = ContainerUtil.createLockFreeCopyOnWriteList();

  public DisposableWrapperList() {
  }

  @Override
  public boolean add(E element) {
    return myWrappedList.add(new DisposableWrapper<>(element));
  }

  @Override
  public void add(int index, E element) {
    myWrappedList.add(index, new DisposableWrapper<>(element));
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
  public Disposable add(E element, @NotNull Disposable parentDisposable) {
    DisposableWrapper<E> disposableWrapper = createDisposableWrapper(element, parentDisposable);
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
  public Disposable add(int index, E element, @NotNull Disposable parentDisposable) {
    DisposableWrapper<E> disposableWrapper = createDisposableWrapper(element, parentDisposable);
    myWrappedList.add(index, disposableWrapper);
    return disposableWrapper;
  }


  @Override
  public boolean addAll(@NotNull Collection<? extends E> collection) {
    Collection<DisposableWrapper<E>> disposableWrappers = wrapAll(collection);
    return myWrappedList.addAll(disposableWrappers);
  }

  @Override
  public boolean addAll(int index, @NotNull Collection<? extends E> collection) {
    Collection<DisposableWrapper<E>> disposableWrappers = wrapAll(collection);
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
    DisposableWrapper<E> removedWrapper = myWrappedList.remove(index);
    return unwrapAndDispose(removedWrapper);
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> objects) {
    return removeIf(element -> objects.contains(element));
  }

  @Override
  public boolean removeIf(@NotNull Predicate<? super E> filter) {
    Set<DisposableWrapper> removedWrappers = ContainerUtil.newIdentityTroveSet(myWrappedList.size());
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
    removeIf(element -> true);
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
    return new DisposableWrapperListIterator<>(myWrappedList, 0);
  }

  @Override
  @NotNull
  public Object[] toArray() {
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
  @NotNull
  public <T> T[] toArray(@NotNull T[] array) {
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
    DisposableWrapper<E> replaced = myWrappedList.set(index, new DisposableWrapper<>(element));
    return unwrapAndDispose(replaced);
  }

  @Override
  public boolean contains(@Nullable Object obj) {
    return obj != null && myWrappedList.contains(new DisposableWrapper<>(obj));
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> collection) {
    Collection<DisposableWrapper<Object>> disposableWrappers = wrapAll(collection);
    return myWrappedList.containsAll(disposableWrappers);
  }

  @Override
  public int indexOf(@Nullable Object obj) {
    return obj == null ? -1 : myWrappedList.indexOf(new DisposableWrapper<>(obj));
  }

  @Override
  public int lastIndexOf(@Nullable Object obj) {
    return obj == null ? -1 : myWrappedList.lastIndexOf(new DisposableWrapper<>(obj));
  }

  @Override
  @NotNull
  public ListIterator<E> listIterator() {
    return new DisposableWrapperListIterator<>(myWrappedList, 0);
  }

  @Override
  @NotNull
  public ListIterator<E> listIterator(int index) {
    return new DisposableWrapperListIterator<>(myWrappedList, index);
  }

  @Override
  @NotNull
  public List<E> subList(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  private DisposableWrapper<E> createDisposableWrapper(E element, @NotNull Disposable parentDisposable) {
    DisposableWrapper<E> disposableWrapper = new DisposableWrapper<>(element, myWrappedList);
    Disposer.register(parentDisposable, disposableWrapper);
    return disposableWrapper;
  }

  @NotNull
  private static <T> Collection<DisposableWrapper<T>> wrapAll(@NotNull Collection<? extends T> collection) {
    if (collection.isEmpty()) {
      return Collections.emptyList();
    }
    List<DisposableWrapper<T>> result = new ArrayList<>(collection.size());
    for (T obj : collection) {
      result.add(new DisposableWrapper<>(obj));
    }
    return result;
  }

  @Nullable
  private E unwrapAndDispose(@Nullable DisposableWrapper<E> disposableWrapper) {
    if (disposableWrapper == null) {
      return null;
    }
    E unwrapped = disposableWrapper.delegate;
    disposableWrapper.disposeWithoutRemoval();
    return unwrapped;
  }

  private static class DisposableWrapper<T> implements Disposable {
    private static final AtomicFieldUpdater<DisposableWrapper, Integer> UNIQUENESS_UPDATER =
        AtomicFieldUpdater.forIntFieldIn(DisposableWrapper.class);

    @NotNull private final T delegate;
    @Nullable private Collection<DisposableWrapper<T>> myContainer;
    @SuppressWarnings("unused") // Set using UNIQUENESS_UPDATER.
    private volatile int myIsUnique; // A boolean value encoded using 0 for false and 1 for true.

    DisposableWrapper(@NotNull T obj) {
      this(obj, null);
    }

    DisposableWrapper(@NotNull T obj, @Nullable Collection<DisposableWrapper<T>> container) {
      this.delegate = obj;
      this.myContainer = container;
    }

    @Override
    public void dispose() {
      if (myContainer != null) {
        makeUnique(); // Make sure that exactly this wrapper is removed.
        myContainer.remove(this);
      }
    }

    void disposeWithoutRemoval() {
      if (myContainer != null) {
        myContainer = null;
        Disposer.dispose(this);
      }
    }

    /**
     * Makes the object equal only to itself.
     *
     * @return true if the object's state changed as a result of the method call
     */
    boolean makeUnique() {
      return UNIQUENESS_UPDATER.compareAndSetInt(this, 0, 1);
    }

    private boolean isUnique() {
      return myIsUnique != 0;
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
      return delegate.equals(other.delegate) && !isUnique() && !other.isUnique();
    }
  }

  private static class DisposableWrapperListIterator<T> implements ListIterator<T> {
    @NotNull private final ListIterator<DisposableWrapper<T>> myDelegate;
    @Nullable private DisposableWrapper<T> myLastReturned;

    DisposableWrapperListIterator(@NotNull List<DisposableWrapper<T>> list, int initialCursor) {
      myDelegate = list.listIterator(initialCursor);
    }

    @Override
    public boolean hasNext() {
      return myDelegate.hasNext();
    }

    @Override
    public T next() {
      myLastReturned = myDelegate.next();
      return myLastReturned.delegate;
    }

    @Override
    public boolean hasPrevious() {
      return myDelegate.hasPrevious();
    }

    @Override
    public T previous() {
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
    public void set(T element) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(T element) {
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
