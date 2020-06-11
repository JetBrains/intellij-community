/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Hash set (based on THashSet) which is fast when contains one or zero elements (avoids to calculate hash codes and call equals whenever possible).
 * For other sizes it delegates to THashSet.
 * Null keys are NOT PERMITTED.
 */
public class SmartHashSet<T> extends THashSet<T> {
  private T theElement; // contains the only element if size() == 1

  public SmartHashSet() {
  }

  public SmartHashSet(@NotNull TObjectHashingStrategy<T> strategy) {
    super(strategy);
  }

  public SmartHashSet(int initialCapacity) {
    super(initialCapacity);
  }

  public SmartHashSet(int initialCapacity, @NotNull TObjectHashingStrategy<T> strategy) {
    super(initialCapacity, strategy);
  }

  public SmartHashSet(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public SmartHashSet(int initialCapacity, float loadFactor, @NotNull TObjectHashingStrategy<T> strategy) {
    super(initialCapacity, loadFactor, strategy);
  }

  public SmartHashSet(@NotNull Collection<? extends @NotNull T> collection) {
    this(collection, ContainerUtil.canonicalStrategy());
  }

  public SmartHashSet(@NotNull Collection<? extends @NotNull T> collection, @NotNull TObjectHashingStrategy<T> strategy) {
    super(collection.size() == 1 ? Collections.emptyList() : collection, strategy);
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
