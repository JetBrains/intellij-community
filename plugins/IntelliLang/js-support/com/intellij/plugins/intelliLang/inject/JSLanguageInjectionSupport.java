package com.intellij.plugins.intelliLang.inject;

import com.intellij.lang.javascript.patterns.JSPatterns;
import com.intellij.lang.javascript.psi.JSElement;
import com.intellij.psi.PsiElement;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class JSLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  @NonNls private static final String SUPPORT_ID = "js";

  @NotNull
  @Override
  public String getId() {
    return SUPPORT_ID;
  }

  @NotNull
  @Override
  public Class[] getPatternClasses() {
    return new Class[] {JSPatterns.class};
  }

  @Override
  public boolean useDefaultInjector(PsiElement host) {
    return host instanceof JSElement;
  }
}
