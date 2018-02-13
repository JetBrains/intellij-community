// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe lazy initializer.
 *
 * @author tav
 */
public class LazyInitializer<T> {
  private volatile @Nullable T value;
  private volatile Initializer<T> initializer;

  private static class Initializer<T> {
    private final Callable<T> initializer;
    private final ReentrantLock lock = new ReentrantLock();

    Initializer(Callable<T> initializer) {
      this.initializer = initializer;
    }

    T init() {
      try {
        return this.initializer.call();
      }
      catch (Exception e) {
        Logger.getInstance(LazyInitializer.class).error(e);
      }
      return null;
    }
  }

  public LazyInitializer(@NotNull Callable<T> initializer) {
    this.initializer = new Initializer(initializer);
  }

  /**
   * Initializes the value if necessary and returns it.
   *
   * @return the initialized value
   */
  public @Nullable T get() {
    Initializer init = initializer;
    if (init != null) {
      init.lock.lock();
      try {
        if (initializer != null) {
          value = initializer.init();
        }
      }
      finally {
        initializer = null;
        init.lock.unlock();
      }
      onInitialized();
    }
    return value;
  }

  /**
   * Checks if the value is initialized to non-null, forces initialization if necessary.
   *
   * @return true if the value is initialized to non-null
   */
  public boolean isSet() {
    Initializer init = initializer;
    if (init == null) {
      return get() != null; // already initialized, just get
    }
    init.lock.lock();
    try {
      if (init.lock.getHoldCount() > 1) {
        return false; // called from inside Initializer.init()
      }
      return get() != null; // get and init if necessary
    }
    finally {
      init.lock.unlock();
    }
  }

  /**
   * Called right after the value is initialized.
   */
  protected void onInitialized() {
  }

  @Override
  public String toString() {
    return value != null ? value.toString() : "null";
  }

  @Override
  public int hashCode() {
    return value != null ? value.hashCode() : super.hashCode();
  }
}
