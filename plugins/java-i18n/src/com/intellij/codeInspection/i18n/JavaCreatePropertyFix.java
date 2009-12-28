/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.CreatePropertyFix;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class JavaCreatePropertyFix extends CreatePropertyFix {
  public JavaCreatePropertyFix() {}

  public JavaCreatePropertyFix(PsiElement element, String key, final List<PropertiesFile> propertiesFiles) {
    super(element, key, propertiesFiles);
  }

  @Nullable
  protected static Pair<String, String> invokeAction(@NotNull final Project project,
                                                     @NotNull PsiFile file,
                                                     @NotNull PsiElement psiElement,
                                                     @Nullable final String suggestedKey,
                                                     @Nullable String suggestedValue,
                                                     @Nullable final List<PropertiesFile> propertiesFiles) {
    final PsiLiteralExpression literalExpression = psiElement instanceof PsiLiteralExpression ? (PsiLiteralExpression)psiElement : null;
    final String propertyValue = suggestedValue == null ? "" : suggestedValue;

    final I18nizeQuickFixDialog dialog = new JavaI18nizeQuickFixDialog(
      project,
      file,
      literalExpression,
      propertyValue,
      createDefaultCustomization(suggestedKey, propertiesFiles),
      false,
      false
    );
    return doAction(project, psiElement, dialog);
  }

}
