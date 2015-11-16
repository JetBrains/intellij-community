/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ControlFlowUtils {

  private ControlFlowUtils() {}

  public static boolean isElseIf(PsiIfStatement ifStatement) {
    final PsiElement parent = ifStatement.getParent();
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
      numCases++;
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

  public static boolean statementContainsReturn(@NotNull PsiStatement statement) {
    final ReturnFinder returnFinder = new ReturnFinder();
    statement.accept(returnFinder);
    return returnFinder.returnFound();
  }

  public static boolean statementIsContinueTarget(@NotNull PsiStatement statement) {
    final ContinueFinder continueFinder = new ContinueFinder(statement);
    statement.accept(continueFinder);
    return continueFinder.continueFound();
  }

  public static boolean statementContainsSystemExit(@NotNull PsiStatement statement) {
    final SystemExitFinder systemExitFinder = new SystemExitFinder();
    statement.accept(systemExitFinder);
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

  private static boolean isInThrowStatementArgument(@NotNull PsiExpression expression) {
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
    final ReturnFinder returnFinder = new ReturnFinder();
    body.accept(returnFinder);
    return !returnFinder.returnFound() && !codeBlockMayCompleteNormally(body);
  }

  public static boolean statementContainsNakedBreak(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    final NakedBreakFinder breakFinder = new NakedBreakFinder();
    statement.accept(breakFinder);
    return breakFinder.breakFound();
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

    private boolean m_found;

    private boolean returnFound() {
      return m_found;
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
      if (m_found) {
        return;
      }
      super.visitReturnStatement(returnStatement);
      m_found = true;
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
