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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ResultOfObjectAllocationIgnoredInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField") public OrderedSet<String> ignoredClasses = new OrderedSet<>();

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("result.of.object.allocation.ignored.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("result.of.object.allocation.ignored.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ResultOfObjectAllocationIgnoredVisitor();
  }

  private class ResultOfObjectAllocationIgnoredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
      super.visitExpressionStatement(statement);
      final PsiExpression expression = statement.getExpression();
      if (!(expression instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
      if (arrayDimensions.length != 0 || newExpression.getArrayInitializer() != null) {
        return;
      }
      final PsiJavaCodeReferenceElement reference = newExpression.getClassOrAnonymousClassReference();
      if (reference == null) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      if (!(expression instanceof PsiAnonymousClass) && ignoredClasses.contains(aClass.getQualifiedName())) {
        return;
      }
      registerNewExpressionError(newExpression, newExpression);
    }
  }
}