// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.repositoryModel;

import org.jetbrains.annotations.NotNull;
import com.intellij.gradle.toolingExtension.model.repositoryModel.ProjectRepositoriesModel;
import com.intellij.gradle.toolingExtension.model.repositoryModel.RepositoryModel;

import java.util.List;

public class DefaultProjectRepositoriesModel implements ProjectRepositoriesModel {

  private final @NotNull List<RepositoryModel> repositories;

  public DefaultProjectRepositoriesModel(@NotNull List<RepositoryModel> repositories) {
    this.repositories = repositories;
  }

  @Override
  public @NotNull List<RepositoryModel> getRepositories() {
    return repositories;
  }
}
