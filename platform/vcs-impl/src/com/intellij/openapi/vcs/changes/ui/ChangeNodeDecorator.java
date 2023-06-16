// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;

public interface ChangeNodeDecorator {
  void decorate(@NotNull Change change, @NotNull SimpleColoredComponent component, boolean isShowFlatten);

  void preDecorate(@NotNull Change change, @NotNull ChangesBrowserNodeRenderer renderer, boolean isShowFlatten);
}
