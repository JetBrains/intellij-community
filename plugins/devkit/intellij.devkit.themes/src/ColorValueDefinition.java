// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.psi.PsiAnchor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public record ColorValueDefinition(
  @NotNull String colorName,
  @NotNull String colorValue,
  @NotNull PsiAnchor declaration
) {}