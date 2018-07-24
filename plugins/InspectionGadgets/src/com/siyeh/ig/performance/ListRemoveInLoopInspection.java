// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class ListRemoveInLoopInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher LIST_REMOVE = instanceCall(CommonClassNames.JAVA_UTIL_LIST, "remove").parameterTypes("int");
  private static final CallMatcher LIST_SIZE = instanceCall(CommonClassNames.JAVA_UTIL_LIST, "size").parameterCount(0);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!LIST_REMOVE.test(call)) return;
        PsiExpression listExpression = call.getMethodExpression().getQualifierExpression();
        if (listExpression == null) return;
        PsiElement parent = call.getParent();
        if (!(parent instanceof PsiExpressionStatement)) return;
        PsiLoopStatement loop = PsiTreeUtil.getParentOfType(parent, PsiLoopStatement.class, true, PsiMember.class);
        if (loop == null) return;
        if (ControlFlowUtils.stripBraces(loop.getBody()) != parent) return;
        PsiExpression arg = call.getArgumentList().getExpressions()[0];

        ProblemHighlightType type;
        if (isRemoveInCountingLoop(loop, arg)) {
          type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        } else if (isRemoveInWhileLoop(loop, listExpression, arg)) {
          // while-loop scenario is not so bad (always the last element is removed which is usually fast),
          // the replacement is often longer and harder to read
          // and such loop is often used to remove at most one element which is faster than using subList().clear()
          if (!isOnTheFly) return;
          type = ProblemHighlightType.INFORMATION;
        } else {
          return;
        }
        holder.registerProblem(loop.getFirstChild(), InspectionGadgetsBundle.message("inspection.list.remove.in.loop.message"),
                               type, new ListRemoveInLoopFix());
      }

      /**
       * Looks for pattern like
       * <pre>{@code
       * while (list.size() > smth) {
       *   list.remove(list.size() - 1);
       * }}</pre>
       */
      private boolean isRemoveInWhileLoop(PsiLoopStatement loop, PsiExpression listExpression, PsiExpression arg) {
        if (!(loop instanceof PsiWhileStatement)) return false;
        PsiBinaryExpression condition =
          tryCast(PsiUtil.skipParenthesizedExprDown(((PsiWhileStatement)loop).getCondition()), PsiBinaryExpression.class);
        if (condition == null) return false;
        DfaRelationValue.RelationType relationType = DfaRelationValue.RelationType.fromElementType(condition.getOperationTokenType());
        if (relationType == null) return false;
        PsiExpression sizeExpression;
        switch (relationType) {
          case GE:
          case GT:
            sizeExpression = condition.getLOperand();
            break;
          case LE:
          case LT:
            sizeExpression = condition.getROperand();
            break;
          default:
            return false;
        }
        PsiMethodCallExpression sizeCall = tryCast(PsiUtil.skipParenthesizedExprDown(sizeExpression), PsiMethodCallExpression.class);
        if (!LIST_SIZE.test(sizeCall)) return false;
        PsiExpression sizeQualifier = sizeCall.getMethodExpression().getQualifierExpression();
        if (sizeQualifier == null || !PsiEquivalenceUtil.areElementsEquivalent(sizeQualifier, listExpression)) return false;
        PsiBinaryExpression diff = tryCast(PsiUtil.skipParenthesizedExprDown(arg), PsiBinaryExpression.class);
        if (diff == null || !diff.getOperationTokenType().equals(JavaTokenType.MINUS)) return false;
        if (!ExpressionUtils.isLiteral(diff.getROperand(), 1)) return false;
        return PsiEquivalenceUtil.areElementsEquivalent(sizeCall, diff.getLOperand());
      }

      /**
       * Looks for pattern like
       * <pre>{@code
       * for (int i=from; i<to; i++) {
       *   list.remove(from);
       * }}</pre> or
       * <pre>{@code
       * for (int i=to-1; i>=from; i--) {
       *   list.remove(i);
       * }}</pre>
       */
      private boolean isRemoveInCountingLoop(PsiLoopStatement forLoop, PsiExpression arg) {
        if (!(forLoop instanceof PsiForStatement)) return false;
        CountingLoop loop = CountingLoop.from((PsiForStatement)forLoop);
        if (loop == null) return false;
        if (loop.isDescending()) {
          return ExpressionUtils.isReferenceTo(arg, loop.getCounter());
        }
        return PsiEquivalenceUtil.areElementsEquivalent(arg, loop.getInitializer());
      }
    };
  }

  private static class ListRemoveInLoopFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.list.remove.in.loop.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLoopStatement loopStatement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiLoopStatement.class);
      if (loopStatement == null) return;
      PsiExpressionStatement statement = tryCast(ControlFlowUtils.stripBraces(loopStatement.getBody()), PsiExpressionStatement.class);
      if (statement == null) return;
      PsiMethodCallExpression call = tryCast(statement.getExpression(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression listExpression = call.getMethodExpression().getQualifierExpression();
      if (listExpression == null) return;
      CommentTracker ct = new CommentTracker();

      Pair<String, String> startEnd = getStartEnd(loopStatement, ct);
      if (startEnd == null) return;

      String start = startEnd.getFirst();
      String end = startEnd.getSecond();
      String replacement = ct.text(listExpression) + ".subList(" + start + "," + end + ").clear();";
      replacement = "if(" + end + ">" + start + "){" + replacement + "}";
      PsiIfStatement ifStatement = (PsiIfStatement)ct.replaceAndRestoreComments(loopStatement, replacement);
      ct = new CommentTracker();
      PsiExpression condition = ifStatement.getCondition();
      String simplified = JavaPsiMathUtil.simplifyComparison(condition, ct);
      if (simplified != null) {
        condition = (PsiExpression)ct.replaceAndRestoreComments(condition, simplified);
        ct = new CommentTracker();
      }
      if (Boolean.TRUE.equals(DfaUtil.evaluateCondition(condition))) {
        PsiStatement nakedSubListClear = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
        assert nakedSubListClear != null;
        ct.replaceAndRestoreComments(ifStatement, ct.markUnchanged(nakedSubListClear));
      }
    }

    public Couple<String> getStartEnd(PsiLoopStatement loopStatement, CommentTracker ct) {
      if (loopStatement instanceof PsiForStatement) {
        CountingLoop loop = CountingLoop.from((PsiForStatement)loopStatement);
        if (loop == null) return null;

        String start, end;
        if (loop.isDescending()) {
          start = loop.isIncluding() ? ct.text(loop.getBound()) : JavaPsiMathUtil.add(loop.getBound(), 1, ct);
          end = JavaPsiMathUtil.add(loop.getInitializer(), 1, ct);
        }
        else {
          start = ct.text(loop.getInitializer());
          end = loop.isIncluding() ? JavaPsiMathUtil.add(loop.getBound(), 1, ct) : ct.text(loop.getBound());
        }
        return Couple.of(start, end);
      }
      if (loopStatement instanceof PsiWhileStatement) {
        PsiBinaryExpression condition =
          tryCast(PsiUtil.skipParenthesizedExprDown(((PsiWhileStatement)loopStatement).getCondition()), PsiBinaryExpression.class);
        if (condition == null) return null;
        DfaRelationValue.RelationType relationType = DfaRelationValue.RelationType.fromElementType(condition.getOperationTokenType());
        if (relationType == null) return null;
        PsiExpression left = condition.getLOperand();
        PsiExpression right = condition.getROperand();
        if (right == null) return null;
        String start, end;
        switch (relationType) {
          case GE:
            start = JavaPsiMathUtil.add(right, -1, ct);
            end = ct.text(left);
            break;
          case GT:
            start = ct.text(right);
            end = ct.text(left);
            break;
          case LE:
            start = JavaPsiMathUtil.add(left, -1, ct);
            end = ct.text(right);
            break;
          case LT:
            start = ct.text(left);
            end = ct.text(right);
            break;
          default:
            return null;
        }
        return Couple.of(start, end);
      }
      return null;
    }
  }
}
