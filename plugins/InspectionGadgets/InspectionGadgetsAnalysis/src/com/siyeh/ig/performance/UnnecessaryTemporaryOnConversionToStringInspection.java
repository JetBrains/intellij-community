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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class UnnecessaryTemporaryOnConversionToStringInspection
  extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String replacementString = calculateReplacementExpression((PsiMethodCallExpression)infos[0], new CommentTracker());
    return InspectionGadgetsBundle.message(
      "unnecessary.temporary.on.conversion.from.string.problem.descriptor",
      replacementString);
  }

  @Nullable
  @NonNls
  static String calculateReplacementExpression(@NotNull PsiMethodCallExpression expression, @NotNull CommentTracker commentTracker) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiNewExpression qualifier = ObjectUtils.tryCast(methodExpression.getQualifierExpression(), PsiNewExpression.class);
    if (qualifier == null) return null;
    final PsiExpressionList argumentList = qualifier.getArgumentList();
    if (argumentList == null) return null;
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 1) return null;
    final PsiType type = qualifier.getType();
    if (type == null) return null;
    final String argumentText = commentTracker.text(arguments[0]);
    final String qualifierType = type.getPresentableText();
    return qualifierType + ".toString(" + argumentText + ')';
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final String replacement = calculateReplacementExpression((PsiMethodCallExpression)infos[0], new CommentTracker());
    final String name = CommonQuickFixBundle.message("fix.replace.with.x", replacement);
    return new UnnecessaryTemporaryObjectFix(name);
  }

  private static final class UnnecessaryTemporaryObjectFix
    extends InspectionGadgetsFix {

    private final String m_name;

    private UnnecessaryTemporaryObjectFix(
      String name) {
      m_name = name;
    }

    @Override
    @NotNull
    public String getName() {
      return m_name;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiMethodCallExpression expression = (PsiMethodCallExpression)descriptor.getPsiElement();
      CommentTracker commentTracker = new CommentTracker();
      final String newExpression = calculateReplacementExpression(expression, commentTracker);
      if (newExpression == null) return;
      PsiReplacementUtil.replaceExpression(expression, newExpression, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryTemporaryObjectVisitor();
  }

  private static class UnnecessaryTemporaryObjectVisitor
    extends BaseInspectionVisitor {

    /**
     */
    private static final Set<String> s_basicTypes = new HashSet<>(8);

    static {
      s_basicTypes.add(CommonClassNames.JAVA_LANG_BOOLEAN);
      s_basicTypes.add(CommonClassNames.JAVA_LANG_BYTE);
      s_basicTypes.add(CommonClassNames.JAVA_LANG_CHARACTER);
      s_basicTypes.add(CommonClassNames.JAVA_LANG_DOUBLE);
      s_basicTypes.add(CommonClassNames.JAVA_LANG_FLOAT);
      s_basicTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
      s_basicTypes.add(CommonClassNames.JAVA_LANG_LONG);
      s_basicTypes.add(CommonClassNames.JAVA_LANG_SHORT);
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)qualifier;
      final PsiExpressionList argumentList =
        newExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length < 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();
      if (argumentType != null &&
          argumentType.equalsToText(
            CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (type == null) {
        return;
      }
      final String typeName = type.getCanonicalText();
      if (!s_basicTypes.contains(typeName)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}