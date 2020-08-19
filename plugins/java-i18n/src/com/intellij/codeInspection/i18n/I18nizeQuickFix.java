// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog.DialogCustomization;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class I18nizeQuickFix implements LocalQuickFix, I18nQuickFixHandler, HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(I18nizeQuickFix.class);
  private static final Set<String> AUXILIARY_WORDS = Set.of("is", "the", "of", "and", "a", "an");
  private final NlsInfo.Localized myInfo;
  private TextRange mySelectionRange;

  public I18nizeQuickFix(NlsInfo.Localized info) {
    myInfo = info;
  }

  public I18nizeQuickFix() {
    this(NlsInfo.localized());
  }

  @Override
  public final void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    doFix(descriptor, project);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
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
  public void performI18nization(final PsiFile psiFile,
                                 final Editor editor,
                                 UInjectionHost literalExpression,
                                 Collection<PropertiesFile> propertiesFiles,
                                 String key, String value, String i18nizedText,
                                 UExpression[] parameters,
                                 final PropertyCreationHandler propertyCreationHandler) throws IncorrectOperationException {
    Project project = psiFile.getProject();
    propertyCreationHandler.createProperty(project, propertiesFiles, key, value, parameters);
    try {
      doReplacement(psiFile, editor, literalExpression, i18nizedText);
    }
    catch (IncorrectOperationException e) {
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project,
                                                                                     JavaI18nBundle.message("inspection.i18n.expression.is.invalid.error.message"),
                                                                                     JavaI18nBundle.message("inspection.error.dialog.title")));
    }
  }

  @Override
  public JavaI18nizeQuickFixDialog createDialog(Project project, Editor editor, PsiFile psiFile) {
    UInjectionHost literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    return createDialog(project, psiFile, literalExpression);
  }

  private void doFix(final ProblemDescriptor descriptor, final Project project) {
    final PsiElement psi = descriptor.getPsiElement();
    UInjectionHost uast = UastContextKt.toUElement(psi, UInjectionHost.class);
    final PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
    if (!JavaI18nizeQuickFixDialog.isAvailable(psiFile)) {
      return;
    }
    final JavaI18nizeQuickFixDialog dialog = createDialog(project, psiFile, uast);
    if (!dialog.showAndGet()) {
      return;
    }
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();

    if (!FileModificationService.getInstance().preparePsiElementForWrite(psi)) return;
    for (PropertiesFile file : propertiesFiles) {
      if (file.findPropertyByKey(dialog.getKey()) == null &&
          !FileModificationService.getInstance().prepareFileForWrite(file.getContainingFile())) return;
    }

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        performI18nization(psiFile, PsiEditorUtil.findEditor(psiFile), dialog.getLiteralExpression(), propertiesFiles, dialog.getKey(),
                           dialog.getValue(), dialog.getI18nizedText(), dialog.getParameters(), dialog.getPropertyCreationHandler());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), PropertiesBundle.message("quickfix.i18n.command.name"), project);
  }

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

  protected static void doDocumentReplacement(@NotNull PsiFile psiFile,
                                              UElement literalExpression,
                                              String i18nizedText,
                                              Document document,
                                              @Nullable UastCodeGenerationPlugin generationPlugin) {
    PsiElement psi = literalExpression.getSourcePsi();
    if (psi == null) {
      return;
    }
    Language language = psi.getLanguage();
    int startOffset = psi.getTextRange().getStartOffset();
    document.replaceString(startOffset, psi.getTextRange().getEndOffset(), i18nizedText);
    PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);
    if (generationPlugin == null) {
      return;
    }
    PsiElement notShortenPsi =
      CodeInsightUtilCore.findElementInRange(psiFile, startOffset, startOffset + i18nizedText.length(), PsiElement.class, language);
    shortenReferences(notShortenPsi, generationPlugin);
  }

  private static void shortenReferences(@Nullable PsiElement element, @NotNull UastCodeGenerationPlugin generationPlugin) {
    UElement uElement = UastContextKt.toUElement(element);
    if (uElement == null) {
      return;
    }
    // Here we rely on that replacing same element will shorten references
    generationPlugin.replace(uElement, uElement, UElement.class);
  }

  protected JavaI18nizeQuickFixDialog createDialog(final Project project, final PsiFile context, final UInjectionHost literalExpression) {
    String value = StringUtil.notNullize(literalExpression.evaluateToString());
    if (mySelectionRange != null) {
      TextRange literalRange = literalExpression.getSourcePsi().getTextRange();
      TextRange intersection = literalRange.intersection(mySelectionRange);
      value = literalExpression.asSourceString().substring(intersection.getStartOffset() - literalRange.getStartOffset(), intersection.getEndOffset() - literalRange.getStartOffset());
    }
    return new JavaI18nizeQuickFixDialog(project, context, literalExpression, value, getCustomization(value), true, true);
  }

  @NotNull
  I18nizeQuickFixDialog.DialogCustomization getCustomization(String value) {
    return new DialogCustomization(null, true, false, null, getSuggestedName(value));
  }

  private String getSuggestedName(String value) {
    String prefix = myInfo.getPrefix();
    String suffix = myInfo.getSuffix();
    if (prefix.isEmpty() && suffix.isEmpty()) return null;
    if (!prefix.isEmpty()) {
      prefix += "."; 
    }
    if (!suffix.isEmpty()) {
      suffix = "." + suffix;
    }
    String payload = I18nizeQuickFixDialog.generateDefaultPropertyKey(value);
    payload = Stream.of(payload.split("\\."))
      .filter(s -> !s.matches("\\d+") && !AUXILIARY_WORDS.contains(s)).collect(Collectors.joining("."));
    return prefix + payload + suffix;
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
    int breakIndex = offset - literalRange.getStartOffset()-1;
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
