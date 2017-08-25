/*
 * Copyright 2008-2017 Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ExtractParameterAsLocalVariableFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiExpression)) {
      return;
    }
    final PsiExpression expression = ParenthesesUtils.stripParentheses((PsiExpression)element);
    if (!(expression instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression parameterReference = (PsiReferenceExpression)expression;
    final PsiElement target = parameterReference.resolve();
    if (!(target instanceof PsiParameter)) {
      return;
    }
    final PsiParameter parameter = (PsiParameter)target;
    final PsiElement declarationScope = parameter.getDeclarationScope();
    final PsiElement body;
    if (declarationScope instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)declarationScope;
      body = method.getBody();
    }
    else if (declarationScope instanceof PsiCatchSection) {
      final PsiCatchSection catchSection = (PsiCatchSection)declarationScope;
      body = catchSection.getCatchBlock();
    }
    else if (declarationScope instanceof PsiLoopStatement) {
      final PsiLoopStatement forStatement = (PsiLoopStatement)declarationScope;
      final PsiStatement forBody = forStatement.getBody();
      if (forBody instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)forBody;
        body = blockStatement.getCodeBlock();
      }
      else {
        body = forBody;
      }
    }
    else if (declarationScope instanceof PsiLambdaExpression) {
      final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)declarationScope;
      body = lambdaExpression.getBody();
    }
    else {
      return;
    }
    if (body == null) {
      return;
    }
    final String parameterName = parameterReference.getText();
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    final String variableName = javaCodeStyleManager.suggestUniqueVariableName(parameterName, parameterReference, true);
    final Query<PsiReference> search = ReferencesSearch.search(parameter, new LocalSearchScope(body));
    final PsiReference reference = search.findFirst();
    if (reference == null) {
      return;
    }
    final PsiElement referenceElement = reference.getElement();
    if (!(referenceElement instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression firstReference = (PsiReferenceExpression)referenceElement;
    final PsiElement[] children = body.getChildren();
    final int startIndex;
    final int endIndex;
    if (body instanceof PsiCodeBlock) {
      startIndex = 1;
      endIndex = children.length - 1;
    }
    else {
      startIndex = 0;
      endIndex = children.length;
    }
    boolean newDeclarationCreated = false;
    final StringBuilder buffer = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      final PsiElement child = children[i];
      newDeclarationCreated |= replaceVariableName(child, firstReference, variableName, parameterName, buffer);
    }
    if (body instanceof PsiExpression) { // expression lambda
      buffer.insert(0, "return ");
      buffer.append(';');
    }
    final String replacementText;
    if (newDeclarationCreated) {
      replacementText = "{" + buffer + '}';
    }
    else {
      final PsiType type = parameter.getType();
      final String className = type.getCanonicalText();
      replacementText = '{' + className + ' ' + variableName + " = " + parameterName + ';' + buffer + '}';
    }
    final PsiCodeBlock block = JavaPsiFacade.getElementFactory(project).createCodeBlockFromText(replacementText, declarationScope);
    body.replace(block);
    CodeStyleManager.getInstance(project).reformat(declarationScope);
  }

  /**
   * @return true, if a declaration was introduced, false otherwise
   */
  private static boolean replaceVariableName(
    PsiElement element, PsiReferenceExpression firstReference,
    String newName, String originalName, StringBuilder out) {
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)element;
      if (element.equals(firstReference) &&
          isLeftSideOfSimpleAssignment(referenceExpression)) {
        final PsiType type = firstReference.getType();
        if (type != null) {
          out.append(type.getCanonicalText());
          out.append(' ');
          out.append(newName);
          return true;
        }
      }
      final String text = element.getText();
      if (text.equals(originalName)) {
        out.append(newName);
        return false;
      }
    }
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      final String text = element.getText();
      out.append(text);
    }
    else {
      boolean result = false;
      for (final PsiElement child : children) {
        if (result) {
          out.append(child.getText());
        }
        else {
          result = replaceVariableName(child, firstReference,
                                       newName, originalName, out);
        }
      }
      return result;
    }
    return false;
  }

  private static boolean isLeftSideOfSimpleAssignment(
    PsiReferenceExpression reference) {
    if (reference == null) {
      return false;
    }
    final PsiElement parent = reference.getParent();
    if (!(parent instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)parent;
    final IElementType tokenType =
      assignmentExpression.getOperationTokenType();
    if (!JavaTokenType.EQ.equals(tokenType)) {
      return false;
    }
    final PsiExpression lExpression =
      assignmentExpression.getLExpression();
    if (!reference.equals(lExpression)) {
      return false;
    }
    final PsiExpression rExpression =
      assignmentExpression.getRExpression();
    if (rExpression instanceof PsiAssignmentExpression) {
      return false;
    }
    final PsiElement grandParent = parent.getParent();
    return grandParent instanceof PsiExpressionStatement;
  }
}