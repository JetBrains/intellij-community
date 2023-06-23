// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class RedundantCompareToJavaTimeInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {

  private static final String JAVA_TIME_LOCAL_DATE = "java.time.LocalDate";
  private static final String JAVA_TIME_LOCAL_DATE_TIME = "java.time.LocalDateTime";
  private static final String JAVA_TIME_LOCAL_TIME = "java.time.LocalTime";
  private static final String JAVA_TIME_OFFSET_DATE_TIME = "java.time.OffsetDateTime";
  private static final String JAVA_TIME_OFFSET_TIME = "java.time.OffsetTime";

  private static final CallMatcher COMPARE_TO_METHODS = CallMatcher.anyOf(
    CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE, "compareTo").parameterTypes("java.time.chrono.ChronoLocalDate"),
    CallMatcher.instanceCall(JAVA_TIME_LOCAL_TIME, "compareTo").parameterTypes(JAVA_TIME_LOCAL_TIME),
    CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "compareTo")
      .parameterTypes("java.time.chrono.ChronoLocalDateTime"),
    CallMatcher.instanceCall(JAVA_TIME_OFFSET_TIME, "compareTo").parameterTypes(JAVA_TIME_OFFSET_TIME),
    CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "compareTo")
      .parameterTypes(JAVA_TIME_OFFSET_DATE_TIME)
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!COMPARE_TO_METHODS.test(call)) return;
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;

        final PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
        if (qualifierExpression == null) {
          return;
        }
        PsiType[] types = call.getArgumentList().getExpressionTypes();
        if (types.length != 1) {
          return;
        }
        final PsiType argumentType = types[0];
        if (argumentType == null || !argumentType.equals(qualifierExpression.getType())) {
          return;
        }

        PsiBinaryExpression binOp = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiBinaryExpression.class);
        if (binOp == null) return;
        RelationType relationType = DfaPsiUtil.getRelationByToken(binOp.getOperationTokenType());
        if (relationType == RelationType.IS || relationType == RelationType.IS_NOT) {
          return;
        }
        if (relationType == null) return;
        if (ExpressionUtils.isZero(binOp.getLOperand())) {
          relationType = relationType.getFlipped();
          if (relationType == null) return;
        }
        else if (!ExpressionUtils.isZero(binOp.getROperand())) {
          return;
        }
        holder.registerProblem(nameElement,
                               InspectionGadgetsBundle.message("inspection.simplifiable.compare.java.time.problem.descriptor"),
                               new InlineCompareToTimeCallFix(relationType, argumentType.getCanonicalText()));
      }
    };
  }

  private static class InlineCompareToTimeCallFix extends PsiUpdateModCommandQuickFix {
    private @NotNull final RelationType myRelationType;
    private @NotNull final String myArgumentType;

    InlineCompareToTimeCallFix(@NotNull RelationType relationType, @NotNull @NlsSafe String argumentType) {
      myRelationType = relationType;
      myArgumentType = argumentType;
    }

    @NotNull
    @Override
    public String getName() {
      String method = getMethodName();
      return CommonQuickFixBundle.message("fix.replace.with.x.call", method);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.simplifiable.compare.java.time.family.name");
    }

    @NotNull
    private String getMethodName() {
      return switch (myRelationType) {
        case EQ, NE -> myArgumentType.equals(JAVA_TIME_LOCAL_TIME) ? "equals" : "isEqual";
        case GT, LE -> "isAfter";
        case LT, GE -> "isBefore";
        default -> throw new UnsupportedOperationException(myRelationType.toString());
      };
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression first = call.getMethodExpression().getQualifierExpression();
      if (first == null) {
        return;
      }
      PsiExpression second = call.getArgumentList().getExpressions()[0];
      CommentTracker ct = new CommentTracker();
      String text = ct.text(first) + "." + getMethodName() + "(" + ct.text(second) + ")";

      if (myRelationType == RelationType.NE || myRelationType == RelationType.LE || myRelationType == RelationType.GE) {
        text = "!" + text;
      }
      PsiBinaryExpression parent = PsiTreeUtil.getParentOfType(call, PsiBinaryExpression.class);
      if (parent == null) return;
      ct.replaceAndRestoreComments(parent, text);
    }
  }
}
