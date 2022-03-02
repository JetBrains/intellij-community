// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

public abstract class AtomicClearableLazyValue<T> extends ClearableLazyValue<T> {
  @Override
  public final synchronized @NotNull T getValue() {
    return super.getValue();
  }

  @Override
  public final synchronized boolean isCached() {
    return super.isCached();
  }

  @Override
  public final synchronized void drop() {
    super.drop();
  }
}
