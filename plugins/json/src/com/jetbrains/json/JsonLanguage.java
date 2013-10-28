package com.jetbrains.json;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public class JsonLanguage extends Language {
  @NotNull public static final Language INSTANCE = new JsonLanguage();

  private JsonLanguage() {
    super("AppCode.JSON");
  }
}
