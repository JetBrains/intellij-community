/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class I18nizeAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeAction");

  @Override
  public void update(AnActionEvent e) {
    boolean active = getHandler(e) != null;
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(active);
    }
    else {
      e.getPresentation().setEnabled(active);
    }
  }

  @Nullable
  public static I18nQuickFixHandler getHandler(final AnActionEvent e) {
    final Editor editor = getEditor(e);
    if (editor == null) return null;

    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (psiFile == null) return null;

    TextRange range = JavaI18nUtil.getSelectedRange(editor, psiFile);
    if (range == null) return null;

    final PsiLiteralExpression literalExpression = getEnclosingStringLiteral(psiFile, editor);
    PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;
    if (I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(element) != null) {
      return new I18nizeConcatenationQuickFix();
    }
    else if (literalExpression != null && literalExpression.getTextRange().contains(range)) {
      return new I18nizeQuickFix();
    }

    for (I18nizeHandlerProvider handlerProvider : I18nizeHandlerProvider.EP_NAME.getExtensions()) {
      I18nQuickFixHandler handler = handlerProvider.getHandler(psiFile, editor, range);
      if (handler != null) {
        return handler;
      }
    }

    return null;
  }


  @Nullable
  public static PsiLiteralExpression getEnclosingStringLiteral(final PsiFile psiFile, final Editor editor) {
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement == null) return null;
    PsiLiteralExpression expression = PsiTreeUtil.getParentOfType(psiElement, PsiLiteralExpression.class);
    if (expression == null || !(expression.getValue() instanceof String)) return null;
    return expression;
  }

  private static Editor getEditor(final AnActionEvent e) {
    return CommonDataKeys.EDITOR.getData(e.getDataContext());
  }

  public static void doI18nSelectedString(final @NotNull Project project,
                                          final @NotNull Editor editor,
                                          final @NotNull PsiFile psiFile,
                                          final @NotNull I18nQuickFixHandler handler) {
    try {
      handler.checkApplicability(psiFile, editor);
    }
    catch (IncorrectOperationException ex) {
      CommonRefactoringUtil.showErrorHint(project, editor, ex.getMessage(), CodeInsightBundle.message("i18nize.error.title"), null);
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

    final JavaI18nizeQuickFixDialog dialog = handler.createDialog(project, editor, psiFile);
    if (dialog == null) return;
    if (!dialog.showAndGet()) {
      return;
    }

    if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();
    for (PropertiesFile file : propertiesFiles) {
      if (!FileModificationService.getInstance().prepareFileForWrite(file.getContainingFile())) return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            try {
              handler.performI18nization(psiFile, editor, dialog.getLiteralExpression(), propertiesFiles, dialog.getKey(),
                                         StringUtil.unescapeStringCharacters(dialog.getValue()),
                                         dialog.getI18nizedText(), dialog.getParameters(),
                                         dialog.getPropertyCreationHandler());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }, CodeInsightBundle.message("quickfix.i18n.command.name"), project);
      }
    });
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Editor editor = getEditor(e);
    final Project project = editor.getProject();
    assert project != null;
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (psiFile == null) return;
    final I18nQuickFixHandler handler = getHandler(e);
    if (handler == null) return;

    doI18nSelectedString(project, editor, psiFile, handler);
  }

}
