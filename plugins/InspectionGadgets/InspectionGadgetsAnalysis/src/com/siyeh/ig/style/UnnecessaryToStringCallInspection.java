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
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.*;

public class UnnecessaryToStringCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean notNullQualifierOnly = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("notNullQualifierOnly", JavaAnalysisBundle.message("inspection.redundant.tostring.option.notnull.qualifier")));
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.tostring.call.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final String text = (String)infos[0];
    return new UnnecessaryToStringCallFix(text);
  }

  private static final class UnnecessaryToStringCallFix extends InspectionGadgetsFix {
    private final @Nullable String replacementText;

    private UnnecessaryToStringCallFix(@Nullable String replacementText) {
      this.replacementText = replacementText;
    }

    @Override
    @NotNull
    public String getName() {
      if (replacementText == null) {
        return InspectionGadgetsBundle.message("inspection.redundant.string.remove.fix.name", "toString");
      }
      return CommonQuickFixBundle.message("fix.replace.with.x", replacementText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiMethodCallExpression call =
        ObjectUtils.tryCast(descriptor.getPsiElement().getParent().getParent(), PsiMethodCallExpression.class);
      if (!isRedundantToString(call)) return;
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
      if (qualifier == null) {
        // Should not happen normally as toString() should always resolve to the innermost class
        // Probably may happen only if SDK is broken (e.g. no java.lang.Object found)
        return;
      }
      new CommentTracker().replaceAndRestoreComments(call, qualifier);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryToStringCallVisitor();
  }

  private class UnnecessaryToStringCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (!isRedundantToString(call)) return;
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
      if (referenceNameElement == null) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
      if (qualifier == null) return;
      if (notNullQualifierOnly && PsiTreeUtil.isAncestor(methodExpression, qualifier, true) &&
          NullabilityUtil.getExpressionNullability(qualifier, true) != Nullability.NOT_NULL) {
        return;
      }
      registerError(referenceNameElement, qualifier.isPhysical() ? null : qualifier.getText());
    }
  }

  @Contract("null -> false")
  private static boolean isRedundantToString(PsiMethodCallExpression call) {
    if (call == null) return false;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String referenceName = methodExpression.getReferenceName();
    if (!"toString".equals(referenceName) || !call.getArgumentList().isEmpty()) return false;
    final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
    if (qualifier == null || qualifier.getType() instanceof PsiArrayType) {
      // do not warn on nonsensical code
      return false;
    }
    if (qualifier instanceof PsiSuperExpression) return false;
    final boolean throwable = TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_THROWABLE);
    return !ExpressionUtils.isConversionToStringNecessary(call, throwable);
  }
}
