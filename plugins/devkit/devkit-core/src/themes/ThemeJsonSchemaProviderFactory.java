// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ThemeJsonSchemaProviderFactory implements JsonSchemaProviderFactory {
  private static final String THEME_SCHEMA = "/schemes/theme.schema.json";

  @NotNull
  @Override
  public List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
    return Collections.singletonList(new JsonSchemaFileProvider() {
      @Override
      public boolean isAvailable(@NotNull VirtualFile file) {
        return file.getName().endsWith(".theme.json");
      }

      @NotNull
      @Override
      public String getName() {
        return "IntelliJ Themes";
      }

      @Nullable
      @Override
      public VirtualFile getSchemaFile() {
        return VfsUtil.findFileByURL(getClass().getResource(THEME_SCHEMA));
      }

      @NotNull
      @Override
      public SchemaType getSchemaType() {
        return SchemaType.embeddedSchema;
      }
    });
  }
}
