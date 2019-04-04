// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe lazy initializer.
 *
 * @author tav
 */
public class LazyInitializer {
  public abstract static class NullableValue<T> {
    private class Initializer {
      private final ReentrantLock lock = new ReentrantLock();

      T init() {
        try {
          return initialize();
        }
        catch (Exception e) {
          Logger.getInstance(LazyInitializer.class).error(e);
        }
        return null;
      }
    }

    protected volatile T value;
    private volatile Initializer initializer = new Initializer(); // dropped when initialized

    @Nullable
    public abstract T initialize();

    /**
     * Initializes the value if necessary and returns it.
     *
     * @return the initialized value
     */
    @Nullable
    public T get() {
      Initializer init = initializer;
      if (init != null) {
        init.lock.lock();
        try {
          if (init.lock.getHoldCount() > 1) {
            return null;
          }
          if (initializer != null) {
            value = initializer.init();
            initializer = null;
          }
        }
        finally {
          init.lock.unlock();
        }
        onInitialized(value);
      }
      return value;
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

  public static abstract class MutableNullableValue<T> extends NullableValue<T> {
    /**
     * Sets the value. If it hasn't been initialized - forces initialization.
     *
     * @param value the value to set
     */
    public void set(T value) {
      get(); // force init in case it has a side effect
      this.value = value;
    }
  }

  public static abstract class MutableNotNullValue<T> extends MutableNullableValue<T> {
    @Override
    public void set(@NotNull T value) {
      super.set(value);
    }

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
}