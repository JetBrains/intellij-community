// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;

public interface ChangeNodeDecorator {
  void decorate(@NotNull Change change, @NotNull SimpleColoredComponent component, boolean isShowFlatten);

  void preDecorate(@NotNull Change change, @NotNull ChangesBrowserNodeRenderer renderer, boolean isShowFlatten);
}
