// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.uast.UastPatterns.injectionHostUExpression;

class AdvancedSettingsIdContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    UastReferenceRegistrar
      .registerUastReferenceProvider(registrar,
                                     injectionHostUExpression().methodCallParameter(0, psiMethod()
                                       .withName(string().oneOf(
                                         "getBoolean", "getInt", "getString", "getEnum",
                                         "getDefaultBoolean", "getDefaultInt", "getDefaultString", "getDefaultEnum",
                                         "setBoolean", "setInt", "setString", "setEnum")
                                       )
                                       .definedInClass(AdvancedSettings.class.getName())),
                                     UastReferenceRegistrar.uastInjectionHostReferenceProvider(
                                       (expression, host) -> new PsiReference[]{new AdvancedSettingReference(host)}),
                                     PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  static class AdvancedSettingReference extends ExtensionPointReferenceBase {

    AdvancedSettingReference(PsiElement element) {
      super(element);
    }

    AdvancedSettingReference(PsiElement element, TextRange range) {
      super(element, range);
    }

    @Override
    protected String getExtensionPointFqn() {
      return "com.intellij.advancedSetting";
    }

    @Override
    public @NotNull String getUnresolvedMessagePattern() {
      return DevKitBundle.message("message.bundle.convert.advanced.setting.id.cannot.resolve", getValue());
    }

    @Override
    public Object @NotNull [] getVariants() {
      final List<LookupElement> variants = Collections.synchronizedList(new SmartList<>());
      processCandidates(extension -> {
        final GenericAttributeValue<String> id = extension.getId();
        if (id == null || extension.getXmlElement() == null) return true;

        final String value = id.getStringValue();
        if (value == null) return true;

        variants.add(LookupElementBuilder.create(extension.getXmlElement(), value)
                       .withIcon(AllIcons.General.Settings)
                       .withTypeText(StringUtil.notNullize(getAttributeValue(extension, "default"))));
        return true;
      });
      return variants.toArray(LookupElement.EMPTY_ARRAY);
    }
  }
}