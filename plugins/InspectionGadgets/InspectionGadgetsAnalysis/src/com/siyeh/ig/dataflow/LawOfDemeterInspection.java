/*
 * Copyright 2006-2007 Bas Leijdekkers
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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LawOfDemeterInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreLibraryCalls = true;

  private static final Key<Integer> key =
    Key.create("LawOfDemeterInspection");

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("law.of.demeter.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "law.of.demeter.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "law.of.demeter.ignore.library.calls.option"), this,
                                          "ignoreLibraryCalls");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LawOfDemeterVisitor();
  }

  private class LawOfDemeterVisitor extends BaseInspectionVisitor {

    private static final int threshold = 2;

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (ignoreLibraryCalls &&
          LibraryUtil.callOnLibraryMethod(expression)) {
        return;
      }
      expression.putUserData(key, Integer.valueOf(1));
      checkParents(expression, Integer.valueOf(1));
    }

    public void checkParents(PsiExpression expression, Integer count) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiLocalVariable) {
        final Integer localCount = expression.getUserData(key);
        parent.putUserData(key, localCount);
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
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiLocalVariable)) {
          return;
        }
        final Integer localCount = expression.getUserData(key);
        element.putUserData(key, localCount);
      }
      else if (parent instanceof PsiReferenceExpression) {
        final PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
          return;
        }
        final PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)grandParent;
        final Integer userData = grandParent.getUserData(key);
        if (userData == null) {
          return;
        }
        final int localCount = userData.intValue();
        final int newCount = localCount + count.intValue();
        if (newCount == threshold) {
          registerMethodCallError(methodCallExpression);
        }
        grandParent.putUserData(key, Integer.valueOf(newCount));
        checkParents(methodCallExpression, count);
      }
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiLocalVariable)) {
        return;
      }
      final Integer count = element.getUserData(key);
      if (count != null) {
        checkParents(expression, count);
      }
    }
  }
}