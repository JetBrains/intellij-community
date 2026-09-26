// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.project;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.Invoker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

class ProjectFileNodeUpdaterLegacyInvoker implements ProjectFileNodeUpdaterInvoker {
  private final @NotNull Invoker myInvoker;

  ProjectFileNodeUpdaterLegacyInvoker(@NotNull Invoker invoker) {
    myInvoker = invoker;
    Disposer.register(invoker, this);
  }

  @Override
  public Promise<?> invoke(@NotNull Runnable runnable) {
    return myInvoker.invoke(runnable);
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, int delay) {
    myInvoker.invokeLater(runnable, delay);
  }

  @Override
  public void dispose() { }
}
