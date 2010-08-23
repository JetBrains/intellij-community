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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.impl.ConcatenationToMessageFormatAction;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class I18nizeAction extends AnAction implements I18nQuickFixHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeAction");

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
  public I18nQuickFixHandler getHandler(final AnActionEvent e) {
    final Editor editor = getEditor(e);
    if (editor == null) return null;
    PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
    if (psiFile == null) return null;
    final PsiLiteralExpression literalExpression = getEnclosingStringLiteral(psiFile, editor);
    TextRange range = getSelectedRange(getEditor(e), psiFile);
    PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;
    if (range != null && ConcatenationToMessageFormatAction.getEnclosingLiteralConcatenation(element) != null) {
      return new I18nizeConcatenationQuickFix();
    }
    else if (literalExpression != null && range != null && literalExpression.getTextRange().contains(range)) {
      return new I18nizeQuickFix();
    }
    else if (psiFile instanceof JspFile && range != null) {
      return this;
    }
    else {
      return null;
    }
  }

  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
    if (!canBeExtractedAway((JspFile)psiFile, editor)) {
      throw new IncorrectOperationException(CodeInsightBundle.message("i18nize.jsp.error"));
    }
  }

  public void performI18nization(final PsiFile psiFile,
                                 final Editor editor,
                                 PsiLiteralExpression literalExpression,
                                 Collection<PropertiesFile> propertiesFiles,
                                 String key, String value, String i18nizedText,
                                 PsiExpression[] parameters,
                                 final PropertyCreationHandler propertyCreationHandler) throws IncorrectOperationException {
    Project project = psiFile.getProject();
    TextRange selectedText = getSelectedRange(editor, psiFile);
    if (selectedText == null) return;
    propertyCreationHandler.createProperty(project, propertiesFiles, key, value, parameters);
    editor.getDocument().replaceString(selectedText.getStartOffset(), selectedText.getEndOffset(), i18nizedText);
  }

  public static PsiLiteralExpression getEnclosingStringLiteral(final PsiFile psiFile, final Editor editor) {
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement == null) return null;
    PsiLiteralExpression expression = PsiTreeUtil.getParentOfType(psiElement, PsiLiteralExpression.class);
    if (expression == null || !(expression.getValue() instanceof String)) return null;
    return expression;
  }

  private static boolean canBeExtractedAway(final JspFile jspFile, final Editor editor) {
    final TextRange selectedRange=getSelectedRange(editor, jspFile);
    // must contain no or balanced tags only
    // must not contain scriptlets or custom tags
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.TRUE);
    PsiFile root = jspFile.getBaseLanguageRoot();
    root.accept(new PsiRecursiveElementVisitor(){
      @Override public void visitElement(PsiElement element) {
        if (!result.get().booleanValue()) return;
        TextRange elementRange = element.getTextRange();
        if (elementRange.intersectsStrict(selectedRange)) {
          // in JSPX base language root is a Jspx file itself
          if (jspFile.getLanguage() != StdLanguages.JSPX && element instanceof OuterLanguageElement ||

              element instanceof XmlTag
              && !selectedRange.contains(elementRange)
              && (!elementRange.contains(selectedRange) || !((XmlTag)element).getValue().getTextRange().contains(selectedRange))) {
            result.set(Boolean.FALSE);
            return;
          }
        }
        super.visitElement(element);
      }
    });
    return result.get().booleanValue();
  }

  @Nullable private static TextRange getSelectedRange(Editor editor, final PsiFile psiFile) {
    if (editor == null) return null;
    String selectedText = editor.getSelectionModel().getSelectedText();
    if (selectedText != null) {
      return new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
    }
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement==null || psiElement instanceof PsiWhiteSpace) return null;
    return psiElement.getTextRange();
  }

  private static Editor getEditor(final AnActionEvent e) {
    return PlatformDataKeys.EDITOR.getData(e.getDataContext());
  }

  public void actionPerformed(AnActionEvent e) {
    final Editor editor = getEditor(e);
    final Project project = editor.getProject();
    final PsiFile psiFile = LangDataKeys.PSI_FILE.getData(e.getDataContext());
    if (psiFile == null) return;
    final I18nQuickFixHandler handler = getHandler(e);
    if (handler == null) return;
    try {
      handler.checkApplicability(psiFile, editor);
    }
    catch (IncorrectOperationException ex) {
      CommonRefactoringUtil.showErrorHint(project, editor, ex.getMessage(), CodeInsightBundle.message("i18nize.error.title"), null);
      return;
    }

    final JavaI18nizeQuickFixDialog dialog = handler.createDialog(project, editor, psiFile);
    if (dialog == null) return;
    dialog.show();
    if (!dialog.isOK()) return;

    if (!CodeInsightUtilBase.prepareFileForWrite(psiFile)) return;
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();
    for (PropertiesFile file : propertiesFiles) {
      if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable(){
          public void run() {
            try {
              handler.performI18nization(psiFile, editor, dialog.getLiteralExpression(), propertiesFiles, dialog.getKey(), dialog.getValue(),
                                         dialog.getI18nizedText(), dialog.getParameters(),
                                         dialog.getPropertyCreationHandler());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }, CodeInsightBundle.message("quickfix.i18n.command.name"),project);
      }
    });
  }

  public JavaI18nizeQuickFixDialog createDialog(final Project project, final Editor editor, final PsiFile psiFile) {
    JspFile jspFile = (JspFile)psiFile;

    TextRange selectedRange = getSelectedRange(editor, psiFile);
    if (selectedRange == null) return null;
    String text = editor.getDocument().getText(selectedRange);
    return new JavaI18nizeQuickFixDialog(project, jspFile, null, text, null, false, true){
      protected String getTemplateName() {
        return JavaTemplateUtil.TEMPLATE_I18NIZED_JSP_EXPRESSION;
      }
    };
  }

}
