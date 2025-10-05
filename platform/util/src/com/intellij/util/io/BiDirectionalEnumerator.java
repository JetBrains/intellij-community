// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

@ApiStatus.Internal
public final class BiDirectionalEnumerator<T> extends Enumerator<T> {
  private final @NotNull Int2ObjectMap<T> myIntToObjectMap;

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

  public @NotNull T getValue(int index) {
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
