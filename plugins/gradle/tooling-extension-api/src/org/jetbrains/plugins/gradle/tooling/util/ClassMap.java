// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// duplicate of com.intellij.util.containers.ClassMap
public final class ClassMap<T> {
  private final ConcurrentMap<Class<?>, T> myMap;

  public ClassMap() {
    this(new ConcurrentHashMap<>());
  }

  private ClassMap(@NotNull ConcurrentMap<Class<?>, T> map) {
    myMap = map;
  }

  public void put(@NotNull Class<?> aClass, T value) {
    myMap.put(aClass, value);
  }
  public void remove(@NotNull Class<?> aClass) {
    myMap.remove(aClass);
  }

  public T get(@NotNull Class<?> aClass) {
    T t = myMap.get(aClass);
    if (t != null) {
      return t;
    }

    T bySuperType = getBySuperType(aClass);

    if (bySuperType != null) {
      T previous = myMap.putIfAbsent(aClass, bySuperType);
      return previous == null ? bySuperType : previous;
    }
    return null;
  }

  @Nullable private T getBySuperType(@NotNull Class<?> aClass) {
    for (Class<?> iface : aClass.getInterfaces()) {
      T byInterface = get(iface);
      if (byInterface != null) {
        return byInterface;
      }
    }
    Class<?> superclass = aClass.getSuperclass();
    if (superclass != null) {
      T bySuperclass = get(superclass);
      if (bySuperclass != null) {
        return bySuperclass;
      }
    }
    return null;
  }

  public @NotNull Collection<T> values() {
    return myMap.values();
  }

  public void clear() {
    myMap.clear();
  }
}
