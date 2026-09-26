// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

@ApiStatus.NonExtendable
public class Couple<T> extends Pair<T, T> implements Iterable<T> {
  private static final Couple<Object> EMPTY_COUPLE = new Couple<>(null, null);

  public Couple(T first, T second) {
    super(first, second);
  }

  @NotNull
  public static <T> Couple<T> of(T first, T second) {
    return new Couple<>(first, second);
  }

  @NotNull
  public static <T> Couple<T> getEmpty() {
    //noinspection unchecked
    return (Couple<T>)EMPTY_COUPLE;
  }

  @Override
  public @NotNull Iterator<T> iterator() {
    return new Itr();
  }

  private class Itr implements Iterator<T> {
    private int counter = 0;

    @Override
    public boolean hasNext() {
      return counter < 2;
    }

    @Override
    public T next() {
      if (counter >= 2) {
        throw new NoSuchElementException();
      }
      T value = counter == 0 ? getFirst() : getSecond();
      counter++;
      return value;
    }
  }
}
