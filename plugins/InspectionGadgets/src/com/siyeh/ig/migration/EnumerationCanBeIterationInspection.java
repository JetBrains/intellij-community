/*
 * Copyright 2007-2018 Bas Leijdekkers
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
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnumerationCanBeIterationInspection extends EnumerationCanBeIterationInspectionBase {

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new EnumerationCanBeIterationFix();
  }

  private static class EnumerationCanBeIterationFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "enumeration.can.be.iteration.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression =
        (PsiReferenceExpression)element.getParent();
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)methodExpression.getParent();
      final PsiElement parent =
        methodCallExpression.getParent();
      final PsiVariable variable;
      if (parent instanceof PsiVariable) {
        variable = (PsiVariable)parent;
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)parent;
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        variable = (PsiVariable)target;
      }
      else {
        return;
      }
      final String variableName = createVariableName(element);
      final PsiStatement statement =
        PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      if (statement == null) {
        return;
      }
      final int result = replaceMethodCalls(variable,
                                            statement.getTextOffset(), variableName);
      final PsiType variableType = variable.getType();
      if (!(variableType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)variableType;
      final PsiType[] parameterTypes = classType.getParameters();
      final PsiType parameterType;
      if (parameterTypes.length > 0) {
        parameterType = parameterTypes[0];
      }
      else {
        parameterType = null;
      }
      final PsiStatement newStatement =
        createDeclaration(methodCallExpression, variableName,
                          parameterType);
      if (newStatement == null) {
        return;
      }
      if (parent.equals(variable)) {
        if (result == KEEP_NOTHING) {
          statement.replace(newStatement);
        }
        else {
          insertNewStatement(statement, newStatement);
          if (result != KEEP_INITIALIZATION) {
            final PsiExpression initializer =
              variable.getInitializer();
            if (initializer != null) {
              initializer.delete();
            }
          }
        }
      }
      else {
        if (result == KEEP_NOTHING || result == KEEP_DECLARATION) {
          statement.replace(newStatement);
        }
        else {
          insertNewStatement(statement, newStatement);
        }
      }
    }

    private static void insertNewStatement(PsiStatement anchor, PsiStatement newStatement) {
      final PsiElement statementParent = anchor.getParent();
      if (statementParent instanceof PsiForStatement) {
        final PsiElement statementGrandParent =
          statementParent.getParent();
        statementGrandParent.addBefore(newStatement,
                                       statementParent);
      }
      else {
        statementParent.addAfter(newStatement, anchor);
      }
    }

    @Nullable
    private static PsiStatement createDeclaration(PsiMethodCallExpression methodCallExpression,
                                                  String variableName,
                                                  PsiType parameterType) {
      @NonNls final StringBuilder newStatementText = new StringBuilder();
      final Project project = methodCallExpression.getProject();
      final CodeStyleSettings codeStyleSettings =
        CodeStyleSettingsManager.getSettings(project);
      if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
        newStatementText.append("final ");
      }
      newStatementText.append(CommonClassNames.JAVA_UTIL_ITERATOR);
      if (parameterType != null) {
        final String typeText = parameterType.getCanonicalText();
        newStatementText.append('<');
        newStatementText.append(typeText);
        newStatementText.append('>');
      }
      newStatementText.append(' ');
      newStatementText.append(variableName);
      newStatementText.append('=');
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      final String qualifierText;
      if (qualifier == null) {
        qualifierText = "";
      }
      else {
        qualifierText = qualifier.getText() + '.';
      }
      newStatementText.append(qualifierText);
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if ("elements".equals(methodName)) {
        if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                 "java.util.Vector")) {
          newStatementText.append(ITERATOR_TEXT);
        }
        else if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                      "java.util.Hashtable")) {
          newStatementText.append(VALUES_ITERATOR_TEXT);
        }
        else {
          return null;
        }
      }
      else if ("keys".equals(methodName)) {
        if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                 "java.util.Hashtable")) {
          newStatementText.append(KEY_SET_ITERATOR_TEXT);
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }
      newStatementText.append(';');
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiStatement statement =
        factory.createStatementFromText(newStatementText.toString(),
                                        methodExpression);
      final JavaCodeStyleManager styleManager =
        JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(statement);
      return statement;
    }

    /**
     * @return true if the initialization of the Enumeration variable can
     * be deleted.
     */
    private static int replaceMethodCalls(PsiVariable enumerationVariable, int startOffset, String newVariableName) {
      final PsiManager manager = enumerationVariable.getManager();
      final Project project = manager.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final Query<PsiReference> query = ReferencesSearch.search(
        enumerationVariable);
      final List<PsiElement> referenceElements = new ArrayList<>();
      for (PsiReference reference : query) {
        final PsiElement referenceElement = reference.getElement();
        referenceElements.add(referenceElement);
      }
      Collections.sort(referenceElements,
                       PsiElementOrderComparator.getInstance());
      int result = 0;
      for (PsiElement referenceElement : referenceElements) {
        if (!(referenceElement instanceof PsiReferenceExpression)) {
          result = KEEP_DECLARATION;
          continue;
        }
        if (referenceElement.getTextOffset() <= startOffset) {
          result = KEEP_DECLARATION;
          continue;
        }
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)referenceElement;
        final PsiElement referenceParent =
          referenceExpression.getParent();
        if (!(referenceParent instanceof PsiReferenceExpression)) {
          if (referenceParent instanceof PsiAssignmentExpression) {
            result = KEEP_DECLARATION;
            break;
          }
          result = KEEP_INITIALIZATION;
          continue;
        }
        final PsiElement referenceGrandParent =
          referenceParent.getParent();
        if (!(referenceGrandParent instanceof PsiMethodCallExpression)) {
          result = KEEP_INITIALIZATION;
          continue;
        }
        final PsiMethodCallExpression callExpression =
          (PsiMethodCallExpression)referenceGrandParent;
        final PsiReferenceExpression foundReferenceExpression =
          callExpression.getMethodExpression();
        @NonNls final String foundName =
          foundReferenceExpression.getReferenceName();
        @NonNls final String newExpressionText;
        if ("hasMoreElements".equals(foundName)) {
          newExpressionText = newVariableName + ".hasNext()";
        }
        else if ("nextElement".equals(foundName)) {
          newExpressionText = newVariableName + ".next()";
        }
        else {
          result = KEEP_INITIALIZATION;
          continue;
        }
        final PsiExpression newExpression =
          factory.createExpressionFromText(newExpressionText,
                                           callExpression);
        callExpression.replace(newExpression);
      }
      return result;
    }

    @NonNls
    private static String createVariableName(PsiElement context) {
      final Project project = context.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiClass iteratorClass =
        facade.findClass(CommonClassNames.JAVA_UTIL_ITERATOR, scope);
      if (iteratorClass == null) {
        return "iterator";
      }
      final JavaCodeStyleManager codeStyleManager =
        JavaCodeStyleManager.getInstance(project);
      final PsiType iteratorType = factory.createType(iteratorClass);
      final SuggestedNameInfo baseNameInfo =
        codeStyleManager.suggestVariableName(
          VariableKind.LOCAL_VARIABLE, null, null,
          iteratorType);
      final SuggestedNameInfo nameInfo =
        codeStyleManager.suggestUniqueVariableName(baseNameInfo,
                                                   context, true);
      if (nameInfo.names.length <= 0) {
        return "iterator";
      }
      return nameInfo.names[0];
    }
  }
}