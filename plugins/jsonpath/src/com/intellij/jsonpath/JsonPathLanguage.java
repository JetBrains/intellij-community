// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jsonpath;

import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;

public final class JsonPathLanguage extends Language implements InjectableLanguage {

  public static final JsonPathLanguage INSTANCE = new JsonPathLanguage();

  private JsonPathLanguage() {
    super("JSONPath");
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }

  @Override
  public LanguageFileType getAssociatedFileType() {
    return JsonPathFileType.INSTANCE;
  }
}
