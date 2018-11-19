// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.actionholder;

import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

class SimpleActionRef<T extends AnAction> extends ActionRef<T> {
  private final T myAction;

  SimpleActionRef(@NotNull T action) {
    myAction = action;
  }

  @NotNull
  @Override
  public T getAction() {
    return myAction;
  }
}
