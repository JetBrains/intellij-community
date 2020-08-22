// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.psi.I18nizedTextGenerator;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PartiallyKnownString;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class I18nizeConcatenationQuickFix extends I18nizeQuickFix {
  @NonNls public static final String PARAMETERS_OPTION_KEY = "PARAMETERS";

  public I18nizeConcatenationQuickFix(NlsInfo.Localized info) {
    super(info);
  }

  @Override
  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
    @Nullable UPolyadicExpression concatenation = getEnclosingLiteralConcatenation(psiFile, editor);
    if (concatenation != null) return;
    String message = JavaI18nBundle.message("quickfix.i18n.concatentation.error");
    throw new IncorrectOperationException(message);
  }

  @Override
  public JavaI18nizeQuickFixDialog createDialog(Project project, Editor editor, PsiFile psiFile) {
    @Nullable UPolyadicExpression concatenation = getEnclosingLiteralConcatenation(psiFile, editor);
    assert concatenation != null;
    UInjectionHost literalExpression = getContainingLiteral(concatenation);
    if (literalExpression == null) return null;
    return createDialog(project, psiFile, literalExpression);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaI18nBundle.message("quickfix.i18n.concatentation");
  }

  @Override
  protected void doReplacement(@NotNull final PsiFile psiFile,
                               @NotNull final Editor editor,
                               @Nullable UInjectionHost literalExpression,
                               String i18nizedText) throws IncorrectOperationException {
    @Nullable UPolyadicExpression concatenation = getEnclosingLiteralConcatenation(psiFile, editor);
    assert concatenation != null;
    UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(psiFile.getLanguage());
    doDocumentReplacement(psiFile, concatenation, i18nizedText, editor.getDocument(), generationPlugin);
  }

  @Override
  protected JavaI18nizeQuickFixDialog createDialog(final Project project, final PsiFile context, final UInjectionHost literalExpression) {
    final List<UExpression> args = new ArrayList<>();
    UExpression expression = getEnclosingLiteralConcatenation(literalExpression.getSourcePsi());
    String formatString = JavaI18nUtil
      .buildUnescapedFormatString(Objects.requireNonNull(UStringConcatenationsFacade.createFromTopConcatenation(expression)), args, project);

    return new JavaI18nizeQuickFixDialog(project, context, literalExpression, formatString, getCustomization(formatString), true, true) {
      @Override
      @Nullable
      protected String getTemplateName() {
        return myResourceBundleManager.getConcatenationTemplateName();
      }

      @Override
      protected String generateText(final I18nizedTextGenerator textGenerator, final @NotNull String propertyKey, final PropertiesFile propertiesFile,
                                    final PsiElement context) {
        return textGenerator.getI18nizedConcatenationText(propertyKey, JavaI18nUtil.composeParametersText(args), propertiesFile, literalExpression.getSourcePsi());
      }

      @Override
      public UExpression[] getParameters() {
        return args.toArray(new UExpression[0]);
      }

      @Override
      protected void addAdditionalAttributes(final Map<String, String> attributes) {
        attributes.put(PARAMETERS_OPTION_KEY, JavaI18nUtil.composeParametersText(args));
      }
    };
  }

  private static @Nullable UPolyadicExpression getEnclosingLiteralConcatenation(@NotNull PsiFile file, @NotNull Editor editor) {
    final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    return getEnclosingLiteralConcatenation(elementAt);
  }

  @Nullable
  public static UPolyadicExpression getEnclosingLiteralConcatenation(final PsiElement psiElement) {
    UExpression topExpression = UastContextKt.getUastParentOfType(psiElement, UPolyadicExpression.class);
    while (topExpression != null) {
      UElement parent = topExpression.getUastParent();
      if (parent instanceof UParenthesizedExpression || 
          parent instanceof UIfExpression || 
          parent instanceof UPolyadicExpression) {
        topExpression = (UExpression)parent;
      }
      else {
        break;
      }
    }
    UStringConcatenationsFacade concatenation = UStringConcatenationsFacade.createFromTopConcatenation(topExpression);
    if (concatenation != null) {
      PartiallyKnownString pks = concatenation.asPartiallyKnownString();
      if (pks.getSegments().size() == 1) {
        return null;
      }
      return (UPolyadicExpression)concatenation.getRootUExpression();
    }
    return null;
  }

  private static UInjectionHost getContainingLiteral(final UPolyadicExpression concatenation) {
    for (UExpression operand : concatenation.getOperands()) {
      if (operand instanceof UInjectionHost) {
        return (UInjectionHost)operand;
      }
    }
    return null;
  }
}
