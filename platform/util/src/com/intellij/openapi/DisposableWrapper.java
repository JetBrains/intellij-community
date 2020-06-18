// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class DisposableWrapper<T extends Disposable> implements Disposable {
  private final @NotNull AtomicReference<T> myObjectRef;

  public DisposableWrapper(@Nullable T object, @NotNull Disposable parent) {
    myObjectRef = new AtomicReference<>(object);
    Disposer.register(parent, this);
  }

  public @Nullable DisposableWrapper<T> moveTo(@NotNull Disposable parent) {
    T object = myObjectRef.getAndSet(null);
    if (object != null) {
      Disposer.dispose(this);  // only unregisters this; can't harm the child anymore
      return createNewWrapper(object, parent);
    }
    return null;  // has been moved or disposed already
  }

  @NotNull
  protected DisposableWrapper<T> createNewWrapper(@Nullable T object, @NotNull Disposable parent) {
    return new DisposableWrapper<>(object, parent);
  }

  @Override
  public void dispose() {
    T object = myObjectRef.getAndSet(null);
    if (object != null) {
      Disposer.dispose(object);
    }
  }
}
