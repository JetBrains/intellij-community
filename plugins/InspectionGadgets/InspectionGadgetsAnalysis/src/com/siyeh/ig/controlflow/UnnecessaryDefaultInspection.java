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
package com.siyeh.ig.controlflow;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class UnnecessaryDefaultInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.default.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.default.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryDefaultVisitor();
  }

  private static class UnnecessaryDefaultVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final PsiSwitchLabelStatement defaultStatement = retrieveUnnecessaryDefault(statement);
      if (defaultStatement == null) {
        return;
      }
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(defaultStatement, PsiStatement.class);
      if (nextStatement instanceof PsiThrowStatement) {
        // consider a single throw statement a guard against future changes that update the code only partially
        return;
      }
      while (nextStatement != null) {
        if (isDefaultNeededForInitializationOfVariable(statement)) {
          return;
        }
        if (!ControlFlowUtils.statementMayCompleteNormally(nextStatement)) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
          if (method != null && !PsiType.VOID.equals(method.getReturnType()) &&
              !ControlFlowUtils.statementContainsNakedBreak(nextStatement)) {
            final PsiCodeBlock body = method.getBody();
            assert body != null;
            if (ControlFlowUtils.blockCompletesWithStatement(body, statement)) {
              return;
            }
          }
          else {
            break;
          }
        }
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      }
      registerStatementError(defaultStatement);
    }

    private static boolean isDefaultNeededForInitializationOfVariable(PsiSwitchStatement switchStatement) {
      final SmartList<PsiReferenceExpression> expressions = new SmartList<>();
      final PsiElementProcessor.CollectFilteredElements<PsiReferenceExpression> collector =
        new PsiElementProcessor.CollectFilteredElements<>(e -> e instanceof PsiReferenceExpression, expressions);
      PsiTreeUtil.processElements(switchStatement, collector);
      final Set<PsiElement> checked = new THashSet<>();
      for (PsiReferenceExpression expression : expressions) {
        final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
        if (!(parent instanceof PsiAssignmentExpression)) {
          continue;
        }
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
        if (JavaTokenType.EQ != assignmentExpression.getOperationTokenType()) {
          continue;
        }
        if (!PsiTreeUtil.isAncestor(assignmentExpression.getLExpression(), expression, false)) {
          continue;
        }
        final PsiElement target = expression.resolve();
        if (!checked.add(target)) {
          continue;
        }
        if (target instanceof PsiLocalVariable || target instanceof PsiField && ((PsiField)target).hasModifierProperty(PsiModifier.FINAL)) {
          final PsiVariable variable = (PsiVariable)target;
          if (variable.getInitializer() != null) {
            continue;
          }
          final PsiElement context = getContext(switchStatement);
          if (context == null) {
            return false;
          }
          try {
            final LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
            final ControlFlow controlFlow = ControlFlowFactory.getInstance(context.getProject()).getControlFlow(context, policy);
            final int switchStart = controlFlow.getStartOffset(switchStatement);
            final int switchEnd = controlFlow.getEndOffset(switchStatement);
            final ControlFlow beforeFlow = new ControlFlowSubRange(controlFlow, 0, switchStart);
            final ControlFlow switchFlow = new ControlFlowSubRange(controlFlow, switchStart, switchEnd);
            if (!ControlFlowUtil.isVariableDefinitelyAssigned(variable, beforeFlow) &&
                ControlFlowUtil.isVariableDefinitelyAssigned(variable, switchFlow) &&
                ControlFlowUtil.needVariableValueAt(variable, controlFlow, switchEnd)) {
              return true;
            }
          }
          catch (AnalysisCanceledException e) {
            return true;
          }
        }
      }
      return false;
    }

    private static PsiElement getContext(PsiElement element) {
      final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiLambdaExpression.class);
      if (context instanceof PsiField) {
        return ((PsiField)context).getInitializer();
      }
      else if (context instanceof PsiClassInitializer) {
        return ((PsiClassInitializer)context).getBody();
      }
      else if (context instanceof PsiMethod) {
        return ((PsiMethod)context).getBody();
      }
      else if (context instanceof PsiLambdaExpression) {
        return ((PsiLambdaExpression)context).getBody();
      }
      throw new AssertionError();
    }

    @Nullable
    private static PsiSwitchLabelStatement retrieveUnnecessaryDefault(PsiSwitchStatement statement) {
      final PsiExpression expression = statement.getExpression();
      if (expression == null) {
        return null;
      }
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null || !aClass.isEnum()) {
        return null;
      }
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return null;
      }
      final PsiStatement[] statements = body.getStatements();
      int numCases = 0;
      PsiSwitchLabelStatement result = null;
      for (final PsiStatement child : statements) {
        if (!(child instanceof PsiSwitchLabelStatement)) {
          continue;
        }
        final PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)child;
        if (labelStatement.isDefaultCase()) {
          result = labelStatement;
        }
        else {
          numCases++;
        }
      }
      if (result == null) {
        return null;
      }
      final PsiField[] fields = aClass.getFields();
      int numEnums = 0;
      for (final PsiField field : fields) {
        final PsiType fieldType = field.getType();
        if (fieldType.equals(type)) {
          numEnums++;
        }
      }
      if (numEnums != numCases) {
        return null;
      }
      return result;
    }
  }
}