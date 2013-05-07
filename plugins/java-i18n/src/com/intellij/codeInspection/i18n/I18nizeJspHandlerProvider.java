/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author sergey.evdokimov
 */
public class I18nizeJspHandlerProvider extends I18nizeHandlerProvider {

  private static final I18nQuickFixHandler HADLER = new I18nQuickFixHandler() {
    @Override
    public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
      final JspFile jspFile = (JspFile)psiFile;

      final TextRange selectedRange = JavaI18nUtil.getSelectedRange(editor, jspFile);
      // must contain no or balanced tags only
      // must not contain scriptlets or custom tags
      PsiFile root = jspFile.getBaseLanguageRoot();
      root.accept(new PsiRecursiveElementVisitor(){
        @Override public void visitElement(PsiElement element) {
          TextRange elementRange = element.getTextRange();
          if (elementRange.intersectsStrict(selectedRange)) {
            // in JSPX base language root is a Jspx file itself
            if (jspFile.getLanguage() != StdLanguages.JSPX && element instanceof OuterLanguageElement ||

                element instanceof XmlTag
                && !selectedRange.contains(elementRange)
                && (!elementRange.contains(selectedRange) || !((XmlTag)element).getValue().getTextRange().contains(selectedRange))) {
              throw new IncorrectOperationException(CodeInsightBundle.message("i18nize.jsp.error"));
            }
          }
          super.visitElement(element);
        }
      });
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
      TextRange selectedText = JavaI18nUtil.getSelectedRange(editor, psiFile);
      if (selectedText == null) return;
      propertyCreationHandler.createProperty(project, propertiesFiles, key, value, parameters);
      editor.getDocument().replaceString(selectedText.getStartOffset(), selectedText.getEndOffset(), i18nizedText);
    }

    @Override
    public JavaI18nizeQuickFixDialog createDialog(final Project project, final Editor editor, final PsiFile psiFile) {
      JspFile jspFile = (JspFile)psiFile;

      TextRange selectedRange = JavaI18nUtil.getSelectedRange(editor, psiFile);
      if (selectedRange == null) return null;
      String text = editor.getDocument().getText(selectedRange);
      return new JavaI18nizeQuickFixDialog(project, jspFile, null, text, null, false, true){
        @Override
        protected String getTemplateName() {
          return JavaTemplateUtil.TEMPLATE_I18NIZED_JSP_EXPRESSION;
        }
      };
    }
  };

  @Override
  public I18nQuickFixHandler getHandler(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull TextRange range) {
    return psiFile instanceof JspFile ? HADLER : null;
  }
}
