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
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.util.StringLiteralReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides completion for available Language-IDs in
 * <pre>@Language("[ctrl-space]")</pre>
 */
final class LanguageReference extends StringLiteralReference {

  public LanguageReference(PsiLiteralExpression value) {
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
    final String[] ids = InjectedLanguage.getAvailableLanguageIDs();
    return ContainerUtil.map2Array(ids, LookupElement.class, new Function<String, LookupElement>() {
      public LookupElement fun(String s) {
        final Language l = InjectedLanguage.findLanguageById(s);
        assert l != null;

        final FileType ft = l.getAssociatedFileType();
        if (ft != null) {
          return LookupElementBuilder.create(s).setIcon(ft.getIcon()).setTypeText(ft.getDescription());
//                } else if (l == StdLanguages.EL) {
//                    // IDEA-10012
//                    return new LanguageLookupValue(s, StdFileTypes.JSP.getIcon(), "Expression Language");
        }
        return LookupElementBuilder.create(s).setIcon(new EmptyIcon(16));
      }
    });
  }

}
