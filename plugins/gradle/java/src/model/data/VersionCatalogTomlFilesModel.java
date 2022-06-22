// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class VersionCatalogTomlFilesModel {
  public static final Key<VersionCatalogTomlFilesModel> KEY = Key.create(VersionCatalogTomlFilesModel.class, ExternalSystemConstants.UNORDERED);

  private final Map<String, String> tomlFileRegistry;

  @PropertyMapping("tomlFileRegistry")
  public VersionCatalogTomlFilesModel(@NotNull Map<String, String> tomlFileRegistry) {
    this.tomlFileRegistry = tomlFileRegistry;
  }

  public @NotNull Map<String, String> getTomlFileRegistry() {
    return tomlFileRegistry;
  }
}
