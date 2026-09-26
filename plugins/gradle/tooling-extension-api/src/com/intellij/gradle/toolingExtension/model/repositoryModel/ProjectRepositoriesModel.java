// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.model.repositoryModel;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;

/**
 * Container for repositories declared in a Project.
 */
public interface ProjectRepositoriesModel extends Serializable {

  @NotNull
  List<RepositoryModel> getRepositories();
}
