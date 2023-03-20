// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.psi.I18nizedTextGenerator;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PartiallyKnownString;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.util.UastExpressionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class I18nizeConcatenationQuickFix extends AbstractI18nizeQuickFix<UPolyadicExpression> {
  @NonNls public static final String PARAMETERS_OPTION_KEY = "PARAMETERS";

  public I18nizeConcatenationQuickFix(NlsInfo.Localized info) {
    super(info);
  }

  @Override
  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
    UPolyadicExpression concatenation = getEnclosingLiteral(psiFile, editor);
    if (concatenation != null) return;
    String message = JavaI18nBundle.message("quickfix.i18n.concatentation.error");
    throw new IncorrectOperationException(message);
  }

  @Override
  public UPolyadicExpression getEnclosingLiteral(PsiFile file, Editor editor) {
    final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    return getEnclosingLiteralConcatenation(elementAt);
  }

  @Override
  public JavaI18nizeQuickFixDialog<UPolyadicExpression> createDialog(Project project, Editor editor, PsiFile psiFile) {
    UPolyadicExpression concatenation = getEnclosingLiteral(psiFile, editor);
    return concatenation == null ? null : createDialog(project, psiFile, concatenation);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaI18nBundle.message("quickfix.i18n.concatentation");
  }

  @Override
  protected void doReplacement(@NotNull final PsiFile psiFile,
                               @NotNull final Editor editor,
                               @Nullable UPolyadicExpression literalExpression,
                               String i18nizedText) throws IncorrectOperationException {
    @Nullable UPolyadicExpression concatenation = getEnclosingLiteralConcatenation(literalExpression);
    assert concatenation != null;
    UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(psiFile.getLanguage());
    doDocumentReplacement(psiFile, concatenation, i18nizedText, editor.getDocument(), generationPlugin);
  }

  @Override
  protected JavaI18nizeQuickFixDialog<UPolyadicExpression> createDialog(final Project project,
                                                                        final PsiFile context,
                                                                        @NotNull UPolyadicExpression concatenation) {
    final List<UExpression> args = new ArrayList<>();
    String formatString = JavaI18nUtil
      .buildUnescapedFormatString(Objects.requireNonNull(UStringConcatenationsFacade.createFromTopConcatenation(concatenation)), args, project);

    return new JavaI18nizeQuickFixDialog<>(project, context, concatenation, formatString, getCustomization(formatString), true, true) {
      @Override
      @Nullable
      protected String getTemplateName() {
        return myResourceBundleManager.getConcatenationTemplateName();
      }

      @Override
      protected String generateText(final I18nizedTextGenerator textGenerator,
                                    final @NotNull String propertyKey,
                                    final PropertiesFile propertiesFile,
                                    final PsiElement context) {
        return textGenerator.getI18nizedConcatenationText(
          propertyKey,
          JavaI18nUtil.composeParametersText(args),
          propertiesFile,
          concatenation.getSourcePsi()
        );
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

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    UPolyadicExpression concatenation = getEnclosingLiteralConcatenation(descriptor.getPsiElement());
    if (concatenation == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    final List<UExpression> args = new ArrayList<>();
    @NlsSafe
    String string = JavaI18nUtil
      .buildUnescapedFormatString(Objects.requireNonNull(UStringConcatenationsFacade.createFromTopConcatenation(concatenation)), args, project);
    return new IntentionPreviewInfo.Html(new HtmlBuilder().append(JavaI18nBundle.message("i18n.quickfix.preview.description"))
                                           .br().append(HtmlChunk.text(string).code()).toFragment());
  }


  @Nullable
  public static UPolyadicExpression getEnclosingLiteralConcatenation(final PsiElement psiElement) {
    return getEnclosingLiteralConcatenation(UastContextKt.getUastParentOfType(psiElement, UPolyadicExpression.class));
  }

  public static @Nullable UPolyadicExpression getEnclosingLiteralConcatenation(@Nullable UExpression literalExpression) {
    if (literalExpression == null) {
      return null;
    }
    UExpression topExpression = UastUtils.getParentOfType(literalExpression, UPolyadicExpression.class, false);
    UStringConcatenationsFacade concatenation = null;
    while (topExpression != null) {
      UStringConcatenationsFacade nextConcatenation = UStringConcatenationsFacade.createFromTopConcatenation(topExpression);
      if (nextConcatenation != null) {
        concatenation = nextConcatenation;
      }
      UElement parent = topExpression.getUastParent();
      if (parent instanceof UParenthesizedExpression ||
          parent instanceof UIfExpression ||
          parent instanceof UPolyadicExpression && !UastExpressionUtils.isAssignment(parent)) {
        topExpression = (UExpression)parent;
      }
      else {
        break;
      }
    }

    if (concatenation != null) {
      PartiallyKnownString pks = concatenation.asPartiallyKnownString();
      if (pks.getSegments().size() == 1) {
        return null;
      }
      return (UPolyadicExpression)concatenation.getRootUExpression();
    }
    return null;
  }
}
