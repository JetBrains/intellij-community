// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull Collection<LanguageResolvingUtil.LanguageDefinition> getVariants(final @NotNull ConvertContext context) {
    return LanguageResolvingUtil.getAllLanguageDefinitions(context);
  }

  @Override
  public @Nullable LookupElement createLookupElement(LanguageResolvingUtil.LanguageDefinition o) {
    String displayName = o.displayName.get();
    return LookupElementBuilder.create(o.clazz, o.id)
      .withIcon(o.icon)
      .withTailText(displayName == null ? null : " (" + displayName + ")")
      .withTypeText(o.type, true);
  }

  @Override
  public @Nullable LanguageResolvingUtil.LanguageDefinition fromString(final @Nullable @NonNls String s, @NotNull ConvertContext context) {
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

  @Override
  public @Nullable PsiElement getPsiElement(@Nullable LanguageResolvingUtil.LanguageDefinition resolvedValue) {
    return resolvedValue != null ? resolvedValue.clazz : null;
  }

  @Override
  public @Nullable String toString(@Nullable LanguageResolvingUtil.LanguageDefinition o, @NotNull ConvertContext context) {
    return o != null ? o.id : null;
  }

  @Override
  public String getErrorMessage(@Nullable String s, @NotNull ConvertContext context) {
    return DevKitBundle.message("plugin.xml.convert.language.id.cannot.resolve", s);
  }
}
