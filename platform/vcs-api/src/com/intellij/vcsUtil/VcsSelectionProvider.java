// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface VcsSelectionProvider {
  ExtensionPointName<VcsSelectionProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcsSelectionProvider");

  @Nullable
  VcsSelection getSelection(@NotNull DataContext context);
}
