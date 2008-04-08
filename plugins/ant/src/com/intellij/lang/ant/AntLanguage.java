package com.intellij.lang.ant;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;

public class AntLanguage extends Language implements DependentLanguage{

  public AntLanguage() {
    super("ANT");
    StdLanguages.ANT = this;
  }
}
