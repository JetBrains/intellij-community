/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.HighlightUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReuseOfLocalVariableInspection extends ReuseOfLocalVariableInspectionBase {
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReuseOfLocalVariableFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private static class ReuseOfLocalVariableFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("reuse.of.local.variable.split.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)descriptor.getPsiElement();
      final PsiLocalVariable variable = (PsiLocalVariable)referenceExpression.resolve();
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)referenceExpression.getParent();
      assert assignment != null;
      final PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)assignment.getParent();
      final PsiExpression lExpression = assignment.getLExpression();
      final String originalVariableName = lExpression.getText();
      assert variable != null;
      final PsiType type = variable.getType();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final PsiCodeBlock variableBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      final String newVariableName = codeStyleManager.suggestUniqueVariableName(originalVariableName, variableBlock, false);
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(assignmentStatement, PsiCodeBlock.class);
      final SearchScope scope;
      if (codeBlock != null) {
        scope = new LocalSearchScope(codeBlock);
      }
      else {
        scope = variable.getUseScope();
      }
      final Query<PsiReference> query = ReferencesSearch.search(variable, scope, false);
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      List<PsiReferenceExpression> collectedReferences = new ArrayList<>();
      for (PsiReference reference : query) {
        final PsiElement referenceElement = reference.getElement();
        if (referenceElement == null) {
          continue;
        }
        final TextRange textRange = assignmentStatement.getTextRange();
        if (referenceElement.getTextOffset() <= textRange.getEndOffset()) {
          continue;
        }
        final PsiExpression newExpression = factory.createExpressionFromText(newVariableName, referenceElement);
        final PsiReferenceExpression replacementExpression = (PsiReferenceExpression)referenceElement.replace(newExpression);
        collectedReferences.add(replacementExpression);
      }
      CommentTracker commentTracker = new CommentTracker();
      final PsiExpression rhs = assignment.getRExpression();
      final String rhsText = rhs == null ? "" : commentTracker.text(rhs);
      @NonNls final String newStatementText = type.getCanonicalText() + ' ' + newVariableName + " =  " + rhsText + ';';

      final PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)commentTracker.replaceAndRestoreComments(assignmentStatement, newStatementText);
      final PsiElement[] elements = declarationStatement.getDeclaredElements();
      final PsiLocalVariable newVariable = (PsiLocalVariable)elements[0];
      final PsiElement context = declarationStatement.getParent();
      HighlightUtils.showRenameTemplate(context, newVariable, collectedReferences.toArray(new PsiReferenceExpression[0]));
    }
  }
}