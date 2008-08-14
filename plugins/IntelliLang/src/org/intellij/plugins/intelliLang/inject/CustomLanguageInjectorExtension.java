package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.PairProcessor;
import org.intellij.plugins.intelliLang.Configuration;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Gregory.Shrago
 */
public interface CustomLanguageInjectorExtension {
  ExtensionPointName<CustomLanguageInjectorExtension> EP_NAME = ExtensionPointName.create("org.intellij.intelliLang.CustomLanguageInjectorExtension");

  void getInjectedLanguage(final Configuration configuration, final PsiElement place, final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor);

  void elementsToInjectIn(final ArrayList<Class<? extends PsiElement>> result);
}
