/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus.*;

public class ControlFlowUtils {

  private ControlFlowUtils() {}

  public static boolean isElseIf(PsiIfStatement ifStatement) {
    PsiElement parent = ifStatement.getParent();
    if (parent instanceof PsiCodeBlock &&
        ((PsiCodeBlock)parent).getStatements().length == 1 &&
        parent.getParent() instanceof PsiBlockStatement) {
      parent = parent.getParent().getParent();
    }
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement parentStatement = (PsiIfStatement)parent;
    final PsiStatement elseBranch = parentStatement.getElseBranch();
    return ifStatement.equals(elseBranch);
  }

  public static boolean statementMayCompleteNormally(@Nullable PsiStatement statement) {
    if (statement == null) {
      return true;
    }
    if (statement instanceof PsiBreakStatement || statement instanceof PsiContinueStatement ||
        statement instanceof PsiReturnStatement || statement instanceof PsiThrowStatement) {
      return false;
    }
    else if (statement instanceof PsiExpressionListStatement || statement instanceof PsiEmptyStatement ||
             statement instanceof PsiAssertStatement || statement instanceof PsiDeclarationStatement ||
             statement instanceof PsiSwitchLabelStatement || statement instanceof PsiForeachStatement) {
      return true;
    }
    else if (statement instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return true;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return true;
      }
      @NonNls final String methodName = method.getName();
      if (!methodName.equals("exit")) {
        return true;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return true;
      }
      final String className = aClass.getQualifiedName();
      return !"java.lang.System".equals(className);
    }
    else if (statement instanceof PsiForStatement) {
      return forStatementMayCompleteNormally((PsiForStatement)statement);
    }
    else if (statement instanceof PsiWhileStatement) {
      return whileStatementMayCompleteNormally((PsiWhileStatement)statement);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      return doWhileStatementMayCompleteNormally((PsiDoWhileStatement)statement);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      final PsiCodeBlock body = ((PsiSynchronizedStatement)statement).getBody();
      return codeBlockMayCompleteNormally(body);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiCodeBlock codeBlock = ((PsiBlockStatement)statement).getCodeBlock();
      return codeBlockMayCompleteNormally(codeBlock);
    }
    else if (statement instanceof PsiLabeledStatement) {
      return labeledStatementMayCompleteNormally((PsiLabeledStatement)statement);
    }
    else if (statement instanceof PsiIfStatement) {
      return ifStatementMayCompleteNormally((PsiIfStatement)statement);
    }
    else if (statement instanceof PsiTryStatement) {
      return tryStatementMayCompleteNormally((PsiTryStatement)statement);
    }
    else if (statement instanceof PsiSwitchStatement) {
      return switchStatementMayCompleteNormally((PsiSwitchStatement)statement);
    }
    else if (statement instanceof PsiTemplateStatement || statement instanceof PsiClassLevelDeclarationStatement) {
      return true;
    }
    else {
      assert false : "unknown statement type: " + statement.getClass();
      return true;
    }
  }

  private static boolean doWhileStatementMayCompleteNormally(@NotNull PsiDoWhileStatement loopStatement) {
    final PsiExpression condition = loopStatement.getCondition();
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    final PsiStatement body = loopStatement.getBody();
    return statementMayCompleteNormally(body) && value != Boolean.TRUE
           || statementIsBreakTarget(loopStatement) || statementContainsContinueToAncestor(loopStatement);
  }

  private static boolean whileStatementMayCompleteNormally(@NotNull PsiWhileStatement loopStatement) {
    final PsiExpression condition = loopStatement.getCondition();
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    return value != Boolean.TRUE || statementIsBreakTarget(loopStatement) || statementContainsContinueToAncestor(loopStatement);
  }

  private static boolean forStatementMayCompleteNormally(@NotNull PsiForStatement loopStatement) {
    if (statementIsBreakTarget(loopStatement)) {
      return true;
    }
    if (statementContainsContinueToAncestor(loopStatement)) {
      return true;
    }
    final PsiExpression condition = loopStatement.getCondition();
    if (condition == null) {
      return false;
    }
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    return Boolean.TRUE != value;
  }

  private static boolean switchStatementMayCompleteNormally(@NotNull PsiSwitchStatement switchStatement) {
    if (statementIsBreakTarget(switchStatement)) {
      return true;
    }
    final PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return true;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return true;
    }
    int numCases = 0;
    boolean hasDefaultCase = false;
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiSwitchLabelStatement) {
        numCases++;
        final PsiSwitchLabelStatement switchLabelStatement = (PsiSwitchLabelStatement)statement;
        if (switchLabelStatement.isDefaultCase()) {
          hasDefaultCase = true;
        }
      }
      if (statement instanceof PsiBreakStatement) {
        final PsiBreakStatement breakStatement = (PsiBreakStatement)statement;
        if (breakStatement.getLabelIdentifier() == null) {
          return true;
        }
      }
    }
    final boolean isEnum = isEnumSwitch(switchStatement);
    if (!hasDefaultCase && !isEnum) {
      return true;
    }
    if (!hasDefaultCase) {
      final PsiExpression expression = switchStatement.getExpression();
      if (expression == null) {
        return true;
      }
      final PsiClassType type = (PsiClassType)expression.getType();
      if (type == null) {
        return true;
      }
      final PsiClass aClass = type.resolve();
      if (aClass == null) {
        return true;
      }
      final PsiField[] fields = aClass.getFields();
      int numEnums = 0;
      for (final PsiField field : fields) {
        final PsiType fieldType = field.getType();
        if (fieldType.equals(type)) {
          numEnums++;
        }
      }
      if (numEnums != numCases) {
        return true;
      }
    }
    return statementMayCompleteNormally(statements[statements.length - 1]);
  }

  private static boolean isEnumSwitch(PsiSwitchStatement statement) {
    final PsiExpression expression = statement.getExpression();
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClass aClass = ((PsiClassType)type).resolve();
    return aClass != null && aClass.isEnum();
  }

  private static boolean tryStatementMayCompleteNormally(@NotNull PsiTryStatement tryStatement) {
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      if (!codeBlockMayCompleteNormally(finallyBlock)) {
        return false;
      }
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (codeBlockMayCompleteNormally(tryBlock)) {
      return true;
    }
    final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (final PsiCodeBlock catchBlock : catchBlocks) {
      if (codeBlockMayCompleteNormally(catchBlock)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementMayCompleteNormally(@NotNull PsiIfStatement ifStatement) {
    final PsiExpression condition = ifStatement.getCondition();
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (value == Boolean.TRUE) {
      return statementMayCompleteNormally(thenBranch);
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (value == Boolean.FALSE) {
      return statementMayCompleteNormally(elseBranch);
    }
    // process branch with fewer statements first
    PsiStatement branch1;
    PsiStatement branch2;
    if ((thenBranch == null ? 0 : thenBranch.getTextLength()) < (elseBranch == null ? 0 : elseBranch.getTextLength())) {
      branch1 = thenBranch;
      branch2 = elseBranch;
    }
    else {
      branch2 = thenBranch;
      branch1 = elseBranch;
    }
    return statementMayCompleteNormally(branch1) || statementMayCompleteNormally(branch2);
  }

  private static boolean labeledStatementMayCompleteNormally(@NotNull PsiLabeledStatement labeledStatement) {
    final PsiStatement statement = labeledStatement.getStatement();
    if (statement == null) {
      return false;
    }
    return statementMayCompleteNormally(statement) || statementIsBreakTarget(statement);
  }

  public static boolean codeBlockMayCompleteNormally(@Nullable PsiCodeBlock block) {
    if (block == null) {
      return true;
    }
    final PsiStatement[] statements = block.getStatements();
    for (final PsiStatement statement : statements) {
      if (!statementMayCompleteNormally(statement)) {
        return false;
      }
    }
    return true;
  }

  private static boolean statementIsBreakTarget(@NotNull PsiStatement statement) {
    final BreakFinder breakFinder = new BreakFinder(statement);
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  private static boolean statementContainsContinueToAncestor(@NotNull PsiStatement statement) {
    PsiElement parent = statement.getParent();
    while (parent instanceof PsiLabeledStatement) {
      statement = (PsiStatement)parent;
      parent = parent.getParent();
    }
    final ContinueToAncestorFinder continueToAncestorFinder = new ContinueToAncestorFinder(statement);
    statement.accept(continueToAncestorFinder);
    return continueToAncestorFinder.continueToAncestorFound();
  }

  public static boolean containsReturn(@NotNull PsiElement element) {
    final ReturnFinder returnFinder = new ReturnFinder();
    element.accept(returnFinder);
    return returnFinder.returnFound();
  }

  public static boolean statementIsContinueTarget(@NotNull PsiStatement statement) {
    final ContinueFinder continueFinder = new ContinueFinder(statement);
    statement.accept(continueFinder);
    return continueFinder.continueFound();
  }

  public static boolean containsSystemExit(@NotNull PsiElement element) {
    final SystemExitFinder systemExitFinder = new SystemExitFinder();
    element.accept(systemExitFinder);
    return systemExitFinder.exitFound();
  }

  public static boolean elementContainsCallToMethod(PsiElement context, String containingClassName, PsiType returnType,
    String methodName, PsiType... parameterTypes) {
    final MethodCallFinder methodCallFinder = new MethodCallFinder(containingClassName, returnType, methodName, parameterTypes);
    context.accept(methodCallFinder);
    return methodCallFinder.containsCallToMethod();
  }

  public static boolean isInLoop(@NotNull PsiElement element) {
    final PsiLoopStatement loopStatement = PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class, true, PsiClass.class);
    if (loopStatement == null) {
      return false;
    }
    final PsiStatement body = loopStatement.getBody();
    return body != null && PsiTreeUtil.isAncestor(body, element, true);
  }

  public static boolean isInFinallyBlock(@NotNull PsiElement element) {
    PsiElement currentElement = element;
    while (true) {
      final PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(currentElement, PsiTryStatement.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (tryStatement == null) {
        return false;
      }
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        if (PsiTreeUtil.isAncestor(finallyBlock, currentElement, true)) {
          final PsiMethod elementMethod = PsiTreeUtil.getParentOfType(currentElement, PsiMethod.class);
          final PsiMethod finallyMethod = PsiTreeUtil.getParentOfType(finallyBlock, PsiMethod.class);
          return elementMethod != null && elementMethod.equals(finallyMethod);
        }
      }
      currentElement = tryStatement;
    }
  }

  public static boolean isInCatchBlock(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiCatchSection.class, true, PsiClass.class) != null;
  }

  public static boolean isInExitStatement(@NotNull PsiExpression expression) {
    return isInReturnStatementArgument(expression) || isInThrowStatementArgument(expression);
  }

  private static boolean isInReturnStatementArgument(@NotNull PsiExpression expression) {
    return PsiTreeUtil.getParentOfType(expression, PsiReturnStatement.class) != null;
  }

  public static boolean isInThrowStatementArgument(@NotNull PsiExpression expression) {
    return PsiTreeUtil.getParentOfType(expression, PsiThrowStatement.class) != null;
  }

  @Nullable
  public static PsiStatement stripBraces(@Nullable PsiStatement statement) {
    if (statement instanceof PsiBlockStatement) {
      final PsiBlockStatement block = (PsiBlockStatement)statement;
      final PsiStatement onlyStatement = getOnlyStatementInBlock(block.getCodeBlock());
      return (onlyStatement != null) ? onlyStatement : block;
    }
    else {
      return statement;
    }
  }


  @NotNull
  public static PsiStatement[] unwrapBlock(@Nullable PsiStatement statement) {
    PsiBlockStatement block = ObjectUtils.tryCast(statement, PsiBlockStatement.class);
    if (block != null) {
      return block.getCodeBlock().getStatements();
    }
    return statement == null ? PsiStatement.EMPTY_ARRAY : new PsiStatement[]{statement};
  }

  public static boolean statementCompletesWithStatement(@NotNull PsiStatement containingStatement, @NotNull PsiStatement statement) {
    PsiElement statementToCheck = statement;
    while (true) {
      if (statementToCheck.equals(containingStatement)) {
        return true;
      }
      final PsiElement container = getContainingStatementOrBlock(statementToCheck);
      if (container == null) {
        return false;
      }
      if (container instanceof PsiCodeBlock) {
        if (!statementIsLastInBlock((PsiCodeBlock)container, (PsiStatement)statementToCheck)) {
          return false;
        }
      }
      if (container instanceof PsiLoopStatement) {
        return false;
      }
      statementToCheck = container;
    }
  }

  public static boolean blockCompletesWithStatement(@NotNull PsiCodeBlock body, @NotNull PsiStatement statement) {
    PsiElement statementToCheck = statement;
    while (true) {
      if (statementToCheck == null) {
        return false;
      }
      final PsiElement container = getContainingStatementOrBlock(statementToCheck);
      if (container == null) {
        return false;
      }
      if (container instanceof PsiLoopStatement) {
        return false;
      }
      if (container instanceof PsiCodeBlock) {
        if (!statementIsLastInBlock((PsiCodeBlock)container, (PsiStatement)statementToCheck)) {
          return false;
        }
        if (container.equals(body)) {
          return true;
        }
        statementToCheck = PsiTreeUtil.getParentOfType(container, PsiStatement.class);
      }
      else {
        statementToCheck = container;
      }
    }
  }

  @Nullable
  private static PsiElement getContainingStatementOrBlock(@NotNull PsiElement statement) {
    return PsiTreeUtil.getParentOfType(statement, PsiStatement.class, PsiCodeBlock.class);
  }

  private static boolean statementIsLastInBlock(@NotNull PsiCodeBlock block, @NotNull PsiStatement statement) {
    for (PsiElement child = block.getLastChild(); child != null; child = child.getPrevSibling()) {
      if (!(child instanceof PsiStatement)) {
        continue;
      }
      final PsiStatement childStatement = (PsiStatement)child;
      if (statement.equals(childStatement)) {
        return true;
      }
      if (!(statement instanceof PsiEmptyStatement)) {
        return false;
      }
    }
    return false;
  }

  @Nullable
  public static PsiStatement getFirstStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
    return PsiTreeUtil.getChildOfType(codeBlock, PsiStatement.class);
  }

  @Nullable
  public static PsiStatement getLastStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
    return getLastChildOfType(codeBlock, PsiStatement.class);
  }

  private static <T extends PsiElement> T getLastChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return null;
    for (PsiElement child = element.getLastChild(); child != null; child = child.getPrevSibling()) {
      if (aClass.isInstance(child)) {
        //noinspection unchecked
        return (T)child;
      }
    }
    return null;
  }

  /**
   * @return null, if zero or more than one statements in the specified code block.
   */
  @Nullable
  public static PsiStatement getOnlyStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
    return getOnlyChildOfType(codeBlock, PsiStatement.class);
  }

  static <T extends PsiElement> T getOnlyChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return null;
    T result = null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        if (result == null) {
          //noinspection unchecked
          result = (T)child;
        }
        else {
          return null;
        }
      }
    }
    return result;
  }

  public static boolean hasStatementCount(@Nullable PsiCodeBlock codeBlock, int count) {
    return hasChildrenOfTypeCount(codeBlock, count, PsiStatement.class);
  }

  static <T extends PsiElement> boolean hasChildrenOfTypeCount(@Nullable PsiElement element, int count, @NotNull Class<T> aClass) {
    if (element == null) return false;
    int i = 0;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        i++;
        if (i > count) return false;
      }
    }
    return i == count;
  }

  public static boolean isEmptyCodeBlock(PsiCodeBlock codeBlock) {
    return hasStatementCount(codeBlock, 0);
  }

  public static boolean methodAlwaysThrowsException(@NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return true;
    }
    return !containsReturn(body) && !codeBlockMayCompleteNormally(body);
  }

  public static boolean lambdaExpressionAlwaysThrowsException(PsiLambdaExpression expression) {
    final PsiElement body = expression.getBody();
    if (body instanceof PsiExpression) {
      return false;
    }
    if (!(body instanceof PsiCodeBlock)) {
      return true;
    }
    final PsiCodeBlock codeBlock = (PsiCodeBlock)body;
    return !containsReturn(codeBlock) && !codeBlockMayCompleteNormally(codeBlock);
  }

  public static boolean statementContainsNakedBreak(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    final NakedBreakFinder breakFinder = new NakedBreakFinder();
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  /**
   * Checks whether the given statement effectively breaks given loop. Returns true
   * if the statement is {@link PsiBreakStatement} having given loop as a target. Also may return
   * true in other cases if the statement is semantically equivalent to break like this:
   *
   * <pre>{@code
   * int myMethod(int[] data) {
   *   for(int val : data) {
   *     if(val == 5) {
   *       System.out.println(val);
   *       return 0; // this statement is semantically equivalent to break.
   *     }
   *   }
   *   return 0;
   * }}</pre>
   *
   * @param statement statement which may break the loop
   * @param loop a loop to break
   * @return true if the statement actually breaks the loop
   */
  @Contract("null, _ -> false")
  public static boolean statementBreaksLoop(PsiStatement statement, PsiLoopStatement loop) {
    if(statement instanceof PsiBreakStatement) {
      return ((PsiBreakStatement)statement).findExitedStatement() == loop;
    }
    if(statement instanceof PsiReturnStatement) {
      PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
      PsiElement cur = loop;
      for(PsiElement parent = cur.getParent();;parent = cur.getParent()) {
        if(parent instanceof PsiLabeledStatement) {
          cur = parent;
        } else if(parent instanceof PsiCodeBlock) {
          PsiCodeBlock block = (PsiCodeBlock)parent;
          PsiStatement[] statements = block.getStatements();
          if(block.getParent() instanceof PsiBlockStatement && statements.length > 0 && statements[statements.length-1] == cur) {
            cur = block.getParent();
          } else break;
        } else if(parent instanceof PsiIfStatement) {
          if(cur == ((PsiIfStatement)parent).getThenBranch() || cur == ((PsiIfStatement)parent).getElseBranch()) {
            cur = parent;
          } else break;
        } else break;
      }
      PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(cur);
      if(nextElement instanceof PsiReturnStatement) {
        return EquivalenceChecker.getCanonicalPsiEquivalence()
          .expressionsAreEquivalent(returnValue, ((PsiReturnStatement)nextElement).getReturnValue());
      }
      if(returnValue == null &&
         cur.getParent() instanceof PsiCodeBlock &&
         cur.getParent().getParent() instanceof PsiMethod &&
         nextElement instanceof PsiJavaToken && ((PsiJavaToken)nextElement).getTokenType().equals(JavaTokenType.RBRACE)) {
        return true;
      }
    }
    return false;
  }

  private static StreamEx<PsiExpression> conditions(PsiElement element) {
    return StreamEx.iterate(element, e -> e != null &&
                                          !(e instanceof PsiLambdaExpression) && !(e instanceof PsiMethod), PsiElement::getParent)
      .pairMap((child, parent) -> parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getThenBranch() == child ? parent : null)
      .select(PsiIfStatement.class)
      .map(PsiIfStatement::getCondition)
      .flatMap(cond -> cond instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)cond).getOperationTokenType().equals(
        JavaTokenType.ANDAND) ? StreamEx.of(((PsiPolyadicExpression)cond).getOperands()) : StreamEx.of(cond));
  }

  /**
   * @param statement statement to check
   * @param loop      surrounding loop
   * @return true if it could be statically determined that given statement is executed at most once
   */
  public static boolean isExecutedOnceInLoop(PsiStatement statement, PsiLoopStatement loop) {
    if (flowBreaksLoop(statement, loop)) return true;
    if (loop instanceof PsiForStatement) {
      // Check that we're inside counted loop which increments some loop variable and
      // the code is executed under condition like if(var == something)
      PsiDeclarationStatement initialization =
        ObjectUtils.tryCast(((PsiForStatement)loop).getInitialization(), PsiDeclarationStatement.class);
      PsiStatement update = ((PsiForStatement)loop).getUpdate();
      if (initialization != null && update != null) {
        PsiLocalVariable variable = StreamEx.of(initialization.getDeclaredElements()).select(PsiLocalVariable.class)
          .findFirst(var -> VariableAccessUtils.variableIsIncremented(var, update) ||
                            VariableAccessUtils.variableIsDecremented(var, update)).orElse(null);
        if (variable != null) {
          boolean hasLoopVarCheck = conditions(statement).select(PsiBinaryExpression.class)
            .filter(binOp -> binOp.getOperationTokenType().equals(JavaTokenType.EQEQ))
            .anyMatch(binOp -> ExpressionUtils.getOtherOperand(binOp, variable) != null);
          if (hasLoopVarCheck) {
            boolean notWritten = ReferencesSearch.search(variable).forEach(ref -> {
              PsiExpression expression = ObjectUtils.tryCast(ref.getElement(), PsiExpression.class);
              return expression == null || PsiTreeUtil.isAncestor(update, expression, false) || !PsiUtil.isAccessedForWriting(expression);
            });
            if (notWritten) return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Returns true if the variable is definitely reassigned to fresh value after executing given statement
   * without intermediate usages (ignoring possible exceptions in-between)
   *
   * @param statement statement to start checking from
   * @param variable variable to check
   * @return true if variable is reassigned
   */
  public static boolean isVariableReassigned(PsiStatement statement, PsiVariable variable) {
    for (PsiStatement sibling = nextExecutedStatement(statement); sibling != null; sibling = nextExecutedStatement(sibling)) {
      PsiExpression rValue = ExpressionUtils.getAssignmentTo(sibling, variable);
      if (rValue != null && !VariableAccessUtils.variableIsUsed(variable, rValue)) return true;
      if (VariableAccessUtils.variableIsUsed(variable, sibling)) return false;
    }
    return false;
  }

  /**
   * Checks whether control flow after executing given statement will definitely not go into the next iteration of given loop.
   *
   * @param statement executed statement. It's not checked whether this statement itself breaks the loop.
   * @param loop a surrounding loop. Must be parent of statement
   * @return true if it can be statically defined that next loop iteration will not be executed.
   */
  @Contract("null, _ -> false")
  public static boolean flowBreaksLoop(PsiStatement statement, PsiLoopStatement loop) {
    if(statement == null || statement == loop) return false;
    for (PsiStatement sibling = statement; sibling != null; sibling = nextExecutedStatement(sibling)) {
      if(sibling instanceof PsiContinueStatement) return false;
      if(sibling instanceof PsiThrowStatement || sibling instanceof PsiReturnStatement) return true;
      if(sibling instanceof PsiBreakStatement) {
        PsiBreakStatement breakStatement = (PsiBreakStatement)sibling;
        PsiStatement exitedStatement = breakStatement.findExitedStatement();
        if(exitedStatement == loop) return true;
        return flowBreaksLoop(nextExecutedStatement(exitedStatement), loop);
      }
      if (sibling instanceof PsiIfStatement || sibling instanceof PsiSwitchStatement) {
        if (!PsiTreeUtil.collectElementsOfType(sibling, PsiContinueStatement.class).isEmpty()) return false;
      }
      if (sibling instanceof PsiLoopStatement) {
        if (PsiTreeUtil.collectElements(sibling, e -> e instanceof PsiContinueStatement &&
                                                      ((PsiContinueStatement)e).getLabelIdentifier() != null).length > 0) {
          return false;
        }
      }
    }
    return false;
  }

  @Nullable
  private static PsiStatement firstStatement(@Nullable PsiStatement statement) {
    while (statement instanceof PsiBlockStatement) {
      PsiStatement[] statements = ((PsiBlockStatement)statement).getCodeBlock().getStatements();
      if (statements.length == 0) break;
      statement = statements[0];
    }
    return statement;
  }

  @Nullable
  private static PsiStatement nextExecutedStatement(PsiStatement statement) {
    PsiStatement next = firstStatement(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class));
    if (next == null) {
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiCodeBlock) {
        PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiBlockStatement || gParent instanceof PsiSwitchStatement) {
          return nextExecutedStatement((PsiStatement)gParent);
        }
      }
      else if (parent instanceof PsiLabeledStatement || parent instanceof PsiIfStatement || parent instanceof PsiSwitchLabelStatement
               || parent instanceof PsiSwitchStatement) {
        return nextExecutedStatement((PsiStatement)parent);
      }
    }
    return next;
  }

  /**
   * Checks whether variable can be referenced between start and loop entry. Back-edges are also considered, so the actual place
   * where it referenced might be outside of (start, loop entry) interval.
   *
   * @param flow ControlFlow to analyze
   * @param start start point
   * @param loop loop to check
   * @param variable variable to analyze
   * @return true if variable can be referenced between start and stop points
   */
  private static boolean isVariableReferencedBeforeLoopEntry(final ControlFlow flow,
                                                             final int start,
                                                             final PsiStatement loop,
                                                             final PsiVariable variable) {
    final int loopStart = flow.getStartOffset(loop);
    final int loopEnd = flow.getEndOffset(loop);
    if(start == loopStart) return false;

    List<ControlFlowUtil.ControlFlowEdge> edges = ControlFlowUtil.getEdges(flow, start);
    // DFS visits instructions mainly in backward direction while here visiting in forward direction
    // greatly reduces number of iterations.
    Collections.reverse(edges);

    BitSet referenced = new BitSet();
    boolean changed = true;
    while(changed) {
      changed = false;
      for(ControlFlowUtil.ControlFlowEdge edge: edges) {
        int from = edge.myFrom;
        int to = edge.myTo;
        if(referenced.get(from)) {
          // jump to the loop start from within the loop is not considered as loop entry
          if(to == loopStart && (from < loopStart || from >= loopEnd)) {
            return true;
          }
          if(!referenced.get(to)) {
            referenced.set(to);
            changed = true;
          }
          continue;
        }
        if(ControlFlowUtil.isVariableAccess(flow, from, variable)) {
          referenced.set(from);
          referenced.set(to);
          if(to == loopStart) return true;
          changed = true;
        }
      }
    }
    return false;
  }

  /**
   * Returns an {@link InitializerUsageStatus} for given variable with respect to given loop
   * @param var a variable to determine an initializer usage status for
   * @param loop a loop where variable is used
   * @return initializer usage status for variable
   */
  @NotNull
  public static InitializerUsageStatus getInitializerUsageStatus(PsiVariable var, PsiStatement loop) {
    if(!(var instanceof PsiLocalVariable) || var.getInitializer() == null) return UNKNOWN;
    if(isDeclarationJustBefore(var, loop)) return DECLARED_JUST_BEFORE;
    // Check that variable is declared in the same method or the same lambda expression
    if(PsiTreeUtil.getParentOfType(var, PsiLambdaExpression.class, PsiMethod.class) !=
       PsiTreeUtil.getParentOfType(loop, PsiLambdaExpression.class, PsiMethod.class)) return UNKNOWN;
    PsiElement block = PsiUtil.getVariableCodeBlock(var, null);
    if(block == null) return UNKNOWN;
    final ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(loop.getProject())
        .getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    }
    catch (AnalysisCanceledException ignored) {
      return UNKNOWN;
    }
    int start = controlFlow.getEndOffset(var.getInitializer())+1;
    int stop = controlFlow.getStartOffset(loop);
    if(isVariableReferencedBeforeLoopEntry(controlFlow, start, loop, var)) return UNKNOWN;
    if (!ControlFlowUtil.isValueUsedWithoutVisitingStop(controlFlow, start, stop, var)) return AT_WANTED_PLACE_ONLY;
    return var.hasModifierProperty(PsiModifier.FINAL) ? UNKNOWN : AT_WANTED_PLACE;
  }

  static boolean isDeclarationJustBefore(PsiVariable var, PsiStatement nextStatement) {
    PsiElement declaration = var.getParent();
    PsiElement nextStatementParent = nextStatement.getParent();
    if(nextStatementParent instanceof PsiLabeledStatement) {
      nextStatement = (PsiStatement)nextStatementParent;
    }
    if(declaration instanceof PsiDeclarationStatement) {
      PsiElement[] elements = ((PsiDeclarationStatement)declaration).getDeclaredElements();
      if (ArrayUtil.getLastElement(elements) == var && nextStatement.equals(
        PsiTreeUtil.skipWhitespacesAndCommentsForward(declaration))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if statement essentially contains no executable code
   *
   * @param statement statement to test
   * @return true if statement essentially contains no executable code
   */
  @Contract("null -> false")
  public static boolean statementIsEmpty(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof PsiEmptyStatement) {
      return true;
    }
    if (statement instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiStatement[] codeBlockStatements = codeBlock.getStatements();
      for (PsiStatement codeBlockStatement : codeBlockStatements) {
        if (!statementIsEmpty(codeBlockStatement)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * @param expression expression to check
   * @return true if given expression is always executed and can be converted to a statement
   */
  public static boolean canExtractStatement(PsiExpression expression) {
    return canExtractStatement(expression, true);
  }

  /**
   * @param expression    expression to check
   * @param checkExecuted if true, expression will be considered non-extractable if it is not always executed within its topmost expression
   *                      (e.g. appears in then/else branches in ?: expression)
   * @return true if given expression can be converted to a statement
   */
  public static boolean canExtractStatement(PsiExpression expression, boolean checkExecuted) {
    PsiElement cur = expression;
    PsiElement parent = cur.getParent();
    while(parent instanceof PsiExpression || parent instanceof PsiExpressionList) {
      if(parent instanceof PsiLambdaExpression) {
        return true;
      }
      if (checkExecuted && parent instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
        IElementType type = polyadicExpression.getOperationTokenType();
        if ((type.equals(JavaTokenType.ANDAND) || type.equals(JavaTokenType.OROR)) && polyadicExpression.getOperands()[0] != cur) {
          // not the first in the &&/|| chain: we cannot properly generate code which would short-circuit as well
          return false;
        }
      }
      if (checkExecuted && parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != cur) {
        return false;
      }
      if(parent instanceof PsiMethodCallExpression) {
        PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)parent).getMethodExpression();
        if(methodExpression.textMatches("this") || methodExpression.textMatches("super")) {
          return false;
        }
      }
      cur = parent;
      parent = cur.getParent();
    }
    if (parent instanceof PsiStatement) {
      PsiElement grandParent = parent.getParent();
      if (checkExecuted && grandParent instanceof PsiForStatement && ((PsiForStatement)grandParent).getUpdate() == parent) {
        return false;
      }
    }
    if(parent instanceof PsiReturnStatement || parent instanceof PsiExpressionStatement) return true;
    if(parent instanceof PsiLocalVariable) {
      PsiElement grandParent = parent.getParent();
      if(grandParent instanceof PsiDeclarationStatement && ((PsiDeclarationStatement)grandParent).getDeclaredElements().length == 1) {
        return true;
      }
    }
    if (parent instanceof PsiField) {
      PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(parent);
      PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(parent);
      boolean multipleFieldsDeclaration = prev instanceof PsiJavaToken && ((PsiJavaToken)prev).getTokenType() == JavaTokenType.COMMA ||
                                          next instanceof PsiJavaToken && ((PsiJavaToken)next).getTokenType() == JavaTokenType.COMMA;
      return !multipleFieldsDeclaration;
    }
    if(parent instanceof PsiForeachStatement && ((PsiForeachStatement)parent).getIteratedValue() == cur) return true;
    if(parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getCondition() == cur) return true;
    return false;
  }

  public enum InitializerUsageStatus {
    // Variable is declared just before the wanted place
    DECLARED_JUST_BEFORE,
    // All initial value usages go through wanted place and at wanted place the variable value is guaranteed to be the initial value
    AT_WANTED_PLACE_ONLY,
    // At wanted place the variable value is guaranteed to have the initial value, but this initial value might be used somewhere else
    AT_WANTED_PLACE,
    // It's not guaranteed that the variable value at wanted place is initial value
    UNKNOWN
  }

  private static class NakedBreakFinder extends JavaRecursiveElementWalkingVisitor {
    private boolean m_found;

    private boolean breakFound() {
      return m_found;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (m_found) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
    }

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
      if (statement.getLabelIdentifier() != null) {
        return;
      }
      m_found = true;
    }

    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      // don't drill down
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      // don't drill down
    }

    @Override
    public void visitSwitchStatement(PsiSwitchStatement statement) {
      // don't drill down
    }
  }

  private static class SystemExitFinder extends JavaRecursiveElementWalkingVisitor {

    private boolean m_found;

    private boolean exitFound() {
      return m_found;
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // do nothing to keep from drilling into inner classes
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      if (m_found) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      @NonNls final String methodName = method.getName();
      if (!methodName.equals("exit")) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!"java.lang.System".equals(className) && !"java.lang.Runtime".equals(className)) {
        return;
      }
      m_found = true;
    }
  }

  private static class ReturnFinder extends JavaRecursiveElementWalkingVisitor {
    private boolean myFound;

    private boolean returnFound() {
      return myFound;
    }

    @Override
    public void visitClass(@NotNull PsiClass psiClass) {
      // do nothing, to keep drilling into inner classes
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement returnStatement) {
      myFound = true;
      stopWalking();
    }
  }

  private static class BreakFinder extends JavaRecursiveElementWalkingVisitor {

    private boolean m_found;
    private final PsiStatement m_target;

    private BreakFinder(@NotNull PsiStatement target) {
      m_target = target;
    }

    private boolean breakFound() {
      return m_found;
    }

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      if (m_found) {
        return;
      }
      super.visitBreakStatement(statement);
      final PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(exitedStatement, m_target, false)) {
        m_found = true;
      }
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      if (m_found) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final Object value = ExpressionUtils.computeConstantExpression(condition);
      if (Boolean.FALSE != value) {
        final PsiStatement thenBranch = statement.getThenBranch();
        if (thenBranch != null) {
          thenBranch.accept(this);
        }
      }
      if (Boolean.TRUE != value) {
        final PsiStatement elseBranch = statement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.accept(this);
        }
      }
    }
  }

  private static class ContinueFinder extends JavaRecursiveElementWalkingVisitor {

    private boolean m_found;
    private final PsiStatement m_target;

    private ContinueFinder(@NotNull PsiStatement target) {
      m_target = target;
    }

    private boolean continueFound() {
      return m_found;
    }

    @Override
    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      if (m_found) {
        return;
      }
      super.visitContinueStatement(statement);
      final PsiStatement continuedStatement = statement.findContinuedStatement();
      if (continuedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(continuedStatement, m_target, false)) {
        m_found = true;
      }
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      if (m_found) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final Object value = ExpressionUtils.computeConstantExpression(condition);
      if (Boolean.FALSE != value) {
        final PsiStatement thenBranch = statement.getThenBranch();
        if (thenBranch != null) {
          thenBranch.accept(this);
        }
      }
      if (Boolean.TRUE != value) {
        final PsiStatement elseBranch = statement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.accept(this);
        }
      }
    }
  }

  private static class MethodCallFinder extends JavaRecursiveElementWalkingVisitor {

    private final String containingClassName;
    private final PsiType returnType;
    private final String methodName;
    private final PsiType[] parameterTypeNames;
    private boolean containsCallToMethod;

    private MethodCallFinder(String containingClassName, PsiType returnType, String methodName, PsiType... parameterTypeNames) {
      this.containingClassName = containingClassName;
      this.returnType = returnType;
      this.methodName = methodName;
      this.parameterTypeNames = parameterTypeNames;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (containsCallToMethod) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      if (containsCallToMethod) {
        return;
      }
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallToMethod(expression, containingClassName, returnType, methodName, parameterTypeNames)) {
        return;
      }
      containsCallToMethod = true;
    }

    private boolean containsCallToMethod() {
      return containsCallToMethod;
    }
  }

  private static class ContinueToAncestorFinder extends JavaRecursiveElementWalkingVisitor {

    private final PsiStatement statement;
    private boolean found;

    private ContinueToAncestorFinder(PsiStatement statement) {
      this.statement = statement;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (found) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitContinueStatement(
      PsiContinueStatement continueStatement) {
      if (found) {
        return;
      }
      super.visitContinueStatement(continueStatement);
      final PsiIdentifier labelIdentifier = continueStatement.getLabelIdentifier();
      if (labelIdentifier == null) {
        return;
      }
      final PsiStatement continuedStatement = continueStatement.findContinuedStatement();
      if (continuedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(continuedStatement, statement, true)) {
        found = true;
      }
    }

    private boolean continueToAncestorFound() {
      return found;
    }
  }
}
