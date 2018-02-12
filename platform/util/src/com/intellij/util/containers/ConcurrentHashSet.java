/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @deprecated use {@link ContainerUtil#newConcurrentSet()} instead
 */
public final class ConcurrentHashSet<K> implements Set<K> {
  private static final Logger LOG = Logger.getInstance(ConcurrentHashSet.class);
  private final ConcurrentMap<K, Boolean> map;

  public ConcurrentHashSet() {
    map = ContainerUtil.newConcurrentMap();
    LOG.warn("ConcurrentHashSet is deprecated and will be removed soon; please use com.intellij.util.containers.ContainerUtil.newConcurrentSet instead.");
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return map.containsKey(o);
  }

  @NotNull
  @Override
  public Iterator<K> iterator() {
    return map.keySet().iterator();
  }

  @NotNull
  @Override
  public Object[] toArray() {
    return map.keySet().toArray();
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    return map.keySet().toArray(a);
  }

  @Override
  public boolean add(@NotNull K o) {
    return map.putIfAbsent(o, Boolean.TRUE) == null;
  }

  @Override
  public boolean remove(@NotNull Object o) {
    return map.remove(o) != null;
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    return map.keySet().containsAll(c);
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends K> c) {
    boolean ret = false;
    for (K o : c) {
      ret |= add(o);
    }

    return ret;
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    return map.keySet().retainAll(c);
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    return map.keySet().removeAll(c);
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public String toString() {
    return map.keySet().toString();
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof ConcurrentHashSet) {
      return map.equals(((ConcurrentHashSet)obj).map);
    }

    if (!(obj instanceof Set)) {
      return false;
    }

    Set<?> c = (Set<?>)obj;
    if (c.size() != size()) {
      return false;
    }

    try {
      return containsAll(c);
    }
    catch (ClassCastException ignored) {
      return false;
    }
    catch (NullPointerException ignored) {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }
}

