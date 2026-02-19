// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public
interface ReferenceDelegatingDisposableInternal extends Disposable {

  @ApiStatus.Internal
  @NotNull
  Disposable getDisposableDelegate();

  @Override
  default void dispose() {
    Disposer.dispose(getDisposableDelegate());
  }
}
