// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public final class BiDirectionalEnumerator<T> extends Enumerator<T> {
  @NotNull
  private final Int2ObjectMap<T> myIntToObjectMap;

  public BiDirectionalEnumerator(int expectNumber) {
    super(expectNumber);

    myIntToObjectMap = new Int2ObjectOpenHashMap<>(expectNumber);
  }

  @Override
  public int enumerateImpl(T object) {
    int index = super.enumerateImpl(object);
    myIntToObjectMap.put(Math.max(index, -index), object);
    return index;
  }

  @Override
  public void clear() {
    super.clear();
    myIntToObjectMap.clear();
  }

  @NotNull
  public T getValue(int index) {
    T value = myIntToObjectMap.get(index);
    if (value == null) {
      throw new RuntimeException("Can not find value by index " + index);
    }
    return value;
  }

  public void forEachValue(@NotNull Predicate<? super T> processor) {
    for (T value : myIntToObjectMap.values()) {
      if (!processor.test(value)) {
        break;
      }
    }
  }
}
