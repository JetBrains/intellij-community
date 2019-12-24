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
package com.siyeh.ig.jdk;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class AutoBoxingInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreAddedToCollection = false;

  @NonNls static final Map<String, String> s_boxingClasses = new HashMap<>(8);

  static {
    s_boxingClasses.put("byte", CommonClassNames.JAVA_LANG_BYTE);
    s_boxingClasses.put("short", CommonClassNames.JAVA_LANG_SHORT);
    s_boxingClasses.put("int", CommonClassNames.JAVA_LANG_INTEGER);
    s_boxingClasses.put("long", CommonClassNames.JAVA_LANG_LONG);
    s_boxingClasses.put("float", CommonClassNames.JAVA_LANG_FLOAT);
    s_boxingClasses.put("double", CommonClassNames.JAVA_LANG_DOUBLE);
    s_boxingClasses.put("boolean", CommonClassNames.JAVA_LANG_BOOLEAN);
    s_boxingClasses.put("char", CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @Override
  public String getAlternativeID() {
    return "boxing";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("auto.boxing.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("auto.boxing.ignore.added.to.collection.option"), this,
                                          "ignoreAddedToCollection");
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AutoBoxingVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 0) {
      return null;
    }
    return new AutoBoxingFix();
  }

  private static class AutoBoxingFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("auto.boxing.make.boxing.explicit.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
      if (expectedType == null) {
        return;
      }
      final String expectedTypeText = expectedType.getCanonicalText();
      final String classToConstruct;
      if (s_boxingClasses.containsValue(expectedTypeText)) {
        classToConstruct = expectedTypeText;
      }
      else {
        final PsiType type = expression.getType();
        if (type == null) {
          return;
        }
        final String expressionTypeText = type.getCanonicalText();
        classToConstruct = s_boxingClasses.get(expressionTypeText);
      }
      if (shortcutReplace(expression, classToConstruct)) {
        return;
      }
      final PsiExpression strippedExpression = ParenthesesUtils.stripParentheses(expression);
      if (strippedExpression == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      @NonNls final String expressionText = strippedExpression.getText();
      @NonNls final String newExpression;
      if ("true".equals(expressionText)) {
        newExpression = "java.lang.Boolean.TRUE";
      }
      else if ("false".equals(expressionText)) {
        newExpression = "java.lang.Boolean.FALSE";
      }
      else {
        commentTracker.markUnchanged(strippedExpression);
        newExpression = classToConstruct + ".valueOf(" + expressionText + ')';
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
        PsiReplacementUtil.replaceExpression(typeCastExpression, newExpression, commentTracker);
      } else {
        PsiReplacementUtil.replaceExpression(expression, newExpression, commentTracker);
      }
    }

    private static boolean shortcutReplace(PsiExpression expression, String classToConstruct) {
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return false;
      }
      if (classToConstruct.equals(CommonClassNames.JAVA_LANG_INTEGER)) {
        if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_INTEGER, PsiType.INT, "intValue")) {
          expression.replace(qualifierExpression);
          return true;
        }
      }
      else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_SHORT)) {
        if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_SHORT, PsiType.SHORT, "shortValue")) {
          expression.replace(qualifierExpression);
          return true;
        }
      }
      else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_BYTE)) {
        if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_BYTE, PsiType.BYTE, "byteValue")) {
          expression.replace(qualifierExpression);
          return true;
        }
      }
      else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_CHARACTER)) {
        if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_CHARACTER, PsiType.CHAR, "charValue")) {
          expression.replace(qualifierExpression);
          return true;
        }
      }
      else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_LONG)) {
        if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_LONG, PsiType.LONG, "longValue")) {
          expression.replace(qualifierExpression);
          return true;
        }
      }
      else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_FLOAT)) {
        if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_FLOAT, PsiType.FLOAT, "floatValue")) {
          expression.replace(qualifierExpression);
          return true;
        }
      }
      else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_DOUBLE)) {
        if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_DOUBLE, PsiType.DOUBLE, "doubleValue")) {
          expression.replace(qualifierExpression);
          return true;
        }
      }
      return false;
    }
  }

  private class AutoBoxingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchExpression(PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
      super.visitArrayAccessExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitUnaryExpression(PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression instanceof PsiMethodReferenceExpression) {
        final PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)expression;
        if (methodReferenceExpression.isConstructor()) {
          return;
        }
        final PsiElement referenceNameElement = methodReferenceExpression.getReferenceNameElement();
        if (referenceNameElement == null) {
          return;
        }
        final PsiElement target = methodReferenceExpression.resolve();
        if (!(target instanceof PsiMethod)) {
          return;
        }
        final PsiMethod method = (PsiMethod)target;
        final PsiType returnType = method.getReturnType();
        if (returnType == null || returnType.equals(PsiType.VOID) || !TypeConversionUtil.isPrimitiveAndNotNull(returnType)) {
          return;
        }
        final PsiPrimitiveType primitiveType = (PsiPrimitiveType)returnType;
        final PsiClassType boxedType = primitiveType.getBoxedType(expression);
        if (boxedType == null) {
          return;
        }
        final PsiType functionalInterfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(methodReferenceExpression);
        if (functionalInterfaceReturnType == null || TypeConversionUtil.isPrimitiveAndNotNull(functionalInterfaceReturnType) ||
            !functionalInterfaceReturnType.isAssignableFrom(boxedType)) {
          return;
        }
        registerError(referenceNameElement);
      }
      else {
        checkExpression(expression);
      }
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(@NotNull PsiExpression expression) {
      if (!ExpressionUtils.isAutoBoxed(expression)) {
        return;
      }
      if (ignoreAddedToCollection && isAddedToCollection(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean isAddedToCollection(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      final PsiElement grandParent = expressionList.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"put".equals(methodName) && !"set".equals(methodName) && !"add".equals(methodName)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      return TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_UTIL_COLLECTION, CommonClassNames.JAVA_UTIL_MAP) != null;
    }
  }
}