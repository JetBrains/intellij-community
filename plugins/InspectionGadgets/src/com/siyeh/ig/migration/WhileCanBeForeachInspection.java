/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class WhileCanBeForeachInspection extends WhileCanBeForeachInspectionBase {

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new WhileCanBeForeachFix();
  }

  private static class WhileCanBeForeachFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("foreach.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement whileElement = descriptor.getPsiElement();
      final PsiWhileStatement whileStatement = (PsiWhileStatement)whileElement.getParent();
      replaceWhileWithForEach(whileStatement);
    }

    private static void replaceWhileWithForEach(@NotNull PsiWhileStatement whileStatement) {
      final PsiStatement body = whileStatement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement initialization = getPreviousStatement(whileStatement);
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      if (declaration == null) {
        return;
      }
      final PsiElement declaredElement = declaration.getDeclaredElements()[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return;
      }
      final PsiLocalVariable iterator = (PsiLocalVariable)declaredElement;
      final PsiMethodCallExpression initializer = (PsiMethodCallExpression)iterator.getInitializer();
      if (initializer == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = initializer.getMethodExpression();
      final PsiExpression collection = ExpressionUtils.getQualifierOrThis(methodExpression);
      final PsiType collectionType = collection.getType();
      if (collectionType == null) {
        return;
      }
      final PsiType contentType = ForCanBeForeachInspection.getContentType(collectionType, CommonClassNames.JAVA_LANG_ITERABLE);
      if (contentType == null) {
        return;
      }
      final PsiType iteratorType = iterator.getType();
      final PsiType iteratorContentType = ForCanBeForeachInspection.getContentType(iteratorType, "java.util.Iterator");
      if (iteratorContentType == null) {
        return;
      }
      final Project project = whileStatement.getProject();
      final PsiStatement firstStatement = ForCanBeForeachInspection.getFirstStatement(body);
      final boolean isDeclaration = ForCanBeForeachInspection.isIteratorNextDeclaration(firstStatement, iterator, contentType);
      final PsiStatement statementToSkip;
      @NonNls final String contentVariableName;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiLocalVariable localVariable = (PsiLocalVariable)declaredElements[0];
        contentVariableName = localVariable.getName();
        statementToSkip = declarationStatement;
      }
      else {
        if (collection instanceof PsiReferenceExpression) {
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)collection;
          final String collectionName = referenceElement.getReferenceName();
          contentVariableName = ForCanBeForeachInspection.createNewVariableName(whileStatement, iteratorContentType, collectionName);
        }
        else {
          contentVariableName = ForCanBeForeachInspection.createNewVariableName(whileStatement, iteratorContentType, null);
        }
        statementToSkip = null;
      }
      @NonNls final StringBuilder out = new StringBuilder();
      out.append("for(");
      if (CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS) {
        out.append("final ");
      }
      out.append(iteratorContentType.getCanonicalText()).append(' ').append(contentVariableName).append(": ");
      if (!TypeConversionUtil.isAssignable(iteratorContentType, contentType)) {
        out.append("(java.lang.Iterable<").append(iteratorContentType.getCanonicalText()).append(">)");
      }
      out.append(collection.getText());
      out.append(')');

      ForCanBeForeachInspection.replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, out);
      final Query<PsiReference> query = ReferencesSearch.search(iterator);
      boolean deleteIterator = true;
      for (PsiReference usage : query) {
        final PsiElement element = usage.getElement();
        if (PsiTreeUtil.isAncestor(whileStatement, element, true)) {
          continue;
        }
        final PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class);
        if (assignment == null) {
          // iterator is read after while loop,
          // so cannot be deleted
          deleteIterator = false;
          break;
        }
        final PsiExpression expression = assignment.getRExpression();
        initializer.delete();
        iterator.setInitializer(expression);
        final PsiElement statement = assignment.getParent();
        final PsiElement lastChild = statement.getLastChild();
        if (lastChild instanceof PsiComment) {
          iterator.add(lastChild);
        }
        statement.replace(iterator);
        break;
      }
      if (deleteIterator) {
        iterator.delete();
      }
      final String result = out.toString();
      PsiReplacementUtil.replaceStatementAndShortenClassNames(whileStatement, result);
    }
  }
}