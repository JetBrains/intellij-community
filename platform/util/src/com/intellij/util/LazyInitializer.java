// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Thread-safe lazy initializer.
 *
 * @author tav
 */
public final class LazyInitializer {
  private static final Object UNINITIALIZED_VALUE = new Object();

  @Contract(value = "_ -> new", pure = true)
  public static <T> @NotNull LazyValue<T> create(@NotNull Supplier<? extends T> initializer) {
    return new LazyValue<>(initializer);
  }

  public static final class LazyValue<T> {
    private final @NotNull Supplier<? extends T> initializer;

    public LazyValue(@NotNull Supplier<? extends T> initializer) {
      this.initializer = initializer;
    }

    @SuppressWarnings("unchecked")
    private volatile T value = (T)UNINITIALIZED_VALUE;

    /**
     * Initializes the value if necessary and returns it.
     */
    public T get() {
      T v = value;
      if (v != UNINITIALIZED_VALUE) {
        return value;
      }

      //noinspection SynchronizeOnThis
      synchronized (this) {
        v = value;
        if (v != UNINITIALIZED_VALUE) {
          return value;
        }

        v = initializer.get();
        value = v;
      }
      return value;
    }

    void set(T value) {
      //noinspection SynchronizeOnThis
      synchronized(this) {
        this.value = value;
      }
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }
}