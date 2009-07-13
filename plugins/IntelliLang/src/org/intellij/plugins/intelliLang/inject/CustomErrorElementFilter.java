package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public class CustomErrorElementFilter extends HighlightErrorFilter {
  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    return !value(element);
  }

  public static boolean value(final PsiErrorElement psiErrorElement) {
    final PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(psiErrorElement.getProject()).getInjectionHost(psiErrorElement);
    return host != null && Boolean.TRUE.equals(host.getUserData(LanguageInjectorSupport.HAS_UNPARSABLE_FRAGMENTS));
  }
}
