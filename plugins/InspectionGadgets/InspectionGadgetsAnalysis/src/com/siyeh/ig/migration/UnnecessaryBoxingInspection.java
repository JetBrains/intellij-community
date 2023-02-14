/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
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

import static com.intellij.codeInspection.options.OptPane.*;

public class UnnecessaryBoxingInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyReportSuperfluouslyBoxed = false;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyReportSuperfluouslyBoxed", InspectionGadgetsBundle.message("unnecessary.boxing.superfluous.option")));
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (infos.length == 0) {
      return InspectionGadgetsBundle.message("unnecessary.boxing.problem.descriptor");
    }
    PsiType type = (PsiType)infos[0];
    String parseMethod = (String)infos[1];
    return InspectionGadgetsBundle.message("unnecessary.boxing.inside.value.of.problem.descriptor", type.getPresentableText(), parseMethod);
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return infos.length == 0 ? new UnnecessaryBoxingFix() : new UnnecessaryBoxingFix((PsiType)infos[0], (String)infos[1]);
  }

  private static final class UnnecessaryBoxingFix extends InspectionGadgetsFix {

    private final @IntentionFamilyName String name;

    private UnnecessaryBoxingFix() {
      this.name = InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix");
    }

    private UnnecessaryBoxingFix(PsiType retType, String parseMethod) {
      this.name = CommonQuickFixBundle.message("fix.replace.with.x", retType.getPresentableText() + '.' + parseMethod + "()");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return name;
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiCallExpression.class);
      if (expression == null) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression unboxedExpression = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      if (unboxedExpression == null) {
        return;
      }
      final PsiType unboxedExpressionType = unboxedExpression.getType();
      if (unboxedExpressionType == null) {
        return;
      }
      final CommentTracker commentTracker = new CommentTracker();
      if (unboxedExpressionType.getCanonicalText().equals("java.lang.String")) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
        final String parseMethodName = JavaPsiBoxingUtils.getParseMethod(methodCall.getType());
        if (parseMethodName == null) {
          return;
        }
        ExpressionUtils.bindCallTo(methodCall, parseMethodName);
        return;
      }
      final Object value = ExpressionUtils.computeConstantExpression(unboxedExpression);
      if (value != null && !(unboxedExpression instanceof PsiReferenceExpression)) {
        if (value == Boolean.TRUE) {
          PsiReplacementUtil.replaceExpression(expression, "java.lang.Boolean.TRUE", commentTracker);
          return;
        }
        else if (value == Boolean.FALSE) {
          PsiReplacementUtil.replaceExpression(expression, "java.lang.Boolean.FALSE", commentTracker);
          return;
        }
      }
      final String replacementText = getUnboxedExpressionText(unboxedExpression, expression, commentTracker);
      if (replacementText == null) {
        return;
      }
      PsiReplacementUtil.replaceExpression(expression, replacementText, commentTracker);
    }

    @Nullable
    private static String getUnboxedExpressionText(@NotNull PsiExpression unboxedExpression,
                                                   @NotNull PsiExpression boxedExpression,
                                                   CommentTracker commentTracker) {
      final PsiType boxedType = boxedExpression.getType();
      if (boxedType == null) {
        return null;
      }
      final PsiType expressionType = unboxedExpression.getType();
      if (expressionType == null) {
        return null;
      }
      final PsiType unboxedType = PsiPrimitiveType.getUnboxedType(boxedType);
      if (unboxedType == null) {
        return null;
      }
      final String text = commentTracker.text(unboxedExpression);
      if (expressionType.equals(unboxedType)) {
        final PsiElement parent = boxedExpression.getParent();
        if (parent instanceof PsiExpression && ParenthesesUtils.areParenthesesNeeded(unboxedExpression, (PsiExpression)parent, false)) {
          return '(' + text + ')';
        }
        else {
          return text;
        }
      }
      if (unboxedExpression instanceof PsiLiteralExpression) {
        String newLiteral = PsiLiteralUtil.tryConvertNumericLiteral((PsiLiteralExpression)unboxedExpression, unboxedType);
        if (newLiteral != null) {
          return newLiteral;
        }
      }
      if (ParenthesesUtils.getPrecedence(unboxedExpression) > ParenthesesUtils.TYPE_CAST_PRECEDENCE) {
        return '(' + unboxedType.getCanonicalText() + ")(" + text + ')';
      }
      else {
        return '(' + unboxedType.getCanonicalText() + ')' + text;
      }
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryBoxingVisitor();
  }

  private class UnnecessaryBoxingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiType constructorType = expression.getType();
      final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(constructorType);
      if (unboxedType == null) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression boxedExpression = arguments[0];
      final PsiType argumentType = boxedExpression.getType();
      if (!(argumentType instanceof PsiPrimitiveType) || isBoxingNecessary(expression, boxedExpression)) {
        return;
      }
      if (onlyReportSuperfluouslyBoxed) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        if (!(expectedType instanceof PsiPrimitiveType)) {
          return;
        }
      }
      registerNewExpressionError(expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression boxedExpression = arguments[0];
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"valueOf".equals(referenceName)) {
        return;
      }
      final PsiMethod method = ObjectUtils.tryCast(methodExpression.resolve(), PsiMethod.class);
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String canonicalText = aClass.getQualifiedName();
      if (canonicalText == null || !TypeConversionUtil.isPrimitiveWrapper(canonicalText)) {
        return;
      }
      final PsiType boxedExpressionType = boxedExpression.getType();
      if (TypeUtils.isJavaLangString(boxedExpressionType)) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        final PsiType methodReturnType = method.getReturnType();
        if (expectedType instanceof PsiPrimitiveType) {
          final String parseMethod = JavaPsiBoxingUtils.getParseMethod(methodReturnType);
          if (parseMethod != null) {
            registerMethodCallError(expression, methodReturnType, parseMethod);
          }
        }
        return;
      }
      if (!(boxedExpressionType instanceof PsiPrimitiveType)) {
        return;
      }
      if (isBoxingNecessary(expression, boxedExpression)) {
        return;
      }
      if (onlyReportSuperfluouslyBoxed) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        if (!(expectedType instanceof PsiPrimitiveType)) {
          return;
        }
      }
      registerMethodCallError(expression);
    }

    private boolean isBoxingNecessary(PsiExpression boxingExpression, PsiExpression boxedExpression) {
      if (ExpressionUtils.isVoidContext(boxingExpression)) {
        // removing the boxing in this case will make the code uncompilable
        return true;
      }
      PsiElement parent = boxingExpression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        boxingExpression = (PsiExpression)parent;
        parent = parent.getParent();
      }
      if (parent instanceof PsiReferenceExpression || parent instanceof PsiSynchronizedStatement) {
        return true;
      }
      else if (parent instanceof PsiVariable) {
        PsiTypeElement typeElement = ((PsiVariable)parent).getTypeElement();
        // Inferred type may change if boxing is removed; if it's possible
        if (typeElement != null && typeElement.isInferredType()) return true;
      }
      else if (parent instanceof PsiTypeCastExpression castExpression) {
        return TypeUtils.isTypeParameter(castExpression.getType());
      }
      else if (parent instanceof PsiConditionalExpression conditionalExpression) {
        final PsiExpression thenExpression = conditionalExpression.getThenExpression();
        final PsiExpression elseExpression = conditionalExpression.getElseExpression();
        if (elseExpression == null || thenExpression == null) {
          return true;
        }
        if (PsiTreeUtil.isAncestor(thenExpression, boxingExpression, false)) {
          final PsiType type = elseExpression.getType();
          return !(type instanceof PsiPrimitiveType);
        }
        else if (PsiTreeUtil.isAncestor(elseExpression, boxingExpression, false)) {
          final PsiType type = thenExpression.getType();
          return !(type instanceof PsiPrimitiveType);
        }
        else {
          return false;
        }
      }
      else if (parent instanceof PsiPolyadicExpression polyadicExpression) {
        return isPossibleObjectComparison(boxingExpression, polyadicExpression);
      }
      return MethodCallUtils.isNecessaryForSurroundingMethodCall(boxingExpression, boxedExpression) ||
             !LambdaUtil.isSafeLambdaReturnValueReplacement(boxingExpression, boxedExpression);
    }

    private boolean isPossibleObjectComparison(PsiExpression expression, PsiPolyadicExpression polyadicExpression) {
      if (!ComparisonUtils.isEqualityComparison(polyadicExpression)) {
        return false;
      }
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (operand == expression) {
          continue;
        }
        if (!(operand.getType() instanceof PsiPrimitiveType)) {
          return true;
        }
        //else if (isUnboxingExpression(operand)) {
        //  return true;
        //}
      }
      return false;
    }
  }
}