// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.XmlElementPattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class ScriptLanguageInjector implements MultiHostInjector {
  private static final class Holder {
    private static final XmlElementPattern.XmlTextPattern SCRIPT_PATTERN = XmlPatterns.xmlText().withParent(
      XmlPatterns.xmlTag().withName(FxmlConstants.FX_SCRIPT));
  }

  @Override
  public void getLanguagesToInject(final @NotNull MultiHostRegistrar registrar, final @NotNull PsiElement host) {
    if (Holder.SCRIPT_PATTERN.accepts(host)) {
      final List<String> registeredLanguages = JavaFxPsiUtil.parseInjectedLanguages((XmlFile)host.getContainingFile());
      for (Language language : Language.getRegisteredLanguages()) {
        for (String registeredLanguage : registeredLanguages) {
          if (StringUtil.equalsIgnoreCase(language.getID(), registeredLanguage)) {
            registrar.startInjecting(language)
              .addPlace(null, null, (PsiLanguageInjectionHost) host,
                        TextRange.from(0, host.getTextLength() - 1))
              .doneInjecting();
            break;
          }
        }
      }
    }
  }

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(XmlText.class);
  }

}