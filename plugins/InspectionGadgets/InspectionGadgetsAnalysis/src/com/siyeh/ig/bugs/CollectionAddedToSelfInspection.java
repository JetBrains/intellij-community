/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CollectionAddedToSelfInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "collection.added.to.self.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String methodName = (String)infos[0];
    return InspectionGadgetsBundle.message("collection.added.to.self.problem.descriptor", methodName);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CollectionAddedToSelfVisitor();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class CollectionAddedToSelfVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if ("equals".equals(methodName)) {
        return; // object method
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)qualifier).resolve();
      if (!(referent instanceof PsiVariable)) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      PsiExpression selfArgument = null;
      final PsiExpression[] arguments = argumentList.getExpressions();
      for (PsiExpression argument : arguments) {
        if (EquivalenceChecker.expressionsAreEquivalent(qualifier, argument)) {
          selfArgument = argument;
        }
      }
      if (selfArgument == null) {
        return;
      }
      final PsiType qualifierType = qualifier.getType();
      if (!InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_COLLECTION) &&
          !InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_MAP)) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (!CollectionUtils.isCollectionClassOrInterface(aClass)) {
        return;
      }
      registerError(selfArgument, methodName);
    }
  }
}