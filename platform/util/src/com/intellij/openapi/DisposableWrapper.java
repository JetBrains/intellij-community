// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DisposableWrapper<T extends Disposable> implements Disposable {
  private volatile T myObject;

  public DisposableWrapper(@Nullable T object, @NotNull Disposable parent) {
    myObject = object;
    Disposer.register(parent, this);
  }

  @NotNull
  public DisposableWrapper<T> moveTo(@NotNull Disposable parent) {
    DisposableWrapper<T> newWrapper = createNewWrapper(myObject, parent);
    myObject = null;
    return newWrapper;
  }

  @NotNull
  protected DisposableWrapper<T> createNewWrapper(@Nullable T object, @NotNull Disposable parent) {
    return new DisposableWrapper<T>(object, parent);
  }

  @Override
  public void dispose() {
    if (myObject != null) {
      Disposer.dispose(myObject);
    }
  }
}
