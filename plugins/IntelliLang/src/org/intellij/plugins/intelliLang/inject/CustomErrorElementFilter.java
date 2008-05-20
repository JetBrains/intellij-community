package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiLanguageInjectionHost;

/**
 * @author Gregory.Shrago
 */
public class CustomErrorElementFilter implements Condition<PsiErrorElement> {
  public boolean value(final PsiErrorElement psiErrorElement) {
    final PsiLanguageInjectionHost host =
        InjectedLanguageManager.getInstance(psiErrorElement.getProject()).getInjectionHost(psiErrorElement);
    if (host != null && Boolean.TRUE.equals(host.getUserData(CustomLanguageInjector.HAS_UPARSABLE_FRAGMENTS))) {
      return true;
    }
    return false;
  }
}
