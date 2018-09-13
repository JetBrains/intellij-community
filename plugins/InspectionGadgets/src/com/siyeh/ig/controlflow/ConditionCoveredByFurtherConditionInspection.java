// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import com.siyeh.ig.fixes.RemoveRedundantPolyadicOperandFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ConditionCoveredByFurtherConditionInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new ConditionCoveredByFurtherConditionVisitor(holder);
  }

  private static class ConditionCoveredByFurtherConditionVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    ConditionCoveredByFurtherConditionVisitor(ProblemsHolder holder) {myHolder = holder;}

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      IElementType type = expression.getOperationTokenType();
      if (type.equals(JavaTokenType.ANDAND)) {
        processConditionChain(expression, true);
      }
      else if (type.equals(JavaTokenType.OROR)) {
        processConditionChain(expression, false);
      }
    }

    private void processConditionChain(PsiPolyadicExpression expression, boolean and) {
      StreamEx.of(expression.getOperands())
        .map(operand -> isAllowed(operand) ? operand : null)
        .groupRuns((o1, o2) -> o1 != null && o2 != null)
        .remove(list -> list.size() == 1)
        .forEach(operands -> process(expression, operands, and));
    }

    private static boolean isAllowed(PsiExpression expression) {
      // Disallow anything which may throw or produce side effect
      return PsiTreeUtil.processElements(expression, element -> {
        if (element instanceof PsiCallExpression || element instanceof PsiArrayAccessExpression ||
            element instanceof PsiTypeCastExpression) {
          return false;
        }
        if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
          return false;
        }
        if (element instanceof PsiReferenceExpression) {
          PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
          if (qualifier != null && NullabilityUtil.getExpressionNullability(qualifier) != Nullability.NOT_NULL) {
            return false;
          }
          PsiType type = ((PsiReferenceExpression)element).getType();
          return !TypeConversionUtil.isPrimitiveWrapper(type);
        }
        if (element instanceof PsiPolyadicExpression) {
          PsiPolyadicExpression expr = (PsiPolyadicExpression)element;
          IElementType type = expr.getOperationTokenType();
          if (type.equals(JavaTokenType.DIV) || type.equals(JavaTokenType.PERC)) {
            PsiExpression[] operands = expr.getOperands();
            if (operands.length != 2) return false;
            Object divisor = ExpressionUtils.computeConstantExpression(operands[1]);
            if ((!(divisor instanceof Integer) && !(divisor instanceof Long)) || ((Number)divisor).longValue() == 0) return false;
          }
          return true;
        }
        return true;
      });
    }

    private void process(PsiPolyadicExpression context, List<PsiExpression> operands, boolean and) {
      int[] indices = getRedundantOperandIndices(context, operands, and);
      for (int index : indices) {
        List<PsiExpression> dependencies = operands.subList(index + 1, operands.size());
        PsiExpression operand = operands.get(index);
        dependencies = minimizeDependencies(context, operand, and, dependencies);
        if (dependencies.isEmpty()) return;
        String operandText = PsiExpressionTrimRenderer.render(operand);
        String description = "Condition '" + operandText + "' covered by subsequent " +
                             (dependencies.size() == 1
                              ? "condition '" +
                                PsiExpressionTrimRenderer.render(PsiUtil.skipParenthesizedExprDown(dependencies.get(0))) +
                                "'"
                              : "conditions");
        myHolder.registerProblem(operand, description, new RemoveRedundantPolyadicOperandFix(operandText));
      }
    }

    private static List<PsiExpression> minimizeDependencies(PsiPolyadicExpression context,
                                                            PsiExpression operand,
                                                            boolean and, List<PsiExpression> dependencies) {
      // This implementation tries to remove some dependencies from the start and the end,
      // so we have at most linear number of getRedundantOperandIndices calls
      int start = 0;
      int end = dependencies.size();
      for (; start < end; end--) {
        List<PsiExpression> minimized = StreamEx.of(dependencies.subList(start, end - 1)).prepend(operand).toList();
        if (ArrayUtil.indexOf(getRedundantOperandIndices(context, minimized, and), 0) == -1) {
          break;
        }
      }
      for (; start < end; start++) {
        List<PsiExpression> minimized = StreamEx.of(dependencies.subList(start + 1, end)).prepend(operand).toList();
        if (ArrayUtil.indexOf(getRedundantOperandIndices(context, minimized, and), 0) == -1) {
          break;
        }
      }
      return dependencies.subList(start, end);
    }

    private static int[] getRedundantOperandIndices(PsiPolyadicExpression context, List<PsiExpression> operands, boolean and) {
      assert !operands.isEmpty();
      List<PsiExpression> reversedOperands;
      Predicate<PsiExpression> isOperand;
      PsiExpression expressionToAnalyze;
      if (operands.size() == 1) {
        expressionToAnalyze = operands.get(0);
        reversedOperands = operands;
        isOperand = Predicate.isEqual(expressionToAnalyze);
      }
      else {
        expressionToAnalyze = JavaPsiFacade.getElementFactory(context.getProject())
          .createExpressionFromText(StreamEx.ofReversed(operands).map(PsiElement::getText).joining(and ? " && " : " || "), context);
        reversedOperands = Arrays.asList(((PsiPolyadicExpression)expressionToAnalyze).getOperands());
        isOperand = expression -> PsiUtil.skipParenthesizedExprUp(expression.getParent()) == expressionToAnalyze;
      }
      DataFlowRunner runner = new StandardDataFlowRunner(false, context);
      Map<PsiExpression, ThreeState> values = new HashMap<>();
      StandardInstructionVisitor visitor = new StandardInstructionVisitor() {
        @Override
        protected void beforeExpressionPush(@NotNull DfaValue value,
                                            @NotNull PsiExpression expression,
                                            @Nullable TextRange range,
                                            @NotNull DfaMemoryState state) {
          super.beforeExpressionPush(value, expression, range, state);
          if (!isOperand.test(expression)) return;
          ThreeState old = values.get(expression);
          if (old == ThreeState.UNSURE) return;
          ThreeState result = ThreeState.UNSURE;
          if (value instanceof DfaConstValue) {
            Object bool = ((DfaConstValue)value).getValue();
            if (bool instanceof Boolean) {
              result = ThreeState.fromBoolean((Boolean)bool);
            }
          }
          values.put(expression, old == null || old == result ? result : ThreeState.UNSURE);
        }
      };
      RunnerResult result = runner.analyzeMethod(expressionToAnalyze, visitor);
      if (result != RunnerResult.OK) {
        return new int[0];
      }
      return StreamEx.ofKeys(values, ThreeState.fromBoolean(and)::equals)
        .mapToInt(operand -> IntStreamEx.ofIndices(reversedOperands, op -> PsiTreeUtil.isAncestor(op, operand, false))
          .findFirst().orElse(0))
        .map(index -> operands.size() - 1 - index)
        .toArray();
    }
  }
}
