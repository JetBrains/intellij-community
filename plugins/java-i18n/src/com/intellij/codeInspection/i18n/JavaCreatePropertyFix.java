// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.CreatePropertyFix;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.lang.properties.references.I18nizeQuickFixModel;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.expressions.UInjectionHost;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class JavaCreatePropertyFix extends CreatePropertyFix {
  private static final Logger LOG = Logger.getInstance(JavaCreatePropertyFix.class);

  public JavaCreatePropertyFix() {}

  public JavaCreatePropertyFix(PsiElement element, String key, final List<PropertiesFile> propertiesFiles) {
    super(element, key, propertiesFiles);
  }

  @Override
  protected Couple<String> doAction(Project project, PsiElement psiElement, I18nizeQuickFixModel model) {
    final Couple<String> result = super.doAction(project, psiElement, model);
    if (result != null && psiElement instanceof PsiLiteralExpression) {
      final String key = result.first;

      final StringBuilder buffer = new StringBuilder();
      buffer.append('"');
      StringUtil.escapeStringCharacters(key.length(), key, buffer);
      buffer.append('"');

      try {
        WriteAction.run(() -> {
          final PsiExpression newKeyLiteral = JavaPsiFacade.getElementFactory(project).createExpressionFromText(buffer.toString(), null);
          psiElement.replace(newKeyLiteral);
        });
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return result;
  }

  protected @Nullable Couple<String> invokeAction(final @NotNull Project project,
                                                  @NotNull PsiFile file,
                                                  @NotNull PsiElement psiElement,
                                                  final @Nullable String suggestedKey,
                                                  @Nullable String suggestedValue,
                                                  final @Nullable List<PropertiesFile> propertiesFiles) {
    final PsiLiteralExpression literalExpression = psiElement instanceof PsiLiteralExpression ? (PsiLiteralExpression)psiElement : null;
    final String propertyValue = suggestedValue == null ? "" : suggestedValue;

    final I18nizeQuickFixDialog dialog = new JavaI18nizeQuickFixDialog<>(
      project,
      file,
      UastContextKt.toUElement(literalExpression, UInjectionHost.class),
      propertyValue,
      createDefaultCustomization(suggestedKey, propertiesFiles),
      false,
      false
    );
    return doAction(project, psiElement, dialog);
  }
}
