package com.jetbrains.json;

import com.intellij.lang.Language;

public class JsonLanguage extends Language {
  public static final JsonLanguage INSTANCE = new JsonLanguage();

  private JsonLanguage() {
    super("JSON");
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }
}
