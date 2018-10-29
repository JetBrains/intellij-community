// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
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
    if (Arrays.stream(operands).allMatch(expression -> isSideEffectFree(expression, false))) {
      return ThreeState.YES;
    }
    if (lastOperandImpliesPrevious(polyadicExpression, operands)) {
      return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  private enum ExceptionKind {
    NullDereference {
      @Override
      boolean isNecessaryCheck(PsiExpression operand, PsiExpression condition, boolean negated) {
        if (condition instanceof PsiBinaryExpression) {
          IElementType tokenType = ((PsiBinaryExpression)condition).getOperationTokenType();
          if (tokenType.equals(negated ? JavaTokenType.EQEQ : JavaTokenType.NE)) {
            PsiExpression left = ((PsiBinaryExpression)condition).getLOperand();
            PsiExpression right = ((PsiBinaryExpression)condition).getROperand();
            if (ExpressionUtils.isNullLiteral(left)) {
              return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(right, operand);
            }
            if (ExpressionUtils.isNullLiteral(right)) {
              return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left, operand);
            }
          }
        }
        return false;
      }

      @Override
      PsiExpression extractOperand(PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression ||
            expression instanceof PsiParenthesizedExpression ||
            expression instanceof PsiTypeCastExpression ||
            expression instanceof PsiConditionalExpression ||
            NullabilityUtil.getExpressionNullability(expression) == Nullability.NOT_NULL) {
          return null;
        }
        PsiExpression realExpression = expression;
        while (realExpression.getParent() instanceof PsiParenthesizedExpression ||
               realExpression.getParent() instanceof PsiTypeCastExpression ||
               (realExpression.getParent() instanceof PsiConditionalExpression &&
                realExpression != ((PsiConditionalExpression)realExpression.getParent()).getCondition())) {
          realExpression = (PsiExpression)realExpression.getParent();
        }
        PsiElement parent = realExpression.getParent();
        if (parent instanceof PsiReferenceExpression || parent instanceof PsiArrayAccessExpression) {
          return expression;
        }
        if (parent instanceof PsiPolyadicExpression) {
          IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
          if (tokenType.equals(JavaTokenType.PLUS)) {
            if (TypeUtils.isJavaLangString(((PsiPolyadicExpression)parent).getType())) {
              return null;
            }
          }
          return expression;
        }
        PsiParameter parameter = MethodCallUtils.getParameterForArgument(realExpression);
        if (parameter != null && NullableNotNullManager.isNotNull(parameter)) {
          return expression;
        }
        return null;
      }
    },
    ClassCast {
      @Override
      boolean isNecessaryCheck(PsiExpression operand, PsiExpression condition, boolean negated) {
        if (negated) return false;
        if (condition instanceof PsiInstanceOfExpression) {
          PsiExpression op = ((PsiInstanceOfExpression)condition).getOperand();
          return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(op, operand);
        }
        return false;
      }

      @Override
      PsiExpression extractOperand(PsiExpression expression) {
        if (expression instanceof PsiTypeCastExpression) {
          return ((PsiTypeCastExpression)expression).getOperand();
        }
        return null;
      }
    },
    ArrayIndex {
      @Override
      boolean isNecessaryCheck(PsiExpression operand, PsiExpression condition, boolean negated) {
        if (condition instanceof PsiBinaryExpression) {
          IElementType token = ((PsiBinaryExpression)condition).getOperationTokenType();
          if (ComparisonUtils.isComparisonOperation(token) && !token.equals(JavaTokenType.EQEQ) && !token.equals(JavaTokenType.NE)) {
            PsiExpression left = ((PsiBinaryExpression)condition).getLOperand();
            PsiExpression right = ((PsiBinaryExpression)condition).getROperand();
            return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left, operand) ||
                   EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(right, operand);
          }
        }
        return false;
      }

      @Override
      PsiExpression extractOperand(PsiExpression expression) {
        if (expression instanceof PsiArrayAccessExpression) {
          return ((PsiArrayAccessExpression)expression).getIndexExpression();
        }
        return null;
      }
    };

    abstract boolean isNecessaryCheck(PsiExpression operand, PsiExpression condition, boolean negated);

    abstract PsiExpression extractOperand(PsiExpression expression);
  }

  private static class Problem {
    private final ExceptionKind myKind;
    private final PsiExpression myOperand;

    private Problem(ExceptionKind kind, PsiExpression operand) {
      myKind = kind;
      myOperand = operand;
    }

    @NotNull
    static List<Problem> fromExpression(PsiExpression expression) {
      List<Problem> problems = new ArrayList<>();
      for (ExceptionKind kind : ExceptionKind.values()) {
        PsiExpression operand = kind.extractOperand(expression);
        if (operand != null) {
          problems.add(new Problem(kind, operand));
        }
      }
      return problems;
    }
    
    boolean isNecessaryCheck(PsiExpression condition, boolean negated) {
      return myKind.isNecessaryCheck(myOperand, condition, negated);
    }
  }

  private static boolean areConditionsNecessaryFor(PsiExpression[] conditions, PsiExpression operand, boolean negated) {
    List<Problem> problems = SyntaxTraverser.psiTraverser(operand)
      .traverse().filter(PsiExpression.class).flatMap(Problem::fromExpression).filter(Objects::nonNull)
      .toList();
    if (problems.isEmpty()) return false;
    for (PsiExpression condition : conditions) {
      if (isConditionNecessary(condition, problems, negated)) return true;
    }
    return false;
  }

  private static boolean isConditionNecessary(PsiExpression condition, List<Problem> problems, boolean negated) {
    condition = PsiUtil.skipParenthesizedExprDown(condition);
    if (condition == null) return false;
    if (BoolUtils.isNegation(condition)) {
      return isConditionNecessary(BoolUtils.getNegated(condition), problems, !negated);
    }
    if (condition instanceof PsiPolyadicExpression) {
      IElementType type = ((PsiPolyadicExpression)condition).getOperationTokenType();
      if((type.equals(JavaTokenType.ANDAND) && !negated) || (type.equals(JavaTokenType.OROR) && negated)) {
        for (PsiExpression operand : ((PsiPolyadicExpression)condition).getOperands()) {
          if (isConditionNecessary(operand, problems, negated)) {
            return true;
          }
        }
        return false;
      }
      if((type.equals(JavaTokenType.ANDAND) && negated) || (type.equals(JavaTokenType.OROR) && !negated)) {
        for (PsiExpression operand : ((PsiPolyadicExpression)condition).getOperands()) {
          if (!isConditionNecessary(operand, problems, negated)) {
            return false;
          }
        }
        return true;
      }
    }
    for (Problem problem : problems) {
      if (problem.isNecessaryCheck(condition, negated)) {
        return true;
      }
    }
    return false;
  }

  private static boolean lastOperandImpliesPrevious(PsiPolyadicExpression expression, PsiExpression[] operands) {
    assert operands.length > 1;
    boolean and = expression.getOperationTokenType() == JavaTokenType.ANDAND;
    if (Arrays.stream(operands).anyMatch(e -> !PsiTreeUtil.processElements(e, element -> !isErroneous(element)))) return false;
    PsiExpression lastOperand = operands[operands.length - 1];
    if (areConditionsNecessaryFor(Arrays.copyOf(operands, operands.length - 1), lastOperand, !and)) return true;
    String expressionText = StreamEx.of(operands, 0, operands.length - 1).prepend(lastOperand)
      .map(PsiExpression::getText).joining(and ? " && " : " || ");
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    PsiPolyadicExpression expressionToAnalyze = (PsiPolyadicExpression)factory.createExpressionFromText(expressionText, expression);
    PsiExpression[] newOperands = expressionToAnalyze.getOperands();
    Map<PsiExpression, ThreeState> map = computeOperandValues(expressionToAnalyze, false);
    ThreeState state = ThreeState.fromBoolean(and);
    Set<PsiExpression> redundantOperands = StreamEx.of(newOperands).skip(1).filterBy(map::get, state).toSet();
    if (redundantOperands.isEmpty()) return false;
    if (operands.length == 2) {
      return !Boolean.valueOf(and).equals(DfaUtil.computeValue(operands[0]));
    }
    expressionText = StreamEx.of(operands, 0, operands.length - 1).map(PsiExpression::getText).joining(and ? " && " : " || ");
    expressionToAnalyze = (PsiPolyadicExpression)factory.createExpressionFromText(expressionText, expression);
    return !computeOperandValues(expressionToAnalyze, false).values().contains(state);
  }

  private static boolean isErroneous(PsiElement element) {
    return element instanceof PsiErrorElement ||
           element instanceof PsiLiteralExpression &&
           PsiLiteralUtil.isUnsafeLiteral((PsiLiteralExpression)element);
  }

  public static boolean isSideEffectFree(PsiExpression expression, boolean allowNpe) {
    // Disallow anything which may throw or produce side effect
    return PsiTreeUtil.processElements(expression, element -> {
      if (element instanceof PsiCallExpression || element instanceof PsiArrayAccessExpression ||
          element instanceof PsiTypeCastExpression || isErroneous(element)) {
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
