// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

final class ThemeJsonSchemaProviderFactory implements JsonSchemaProviderFactory, DumbAware {
  private static final @NonNls String THEME_SCHEMA = "/schemes/theme.schema.json";

  @Override
  public @NotNull List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
    return Collections.singletonList(new JsonSchemaFileProvider() {
      @Override
      public boolean isAvailable(@NotNull VirtualFile file) {
        return ThemeJsonUtil.isThemeFilename(file.getName());
      }

      @Override
      public @NotNull String getName() {
        return DevKitThemesBundle.message("theme.json.display.name");
      }

      @Override
      public @Nullable VirtualFile getSchemaFile() {
        return JsonSchemaProviderFactory.getResourceFile(getClass(), THEME_SCHEMA);
      }

      @Override
      public @NotNull SchemaType getSchemaType() {
        return SchemaType.embeddedSchema;
      }
    });
  }
}
