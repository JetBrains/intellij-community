package com.intellij.htmltools.xml.util;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class XHtmlErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    PsiFile containingFile = element.getContainingFile();
    return containingFile.getLanguage() != XHTMLLanguage.INSTANCE ||
           !(InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile) instanceof PsiComment) ||
           !element.getErrorDescription().contains("Multiple root tags");
  }
}
