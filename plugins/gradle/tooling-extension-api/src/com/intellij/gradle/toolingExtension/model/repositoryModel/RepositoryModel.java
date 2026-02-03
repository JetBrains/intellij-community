// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.model.repositoryModel;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * A repository used in a project.
 * The repository could be a Maven/Ivy/Directory based repository.
 */
public interface RepositoryModel extends Serializable {

  @NotNull
  String getName();
}
