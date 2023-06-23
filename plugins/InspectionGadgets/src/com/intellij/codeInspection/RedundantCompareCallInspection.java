// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RedundantCompareCallInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private static final CallMatcher COMPARE_METHODS = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "compare").parameterTypes("int", "int"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "compare").parameterTypes("long", "long"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_SHORT, "compare").parameterTypes("short", "short"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_CHARACTER, "compare").parameterTypes("char", "char"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_BYTE, "compare").parameterTypes("byte", "byte")
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!COMPARE_METHODS.test(call)) return;
        PsiBinaryExpression binOp = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiBinaryExpression.class);
        if (binOp == null) return;
        RelationType type = DfaPsiUtil.getRelationByToken(binOp.getOperationTokenType());
        if (type == null) return;
        if (ExpressionUtils.isZero(binOp.getLOperand())) {
          type = type.getFlipped();
          if (type == null) return;
        } else if (!ExpressionUtils.isZero(binOp.getROperand())) {
          return;
        }
        holder.registerProblem(call, InspectionGadgetsBundle.message("redundant.call.problem.descriptor"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new TextRange(0, call.getArgumentList().getStartOffsetInParent()),
                               new InlineCompareCallFix(type));
      }
    };
  }

  private static class InlineCompareCallFix extends PsiUpdateModCommandQuickFix {
    private @NotNull final RelationType myRelationType;

    InlineCompareCallFix(@NotNull RelationType relationType) {
      myRelationType = relationType;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.compare.call.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if(args.length != 2) return;
      String maybeCast = "";
      if (myRelationType == RelationType.EQ || myRelationType == RelationType.NE) {
        PsiType leftType = args[0].getType();
        PsiType rightType = args[1].getType();
        if (leftType instanceof PsiClassType && rightType instanceof PsiClassType) {
          PsiPrimitiveType type = PsiPrimitiveType.getOptionallyUnboxedType(leftType);
          if (type != null) {
            maybeCast = "(" + type.getCanonicalText() + ")";
          }
        }
      }
      PsiBinaryExpression parent = PsiTreeUtil.getParentOfType(call, PsiBinaryExpression.class);
      if (parent == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(parent, maybeCast + ct.text(args[0], ParenthesesUtils.EQUALITY_PRECEDENCE) +
                                           myRelationType +
                                           ct.text(args[1], ParenthesesUtils.EQUALITY_PRECEDENCE));
    }
  }
}
