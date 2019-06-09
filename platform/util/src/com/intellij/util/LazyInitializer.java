// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Thread-safe lazy initializer.
 *
 * @author tav
 */
public final class LazyInitializer {
  private static final Object UNINITIALIZED_VALUE = new Object();

  public abstract static class NullableValue<T> {
    @SuppressWarnings("unchecked")
    private volatile T value = (T)UNINITIALIZED_VALUE;

    @Nullable
    public abstract T initialize();

    /**
     * Initializes the value if necessary and returns it.
     */
    @Nullable
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

        v = initialize();
        value = v;
      }
      onInitialized(value);
      return value;
    }

    protected void set(T value) {
      //noinspection SynchronizeOnThis
      synchronized(this) {
        this.value = value;
      }
    }

    /**
     * Checks if the value is initialized to not-null, forces initialization if necessary.
     *
     * @return true if the value is initialized to not-null
     */
    public final boolean isNotNull() {
      return get() != null;
    }

    /**
     * Called on the initialization completion.
     *
     * @param value the initialized value
     */
    protected void onInitialized(T value) {
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  public static abstract class NotNullValue<T> extends NullableValue<T> {
    @NotNull
    @Override
    public T get() {
      //noinspection ConstantConditions
      return super.get();
    }

    @NotNull
    @Override
    public abstract T initialize();
  }

  public static final class MutableNotNullValue<T> extends NullableValue<T> {
    private final Supplier<? extends T> supplier;

    public MutableNotNullValue(@NotNull Supplier<? extends T> supplier) {
      this.supplier = supplier;
    }

    @NotNull
    @Override
    public T get() {
      //noinspection ConstantConditions
      return super.get();
    }

    @Override
    public void set(@NotNull T value) {
      super.set(value);
    }

    @NotNull
    @Override
    public final T initialize() {
      return supplier.get();
    }
  }
}