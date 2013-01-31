/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.Language;
import com.intellij.lang.jsp.JspxFileViewProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.jsp.JspFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class InspectionGadgetsFix implements LocalQuickFix {

  public static final InspectionGadgetsFix[] EMPTY_ARRAY = {};
  private static final Logger LOG =
    Logger.getInstance("#com.siyeh.ig.InspectionGadgetsFix");

  private boolean myOnTheFly = false;

  /**
   * To appear in "Apply Fix" statement when multiple Quick Fixes exist
   */
  @NotNull
  public String getFamilyName() {
    return "";
  }

  public final void applyFix(@NotNull Project project,
                             @NotNull ProblemDescriptor descriptor) {
    final PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement == null || !problemElement.isValid()) {
      return;
    }
    if (isQuickFixOnReadOnlyFile(problemElement)) {
      return;
    }
    try {
      doFix(project, descriptor);
    }
    catch (IncorrectOperationException e) {
      final Class<? extends InspectionGadgetsFix> aClass = getClass();
      final String className = aClass.getName();
      final Logger logger = Logger.getInstance(className);
      logger.error(e);
    }
  }

  protected abstract void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException;

  protected static void deleteElement(@NotNull PsiElement element)
    throws IncorrectOperationException {
    element.delete();
  }

  protected static void replaceExpression(
    @NotNull PsiExpression expression,
    @NotNull @NonNls String newExpressionText)
    throws IncorrectOperationException {
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiExpression newExpression =
      factory.createExpressionFromText(newExpressionText, expression);
    final PsiElement replacementExpression =
      expression.replace(newExpression);
    final CodeStyleManager styleManager =
      CodeStyleManager.getInstance(project);
    styleManager.reformat(replacementExpression);
  }

  protected static void replaceExpressionWithReferenceTo(
    @NotNull PsiExpression expression,
    @NotNull PsiMember target)
    throws IncorrectOperationException {
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiReferenceExpression newExpression = (PsiReferenceExpression)
      factory.createExpressionFromText("xxx", expression);
    final PsiReferenceExpression replacementExpression =
      (PsiReferenceExpression)expression.replace(newExpression);
    final PsiElement element = replacementExpression.bindToElement(target);
    final JavaCodeStyleManager styleManager =
      JavaCodeStyleManager.getInstance(project);
    styleManager.shortenClassReferences(element);
  }

  protected static void replaceExpressionAndShorten(
    @NotNull PsiExpression expression,
    @NotNull @NonNls String newExpressionText)
    throws IncorrectOperationException {
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiExpression newExpression =
      factory.createExpressionFromText(newExpressionText, expression);
    final PsiElement replacementExp = expression.replace(newExpression);
    final JavaCodeStyleManager javaCodeStyleManager =
      JavaCodeStyleManager.getInstance(project);
    javaCodeStyleManager.shortenClassReferences(replacementExp);
    final CodeStyleManager styleManager =
      CodeStyleManager.getInstance(project);
    styleManager.reformat(replacementExp);
  }

  protected static void replaceStatement(
    @NotNull PsiStatement statement,
    @NotNull @NonNls String newStatementText)
    throws IncorrectOperationException {
    final Project project = statement.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiStatement newStatement =
      factory.createStatementFromText(newStatementText, statement);
    final PsiElement replacementExp = statement.replace(newStatement);
    final CodeStyleManager styleManager =
      CodeStyleManager.getInstance(project);
    styleManager.reformat(replacementExp);
  }

  protected static void replaceStatementAndShortenClassNames(
    @NotNull PsiStatement statement,
    @NotNull @NonNls String newStatementText)
    throws IncorrectOperationException {
    final Project project = statement.getProject();
    final CodeStyleManager styleManager =
      CodeStyleManager.getInstance(project);
    final JavaCodeStyleManager javaStyleManager =
      JavaCodeStyleManager.getInstance(project);
    if (JspPsiUtil.isInJspFile(statement)) {
      final PsiDocumentManager documentManager =
        PsiDocumentManager.getInstance(project);
      final JspFile file = JspPsiUtil.getJspFile(statement);
      final Document document = documentManager.getDocument(file);
      if (document == null) {
        return;
      }
      documentManager.doPostponedOperationsAndUnblockDocument(document);
      final TextRange textRange = statement.getTextRange();
      document.replaceString(textRange.getStartOffset(),
                             textRange.getEndOffset(), newStatementText);
      documentManager.commitDocument(document);
      final JspxFileViewProvider viewProvider = file.getViewProvider();
      PsiElement elementAt =
        viewProvider.findElementAt(textRange.getStartOffset(),
                                   StdLanguages.JAVA);
      if (elementAt == null) {
        return;
      }
      final int endOffset = textRange.getStartOffset() +
                            newStatementText.length();
      while (elementAt.getTextRange().getEndOffset() < endOffset ||
             !(elementAt instanceof PsiStatement)) {
        elementAt = elementAt.getParent();
        if (elementAt == null) {
          LOG.error("Cannot decode statement");
          return;
        }
      }
      final PsiStatement newStatement = (PsiStatement)elementAt;
      javaStyleManager.shortenClassReferences(newStatement);
      final TextRange newTextRange = newStatement.getTextRange();
      final Language baseLanguage = viewProvider.getBaseLanguage();
      final PsiFile element = viewProvider.getPsi(baseLanguage);
      if (element != null) {
        styleManager.reformatRange(element,
                                   newTextRange.getStartOffset(),
                                   newTextRange.getEndOffset());
      }
    }
    else {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      PsiStatement newStatement = factory.createStatementFromText(
        newStatementText, statement);
      newStatement = (PsiStatement)statement.replace(newStatement);
      javaStyleManager.shortenClassReferences(newStatement);
      styleManager.reformat(newStatement);
    }
  }

  protected boolean isQuickFixOnReadOnlyFile(PsiElement problemElement) {
    final PsiFile containingPsiFile = problemElement.getContainingFile();
    if (containingPsiFile == null) {
      return false;
    }
    final VirtualFile virtualFile = containingPsiFile.getVirtualFile();
    final Project project = problemElement.getProject();
    final ReadonlyStatusHandler handler =
      ReadonlyStatusHandler.getInstance(project);
    final ReadonlyStatusHandler.OperationStatus status =
      handler.ensureFilesWritable(virtualFile);
    return status.hasReadonlyFiles();
  }

  protected static String getElementText(@NotNull PsiElement element,
                                         @Nullable PsiElement elementToReplace,
                                         @Nullable String replacement) {
    final StringBuilder out = new StringBuilder();
    getElementText(element, elementToReplace, replacement, out);
    return out.toString();
  }

  private static void getElementText(
    @NotNull PsiElement element,
    @Nullable PsiElement elementToReplace,
    @Nullable String replacement,
    @NotNull StringBuilder out) {
    if (element.equals(elementToReplace)) {
      out.append(replacement);
      return;
    }
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      out.append(element.getText());
      return;
    }
    for (PsiElement child : children) {
      getElementText(child, elementToReplace, replacement, out);
    }
  }

  public final void setOnTheFly(boolean onTheFly) {
    myOnTheFly = onTheFly;
  }

  public final boolean isOnTheFly() {
    return myOnTheFly;
  }
}