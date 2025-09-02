// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.repositoryModel;

import org.jetbrains.annotations.NotNull;
import com.intellij.gradle.toolingExtension.model.repositoryModel.FileRepositoryModel;

import java.util.List;

public class DefaultFileRepositoryModel extends DefaultRepositoryModel implements FileRepositoryModel {

  private final @NotNull List<String> files;

  public DefaultFileRepositoryModel(@NotNull String name, @NotNull List<String> files) {
    super(name);
    this.files = files;
  }

  @Override
  public @NotNull List<String> getFiles() {
    return files;
  }
}
