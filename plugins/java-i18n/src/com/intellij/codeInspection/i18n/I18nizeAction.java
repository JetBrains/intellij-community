// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.expressions.UInjectionHost;

import java.util.Collection;
import java.util.Optional;

public class I18nizeAction extends AnAction implements UpdateInBackground {
  private static final Logger LOG = Logger.getInstance(I18nizeAction.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean active = getHandler(e) != null;
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(active);
    }
    else {
      e.getPresentation().setEnabled(active);
    }
  }

  @Nullable
  public static I18nQuickFixHandler<?> getHandler(final AnActionEvent e) {
    final Editor editor = getEditor(e);
    if (editor == null) return null;

    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (psiFile == null) return null;

    return getHandler(editor, psiFile);
  }

  @Nullable
  public static I18nQuickFixHandler<?> getHandler(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    TextRange range = JavaI18nUtil.getSelectedRange(editor, psiFile);
    if (range == null) return null;

    final UInjectionHost literalExpression = getEnclosingStringLiteral(psiFile, editor);
    NlsInfo.Localized localized = NlsInfo.localized();
    if (literalExpression != null) {
      NlsInfo info = NlsInfo.forExpression(literalExpression);
      if (info instanceof NlsInfo.Localized) {
        localized = (NlsInfo.Localized)info;
      }
    }
    PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(element) != null) {
      return new I18nizeConcatenationQuickFix(localized);
    }
    else if (Optional.ofNullable(literalExpression).map(UastUtils::getTextRange).map(it -> it.contains(range)).orElse(false)) {
      return new I18nizeQuickFix(localized);
    }

    for (I18nizeHandlerProvider handlerProvider : I18nizeHandlerProvider.EP_NAME.getExtensions()) {
      I18nQuickFixHandler<?> handler = handlerProvider.getHandler(psiFile, editor, range);
      if (handler != null) {
        return handler;
      }
    }

    return null;
  }

  @Nullable
  public static UInjectionHost getEnclosingStringLiteral(final PsiFile psiFile, final Editor editor) {
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    return getEnclosingStringLiteral(psiElement);
  }

  /**
   * @param psiElement element to search from
   * @return UAST element representing an enclosing string literal
   */
  @Nullable
  public static UInjectionHost getEnclosingStringLiteral(PsiElement psiElement) {
    while (psiElement != null) {
      UInjectionHost uastStringLiteral = UastContextKt.toUElement(psiElement, UInjectionHost.class);
      if (uastStringLiteral != null && uastStringLiteral.isString()) {
        return uastStringLiteral;
      }
      psiElement = psiElement.getParent();
    }
    return null;
  }

  private static Editor getEditor(final AnActionEvent e) {
    return e.getData(CommonDataKeys.EDITOR);
  }

  public static <T extends UExpression> void doI18nSelectedString(final @NotNull Project project,
                                                                  final @NotNull Editor editor,
                                                                  final @NotNull PsiFile psiFile,
                                                                  final @NotNull I18nQuickFixHandler<T> handler) {
    try {
      handler.checkApplicability(psiFile, editor);
    }
    catch (IncorrectOperationException ex) {
      CommonRefactoringUtil.showErrorHint(project, editor, ex.getMessage(), JavaI18nBundle.message("i18nize.error.title"), null);
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      JavaI18nizeQuickFixDialog.isAvailable(psiFile);
    }

    try {
      ResourceBundleManager.getManager(psiFile);
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      return;
    }

    final JavaI18nizeQuickFixDialog<T> dialog = handler.createDialog(project, editor, psiFile);
    if (dialog == null) return;
    if (!dialog.showAndGet()) {
      return;
    }

    if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();
    for (PropertiesFile file : propertiesFiles) {
      if (!FileModificationService.getInstance().prepareFileForWrite(file.getContainingFile())) return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, () -> {
      try {
        handler.performI18nization(psiFile, editor, dialog.getLiteralExpression(), propertiesFiles, dialog.getKey(),
                                   StringUtil.unescapeStringCharacters(dialog.getValue()),
                                   dialog.getI18nizedText(), dialog.getParameters(),
                                   dialog.getPropertyCreationHandler());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }, PropertiesBundle.message("quickfix.i18n.command.name"), project));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Editor editor = getEditor(e);
    final Project project = editor.getProject();
    assert project != null;
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (psiFile == null) return;
    final I18nQuickFixHandler<?> handler = getHandler(e);
    if (handler == null) return;

    doI18nSelectedString(project, editor, psiFile, handler);
  }

}
