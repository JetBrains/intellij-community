// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.project;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

@ApiStatus.Internal
public interface ProjectFileNodeUpdaterInvoker extends Disposable {
  Promise<?> invoke(@NotNull Runnable runnable);
  void invokeLater(@NotNull Runnable runnable, int delay);
}
