// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Hash set (based on THashSet) which is fast when contains one or zero elements (avoids to calculate hash codes and call equals whenever possible).
 * For other sizes it delegates to THashSet.
 * Null keys are NOT PERMITTED.
 */
public final class SmartHashSet<T> extends THashSet<T> {
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
      if (element == null) {
        throw new IllegalArgumentException("Null elements are not permitted but got: "+collection);
      }
      theElement = element;
    }
  }

  @Override
  public boolean contains(@NotNull Object obj) {
    T theElement = this.theElement;
    if (theElement != null) {
      return eq(theElement, (T)obj);
    }
    return !super.isEmpty() && super.contains(obj);
  }

  @Override
  public boolean add(@NotNull T obj) {
    T theElement = this.theElement;
    if (theElement != null) {
      if (eq(theElement, obj)) return false;
      super.add(theElement);
      this.theElement = null;
      // fallthrough
    }
    else if (super.isEmpty()) {
      this.theElement = obj;
      return true;
    }
    return super.add(obj);
  }

  private boolean eq(T obj, T theElement) {
    return theElement == obj || _hashingStrategy.equals(theElement, obj);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    T theElement = this.theElement;
    if (theElement != null) {
      return other instanceof Set && ((Set<?>)other).size() == 1 && eq(theElement, ((Set<T>)other).iterator().next());
    }

    return super.equals(other);
  }

  @Override
  public int hashCode() {
    T theElement = this.theElement;
    if (theElement != null) {
      return _hashingStrategy.computeHashCode(theElement);
    }
    return super.hashCode();
  }

  @Override
  public void clear() {
    theElement = null;
    super.clear();
  }

  @Override
  public int size() {
    if (theElement != null) {
      return 1;
    }
    return super.size();
  }

  @Override
  public boolean isEmpty() {
    return theElement == null && super.isEmpty();
  }

  @Override
  public boolean remove(@NotNull Object obj) {
    T theElement = this.theElement;
    if (theElement != null) {
      if (eq(theElement, (T)obj)) {
        this.theElement = null;
        return true;
      }
      return false;
    }
    return super.remove(obj);
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    if (theElement != null) {
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
    return super.iterator();
  }

  @Override
  public boolean forEach(@NotNull TObjectProcedure<T> procedure) {
    T theElement = this.theElement;
    if (theElement != null) {
      return procedure.execute(theElement);
    }
    return super.forEach(procedure);
  }

  @Override
  public Object @NotNull [] toArray() {
    T theElement = this.theElement;
    if (theElement != null) {
      return new Object[]{theElement};
    }
    return super.toArray();
  }

  @Override
  public <T> T @NotNull [] toArray(T @NotNull [] a) {
    T theElement = (T)this.theElement;
    if (theElement != null) {
      if (a.length == 0) {
        a = ArrayUtil.newArray(ArrayUtil.getComponentType(a), 1);
      }
      a[0] = theElement;
      if (a.length > 1) {
        a[1] = null;
      }
      return a;
    }
    return super.toArray(a);
  }
}
