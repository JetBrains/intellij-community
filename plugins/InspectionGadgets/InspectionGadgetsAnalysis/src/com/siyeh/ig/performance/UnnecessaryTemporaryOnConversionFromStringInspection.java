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
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.JavaPsiBoxingUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryTemporaryOnConversionFromStringInspection
  extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String replacementString = calculateReplacementExpression((PsiMethodCallExpression)infos[0], new CommentTracker(), false);
    return InspectionGadgetsBundle.message(
      "unnecessary.temporary.on.conversion.from.string.problem.descriptor",
      replacementString);
  }

  @Nullable
  @NonNls
  static String calculateReplacementExpression(PsiMethodCallExpression expression,
                                               CommentTracker commentTracker,
                                               boolean isFullyQualified) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiNewExpression qualifier = ObjectUtils.tryCast(methodExpression.getQualifierExpression(), PsiNewExpression.class);
    if (qualifier == null) return null;
    final PsiExpressionList argumentList = qualifier.getArgumentList();
    if (argumentList == null || argumentList.getExpressionCount() != 1) return null;
    final PsiExpression arg = argumentList.getExpressions()[0];
    final PsiType type = qualifier.getType();
    if (type == null) return null;
    final String qualifierType = type.getPresentableText();
    final String canonicalType = type.getCanonicalText();
    final String name = isFullyQualified ? canonicalType : qualifierType;
    final String conversionName = JavaPsiBoxingUtils.getParseMethod(canonicalType);
    if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_BOOLEAN, type) && !PsiUtil.isLanguageLevel5OrHigher(expression)) {
      return name + '.' + "valueOf" + '(' + commentTracker.text(arg) + ").booleanValue()";
    }
    return name + '.' + conversionName + '(' + commentTracker.text(arg) + ')';
  }

  @Override
  @Nullable
  public InspectionGadgetsFix buildFix(Object... infos) {
    final String replacementExpression = calculateReplacementExpression((PsiMethodCallExpression)infos[0], new CommentTracker(), false);
    if (replacementExpression == null) return null;
    final String name = CommonQuickFixBundle.message("fix.replace.with.x", replacementExpression);
    return new UnnecessaryTemporaryObjectFix(name);
  }

  private static final class UnnecessaryTemporaryObjectFix extends InspectionGadgetsFix {

    private final String m_name;

    private UnnecessaryTemporaryObjectFix(String name) {
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
      final CommentTracker commentTracker = new CommentTracker();
      final String newExpression = calculateReplacementExpression(expression, commentTracker, true);
      if (newExpression == null) return;
      PsiReplacementUtil.replaceExpression(expression, newExpression, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryTemporaryObjectVisitor();
  }

  private static class UnnecessaryTemporaryObjectVisitor extends BaseInspectionVisitor {

    /**
     */
    @NonNls private static final Map<String, String> s_basicTypeMap = new HashMap<>(7);

    static {
      s_basicTypeMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "booleanValue");
      s_basicTypeMap.put(CommonClassNames.JAVA_LANG_BYTE, "byteValue");
      s_basicTypeMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "doubleValue");
      s_basicTypeMap.put(CommonClassNames.JAVA_LANG_FLOAT, "floatValue");
      s_basicTypeMap.put(CommonClassNames.JAVA_LANG_INTEGER, "intValue");
      s_basicTypeMap.put(CommonClassNames.JAVA_LANG_LONG, "longValue");
      s_basicTypeMap.put(CommonClassNames.JAVA_LANG_SHORT, "shortValue");
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      final Map<String, String> basicTypeMap = s_basicTypeMap;
      if (!basicTypeMap.containsValue(methodName)) {
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
      if (arguments.length != 1) {
        return;
      }
      final PsiType argumentType = arguments[0].getType();
      if (!TypeUtils.isJavaLangString(argumentType)) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (type == null) {
        return;
      }
      final String typeText = type.getCanonicalText();
      if (!basicTypeMap.containsKey(typeText)) {
        return;
      }
      final String mappingMethod = basicTypeMap.get(typeText);
      if (!mappingMethod.equals(methodName)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}