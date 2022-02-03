// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Hash set which is fast when contains one or zero elements (avoids calculating hash codes and call equals whenever possible).
 * For other sizes it delegates to THashSet.
 * Null keys are NOT PERMITTED.
 */
public final class SmartHashSet<T> extends HashSet<T> {
  private T theElement; // contains the only element if size() == 1

  public SmartHashSet() {
  }

  public SmartHashSet(int initialCapacity) {
    super(initialCapacity);
  }

  public SmartHashSet(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public SmartHashSet(@NotNull Collection<? extends @NotNull T> collection) {
    super(collection.size() == 1 ? Collections.emptyList() : collection);
    if (collection.size() == 1) {
      T element = collection.iterator().next();
      //noinspection ConstantConditions
      if (element == null) {
        throw new IllegalArgumentException("Null elements are not permitted but got: " + collection);
      }
      theElement = element;
    }
  }

  @Override
  public boolean contains(@NotNull Object obj) {
    T theElement = this.theElement;
    if (theElement != null) {
      return Objects.equals(obj, theElement);
    }
    return !super.isEmpty() && super.contains(obj);
  }

  @Override
  public boolean add(@NotNull T obj) {
    T theElement = this.theElement;
    if (theElement != null) {
      if (Objects.equals(obj, theElement)) {
        return false;
      }

      super.add(this.theElement);
      this.theElement = null;
      return super.add(obj);
    }
    else if (super.isEmpty()) {
      this.theElement = obj;
      return true;
    }
    else {
      return super.add(obj);
    }
  }

  @Override
  public boolean equals(@Nullable Object other) {
    T theElement = this.theElement;
    if (theElement != null) {
      return other instanceof Set && ((Set<?>)other).size() == 1 && Objects.equals(((Set<?>)other).iterator().next(), theElement);
    }

    return super.equals(other);
  }

  @Override
  public int hashCode() {
    T theElement = this.theElement;
    return theElement == null ? super.hashCode() : theElement.hashCode();
  }

  @Override
  public void clear() {
    theElement = null;
    super.clear();
  }

  @Override
  public int size() {
    return theElement == null ? super.size() : 1;
  }

  @Override
  public boolean isEmpty() {
    return theElement == null && super.isEmpty();
  }

  @Override
  public boolean remove(@NotNull Object obj) {
    T theElement = this.theElement;
    if (theElement == null) {
      return super.remove(obj);
    }

    if (Objects.equals(obj, theElement)) {
      this.theElement = null;
      return true;
    }
    return false;
  }

  @Override
  public @NotNull Iterator<T> iterator() {
    if (theElement == null) {
      return super.iterator();
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
        return theElement;
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
    T theElement = this.theElement;
    if (theElement == null) {
      super.forEach(action);
    }
    else {
      action.accept(theElement);
    }
  }

  @Override
  public Object @NotNull [] toArray() {
    T theElement = this.theElement;
    if (theElement == null) {
      return super.toArray();
    }
    return new Object[]{theElement};
  }

  @Override
  public <O> O @NotNull [] toArray(O @NotNull [] a) {
    @SuppressWarnings("unchecked")
    O theElement = (O)this.theElement;
    if (theElement == null) {
      //noinspection SuspiciousToArrayCall
      return super.toArray(a);
    }

    if (a.length == 0) {
      a = ArrayUtil.newArray(ArrayUtil.getComponentType(a), 1);
    }
    a[0] = theElement;
    if (a.length > 1) {
      a[1] = null;
    }
    return a;
  }

  @Override
  public Stream<T> stream() {
    return theElement == null ? super.stream() : Stream.of(theElement);
  }

  @Override
  public Spliterator<T> spliterator() {
    return theElement == null ? super.spliterator() : Stream.of(theElement).spliterator();
  }
}
