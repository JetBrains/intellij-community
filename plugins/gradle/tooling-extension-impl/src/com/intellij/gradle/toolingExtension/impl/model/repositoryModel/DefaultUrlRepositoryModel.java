// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.repositoryModel;

import com.intellij.gradle.toolingExtension.model.repositoryModel.UrlRepositoryModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultUrlRepositoryModel extends DefaultRepositoryModel implements UrlRepositoryModel {

  private final @Nullable String url;
  private final @NotNull Type type;

  public DefaultUrlRepositoryModel(@NotNull String name, @Nullable String url, @NotNull Type type) {
    super(name);
    this.url = url;
    this.type = type;
  }

  @Override
  public @Nullable String getUrl() {
    return url;
  }

  @Override
  public @NotNull Type getType() {
    return type;
  }
}
