// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeModel;

class TreeModelWithInvoker extends TreeModelWrapper implements InvokerSupplier {
  private final Invoker invoker;

  TreeModelWithInvoker(@NotNull TreeModel model, @NotNull Invoker invoker) {
    super(model);
    this.invoker = invoker;
  }

  @NotNull
  @Override
  public Invoker getInvoker() {
    return invoker;
  }
}
