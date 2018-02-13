// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;

/**
 * @author tav
 */
public class LazyInitializer<T> {
  private volatile @Nullable T value;
  private volatile Ref<Callable<T>> initializer; // use the ref as an inner object to sync on

  public LazyInitializer(@NotNull Callable<T> initializer) {
    this.initializer = new Ref(initializer);
  }

  public T get() {
    if (initializer != null) {
      synchronized (initializer) {
        if (initializer != null) {
          try {
            value = initializer.get().call();
          }
          catch (Exception e) {
            Logger.getInstance(LazyInitializer.class).error(e);
          }
          initializer = null;
          onInitialized();
        }
      }
    }
    return value;
  }

  public boolean isSet() {
    return initializer == null && get() != null;
  }

  protected void onInitialized() {
  }

  @Override
  public String toString() {
    return isSet() ? value.toString() : "null";
  }
}
