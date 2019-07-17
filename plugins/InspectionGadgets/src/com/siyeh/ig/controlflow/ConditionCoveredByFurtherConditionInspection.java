// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ThreeState;
import com.siyeh.ig.fixes.RemoveRedundantPolyadicOperandFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.ReorderingUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ConditionCoveredByFurtherConditionInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(ConditionCoveredByFurtherConditionInspection.class);

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
      return ReorderingUtils.isSideEffectFree(expression, true);
    }

    private void process(PsiPolyadicExpression context, List<PsiExpression> operands, boolean and) {
      int[] indices = getRedundantOperandIndices(context, operands, and);
      for (int index : indices) {
        List<PsiExpression> dependencies = operands.subList(index + 1, operands.size());
        PsiExpression operand = operands.get(index);
        dependencies = minimizeDependencies(context, operand, and, dependencies);
        if (dependencies.isEmpty()) continue;
        String operandText = PsiExpressionTrimRenderer.render(operand);
        String description = "Condition '" + operandText + "' covered by subsequent " +
                             (dependencies.size() == 1
                              ? "condition '" +
                                PsiExpressionTrimRenderer.render(
                                  Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(dependencies.get(0)))) +
                                "'"
                              : "conditions");
        myHolder.registerProblem(operand, description, new RemoveRedundantPolyadicOperandFix(operandText));
      }
    }

    private static List<PsiExpression> minimizeDependencies(PsiPolyadicExpression context,
                                                            PsiExpression operand,
                                                            boolean and, List<PsiExpression> dependencies) {
      if (dependencies.isEmpty() || getRedundantOperandIndices(context, Collections.singletonList(operand), and).length != 0) {
        // Does not actually depend on dependencies
        return Collections.emptyList();
      }
      if (dependencies.size() == 1) return dependencies;
      for (PsiExpression dependency : dependencies) {
        if (ArrayUtil.indexOf(getRedundantOperandIndices(context, Arrays.asList(operand, dependency), and), 0) != -1) {
          return Collections.singletonList(dependency);
        }
      }
      return dependencies;
    }

    private static int[] getRedundantOperandIndices(PsiPolyadicExpression context, List<PsiExpression> operands, boolean and) {
      assert !operands.isEmpty();
      if (operands.size() == 1) {
        Object value = DfaUtil.computeValue(operands.get(0));
        return Boolean.valueOf(and).equals(value) ? new int[]{0} : ArrayUtilRt.EMPTY_INT_ARRAY;
      }
      String text = StreamEx.ofReversed(operands)
        .map(expression -> ParenthesesUtils.getText(expression, PsiPrecedenceUtil.AND_PRECEDENCE)).joining(and ? " && " : " || ");
      PsiExpression expression = JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(text, context);
      if (!(expression instanceof PsiPolyadicExpression)) {
        LOG.error("Unexpected expression type: " + expression.getClass().getName(), new Attachment("reversed.txt", text));
        return ArrayUtilRt.EMPTY_INT_ARRAY;
      }
      PsiPolyadicExpression expressionToAnalyze = (PsiPolyadicExpression)expression;
      List<PsiExpression> reversedOperands = Arrays.asList(expressionToAnalyze.getOperands());
      Map<PsiExpression, ThreeState> values = computeOperandValues(expressionToAnalyze);
      return StreamEx.ofKeys(values, ThreeState.fromBoolean(and)::equals)
        .mapToInt(operand -> IntStreamEx.ofIndices(reversedOperands, op -> PsiTreeUtil.isAncestor(op, operand, false))
          .findFirst().orElse(0))
        .map(index -> operands.size() - 1 - index)
        .toArray();
    }
  }

  @NotNull
  private static Map<PsiExpression, ThreeState> computeOperandValues(PsiPolyadicExpression expressionToAnalyze) {
    DataFlowRunner runner = new StandardDataFlowRunner(false, expressionToAnalyze);
    Map<PsiExpression, ThreeState> values = new HashMap<>();
    StandardInstructionVisitor visitor = new StandardInstructionVisitor() {
      @Override
      protected boolean checkNotNullable(DfaMemoryState state,
                                         DfaValue value,
                                         @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
        if (value instanceof DfaVariableValue) {
          state.forceVariableFact((DfaVariableValue)value, DfaFactType.NULLABILITY, DfaNullability.NULLABLE);
        }
        return true;
      }

      @Override
      protected void beforeExpressionPush(@NotNull DfaValue value,
                                          @NotNull PsiExpression expression,
                                          @Nullable TextRange range,
                                          @NotNull DfaMemoryState state) {
        super.beforeExpressionPush(value, expression, range, state);
        if (PsiUtil.skipParenthesizedExprUp(expression.getParent()) != expressionToAnalyze) return;
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
    return result == RunnerResult.OK ? values : Collections.emptyMap();
  }
}
