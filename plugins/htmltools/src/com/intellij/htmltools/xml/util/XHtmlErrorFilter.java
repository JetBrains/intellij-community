package com.intellij.htmltools.xml.util;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.parsing.XmlParserBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

final class XHtmlErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return true;

    if (InjectedLanguageManager.getInstance(containingFile.getProject()).isInjectedFragment(containingFile)
        && Objects.equals(XmlParserBundle.message("xml.parsing.multiple.root.tags"), element.getErrorDescription())) {
      return false;
    }

    return true;
  }
}
