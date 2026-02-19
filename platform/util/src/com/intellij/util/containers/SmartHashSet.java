// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Hash set which is fast and footprint-efficient when contains one or zero elements (avoids calculating hash codes and call equals whenever possible).
 * For other sizes, it delegates to {@link java.util.HashSet}.
 * Use only when anticipate empty or one-element sized maps, in all other cases prefer {@link java.util.HashSet}.
 * Null keys are NOT PERMITTED.
 * Not thread-safe
 */
public final class SmartHashSet<T> extends AbstractSet<T> {
  private Object theElement; // null if empty, the only element if size() == 1, MySet otherwise
  public SmartHashSet() {
  }
  // for binary compatibility
  public SmartHashSet(int initialCapacity, float loadFactor) {
  }
  // for binary compatibility
  public SmartHashSet(int initialCapacity) {
  }

  public SmartHashSet(@NotNull Collection<? extends @NotNull T> collection) {
    if (collection.size() == 1) {
      T element = collection.iterator().next();
      //noinspection ConstantConditions
      if (element == null) {
        throw new IllegalArgumentException("Null elements are not permitted but got: " + collection);
      }
      theElement = element;
    }
    else if (!collection.isEmpty()) {
      theElement = new MySet<T>(collection);
    }
  }

  private static final class MySet<T> extends HashSet<T> {
    MySet(@NotNull Collection<? extends T> collection) {
      super(collection);
    }

    MySet(@NotNull T element1, @NotNull T element2) {
      add(element1);
      add(element2);
    }
  }

  @Override
  public boolean contains(@NotNull Object obj) {
    Object element = this.theElement;
    if (element == null) {
      return false;
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet == null) {
      return Objects.equals(obj, element);
    }
    return hashSet.contains(obj);
  }

  @Override
  public boolean add(@NotNull T obj) {
    Object element = this.theElement;
    if (element == null) {
      theElement = obj;
      return true;
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet == null) {
      if (Objects.equals(obj, element)) {
        return false;
      }

      //noinspection unchecked
      this.theElement = new MySet<>((T)element, obj);
      return true;
    }
    else {
      return hashSet.add(obj);
    }
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (!(other instanceof Set)) {
      return false;
    }
    Object element = this.theElement;
    if (element == null) {
      return ((Set<?>)other).isEmpty();
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      return other.equals(hashSet);
    }
    return ((Set<?>)other).size() == 1 && Objects.equals(((Set<?>)other).iterator().next(), element);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(theElement);
  }

  @Override
  public void clear() {
    theElement = null;
  }

  @Override
  public int size() {
    Object element = theElement;
    if (element == null) {
      return 0;
    }
    MySet<T> hashSet = asHashSet(element);
    return hashSet == null ? 1 : hashSet.size();
  }

  @Override
  public boolean isEmpty() {
    return theElement == null;
  }

  @Override
  public boolean remove(@NotNull Object obj) {
    Object element = this.theElement;
    if (element == null) {
      return false;
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      boolean removed = hashSet.remove(obj);
      if (removed) {
        if (hashSet.isEmpty()) {
          theElement = null;
        }
        else if (hashSet.size() == 1) {
          theElement = hashSet.iterator().next();
        }
      }
      return removed;
    }
    else if (Objects.equals(obj, element)) {
      this.theElement = null;
      return true;
    }
    return false;
  }

  private @Nullable MySet<T> asHashSet(Object element) {
    if (element instanceof MySet) {
      //noinspection unchecked
      MySet<T> set = (MySet<T>)element;
      assert set.size()>1 : set;
      return set;
    }
    return null;
  }

  @Override
  public @NotNull Iterator<T> iterator() {
    Object element = theElement;
    if (element == null) {
      return Collections.emptyIterator();
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      return new Iterator<T>() {
        private final Iterator<T> hashIterator = hashSet.iterator();
        @Override
        public boolean hasNext() {
          return hashIterator.hasNext();
        }

        @Override
        public T next() {
          return hashIterator.next();
        }

        @Override
        public void remove() {
          hashIterator.remove();
          if (hashSet.isEmpty()) {
            theElement = null;
          }
          else if (hashSet.size() == 1) {
            theElement = hashSet.iterator().next();
          }
        }
      };
    }
    return new SingletonIteratorBase<T>() {
      @Override
      protected void checkCoModification() {
        if (theElement == null) {
          throw new ConcurrentModificationException();
        }
      }

      @Override
      protected T getElement() {
        //noinspection unchecked
        return (T)element;
      }

      @Override
      public void remove() {
        checkCoModification();
        clear();
      }
    };
  }

  @Override
  public void forEach(Consumer<? super T> action) {
    Object element = this.theElement;
    if (element == null) {
      return;
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      hashSet.forEach(action);
    }
    else {
      //noinspection unchecked
      action.accept((T)element);
    }
  }

  @Override
  public Object @NotNull [] toArray() {
    Object element = this.theElement;
    if (element == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      return hashSet.toArray();
    }
    else {
      return new Object[]{element};
    }
  }

  @Override
  public <O> O @NotNull [] toArray(O @NotNull [] a) {
    Object element = this.theElement;
    if (element == null) {
      if (a.length != 0) {
        a[0] = null;
      }
      return a;
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      return hashSet.toArray(a);
    }

    if (a.length == 0) {
      a = ArrayUtil.newArray(ArrayUtil.getComponentType(a), 1);
    }
    //noinspection unchecked
    a[0] = (O)element;
    if (a.length > 1) {
      a[1] = null;
    }
    return a;
  }

  @Override
  public @NotNull Stream<T> stream() {
    Object element = this.theElement;
    if (element == null) {
      return Stream.empty();
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      return hashSet.stream();
    }

    //noinspection unchecked
    return Stream.of((T)element);
  }

  @Override
  public @NotNull Spliterator<T> spliterator() {
    Object element = this.theElement;
    if (element == null) {
      return Spliterators.emptySpliterator();
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      return hashSet.spliterator();
    }
    //noinspection unchecked
    return Stream.of((T)element).spliterator();
  }

  @Override
  public String toString() {
    Object element = this.theElement;
    if (element == null) {
      return "[]";
    }
    MySet<T> hashSet = asHashSet(element);
    if (hashSet != null) {
      return hashSet.toString();
    }
    return "["+element+"]";
  }
}
