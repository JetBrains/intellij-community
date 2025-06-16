// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.model.repositoryModel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A remote repository that can resolve an artifact from some location defined by URL.
 * The {@link UrlRepositoryModel#getType()} method returns a type of the repository.
 * The "mavenLocal" repository is also considered as a URL repository, but instead of "https://" scheme the "file://" scheme is used.
 */
public interface UrlRepositoryModel extends RepositoryModel {

  @Nullable
  String getUrl();

  @NotNull
  Type getType();

  /**
   * The type of the repository.
   */
  enum Type {
    MAVEN,
    IVY,
    OTHER
  }
}
