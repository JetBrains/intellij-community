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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UseOfPropertiesAsHashtableInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "properties.object.as.hashtable.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "properties.object.as.hashtable.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethodCallExpression methodCallExpression =
      (PsiMethodCallExpression)infos[0];
    final String methodName =
      methodCallExpression.getMethodExpression().getReferenceName();
    final boolean put = HardcodedMethodConstants.PUT.equals(methodName);
    if (!(put || HardcodedMethodConstants.GET.equals(methodName))) {
      return null;
    }
    final PsiExpressionList argumentList =
      methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (PsiExpression argument : arguments) {
      final PsiType type = argument.getType();
      if (type == null ||
          !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return null;
      }
    }
    return new UseOfPropertiesAsHashtableFix(put);
  }

  private static class UseOfPropertiesAsHashtableFix
    extends InspectionGadgetsFix {

    private final boolean put;

    public UseOfPropertiesAsHashtableFix(boolean put) {
      this.put = put;
    }

    @NotNull
    @Override
    public String getName() {
      if (put) {
        return InspectionGadgetsBundle.message(
          "properties.object.as.hashtable.set.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message(
          "properties.object.as.hashtable.get.quickfix");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Fix property access";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      CommentTracker commentTracker = new CommentTracker();
      @NonNls final StringBuilder newExpression = new StringBuilder();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null) {
        newExpression.append(commentTracker.text(qualifierExpression));
        newExpression.append('.');
      }
      if (put) {
        newExpression.append("setProperty(");
      }
      else {
        newExpression.append("getProperty(");
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      newExpression.append(StringUtil.join(arguments, arg -> commentTracker.text(arg), ","));
      newExpression.append(')');

      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfPropertiesAsHashtableVisitor();
  }

  private static class UseOfPropertiesAsHashtableVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!(HardcodedMethodConstants.PUT.equals(methodName) ||
            HardcodedMethodConstants.PUTALL.equals(methodName) ||
            HardcodedMethodConstants.GET.equals(methodName))) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(containingClass,
                                       "java.util.Hashtable")) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                CommonClassNames.JAVA_UTIL_PROPERTIES)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}
