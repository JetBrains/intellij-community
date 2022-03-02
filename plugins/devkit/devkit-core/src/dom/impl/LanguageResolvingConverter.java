// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.Collection;

class LanguageResolvingConverter extends ResolvingConverter<LanguageResolvingUtil.LanguageDefinition> {

  @NotNull
  @Override
  public Collection<LanguageResolvingUtil.LanguageDefinition> getVariants(final ConvertContext context) {
    return LanguageResolvingUtil.getAllLanguageDefinitions(context);
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(LanguageResolvingUtil.LanguageDefinition o) {
    String displayName = o.displayName.get();
    return LookupElementBuilder.create(o.clazz, o.id)
      .withIcon(o.icon)
      .withTailText(displayName == null ? null : " (" + displayName + ")")
      .withTypeText(o.type, true);
  }

  @Nullable
  @Override
  public LanguageResolvingUtil.LanguageDefinition fromString(@Nullable @NonNls final String s, ConvertContext context) {
    Ref<LanguageResolvingUtil.LanguageDefinition> result = new Ref<>();
    LanguageResolvingUtil.processAllLanguageDefinitions(context, definition -> {
      if (definition.id.equals(s)) {
        result.set(definition);
        return false;
      }
      return true;
    });
    return result.get();
  }

  @Nullable
  @Override
  public PsiElement getPsiElement(@Nullable LanguageResolvingUtil.LanguageDefinition resolvedValue) {
    return resolvedValue != null ? resolvedValue.clazz : null;
  }

  @Nullable
  @Override
  public String toString(@Nullable LanguageResolvingUtil.LanguageDefinition o, ConvertContext context) {
    return o != null ? o.id : null;
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return DevKitBundle.message("plugin.xml.convert.language.id.cannot.resolve", s);
  }
}
