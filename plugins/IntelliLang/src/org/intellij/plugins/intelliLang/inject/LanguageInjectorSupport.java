package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiLanguageInjectionHost;

/**
 * @author Gregory.Shrago
 */
public interface LanguageInjectorSupport {
  ExtensionPointName<LanguageInjectorSupport> EP_NAME = ExtensionPointName.create("org.intellij.intelliLang.languageSupport");

  Key<Boolean> HAS_UNPARSABLE_FRAGMENTS = Key.create("HAS_UNPARSABLE_FRAGMENTS");

  boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement);

  boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement);

  boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement);

}
