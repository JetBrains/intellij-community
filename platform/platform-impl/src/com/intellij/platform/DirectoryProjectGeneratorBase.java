// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.facet.ui.ValidationResult;
import org.jetbrains.annotations.NotNull;

public abstract class DirectoryProjectGeneratorBase<T> implements DirectoryProjectGenerator<T> {
  @Override
  public @NotNull ValidationResult validate(@NotNull String baseDirPath) {
    return ValidationResult.OK;
  }
}
