/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryTemporaryOnConversionFromStringInspection extends ExpressionInspection {

  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Map<String, String> s_basicTypeMap = new HashMap<String, String>(6);
  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Map<String, String> s_conversionMap = new HashMap<String, String>(6);

  static {
    s_basicTypeMap.put("java.lang.Short", "shortValue");
    s_basicTypeMap.put("java.lang.Integer", "intValue");
    s_basicTypeMap.put("java.lang.Long", "longValue");
    s_basicTypeMap.put("java.lang.Float", "floatValue");
    s_basicTypeMap.put("java.lang.Double", "doubleValue");
    s_basicTypeMap.put("java.lang.Boolean", "booleanValue");

    s_conversionMap.put("java.lang.Short", "parseShort");
    s_conversionMap.put("java.lang.Integer", "parseInt");
    s_conversionMap.put("java.lang.Long", "parseLong");
    s_conversionMap.put("java.lang.Float", "parseFloat");
    s_conversionMap.put("java.lang.Double", "parseDouble");
    s_conversionMap.put("java.lang.Boolean", "valueOf");
  }

  public String getGroupDisplayName() {
    return GroupNames.PERFORMANCE_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public String buildErrorString(PsiElement location) {
    final String replacementString = calculateReplacementExpression(location);
    return InspectionGadgetsBundle.message("string.can.be.simplified.problem.descriptor", replacementString);
  }

  @NonNls private static String calculateReplacementExpression(PsiElement location) {
    final PsiMethodCallExpression expression = (PsiMethodCallExpression)location;
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiNewExpression qualifier = (PsiNewExpression)methodExpression.getQualifierExpression();
    final PsiExpressionList argumentList = qualifier.getArgumentList();
    assert argumentList != null;
    final PsiExpression arg = argumentList.getExpressions()[0];
    final PsiType type = qualifier.getType();
    final String qualifierType = type.getPresentableText();
    final String canonicalType = type.getCanonicalText();

    final String conversionName = s_conversionMap.get(canonicalType);
    if (TypeUtils.typeEquals("java.lang.Boolean", type)) {
      return qualifierType + '.' + conversionName + '(' + arg.getText() + ").booleanValue()";
    }
    else {
      return qualifierType + '.' + conversionName + '(' + arg.getText() + ')';
    }
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return new UnnecessaryTemporaryObjectFix((PsiMethodCallExpression)location);
  }

  private static class UnnecessaryTemporaryObjectFix extends InspectionGadgetsFix {
    private final String m_name;

    private UnnecessaryTemporaryObjectFix(PsiMethodCallExpression location) {
      super();
      m_name = InspectionGadgetsBundle
        .message("string.replace.quickfix", calculateReplacementExpression(location));
    }

    public String getName() {
      return m_name;
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiMethodCallExpression expression =
        (PsiMethodCallExpression)descriptor.getPsiElement();
      final String newExpression = calculateReplacementExpression(expression);
      replaceExpression(expression, newExpression);
    }

  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryTemporaryObjectVisitor();
  }

  private static class UnnecessaryTemporaryObjectVisitor extends BaseInspectionVisitor {

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
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
      final PsiNewExpression newExp = (PsiNewExpression)qualifier;
      final PsiExpressionList argList = newExp.getArgumentList();
      if (argList == null) {
        return;
      }
      final PsiExpression[] args = argList.getExpressions();
      if (args.length != 1) {
        return;
      }
      final PsiType argType = args[0].getType();
      if (!TypeUtils.isJavaLangString(argType)) {
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
      registerError(expression);
    }
  }
}
