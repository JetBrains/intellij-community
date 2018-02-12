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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AutoUnboxingInspection extends BaseInspection {

  /**
   * @noinspection StaticCollection
   */
  @NonNls static final Map<String, String> s_unboxingMethods = new HashMap<>(8);

  static {
    s_unboxingMethods.put("byte", "byteValue");
    s_unboxingMethods.put("short", "shortValue");
    s_unboxingMethods.put("int", "intValue");
    s_unboxingMethods.put("long", "longValue");
    s_unboxingMethods.put("float", "floatValue");
    s_unboxingMethods.put("double", "doubleValue");
    s_unboxingMethods.put("boolean", "booleanValue");
    s_unboxingMethods.put("char", "charValue");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("auto.unboxing.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("auto.unboxing.problem.descriptor");
  }

  @Override
  @Nullable
  public InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 0 || !isFixApplicable((PsiExpression)infos[0])) {
      return null;
    }
    return new AutoUnboxingFix();
  }

  private static boolean isFixApplicable(PsiExpression location) {
    // conservative check to see if the result value of the postfix
    // expression is used later in the same expression statement.
    // Applying the quick fix in such a case would break the code
    // because the explicit unboxing code would split the expression in
    // multiple statements.
    final PsiElement parent = location.getParent();
    if (!(parent instanceof PsiPostfixExpression)) {
      return true;
    }
    final PsiReferenceExpression reference;
    if (location instanceof PsiReferenceExpression) {
      reference = (PsiReferenceExpression)location;
    }
    else if (location instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)location;
      final PsiExpression expression = arrayAccessExpression.getArrayExpression();
      if (!(expression instanceof PsiReferenceExpression)) {
        return true;
      }
      reference = (PsiReferenceExpression)expression;
    }
    else {
      return true;
    }
    final PsiElement element = reference.resolve();
    if (element == null) {
      return true;
    }
    final PsiStatement statement = PsiTreeUtil.getParentOfType(parent, PsiStatement.class);
    final LocalSearchScope scope = new LocalSearchScope(statement);
    final Query<PsiReference> query = ReferencesSearch.search(element, scope);
    final Collection<PsiReference> references = query.findAll();
    return references.size() <= 1;
  }

  private static class AutoUnboxingFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("auto.unboxing.make.unboxing.explicit.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      final PsiPrimitiveType unboxedType = (PsiPrimitiveType)ExpectedTypeUtils.findExpectedType(expression, false, true);
      if (unboxedType == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final String newExpressionText = buildNewExpressionText(expression, unboxedType, commentTracker);
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiElement parent = expression.getParent();
      final String expressionText = expression.getText();
      if (parent instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
        PsiReplacementUtil.replaceExpression(typeCastExpression, newExpressionText, commentTracker);
      }
      else if (parent instanceof PsiPrefixExpression && !unboxedType.equalsToText("boolean")) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
        final IElementType tokenType = prefixExpression.getOperationTokenType();
        if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
          commentTracker.markUnchanged(expression);
          PsiReplacementUtil.replaceExpression(prefixExpression, expressionText + '=' + newExpressionText + "+1", commentTracker);
        }
        else if (JavaTokenType.MINUSMINUS.equals(tokenType)) {
          commentTracker.markUnchanged(expression);
          PsiReplacementUtil.replaceExpression(prefixExpression, expressionText + '=' + newExpressionText + "-1", commentTracker);
        } else {
          PsiReplacementUtil.replaceExpression(prefixExpression, prefixExpression.getOperationSign().getText() + newExpressionText, commentTracker);
        }
      }
      else if (parent instanceof PsiPostfixExpression) {
        final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)parent;
        final IElementType tokenType = postfixExpression.getOperationTokenType();
        final PsiElement grandParent = postfixExpression.getParent();
        if (grandParent instanceof PsiExpressionStatement) {
          if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
            commentTracker.markUnchanged(expression);
            PsiReplacementUtil.replaceExpression(postfixExpression, expressionText + '=' + newExpressionText + "+1", commentTracker);
          }
          else if (JavaTokenType.MINUSMINUS.equals(tokenType)) {
            commentTracker.markUnchanged(expression);
            PsiReplacementUtil.replaceExpression(postfixExpression, expressionText + '=' + newExpressionText + "-1", commentTracker);
          }
        }
        else {
          final PsiElement element = postfixExpression.replace(postfixExpression.getOperand());
          final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
          if (statement == null) {
            return;
          }
          final PsiStatement newStatement;
          if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
            newStatement = factory.createStatementFromText(expressionText + '=' + newExpressionText + "+1;", statement);
          }
          else {
            newStatement = factory.createStatementFromText(expressionText + '=' + newExpressionText + "-1;", statement);
          }
          final PsiElement greatGrandParent = statement.getParent();
          greatGrandParent.addAfter(newStatement, statement);
        }
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
        final PsiExpression lExpression = assignmentExpression.getLExpression();
        if (expression.equals(lExpression)) {
          final PsiJavaToken operationSign = assignmentExpression.getOperationSign();
          final String operationSignText = operationSign.getText();
          final char sign = operationSignText.charAt(0);
          final PsiExpression rExpression = assignmentExpression.getRExpression();
          if (rExpression == null) {
            return;
          }
          final String text = commentTracker.text(lExpression) + '=' + newExpressionText + sign + commentTracker.text(rExpression);
          final PsiExpression newExpression = factory.createExpressionFromText(text, assignmentExpression);
          commentTracker.replaceAndRestoreComments(assignmentExpression, newExpression);
        }
        else {
          PsiReplacementUtil.replaceExpression(expression, newExpressionText, commentTracker);
        }
      }
      else {
        PsiReplacementUtil.replaceExpression(expression, newExpressionText, commentTracker);
      }
    }

    private static String buildNewExpressionText(PsiExpression expression,
                                                 PsiPrimitiveType unboxedType,
                                                 CommentTracker commentTracker) {
      final String unboxedTypeText = unboxedType.getCanonicalText();
      final String expressionText = expression.getText();
      final String boxMethodName = s_unboxingMethods.get(unboxedTypeText);
      if (expression instanceof PsiTypeCastExpression) {
        commentTracker.markUnchanged(expression);
        return '(' + expressionText + ")." + boxMethodName + "()";
      }
      final String constantText = computeConstantBooleanText(expression);
      if (constantText != null) {
        return constantText;
      }
      if (expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        if (isValueOfCall(methodCallExpression)) {
          final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
          final PsiExpression[] arguments = argumentList.getExpressions();
          final PsiExpression argument = commentTracker.markUnchanged(arguments[0]);
          return argument.getText();
        }
      }
      commentTracker.markUnchanged(expression);
      final PsiType type = expression.getType();
      if (type != null && type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        return "((" + unboxedType.getBoxedTypeName() + ')' + expressionText + ")." + boxMethodName + "()";
      }
      return expressionText + '.' + boxMethodName + "()";
    }

    private static boolean isValueOfCall(PsiMethodCallExpression methodCallExpression) {
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return false;
      }
      final PsiExpression argument = arguments[0];
      final PsiType type = argument.getType();
      return (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_INTEGER, null, "valueOf", PsiType.INT) &&
              PsiType.INT.equals(type)) ||
             (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_SHORT, null, "valueOf", PsiType.SHORT) &&
              PsiType.SHORT.equals(type)) ||
             (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_BYTE, null, "valueOf", PsiType.BYTE) &&
              PsiType.BYTE.equals(type)) ||
             (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_LONG, null, "valueOf", PsiType.LONG) &&
              PsiType.LONG.equals(type)) ||
             (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_CHARACTER, null, "valueOf", PsiType.CHAR) &&
              PsiType.CHAR.equals(type)) ||
             (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_DOUBLE, null, "valueOf", PsiType.DOUBLE) &&
              PsiType.DOUBLE.equals(type)) ||
             (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_FLOAT, null, "valueOf", PsiType.FLOAT) &&
              PsiType.FLOAT.equals(type));
    }

    @NonNls
    private static String computeConstantBooleanText(PsiExpression expression) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return null;
      }
      final PsiField field = (PsiField)target;
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return null;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_BOOLEAN.equals(qualifiedName)) {
        return null;
      }
      @NonNls final String name = field.getName();
      if ("TRUE".equals(name)) {
        return "true";
      }
      else if ("FALSE".equals(name)) {
        return "false";
      }
      else {
        return null;
      }
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AutoUnboxingVisitor();
  }

  private static class AutoUnboxingVisitor extends BaseInspectionVisitor {
    @Override
    public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
      super.visitArrayAccessExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
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
        if (!TypeConversionUtil.isAssignableFromPrimitiveWrapper(returnType)) {
          return;
        }
        final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(returnType);
        if (unboxedType == null) {
          return;
        }
        final PsiType functionalInterfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(methodReferenceExpression);
        if (functionalInterfaceReturnType == null || !ClassUtils.isPrimitive(functionalInterfaceReturnType) ||
            !functionalInterfaceReturnType.isAssignableFrom(unboxedType)) {
          return;
        }
        registerError(referenceNameElement);
      }
      else {
        checkExpression(expression);
      }
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      if (method != null && AnnotationUtil.isAnnotated(method, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, 0)) {
        return;
      }
      checkExpression(expression);
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      if (expression.getParent() instanceof PsiParenthesizedExpression) {
        return;
      }
      final PsiType expressionType = expression.getType();
      if (!TypeConversionUtil.isAssignableFromPrimitiveWrapper(expressionType)) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
      if (!TypeConversionUtil.isPrimitiveAndNotNull(expectedType)) {
        return;
      }
      if (!(expression.getParent() instanceof PsiTypeCastExpression)) {
        final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(expressionType);
        if (unboxedType == null || !expectedType.isAssignableFrom(unboxedType)) {
          return;
        }
      }
      registerError(expression, expression);
    }
  }
}
