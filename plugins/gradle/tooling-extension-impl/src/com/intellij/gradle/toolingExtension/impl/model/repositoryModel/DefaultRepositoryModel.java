// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.repositoryModel;

import org.jetbrains.annotations.NotNull;
import com.intellij.gradle.toolingExtension.model.repositoryModel.RepositoryModel;

public class DefaultRepositoryModel implements RepositoryModel {

  private final @NotNull String name;

  public DefaultRepositoryModel(@NotNull String name) {
    this.name = name;
  }

  @Override
  public @NotNull String getName() {
    return name;
  }
}
