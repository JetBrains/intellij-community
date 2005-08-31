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
package com.siyeh.ig.jdk15;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryUnboxingInspection extends ExpressionInspection {

  @NonNls private static final Map<String, String> s_unboxingMethods = new HashMap<String, String>(8);
  private final UnnecessaryUnboxingFix fix = new UnnecessaryUnboxingFix();

  static {
    s_unboxingMethods.put("java.lang.Integer", "intValue");
    s_unboxingMethods.put("java.lang.Short", "shortValue");
    s_unboxingMethods.put("java.lang.Boolean", "booleanValue");
    s_unboxingMethods.put("java.lang.Long", "longValue");
    s_unboxingMethods.put("java.lang.Byte", "byteValue");
    s_unboxingMethods.put("java.lang.Float", "floatValue");
    s_unboxingMethods.put("java.lang.Long", "longValue");
    s_unboxingMethods.put("java.lang.Double", "doubleValue");
    s_unboxingMethods.put("java.lang.Character", "charValue");
  }

  public String getGroupDisplayName() {
    return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryUnboxingVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class UnnecessaryUnboxingFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.unboxing.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      final PsiElement parent = methodCall.getParent();
      if (parent instanceof PsiExpression) {
        final PsiExpression strippedQualifier =
          ParenthesesUtils.stripParentheses(qualifier);
        final String strippedQualifierText = strippedQualifier.getText();
        if (ParenthesesUtils.getPrecendence(strippedQualifier) >
            ParenthesesUtils.getPrecendence((PsiExpression)parent)) {
          replaceExpression(methodCall, strippedQualifierText);
        }
        else {
          replaceExpression(methodCall,
                            '(' + strippedQualifierText + ')');
        }
      }
      else {
        final PsiExpression strippedQualifier =
          ParenthesesUtils.stripParentheses(qualifier);
        final String strippedQualiferText = strippedQualifier.getText();
        replaceExpression(methodCall, strippedQualiferText);
      }
    }
  }

  private static class UnnecessaryUnboxingVisitor extends BaseInspectionVisitor {


    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiManager manager = expression.getManager();
      final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
      if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
          languageLevel.equals(LanguageLevel.JDK_1_4)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      final String methodName = methodExpression.getReferenceName();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) {
        return;
      }
      final String qualifierTypeName = qualifierType.getCanonicalText();
      if (!s_unboxingMethods.containsKey(qualifierTypeName)) {
        return;
      }
      final String unboxingMethod = s_unboxingMethods.get(qualifierTypeName);
      if (!unboxingMethod.equals(methodName)) {
        return;
      }
      if (getContainingExpression(expression) instanceof PsiTypeCastExpression) {
        return;
      }
      registerError(expression);
    }

    @Nullable
    private PsiExpression getContainingExpression(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent == null || !(parent instanceof PsiExpression)) {
        return null;
      }
      final PsiExpression parentExpression = (PsiExpression)parent;
      if (parent instanceof PsiParenthesizedExpression ||
          parent instanceof PsiConditionalExpression) {
        return getContainingExpression(parentExpression);
      }
      else {
        return parentExpression;
      }
    }


  }
}
