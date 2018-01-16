/*
 * Copyright 2008-2018 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.PsiElementProcessor.CollectFilteredElements;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BlockUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.psiutils.HighlightUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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
    if (declarationScope instanceof PsiLambdaExpression) {
      RefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)declarationScope);
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      final PsiStatement body = ((PsiForeachStatement)declarationScope).getBody();
      if (body == null) {
        return;
      }
      BlockUtils.expandSingleStatementToBlockStatement(body);
    }
    final PsiElement body = BlockUtils.getBody(declarationScope);
    if (body == null) {
      return;
    }
    assert body instanceof PsiCodeBlock;
    final String parameterName = parameter.getName();
    final PsiExpression rhs = parameterReference.isValid() ? getRightSideIfLeftSideOfSimpleAssignment(parameterReference, body) : null;
    assert parameterName != null;
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    final String variableName = javaCodeStyleManager.suggestUniqueVariableName(parameterName, body, true);
    CommentTracker tracker = new CommentTracker();
    final String initializerText = (rhs == null) ? parameterName : tracker.text(rhs);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiDeclarationStatement newStatement = (PsiDeclarationStatement)
      factory.createStatementFromText(parameter.getType().getCanonicalText() + ' ' + variableName + '=' + initializerText + ';', body);
    final CollectFilteredElements<PsiReferenceExpression> collector = new CollectFilteredElements<>(
      e -> e instanceof PsiReferenceExpression && ((PsiReferenceExpression)e).resolve() == parameter);
    final PsiCodeBlock codeBlock = (PsiCodeBlock)body;
    PsiStatement anchor = null;
    for (PsiStatement statement : codeBlock.getStatements()) {
      if (anchor == null) {
        if (rhs == null && !JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
          anchor = statement;
          PsiTreeUtil.processElements(statement, collector);
        }
        else if (statement.getTextRange().contains(parameterReference.getTextRange())) {
          anchor = statement;
        }
      }
      else {
        PsiTreeUtil.processElements(statement, collector);
      }
    }
    assert anchor != null;
    newStatement = (PsiDeclarationStatement)(rhs == null
                                             ? codeBlock.addBefore(newStatement, anchor)
                                             : tracker.replaceAndRestoreComments(anchor, newStatement));
    replaceReferences(collector.getCollection(), variableName, body);
    if (isOnTheFly()) {
      final PsiLocalVariable variable = (PsiLocalVariable)newStatement.getDeclaredElements()[0];
      HighlightUtil.showRenameTemplate(body, variable);
    }
  }

  private static void replaceReferences(Collection<PsiReferenceExpression> collection, String newVariableName, PsiElement context) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    final PsiReferenceExpression newReference = (PsiReferenceExpression)factory.createExpressionFromText(newVariableName, context);
    for (PsiReferenceExpression reference : collection) {
      reference.replace(newReference);
    }
  }

  private static PsiExpression getRightSideIfLeftSideOfSimpleAssignment(PsiReferenceExpression reference, PsiElement block) {
    if (reference == null) {
      return null;
    }
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(reference);
    if (!(parent instanceof PsiAssignmentExpression)) {
      return null;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    if (!JavaTokenType.EQ.equals(tokenType)) {
      return null;
    }
    final PsiExpression lExpression = ParenthesesUtils.stripParentheses(assignmentExpression.getLExpression());
    if (!reference.equals(lExpression)) {
      return null;
    }
    final PsiExpression rExpression = assignmentExpression.getRExpression();
    if (ParenthesesUtils.stripParentheses(rExpression) instanceof PsiAssignmentExpression) {
      return null;
    }
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiExpressionStatement) || grandParent.getParent() != block) {
      return null;
    }
    return rExpression;
  }
}