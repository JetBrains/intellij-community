/*
 * Copyright 2008-2018 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class UnnecessaryCallToStringValueOfInspection extends BaseInspection implements CleanupLocalInspectionTool{
  private static final @NonNls CallMatcher STATIC_TO_STRING_CONVERTERS = CallMatcher.anyOf(
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("boolean"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("char"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("double"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("float"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("int"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes("long"),
    staticCall(JAVA_LANG_STRING, "valueOf").parameterTypes(JAVA_LANG_OBJECT),
    staticCall(JAVA_LANG_BOOLEAN, "toString").parameterTypes("boolean"),
    staticCall(JAVA_LANG_BYTE, "toString").parameterTypes("byte"),
    staticCall(JAVA_LANG_SHORT, "toString").parameterTypes("short"),
    staticCall(JAVA_LANG_CHARACTER, "toString").parameterTypes("char"),
    staticCall(JAVA_LANG_INTEGER, "toString").parameterTypes("int"),
    staticCall(JAVA_LANG_LONG, "toString").parameterTypes("long"),
    staticCall(JAVA_LANG_FLOAT, "toString").parameterTypes("float"),
    staticCall(JAVA_LANG_DOUBLE, "toString").parameterTypes("double"),
    staticCall(JAVA_UTIL_OBJECTS, "toString").parameterTypes(JAVA_LANG_OBJECT)
  );

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.tostring.call.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final String text = (String)infos[0];
    return new UnnecessaryCallToStringValueOfFix(text);
  }

  public static String calculateReplacementText(PsiExpression expression) {
    if (!(expression instanceof PsiPolyadicExpression)) {
      return expression.getText();
    }
    final PsiType type = expression.getType();
    if (TypeUtils.typeEquals(JAVA_LANG_STRING, type) ||
        ParenthesesUtils.getPrecedence(expression) < ParenthesesUtils.ADDITIVE_PRECEDENCE) {
      return expression.getText();
    }
    return '(' + expression.getText() + ')';
  }

  private static class UnnecessaryCallToStringValueOfFix extends InspectionGadgetsFix {

    private final String replacementText;

    UnnecessaryCallToStringValueOfFix(String replacementText) {
      this.replacementText = replacementText;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", replacementText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiMethodCallExpression call = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression arg = tryUnwrapRedundantConversion(call);
      if (arg == null) return;
      CommentTracker tracker = new CommentTracker();
      PsiReplacementUtil.replaceExpression(call, calculateReplacementText(tracker.markUnchanged(arg)), tracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryCallToStringValueOfVisitor();
  }

  private static class UnnecessaryCallToStringValueOfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      final PsiExpression argument = tryUnwrapRedundantConversion(call);
      if (argument == null) return;
      registerErrorAtOffset(call, 0, call.getArgumentList().getStartOffsetInParent(), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    calculateReplacementText(argument));
    }
  }

  @Nullable
  private static PsiExpression tryUnwrapRedundantConversion(PsiMethodCallExpression call) {
    if (!STATIC_TO_STRING_CONVERTERS.test(call)) return null;
    final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
    if (argument == null) return null;
    PsiType argumentType = argument.getType();
    if (argumentType instanceof PsiPrimitiveType) {
      PsiMethod method = call.resolveMethod();
      assert method != null; // otherwise the matcher above won't match
      if (!Objects.requireNonNull(method.getParameterList().getParameter(0)).getType().equals(argumentType)) {
        return null;
      }
    }
    final boolean throwable = TypeUtils.expressionHasTypeOrSubtype(argument, CommonClassNames.JAVA_LANG_THROWABLE);
    if (ExpressionUtils.isConversionToStringNecessary(call, throwable)) {
      if (!TypeUtils.isJavaLangString(argumentType) ||
          NullabilityUtil.getExpressionNullability(argument, true) != Nullability.NOT_NULL) {
        return null;
      }
    }
    if (isReplacementAmbiguous(call, argument)) return null;
    return argument;
  }

  private static boolean isReplacementAmbiguous(PsiMethodCallExpression call, PsiExpression argument) {
    if (!PsiPolyExpressionUtil.isPolyExpression(argument)) return false;
    PsiExpressionList exprList = ObjectUtils.tryCast(ParenthesesUtils.getParentSkipParentheses(call), PsiExpressionList.class);
    if (exprList == null) return false;
    PsiCallExpression parentCall = ObjectUtils.tryCast(exprList.getParent(), PsiCallExpression.class);
    if (parentCall == null) return false;
    PsiCallExpression copy = (PsiCallExpression)parentCall.copy();
    int argIndex = ContainerUtil.indexOf(Arrays.asList(exprList.getExpressions()), expr -> PsiUtil.skipParenthesizedExprDown(expr) == call);
    assert argIndex >= -1;
    PsiExpression argCopy = Objects.requireNonNull(copy.getArgumentList()).getExpressions()[argIndex];
    argCopy.replace(argument);
    JavaResolveResult result = copy.resolveMethodGenerics();
    return !result.isValidResult();
  }
}
