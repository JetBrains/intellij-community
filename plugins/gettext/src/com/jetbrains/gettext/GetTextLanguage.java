package com.jetbrains.gettext;


import com.intellij.lang.Language;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextLanguage extends Language {
  public static GetTextLanguage INSTANCE = new GetTextLanguage();

  private GetTextLanguage() {
    super("GetText");
  }
}