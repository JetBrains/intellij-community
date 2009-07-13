package org.intellij.plugins.intelliLang.inject;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;

/**
 * @author Gregory.Shrago
 */
public interface LanguageInjectorSupport {
  ExtensionPointName<LanguageInjectorSupport> EP_NAME = ExtensionPointName.create("org.intellij.intelliLang.LanguageInjectorSupport");

  Key<Boolean> HAS_UNPARSABLE_FRAGMENTS = Key.create("HAS_UNPARSABLE_FRAGMENTS");

  boolean isAvailable(final PsiElement psiElement);

}
