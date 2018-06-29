/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.injection.Injectable;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.util.StringLiteralReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @deprecated replaced by {@link ULiteralLanguageReference}. Will be removed in IDEA 2019.1
 */
@Deprecated
public final class LanguageReference extends StringLiteralReference {

  public LanguageReference(PsiLiteral value) {
    super(value);
  }

  @Nullable
  public PsiElement resolve() {
    return InjectedLanguage.findLanguageById(getValue()) != null ? myValue : null;
  }

  public boolean isSoft() {
    return false;
  }

  @NotNull
  public Object[] getVariants() {
    List<Injectable> list = InjectLanguageAction.getAllInjectables();
    return ContainerUtil.map2Array(list, LookupElement.class, (Function<Injectable, LookupElement>)injectable -> LookupElementBuilder.create(injectable.getId()).withIcon(injectable.getIcon()).withTailText(
      "(" + injectable.getDisplayName() + ")", true));
  }
}
