// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class DisposableWrapper<T extends Disposable> implements Disposable {
  private volatile T myObject;

  public DisposableWrapper(T object, Disposable parent) {
    myObject = object;
    Disposer.register(parent, this);
  }

  public DisposableWrapper<T> moveTo(@NotNull Disposable parent) {
    DisposableWrapper<T> newWrapper = createNewWrapper(parent, myObject);
    myObject = null;
    return newWrapper;
  }

  @NotNull
  protected DisposableWrapper<T> createNewWrapper(@NotNull Disposable parent, T object) {
    return new DisposableWrapper<T>(object, parent);
  }

  @Override
  public void dispose() {
    if (myObject != null) {
      Disposer.dispose(myObject);
    }
  }
}
