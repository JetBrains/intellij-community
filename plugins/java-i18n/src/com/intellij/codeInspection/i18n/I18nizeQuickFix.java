// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog.DialogCustomization;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class I18nizeQuickFix implements LocalQuickFix, I18nQuickFixHandler, HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(I18nizeQuickFix.class);
  private static final Set<String> AUXILIARY_WORDS = ContainerUtil.immutableSet("is", "the", "of", "and", "a", "an");
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
    PsiLiteralExpression literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    if (literalExpression != null) {
      SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) return;
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();
      TextRange textRange = literalExpression.getTextRange();
      if (textRange.contains(start) && textRange.contains(end)) {
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
                                 PsiLiteralExpression literalExpression,
                                 Collection<PropertiesFile> propertiesFiles,
                                 String key, String value, String i18nizedText,
                                 PsiExpression[] parameters,
                                 final PropertyCreationHandler propertyCreationHandler) throws IncorrectOperationException {
    Project project = psiFile.getProject();
    propertyCreationHandler.createProperty(project, propertiesFiles, key, value, parameters);
    try {
      final PsiElement newExpression = doReplacementInJava(psiFile, editor,literalExpression, i18nizedText);
      reformatAndCorrectReferences(newExpression);
    }
    catch (IncorrectOperationException e) {
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project,
                                                                                     JavaI18nBundle.message("inspection.i18n.expression.is.invalid.error.message"),
                                                                                     JavaI18nBundle.message("inspection.error.dialog.title")));
    }
  }

  @Override
  public JavaI18nizeQuickFixDialog createDialog(Project project, Editor editor, PsiFile psiFile) {
    final PsiLiteralExpression literalExpression = I18nizeAction.getEnclosingStringLiteral(psiFile, editor);
    return createDialog(project, psiFile, literalExpression);
  }

  private void doFix(final ProblemDescriptor descriptor, final Project project) {
    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)descriptor.getPsiElement();
    final PsiFile psiFile = literalExpression.getContainingFile();
    if (!JavaI18nizeQuickFixDialog.isAvailable(psiFile)) {
      return;
    }
    final JavaI18nizeQuickFixDialog dialog = createDialog(project, psiFile, literalExpression);
    if (!dialog.showAndGet()) {
      return;
    }
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();

    if (!FileModificationService.getInstance().preparePsiElementForWrite(literalExpression)) return;
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

  protected PsiElement doReplacementInJava(@NotNull final PsiFile psiFile,
                                           final Editor editor,
                                           final PsiLiteralExpression literalExpression,
                                           String i18nizedText) throws                                                                                                                            IncorrectOperationException {
    return replaceStringLiteral(literalExpression, i18nizedText);
  }

  private static void reformatAndCorrectReferences(PsiElement newExpression) throws IncorrectOperationException {
    final Project project = newExpression.getProject();
    newExpression = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newExpression);
    CodeStyleManager.getInstance(project).reformat(newExpression);
  }

  protected JavaI18nizeQuickFixDialog createDialog(final Project project, final PsiFile context, final PsiLiteralExpression literalExpression) {
    String value = StringUtil.notNullize((String)literalExpression.getValue());
    if (mySelectionRange != null) {
      TextRange literalRange = literalExpression.getTextRange();
      TextRange intersection = literalRange.intersection(mySelectionRange);
      value = literalExpression.getText().substring(intersection.getStartOffset() - literalRange.getStartOffset(), intersection.getEndOffset() - literalRange.getStartOffset());
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
  private static PsiBinaryExpression breakStringLiteral(PsiLiteralExpression literalExpression, int offset) throws IncorrectOperationException {
    TextRange literalRange = literalExpression.getTextRange();
    PsiElementFactory factory = JavaPsiFacade.getInstance(literalExpression.getProject()).getElementFactory();
    if (literalRange.getStartOffset()+1 < offset && offset < literalRange.getEndOffset()-1) {
      PsiBinaryExpression expression = (PsiBinaryExpression)factory.createExpressionFromText("a + b", literalExpression);
      String value = (String)literalExpression.getValue();
      int breakIndex = offset - literalRange.getStartOffset()-1;
      String lsubstring = value.substring(0, breakIndex);
      expression.getLOperand().replace(factory.createExpressionFromText("\""+lsubstring+"\"", literalExpression));
      String rsubstring = value.substring(breakIndex);
      expression.getROperand().replace(factory.createExpressionFromText("\""+rsubstring+"\"", literalExpression));
      return (PsiBinaryExpression)literalExpression.replace(expression);
    }

    return null;
  }

  private PsiElement replaceStringLiteral(PsiLiteralExpression literalExpression, String i18nizedText) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(literalExpression.getProject()).getElementFactory();
    if (mySelectionRange != null) {
      try {
        PsiBinaryExpression binaryExpression = breakStringLiteral(literalExpression, mySelectionRange.getEndOffset());
        if (binaryExpression != null) {
          literalExpression = (PsiLiteralExpression)binaryExpression.getLOperand();
        }
        binaryExpression = breakStringLiteral(literalExpression, mySelectionRange.getStartOffset());
        if (binaryExpression != null) {
          literalExpression = (PsiLiteralExpression)binaryExpression.getROperand();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    PsiExpression expression = factory.createExpressionFromText(i18nizedText, literalExpression);
    return literalExpression.replace(expression);
  }

}
