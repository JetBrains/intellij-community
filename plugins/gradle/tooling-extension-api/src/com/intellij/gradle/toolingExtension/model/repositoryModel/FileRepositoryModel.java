// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.model.repositoryModel;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A folder based repository.
 * "mavenLocal" repository considered as {@link UrlRepositoryModel} because those are organised like a Maven repository
 * with an appropriate folder structure.
 * This repository handles cases such as `flatDir { dirs "libs" }`.
 */
public interface FileRepositoryModel extends RepositoryModel {

  @NotNull
  List<String> getFiles();
}
