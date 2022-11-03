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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SetInspectionOptionFix;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StringEqualsEmptyStringInspection extends BaseInspection {
  public boolean SUPPRESS_FOR_VALUES_WHICH_COULD_BE_NULL = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("string.equals.empty.string.option.do.not.add.null.check"), this,
                                          "SUPPRESS_FOR_VALUES_WHICH_COULD_BE_NULL");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean useIsEmpty = ((Boolean)infos[0]).booleanValue();
    if (useIsEmpty) {
      return InspectionGadgetsBundle.message("string.equals.empty.string.is.empty.problem.descriptor");
    } else {
      return InspectionGadgetsBundle.message("string.equals.empty.string.problem.descriptor");
    }
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final boolean useIsEmpty = ((Boolean)infos[0]).booleanValue();
    final boolean addNullCheck = ((Boolean)infos[1]).booleanValue();
    StringEqualsEmptyStringFix mainFix = new StringEqualsEmptyStringFix(useIsEmpty, addNullCheck);
    if (addNullCheck) {
      SetInspectionOptionFix disableFix = new SetInspectionOptionFix(
        this, "SUPPRESS_FOR_VALUES_WHICH_COULD_BE_NULL",
        InspectionGadgetsBundle.message("string.equals.empty.string.option.do.not.add.null.check"), true);
      return new InspectionGadgetsFix[]{mainFix, new DelegatingFix(disableFix)};
    }
    return new InspectionGadgetsFix[]{mainFix};
  }

  private static PsiExpression getCheckedExpression(boolean useIsEmpty, PsiExpression expression) {
    if (useIsEmpty || !(expression instanceof PsiMethodCallExpression)) {
      return expression;
    }
    // to replace stringBuffer.toString().equals("") with
    // stringBuffer.length() == 0
    final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    final String referenceName = methodExpression.getReferenceName();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      return expression;
    }
    final PsiType type = qualifierExpression.getType();
    if (HardcodedMethodConstants.TO_STRING.equals(referenceName) && type != null && (type.equalsToText(
      CommonClassNames.JAVA_LANG_STRING_BUFFER) || type.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER))) {
      return qualifierExpression;
    }
    else {
      return expression;
    }
  }

  private static class StringEqualsEmptyStringFix extends InspectionGadgetsFix {

    private final boolean myUseIsEmpty;
    private final boolean myAddNullCheck;

    StringEqualsEmptyStringFix(boolean useIsEmpty, boolean addNullCheck) {
      myUseIsEmpty = useIsEmpty;
      myAddNullCheck = addNullCheck;
    }

    @Override
    @NotNull
    public String getName() {
      if (myUseIsEmpty) {
        return CommonQuickFixBundle.message("fix.replace.with.x", "isEmpty()");
      }
      else {
        return CommonQuickFixBundle.message("fix.replace.with.x", "length()==0");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.equals.empty.string.fix.family.name");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiReferenceExpression expression = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiReferenceExpression.class);
      if (expression == null) return;
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression.getParent();
      final PsiExpression[] arguments = call.getArgumentList().getExpressions();
      if (arguments.length == 0) return;
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier == null) return;
      final PsiExpression argument = arguments[0];
      final PsiExpression checkedExpression;
      if (ExpressionUtils.isEmptyStringLiteral(argument)) {
        checkedExpression = getCheckedExpression(myUseIsEmpty, qualifier);
      }
      else {
        checkedExpression = getCheckedExpression(myUseIsEmpty, argument);
      }
      final @NonNls StringBuilder newExpression;
      CommentTracker ct = new CommentTracker();
      final PsiExpression parent = ObjectUtils.tryCast(call.getParent(), PsiExpression.class);
      final boolean isNegation = parent != null && BoolUtils.isNegation(parent);
      if (myAddNullCheck) {
        newExpression = new StringBuilder(ct.text(checkedExpression, ParenthesesUtils.EQUALITY_PRECEDENCE));
        newExpression.append(isNegation ? "==null||" : "!=null&&");
      }
      else {
        newExpression = new StringBuilder();
      }
      final PsiExpression expressionToReplace;
      String expressionText = ct.text(checkedExpression, ParenthesesUtils.METHOD_CALL_PRECEDENCE);
      if (isNegation) {
        expressionToReplace = parent;
        if (myUseIsEmpty) {
          newExpression.append('!').append(expressionText).append(".isEmpty()");
        }
        else {
          newExpression.append(expressionText).append(".length()!=0");
        }
      }
      else {
        expressionToReplace = call;
        if (myUseIsEmpty) {
          newExpression.append(expressionText).append(".isEmpty()");
        }
        else {
          newExpression.append(expressionText).append(".length()==0");
        }
      }

      PsiReplacementUtil.replaceExpression(expressionToReplace, newExpression.toString(), ct);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringEqualsEmptyStringVisitor();
  }

  private class StringEqualsEmptyStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"equals".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiElement context = call.getParent();
      final boolean useIsEmpty = PsiUtil.isLanguageLevel6OrHigher(call);
      if (!useIsEmpty && context instanceof PsiExpressionStatement) {
        // cheesy, but necessary, because otherwise the quickfix will
        // produce uncompilable code (out of merely incorrect code).
        return;
      }

      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      boolean addNullCheck = false;
      final PsiExpression argument = arguments[0];
      if (ExpressionUtils.isEmptyStringLiteral(qualifier)) {
        final PsiType type = argument.getType();
        if (!TypeUtils.isJavaLangString(type)) return;
        PsiExpression expression = getCheckedExpression(useIsEmpty, argument);
        addNullCheck = expression == argument && NullabilityUtil.getExpressionNullability(expression, true) != Nullability.NOT_NULL;
        if (addNullCheck && !ExpressionUtils.isSafelyRecomputableExpression(expression)) return;
      }
      else if (ExpressionUtils.isEmptyStringLiteral(argument)) {
        if (qualifier == null) return;
        final PsiType type = qualifier.getType();
        if (!TypeUtils.isJavaLangString(type)) return;
      }
      else {
        return;
      }
      if (addNullCheck && SUPPRESS_FOR_VALUES_WHICH_COULD_BE_NULL) {
        return;
      }
      registerMethodCallError(call, useIsEmpty, addNullCheck);
    }
  }
}