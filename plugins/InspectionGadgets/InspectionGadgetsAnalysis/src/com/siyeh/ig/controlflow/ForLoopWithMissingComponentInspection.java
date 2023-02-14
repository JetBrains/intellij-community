/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class ForLoopWithMissingComponentInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreCollectionLoops = false;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean hasInitializer = ((Boolean)infos[0]).booleanValue();
    final boolean hasCondition = ((Boolean)infos[1]).booleanValue();
    final boolean hasUpdate = ((Boolean)infos[2]).booleanValue();
    if (hasInitializer) {
      if (hasCondition) {
        return InspectionGadgetsBundle.message("for.loop.with.missing.component.problem.descriptor3");
      }
      else if (hasUpdate) {
        return InspectionGadgetsBundle.message("for.loop.with.missing.component.problem.descriptor2");
      }
      else {
        return InspectionGadgetsBundle.message("for.loop.with.missing.component.problem.descriptor6");
      }
    }
    else if (hasCondition) {
      if (hasUpdate) {
        return InspectionGadgetsBundle.message("for.loop.with.missing.component.problem.descriptor1");
      }
      else {
        return InspectionGadgetsBundle.message("for.loop.with.missing.component.problem.descriptor5");
      }
    }
    else if (hasUpdate) {
      return InspectionGadgetsBundle.message("for.loop.with.missing.component.problem.descriptor4");
    }
    else {
      return InspectionGadgetsBundle.message("for.loop.with.missing.component.problem.descriptor7");
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreCollectionLoops", InspectionGadgetsBundle.message("for.loop.with.missing.component.collection.loop.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ForLoopWithMissingComponentVisitor();
  }

  private class ForLoopWithMissingComponentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      final boolean hasCondition = hasCondition(statement);
      final boolean hasInitializer = hasInitializer(statement);
      final boolean hasUpdate = hasUpdate(statement);
      if (hasCondition && hasInitializer && hasUpdate) {
        return;
      }
      if (ignoreCollectionLoops && isCollectionLoopStatement(statement)) {
        return;
      }
      registerStatementError(statement, Boolean.valueOf(hasInitializer), Boolean.valueOf(hasCondition), Boolean.valueOf(hasUpdate));
    }

    private boolean hasCondition(PsiForStatement statement) {
      return statement.getCondition() != null;
    }

    private boolean hasInitializer(PsiForStatement statement) {
      final PsiStatement initialization = statement.getInitialization();
      return initialization != null && !(initialization instanceof PsiEmptyStatement);
    }

    private boolean hasUpdate(PsiForStatement statement) {
      final PsiStatement update = statement.getUpdate();
      return update != null && !(update instanceof PsiEmptyStatement);
    }

    private boolean isCollectionLoopStatement(PsiForStatement forStatement) {
      final PsiStatement initialization = forStatement.getInitialization();
      if (!(initialization instanceof PsiDeclarationStatement declaration)) {
        return false;
      }
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      final PsiExpression condition = forStatement.getCondition();
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiVariable variable)) {
          continue;
        }
        if (TypeUtils.variableHasTypeOrSubtype(variable, "java.util.ListIterator")) {
          if (isHasNext(condition, variable) || isHasPrevious(condition, variable)) {
            return true;
          }
        }
        if (TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_UTIL_ITERATOR)) {
          if (isHasNext(condition, variable)) {
            return true;
          }
        }
        else if (TypeUtils.variableHasTypeOrSubtype(variable, "java.util.Enumeration")) {
          if (isHasMoreElements(condition, variable)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean isHasNext(PsiExpression condition, PsiVariable iterator) {
      return isCallToBooleanZeroArgumentMethod(HardcodedMethodConstants.HAS_NEXT, condition, iterator);
    }

    private boolean isHasPrevious(PsiExpression condition, PsiVariable iterator) {
      return isCallToBooleanZeroArgumentMethod("hasPrevious", condition, iterator);
    }

    private boolean isHasMoreElements(PsiExpression condition, PsiVariable enumeration) {
      return isCallToBooleanZeroArgumentMethod("hasMoreElements", condition, enumeration);
    }

    private boolean isCallToBooleanZeroArgumentMethod(@NonNls String methodName, PsiExpression expression, PsiVariable calledOn) {
      if (expression instanceof PsiPolyadicExpression polyadicExpression) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          if (isCallToBooleanZeroArgumentMethod(methodName, operand, calledOn)) {
            return true;
          }
        }
        return false;
      }
      if (!(expression instanceof PsiMethodCallExpression call)) {
        return false;
      }
      if (!MethodCallUtils.isCallToMethod(call, null, PsiTypes.booleanType(), methodName)) {
        return false;
      }
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression referenceExpression)) {
        return true;
      }
      final PsiElement target = referenceExpression.resolve();
      return calledOn.equals(target);
    }
  }
}