// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jsonpath;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JsonPathFileType extends LanguageFileType {

  public static final JsonPathFileType INSTANCE = new JsonPathFileType();

  private JsonPathFileType() {
    super(JsonPathLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "JSONPath";
  }

  @Override
  public @NotNull String getDescription() {
    return JsonPathBundle.message("filetype.jsonpath.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "jsonpath";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Json;
  }
}
