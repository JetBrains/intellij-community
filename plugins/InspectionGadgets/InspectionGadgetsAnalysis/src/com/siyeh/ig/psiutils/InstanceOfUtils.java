/*
 * Copyright 2007-2015 Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.ContractValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public final class InstanceOfUtils {

  private InstanceOfUtils() {}

  public static PsiInstanceOfExpression getConflictingInstanceof(PsiType castType, PsiReferenceExpression operand, PsiElement context) {
    if (!(castType instanceof PsiClassType)) {
      return null;
    }
    final PsiClassType classType = (PsiClassType)castType;
    if (((PsiClassType)castType).resolve() instanceof PsiTypeParameter) {
      return null;
    }
    final PsiClassType rawType = classType.rawType();
    final InstanceofChecker checker = new InstanceofChecker(operand, rawType, false);
    PsiStatement sibling = PsiTreeUtil.getParentOfType(context, PsiStatement.class);
    sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiStatement.class);
    while (sibling != null) {
      if (sibling instanceof PsiIfStatement) {
        final PsiIfStatement ifStatement = (PsiIfStatement)sibling;
        final PsiExpression condition = ifStatement.getCondition();
        if (condition != null) {
          if (!ControlFlowUtils.statementMayCompleteNormally(ifStatement.getThenBranch())) {
            checker.negate = true;
            checker.checkExpression(condition);
            if (checker.hasAgreeingInstanceof()) {
              return null;
            }
          }
          else if (!ControlFlowUtils.statementMayCompleteNormally(ifStatement.getElseBranch())) {
            checker.negate = false;
            checker.checkExpression(condition);
            if (checker.hasAgreeingInstanceof()) {
              return null;
            }
          }
        }
      }
      else if (sibling instanceof PsiAssertStatement) {
        final PsiAssertStatement assertStatement = (PsiAssertStatement)sibling;
        final PsiExpression condition = assertStatement.getAssertCondition();
        checker.negate = false;
        checker.checkExpression(condition);
        if (checker.hasAgreeingInstanceof()) {
          return null;
        }
      }
      else if (sibling instanceof PsiExpressionStatement) {
        PsiMethodCallExpression call =
          ObjectUtils.tryCast(((PsiExpressionStatement)sibling).getExpression(), PsiMethodCallExpression.class);
        if (isInstanceOfAssertionCall(checker, call)) return null;
      }
      sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiStatement.class);
    }
    checker.negate = false;
    PsiElement parent = findInterestingParent(context);
    while (parent != null) {
      IElementType tokenType = parent instanceof PsiPolyadicExpression ? ((PsiPolyadicExpression)parent).getOperationTokenType() : null;
      if (JavaTokenType.ANDAND.equals(tokenType) || JavaTokenType.OROR.equals(tokenType)) {
        checker.negate = tokenType.equals(JavaTokenType.OROR);
        for (PsiExpression expression : ((PsiPolyadicExpression)parent).getOperands()) {
          if (PsiTreeUtil.isAncestor(expression, context, false)) break;
          expression.accept(checker);
          if (checker.hasAgreeingInstanceof()) {
            return null;
          }
        }
        checker.negate = false;
      }
      else {
        parent.accept(checker);
        if (checker.hasAgreeingInstanceof()) {
          return null;
        }
      }
      parent = findInterestingParent(parent);
    }
    if (checker.hasAgreeingInstanceof()) {
      return null;
    }
    return checker.getConflictingInstanceof();
  }

  @Nullable
  private static PsiElement findInterestingParent(PsiElement context) {
    while (true) {
      PsiElement parent = context.getParent();
      if (parent == null) return null;
      if (parent instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) return parent;
      }
      if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getCondition() != context) {
        return parent;
      }
      if (parent instanceof PsiConditionalLoopStatement && ((PsiConditionalLoopStatement)parent).getCondition() != context) {
        return parent;
      }
      if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != context) {
        return parent;
      }
      context = parent;
    }
  }

  private static boolean isInstanceOfAssertionCall(InstanceofChecker checker, PsiMethodCallExpression call) {
    if (call == null) return false;
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
    if (contracts.isEmpty()) return false;
    MethodContract contract = contracts.get(0);
    if (!contract.getReturnValue().isFail()) return false;
    ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
    if (condition == null) return false;
    checker.negate = true;
    OptionalInt argNum = condition.getArgumentComparedTo(ContractValue.booleanValue(true), true);
    if (!argNum.isPresent()) {
      checker.negate = false;
      argNum = condition.getArgumentComparedTo(ContractValue.booleanValue(false), true);
    }
    if (!argNum.isPresent()) return false;
    int index = argNum.getAsInt();
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (index >= args.length) return false;
    checker.checkExpression(args[index]);
    return checker.hasAgreeingInstanceof();
  }

  public static boolean hasAgreeingInstanceof(@NotNull PsiTypeCastExpression expression) {
    final PsiType castType = expression.getType();
    final PsiExpression operand = expression.getOperand();
    if (!(operand instanceof PsiReferenceExpression)) return false;
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)operand;
    final InstanceofChecker checker = new InstanceofChecker(referenceExpression, castType, false);
    PsiElement parent = findInterestingParent(expression);
    while (parent != null) {
      parent.accept(checker);
      if (checker.hasAgreeingInstanceof()) return true;
      parent = findInterestingParent(parent);
    }
    return false;
  }

  /**
   * @param cast a cast expression to find parent instanceof for
   * @return a traditional instanceof expression that is a candidate to introduce a pattern that covers given cast.
   */
  @Nullable
  public static PsiInstanceOfExpression findPatternCandidate(PsiTypeCastExpression cast) {
    PsiIdentifier identifier = null;
    PsiElement context = PsiUtil.skipParenthesizedExprUp(cast.getParent());
    if (context instanceof PsiLocalVariable) {
      identifier = ((PsiLocalVariable)context).getNameIdentifier();
      context = context.getParent();
    } else {
      while (true) {
        if (context instanceof PsiPolyadicExpression) {
          IElementType tokenType = ((PsiPolyadicExpression)context).getOperationTokenType();
          if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
            PsiInstanceOfExpression instanceOf = findInstanceOf((PsiExpression)context, cast, tokenType.equals(JavaTokenType.ANDAND));
            if (instanceOf != null) {
              return instanceOf;
            }
          }
        }
        if (context instanceof PsiConditionalExpression) {
          PsiExpression condition = ((PsiConditionalExpression)context).getCondition();
          if (!PsiTreeUtil.isAncestor(condition, cast, true)) {
            boolean whenTrue = PsiTreeUtil.isAncestor(((PsiConditionalExpression)context).getThenExpression(), cast, false);
            PsiInstanceOfExpression instanceOf = findInstanceOf(condition, cast, whenTrue);
            if (instanceOf != null) {
              return instanceOf;
            }
          }
        }
        if ((context instanceof PsiExpression && !(context instanceof PsiLambdaExpression)) ||
            context instanceof PsiExpressionList || context instanceof PsiLocalVariable) {
          context = context.getParent();
          continue;
        }
        break;
      }
      if (!(context instanceof PsiStatement)) return null;
    }
    PsiElement parent = context.getParent();
    if (parent instanceof PsiCodeBlock) {
      for (PsiElement stmt = context.getPrevSibling(); stmt != null; stmt = stmt.getPrevSibling()) {
        if (stmt instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)stmt;
          PsiStatement thenBranch = ifStatement.getThenBranch();
          PsiStatement elseBranch = ifStatement.getElseBranch();
          boolean thenCompletes = canCompleteNormally(parent, thenBranch);
          boolean elseCompletes = canCompleteNormally(parent, elseBranch);
          if (thenCompletes != elseCompletes) {
            PsiInstanceOfExpression instanceOf = findInstanceOf(ifStatement.getCondition(), cast, thenCompletes);
            if (instanceOf != null) {
              return instanceOf;
            }
          }
        }
        if (stmt instanceof PsiWhileStatement || stmt instanceof PsiDoWhileStatement || stmt instanceof PsiForStatement) {
          PsiConditionalLoopStatement loop = (PsiConditionalLoopStatement)stmt;
          if (PsiTreeUtil.processElements(
            loop, e -> !(e instanceof PsiBreakStatement) || ((PsiBreakStatement)e).findExitedStatement() != loop)) {
            PsiInstanceOfExpression instanceOf = findInstanceOf(loop.getCondition(), cast, false);
            if (instanceOf != null) {
              return instanceOf;
            }
          }
        }
        if (isConflictingNameDeclaredInside(identifier, stmt)) return null;
        if (stmt instanceof PsiSwitchLabelStatementBase) break;
      }
      if (parent.getParent() instanceof PsiBlockStatement) {
        context = parent.getParent();
        parent = context.getParent();
      }
    }
    return processParent(cast, context, parent);
  }

  private static PsiInstanceOfExpression processParent(PsiTypeCastExpression cast, PsiElement context, PsiElement parent) {
    if (parent instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)parent;
      if (ifStatement.getThenBranch() == context) {
        return findInstanceOf(ifStatement.getCondition(), cast, true);
      }
      else if (ifStatement.getElseBranch() == context) {
        return findInstanceOf(ifStatement.getCondition(), cast, false);
      }
    }
    if (parent instanceof PsiForStatement || parent instanceof PsiWhileStatement) {
      return findInstanceOf(((PsiConditionalLoopStatement)parent).getCondition(), cast, true);
    }
    return null;
  }

  private static boolean isConflictingNameDeclaredInside(@Nullable PsiIdentifier identifier, @NotNull PsiElement statement) {
    if (identifier == null) return false;
    class Visitor extends JavaRecursiveElementWalkingVisitor {
      boolean hasConflict = false;

      @Override
      public void visitClass(final PsiClass aClass) {}

      @Override
      public void visitVariable(PsiVariable variable) {
        String name = variable.getName();
        if (name != null && identifier.textMatches(name)) {
          hasConflict = true;
          stopWalking();
        }
        super.visitVariable(variable);
      }
    }
    Visitor visitor = new Visitor();
    statement.accept(visitor);
    return visitor.hasConflict;
  }

  private static boolean canCompleteNormally(@NotNull PsiElement parent, @Nullable PsiStatement statement) {
    if (statement == null) return true;
    ControlFlow flow;
    try {
      flow = ControlFlowFactory.getControlFlow(parent, new LocalsControlFlowPolicy(parent), 
                                               ControlFlowOptions.NO_CONST_EVALUATE);
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
    int startOffset = flow.getStartOffset(statement);
    int endOffset = flow.getEndOffset(statement);
    return startOffset != -1 && endOffset != -1 && ControlFlowUtil.canCompleteNormally(flow, startOffset, endOffset);
  }

  @Contract("null, _, _ -> null")
  private static PsiInstanceOfExpression findInstanceOf(@Nullable PsiExpression condition,
                                                        @NotNull PsiTypeCastExpression cast,
                                                        boolean whenTrue) {
    if (condition == null) return null;
    if (condition instanceof PsiParenthesizedExpression) {
      return findInstanceOf(((PsiParenthesizedExpression)condition).getExpression(), cast, whenTrue);
    }
    if (BoolUtils.isNegation(condition)) {
      return findInstanceOf(BoolUtils.getNegated(condition), cast, !whenTrue);
    }
    if (condition instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)condition;
      IElementType tokenType = polyadic.getOperationTokenType();
      if (tokenType == JavaTokenType.ANDAND && whenTrue ||
          tokenType == JavaTokenType.OROR && !whenTrue) {
        for (PsiExpression operand : polyadic.getOperands()) {
          if (PsiTreeUtil.isAncestor(operand, cast, false)) return null;
          PsiInstanceOfExpression result = findInstanceOf(operand, cast, whenTrue);
          if (result != null) {
            return result;
          }
        }
      }
    }
    if (condition instanceof PsiInstanceOfExpression && whenTrue) {
      PsiInstanceOfExpression instanceOf = (PsiInstanceOfExpression)condition;
      PsiPattern pattern = instanceOf.getPattern();
      if (pattern instanceof PsiTypeTestPattern) {
        PsiTypeTestPattern typeTestPattern = (PsiTypeTestPattern)pattern;
        PsiPatternVariable variable = typeTestPattern.getPatternVariable();
        if (variable == null) {
          PsiType type = typeTestPattern.getCheckType().getType();
          PsiType castType = Objects.requireNonNull(cast.getCastType()).getType();
          PsiExpression castOperand = Objects.requireNonNull(cast.getOperand());
          if (typeCompatible(type, castType, castOperand) &&
              PsiEquivalenceUtil.areElementsEquivalent(instanceOf.getOperand(), castOperand)) {
            return instanceOf;
          }
        }
      }
    }
    return null;
  }

  private static boolean typeCompatible(@NotNull PsiType instanceOfType, @NotNull PsiType castType, @NotNull PsiExpression castOperand) {
    if (instanceOfType.equals(castType)) return true;
    if (castType instanceof PsiClassType) {
      PsiClassType rawType = ((PsiClassType)castType).rawType();
      if (instanceOfType.equals(rawType)) {
        PsiType type = castOperand.getType();
        return type != null && !JavaGenericsUtil.isUncheckedCast(castType, type);
      }
    }
    return false;
  }

  private static class InstanceofChecker extends JavaElementVisitor {

    private final PsiReferenceExpression referenceExpression;
    private final PsiType castType;
    private final boolean strict;
    private boolean negate = false;
    private PsiInstanceOfExpression conflictingInstanceof = null;
    private boolean agreeingInstanceof = false;


    InstanceofChecker(PsiReferenceExpression referenceExpression,
                      PsiType castType, boolean strict) {
      this.referenceExpression = referenceExpression;
      this.castType = castType;
      this.strict = strict;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitExpression(expression);
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
        for (PsiExpression operand : expression.getOperands()) {
          checkExpression(operand);
          if (agreeingInstanceof) {
            return;
          }
        }
        if (!negate && conflictingInstanceof != null) {
          agreeingInstanceof = false;
        }
      }
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      processConditionalLoop(statement);
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      processConditionalLoop(statement);
    }

    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      processConditionalLoop(statement);
    }

    private void processConditionalLoop(PsiConditionalLoopStatement loop) {
      PsiStatement body = loop.getBody();
      if (!PsiTreeUtil.isAncestor(body, referenceExpression, true)) return;
      if (isReassignedInside(body)) return;
      checkExpression(loop.getCondition());
    }

    @Override
    public void visitIfStatement(PsiIfStatement ifStatement) {
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      negate = PsiTreeUtil.isAncestor(elseBranch, referenceExpression, true);
      if (isReassignedInside(negate ? elseBranch : ifStatement.getThenBranch())) return;
      checkExpression(ifStatement.getCondition());
    }

    private boolean isReassignedInside(PsiStatement branch) {
      return branch instanceof PsiBlockStatement && VariableAccessUtils.variableIsAssignedBeforeReference(referenceExpression, branch);
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      final PsiExpression elseExpression = expression.getElseExpression();
      negate = PsiTreeUtil.isAncestor(elseExpression, referenceExpression, true);
      checkExpression(expression.getCondition());
    }

    @Override
    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      if (negate) return;
      if (isAgreeing(expression)) {
        agreeingInstanceof = true;
        conflictingInstanceof = null;
      }
      else if (isConflicting(expression) && conflictingInstanceof == null) {
        conflictingInstanceof = expression;
      }
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      PsiExpression operand = expression.getExpression();
      if (operand != null) {
        operand.accept(this);
      }
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      PsiExpression operand = expression.getOperand();
      if (operand != null && expression.getOperationTokenType().equals(JavaTokenType.EXCL)) {
        negate = !negate;
        operand.accept(this);
        negate = !negate;
      }
    }

    private void checkExpression(PsiExpression expression) {
      if (expression != null) {
        expression.accept(this);
      }
    }

    private boolean isConflicting(PsiInstanceOfExpression expression) {
      final PsiExpression conditionOperand = expression.getOperand();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(referenceExpression, conditionOperand)) {
        return false;
      }
      final PsiTypeElement typeElement = expression.getCheckType();
      if (typeElement == null) return false;
      final PsiType type = typeElement.getType();
      if (strict) {
        return !castType.equals(type);
      }
      return !castType.isAssignableFrom(type);
    }

    private boolean isAgreeing(PsiInstanceOfExpression expression) {
      final PsiExpression conditionOperand = expression.getOperand();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(referenceExpression, conditionOperand)) {
        return false;
      }
      final PsiTypeElement typeElement = expression.getCheckType();
      if (typeElement == null) return false;
      final PsiType type = typeElement.getType();
      if (strict) {
        return castType.equals(type);
      }
      return castType.isAssignableFrom(type);
    }

    public boolean hasAgreeingInstanceof() {
      return agreeingInstanceof;
    }

    public PsiInstanceOfExpression getConflictingInstanceof() {
      return conflictingInstanceof;
    }
  }
}
