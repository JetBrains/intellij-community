// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class ReorderingUtils {
  /**
   * Checks whether it's safe to extract given subexpression from the ancestor expression moving it forward
   * without changing the program semantics.
   *
   * @param ancestor an ancestor expression
   * @param expression a subexpression which is necessary to extract (evaluating it before the ancestor)
   * @return YES if extraction is definitely safe;
   * NO if extraction is definitely not safe (program semantics will surely change)
   * UNSURE if it's not known
   */
  public static ThreeState canExtract(@NotNull PsiExpression ancestor, @NotNull PsiExpression expression) {
    if (expression == ancestor) return ThreeState.YES;
    if (PsiUtil.isConstantExpression(expression)) return ThreeState.YES;
    PsiExpression parent = ObjectUtils.tryCast(expression.getParent(), PsiExpression.class);
    if (parent instanceof PsiExpressionList) {
      PsiExpression gParent = ObjectUtils.tryCast(parent.getParent(), PsiExpression.class);
      if (gParent instanceof PsiCallExpression) {
        PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
        int index = ArrayUtil.indexOf(args, expression);
        ThreeState result = ThreeState.YES;
        for (int i=0; i<index; i++) {
          if (SideEffectChecker.mayHaveSideEffects(args[i])) {
            result = ThreeState.UNSURE;
            break;
          }
        }
        return and(result, () -> canExtract(ancestor, parent));
      }
    }
    if (parent == null) {
      if (PsiTreeUtil.isAncestor(ancestor, expression, true)) {
        return ThreeState.UNSURE;
      }
      throw new IllegalArgumentException("Should be an ancestor");
    }
    if (parent instanceof PsiParenthesizedExpression || parent instanceof PsiInstanceOfExpression ||
        parent instanceof PsiTypeCastExpression) {
      return canExtract(ancestor, parent);
    }
    if (parent instanceof PsiReferenceExpression) {
      if (((PsiReferenceExpression)parent).getQualifierExpression() == expression) {
        return canExtract(ancestor, parent);
      }
    }
    if (parent instanceof PsiConditionalExpression) {
      if (((PsiConditionalExpression)parent).getCondition() == expression) {
        return canExtract(ancestor, parent);
      }
    }
    if (parent instanceof PsiLambdaExpression) {
      return ThreeState.NO;
    }
    if (parent instanceof PsiUnaryExpression) {
      if (PsiUtil.isIncrementDecrementOperation(parent)) return ThreeState.NO;
      return canExtract(ancestor, parent);
    }
    if (parent instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)parent;
      PsiExpression[] operands = polyadic.getOperands();
      int index = ArrayUtil.indexOf(operands, expression);
      if (index == 0) {
        return canExtract(ancestor, parent);
      }
      IElementType tokenType = polyadic.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
        return and(canMoveToStart(polyadic, index), () -> canExtract(ancestor, parent));
      }
      ThreeState result = ThreeState.YES;
      for (int i=0; i<index; i++) {
        if (SideEffectChecker.mayHaveSideEffects(operands[i])) {
          result = ThreeState.UNSURE;
          break;
        }
      }
      return and(result, () -> canExtract(ancestor, parent));
    }
    if (parent instanceof PsiAssignmentExpression) {
      if (expression == ((PsiAssignmentExpression)parent).getLExpression()) return ThreeState.NO;
      return canExtract(ancestor, parent);
    }
    return and(ThreeState.UNSURE, () -> canExtract(ancestor, parent));
  }

  @NotNull
  private static ThreeState and(ThreeState state, Supplier<ThreeState> conjunct) {
    if (state == ThreeState.NO) return ThreeState.NO;
    ThreeState state2 = conjunct.get();
    if (state2 == ThreeState.NO) return ThreeState.NO;
    if (state == ThreeState.UNSURE || state2 == ThreeState.UNSURE) return ThreeState.UNSURE;
    return ThreeState.YES;
  }

  @NotNull
  private static ThreeState canMoveToStart(PsiPolyadicExpression polyadicExpression, int operandIndex) {
    if (operandIndex == 0) return ThreeState.YES;
    IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (tokenType != JavaTokenType.ANDAND && tokenType != JavaTokenType.OROR) return ThreeState.UNSURE;
    PsiExpression[] expressionOperands = polyadicExpression.getOperands();
    if (operandIndex < 0 || operandIndex >= expressionOperands.length) {
      throw new IndexOutOfBoundsException("operandIndex = "+operandIndex);
    }
    PsiExpression[] operands = Arrays.copyOfRange(expressionOperands, 0, operandIndex + 1);
    PsiFile file = polyadicExpression.getContainingFile();
    if (Arrays.stream(operands).allMatch(expression -> isSideEffectFree(file, expression, false))) {
      return ThreeState.YES;
    }
    if (lastOperandImpliesPrevious(polyadicExpression, operands)) {
      return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  private static boolean lastOperandImpliesPrevious(PsiPolyadicExpression expression, PsiExpression[] operands) {
    assert operands.length > 1;
    boolean and = expression.getOperationTokenType() == JavaTokenType.ANDAND;
    PsiFile file = expression.getContainingFile();
    if (Arrays.stream(operands).anyMatch(e -> !PsiTreeUtil.processElements(e, element -> !isErroneous(file, element)))) return false;
    String expressionText = StreamEx.of(operands, 0, operands.length - 1).prepend(operands[operands.length - 1])
      .map(PsiExpression::getText).joining(and ? " && " : " || ");
    PsiPolyadicExpression expressionToAnalyze =
      (PsiPolyadicExpression)JavaPsiFacade.getElementFactory(file.getProject()).createExpressionFromText(expressionText, expression);
    PsiExpression[] newOperands = expressionToAnalyze.getOperands();
    Map<PsiExpression, ThreeState> map = computeOperandValues(expressionToAnalyze, false);
    ThreeState state = ThreeState.fromBoolean(and);
    Set<PsiExpression> redundantOperands = StreamEx.of(newOperands).skip(1).filterBy(map::get, state).toSet();
    if (redundantOperands.isEmpty()) return false;
    if (operands.length == 2) {
      return !Boolean.valueOf(and).equals(DfaUtil.computeValue(operands[0]));
    }
    expressionText = StreamEx.of(operands, 0, operands.length - 1).map(PsiExpression::getText).joining(and ? " && " : " || ");
    expressionToAnalyze =
      (PsiPolyadicExpression)JavaPsiFacade.getElementFactory(file.getProject()).createExpressionFromText(expressionText, expression);
    return !computeOperandValues(expressionToAnalyze, false).values().contains(state);
  }

  private static boolean isErroneous(PsiFile file, PsiElement element) {
    return element instanceof PsiErrorElement ||
           element instanceof PsiLiteralExpression &&
           HighlightUtil.checkLiteralExpressionParsingError((PsiLiteralExpression)element, PsiUtil.getLanguageLevel(file), file) != null;
  }

  public static boolean isSideEffectFree(PsiFile file, PsiExpression expression, boolean allowNpe) {
    // Disallow anything which may throw or produce side effect
    return PsiTreeUtil.processElements(expression, element -> {
      if (element instanceof PsiCallExpression || element instanceof PsiArrayAccessExpression ||
          element instanceof PsiTypeCastExpression || isErroneous(file, element)) {
        return false;
      }
      if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
        return false;
      }
      if (element instanceof PsiReferenceExpression) {
        PsiReferenceExpression ref = (PsiReferenceExpression)element;
        if (!allowNpe) {
          PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ref.getQualifierExpression());
          if (qualifier != null && NullabilityUtil.getExpressionNullability(qualifier) != Nullability.NOT_NULL) {
            if (qualifier instanceof PsiReferenceExpression) {
              PsiElement target = ((PsiReferenceExpression)qualifier).resolve();
              return target instanceof PsiClass || target instanceof PsiPackage;
            }
            return false;
          }
        }
        PsiType type = ref.getType();
        PsiType expectedType = ExpectedTypeUtils.findExpectedType(ref, false);
        if (type != null && !(type instanceof PsiPrimitiveType) && expectedType instanceof PsiPrimitiveType) {
          // Unboxing is possible
          return false;
        }
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
      }
      return true;
    });
  }

  @NotNull
  public static Map<PsiExpression, ThreeState> computeOperandValues(PsiPolyadicExpression expressionToAnalyze, boolean skipNullityUpdate) {
    DataFlowRunner runner = new StandardDataFlowRunner(false, expressionToAnalyze);
    Map<PsiExpression, ThreeState> values = new HashMap<>();
    StandardInstructionVisitor visitor = new StandardInstructionVisitor() {
      @Override
      protected boolean checkNotNullable(DfaMemoryState state,
                                         DfaValue value,
                                         @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
        return skipNullityUpdate || super.checkNotNullable(state, value, problem);
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
