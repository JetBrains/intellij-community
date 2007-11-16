package com.intellij.lang.ant;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;

public class AntLanguage extends Language {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.AntLanguage");

  public AntLanguage() {
    super("ANT");
    StdLanguages.ANT = this;
  }
}
