// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastBinaryOperator;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;

import java.util.Objects;

public class I18nizeQuickFix extends AbstractI18nizeQuickFix<UInjectionHost> {
  private static final Logger LOG = Logger.getInstance(I18nizeQuickFix.class);
  private TextRange mySelectionRange;

  public I18nizeQuickFix(NlsInfo.Localized info) {
    super(info);
  }

  public I18nizeQuickFix() {
    this(NlsInfo.localized());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaI18nBundle.message("inspection.i18n.quickfix");
  }

  @Override
  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
    UInjectionHost literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    if (literalExpression != null) {
      SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) return;
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();
      TextRange textRange = UastUtils.getTextRange(literalExpression);
      if (textRange != null && textRange.contains(start) && textRange.contains(end)) {
        mySelectionRange = new TextRange(start, end);
        return;
      }
    }
    String message = JavaI18nBundle.message("i18nize.error.message");
    throw new IncorrectOperationException(message);
  }

  @Override
  public UInjectionHost getEnclosingLiteral(PsiFile file, Editor editor) {
    return I18nizeAction.getEnclosingStringLiteral(file, editor);
  }

  @Override
  public JavaI18nizeQuickFixDialog<UInjectionHost> createDialog(Project project, Editor editor, PsiFile psiFile) {
    UInjectionHost literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    return createDialog(project, psiFile, literalExpression);
  }

  @Override
  protected void doReplacement(@NotNull PsiFile psiFile,
                               Editor editor,
                               UInjectionHost literalExpression,
                               String i18nizedText) throws IncorrectOperationException {
    UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(literalExpression.getLang());
    Document document = editor.getDocument();
    if (mySelectionRange != null && generationPlugin != null) {
      UastElementFactory elementFactory = generationPlugin.getElementFactory(psiFile.getProject());
      try {
        UBinaryExpression binaryExpression = breakStringLiteral(literalExpression, generationPlugin, elementFactory, mySelectionRange.getEndOffset());
        if (binaryExpression != null) {
          literalExpression = (UInjectionHost)binaryExpression.getLeftOperand();
        }
        binaryExpression = breakStringLiteral(literalExpression, generationPlugin, elementFactory, mySelectionRange.getStartOffset());
        if (binaryExpression != null) {
          literalExpression = (UInjectionHost)binaryExpression.getRightOperand();
        }
        PsiDocumentManager.getInstance(psiFile.getProject()).doPostponedOperationsAndUnblockDocument(document);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    doDocumentReplacement(psiFile, literalExpression, i18nizedText, document, generationPlugin);
  }

  @Override
  protected JavaI18nizeQuickFixDialog<UInjectionHost> createDialog(Project project,
                                                                   PsiFile context,
                                                                   @NotNull UInjectionHost literalExpression) {
    String value = getStringToExtract(literalExpression);
    return new JavaI18nizeQuickFixDialog<>(project, context, literalExpression, value, getCustomization(value), true, true);
  }

  private @NotNull @NlsSafe String getStringToExtract(@NotNull UInjectionHost literalExpression) {
    if (mySelectionRange != null) {
      TextRange literalRange = Objects.requireNonNull(literalExpression.getSourcePsi()).getTextRange();
      TextRange intersection = literalRange.intersection(mySelectionRange);
      return literalExpression.asSourceString().substring(intersection.getStartOffset() - literalRange.getStartOffset(),
                                                             intersection.getEndOffset() - literalRange.getStartOffset());
    }
    return StringUtil.notNullize(literalExpression.evaluateToString());
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    UInjectionHost literal = I18nizeAction.getEnclosingStringLiteral(element);
    if (literal == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    String string = getStringToExtract(literal);
    return new IntentionPreviewInfo.Html(new HtmlBuilder().append(JavaI18nBundle.message("i18n.quickfix.preview.description"))
                                           .br().append(HtmlChunk.text(string).code()).toFragment());
  }

  @Nullable
  private static UBinaryExpression breakStringLiteral(@NotNull UInjectionHost literalExpression,
                                                      UastCodeGenerationPlugin generationPlugin,
                                                      UastElementFactory elementFactory,
                                                      int offset) throws IncorrectOperationException {
    PsiElement sourcePsi = literalExpression.getSourcePsi();
    if (sourcePsi == null) {
      return null;
    }
    TextRange literalRange = sourcePsi.getTextRange();
    String value = literalExpression.evaluateToString();
    if (literalRange.getStartOffset() + 1 >= offset || offset >= literalRange.getEndOffset() - 1 || value == null) {
      return null;
    }
    int breakIndex = offset - literalRange.getStartOffset() - 1;
    String lsubstring = value.substring(0, breakIndex);
    String rsubstring = value.substring(breakIndex);
    ULiteralExpression left = elementFactory.createStringLiteralExpression(lsubstring, sourcePsi);
    ULiteralExpression right = elementFactory.createStringLiteralExpression(rsubstring, sourcePsi);
    if (left == null || right == null) {
      return null;
    }
    UBinaryExpression binaryExpression = elementFactory.createBinaryExpression(left, right, UastBinaryOperator.PLUS, sourcePsi);
    if (binaryExpression == null) {
      return null;
    }
    return generationPlugin.replace(literalExpression, binaryExpression, UBinaryExpression.class);
  }
}
