/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return LookupElementBuilder.create(o.clazz, o.id)
      .withIcon(o.icon)
      .withTailText(o.displayName == null ? null : " (" + o.displayName + ")")
      .withTypeText(o.type, true);
  }

  @Nullable
  @Override
  public LanguageResolvingUtil.LanguageDefinition fromString(@Nullable @NonNls final String s, ConvertContext context) {
    return ContainerUtil.find(getVariants(context), new Condition<LanguageResolvingUtil.LanguageDefinition>() {
      @Override
      public boolean value(LanguageResolvingUtil.LanguageDefinition definition) {
        return definition.id.equals(s);
      }
    });
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
    return "Cannot resolve language with id ''" + s + "''";
  }
}
