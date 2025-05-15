// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
final class FrankensteinErrorFilter extends HighlightErrorFilter implements HighlightInfoFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    return !isFrankenstein(element.getContainingFile());
  }

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile psiFile) {
    if (highlightInfo.getSeverity() != HighlightSeverity.WARNING &&
        highlightInfo.getSeverity() != HighlightSeverity.WEAK_WARNING) return true;
    if (!isFrankenstein(psiFile)) return true;
    int start = highlightInfo.getStartOffset();
    int end = highlightInfo.getEndOffset();
    String text = psiFile.getText().substring(start, end);
    return !"missingValue".equals(text);
  }

  private static boolean isFrankenstein(@Nullable PsiFile file) {
    return file != null && InjectedLanguageManager.getInstance(file.getProject()).isFrankensteinInjection(file);
  }
}
