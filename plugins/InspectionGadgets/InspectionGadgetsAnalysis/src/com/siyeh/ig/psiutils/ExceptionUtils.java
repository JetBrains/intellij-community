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
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ExceptionUtils {

  private ExceptionUtils() { }

  private static final Set<String> s_genericExceptionTypes = new HashSet<String>(4);

  static {
    s_genericExceptionTypes.add(CommonClassNames.JAVA_LANG_THROWABLE);
    s_genericExceptionTypes.add(CommonClassNames.JAVA_LANG_EXCEPTION);
    s_genericExceptionTypes.add(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION);
    s_genericExceptionTypes.add(CommonClassNames.JAVA_LANG_ERROR);
  }

  @NotNull
  public static Set<PsiType> calculateExceptionsThrown(@Nullable PsiElement element) {
    return calculateExceptionsThrown(element, new LinkedHashSet<PsiType>(5));
  }

  @NotNull
  public static Set<PsiType> calculateExceptionsThrown(@Nullable PsiElement element, @NotNull Set<PsiType> out) {
    if (element == null) return out;
    if (element instanceof PsiResourceList) {
      final PsiResourceList resourceList = (PsiResourceList)element;
      for (PsiResourceListElement resource : resourceList) {
        final PsiMethod method = PsiUtil.getResourceCloserMethod(resource);
        collectExceptionsThrown(method, out);
      }
    }
    final ExceptionsThrownVisitor visitor = new ExceptionsThrownVisitor(out);
    element.accept(visitor);
    return out;
  }

  public static boolean isGenericExceptionClass(@Nullable PsiType exceptionType) {
    if (!(exceptionType instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)exceptionType;
    final String className = classType.getCanonicalText();
    return s_genericExceptionTypes.contains(className);
  }

  public static boolean isThrowableRethrown(PsiParameter throwable, PsiCodeBlock catchBlock) {
    final PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(catchBlock);
    if (!(lastStatement instanceof PsiThrowStatement)) {
      return false;
    }
    final PsiThrowStatement throwStatement = (PsiThrowStatement)lastStatement;
    final PsiExpression expression = throwStatement.getException();
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement element = referenceExpression.resolve();
    return throwable.equals(element);
  }

  static boolean statementThrowsException(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiAssertStatement ||
        statement instanceof PsiReturnStatement ||
        statement instanceof PsiExpressionStatement ||
        statement instanceof PsiExpressionListStatement ||
        statement instanceof PsiForeachStatement ||
        statement instanceof PsiDeclarationStatement ||
        statement instanceof PsiEmptyStatement ||
        statement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    else if (statement instanceof PsiThrowStatement) {
      return true;
    }
    else if (statement instanceof PsiForStatement) {
      return forStatementThrowsException((PsiForStatement)statement);
    }
    else if (statement instanceof PsiWhileStatement) {
      return whileStatementThrowsException((PsiWhileStatement)statement);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      return doWhileThrowsException((PsiDoWhileStatement)statement);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      final PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)statement;
      final PsiCodeBlock body = synchronizedStatement.getBody();
      return blockThrowsException(body);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      return blockThrowsException(codeBlock);
    }
    else if (statement instanceof PsiLabeledStatement) {
      final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)statement;
      final PsiStatement statementLabeled = labeledStatement.getStatement();
      return statementThrowsException(statementLabeled);
    }
    else if (statement instanceof PsiIfStatement) {
      return ifStatementThrowsException((PsiIfStatement)statement);
    }
    else if (statement instanceof PsiTryStatement) {
      return tryStatementThrowsException((PsiTryStatement)statement);
    }
    else if (statement instanceof PsiSwitchStatement) {
      return false;
    }
    else {
      // unknown statement type
      return false;
    }
  }

  static boolean blockThrowsException(@Nullable PsiCodeBlock block) {
    if (block == null) {
      return false;
    }
    final PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      if (statementThrowsException(statement)) {
        return true;
      }
    }
    return false;
  }

  private static boolean tryStatementThrowsException(PsiTryStatement tryStatement) {
    final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    if (catchBlocks.length == 0) {
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (blockThrowsException(tryBlock)) {
        return true;
      }
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    return blockThrowsException(finallyBlock);
  }

  private static boolean ifStatementThrowsException(PsiIfStatement ifStatement) {
    return statementThrowsException(ifStatement.getThenBranch()) && statementThrowsException(ifStatement.getElseBranch());
  }

  private static boolean doWhileThrowsException(PsiDoWhileStatement doWhileStatement) {
    return statementThrowsException(doWhileStatement.getBody());
  }

  private static boolean whileStatementThrowsException(PsiWhileStatement whileStatement) {
    final PsiExpression condition = whileStatement.getCondition();
    if (BoolUtils.isTrue(condition)) {
      final PsiStatement body = whileStatement.getBody();
      if (statementThrowsException(body)) {
        return true;
      }
    }
    return false;
  }

  private static boolean forStatementThrowsException(PsiForStatement forStatement) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (statementThrowsException(initialization)) {
      return true;
    }
    final PsiExpression test = forStatement.getCondition();
    if (BoolUtils.isTrue(test)) {
      final PsiStatement body = forStatement.getBody();
      if (statementThrowsException(body)) {
        return true;
      }
      final PsiStatement update = forStatement.getUpdate();
      if (statementThrowsException(update)) {
        return true;
      }
    }
    return false;
  }

  private static void collectExceptionsThrown(@Nullable PsiMethod method, @NotNull Set<PsiType> out) {
    if (method == null) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    for (PsiJavaCodeReferenceElement referenceElement : method.getThrowsList().getReferenceElements()) {
      final PsiClass exceptionClass = (PsiClass)referenceElement.resolve();
      if (exceptionClass != null) {
        out.add(factory.createType(exceptionClass));
      }
    }
  }

  public static Set<PsiType> getExceptionTypesHandled(PsiTryStatement statement) {
    final Set<PsiType> out = new HashSet<PsiType>(5);
    for (PsiParameter parameter : statement.getCatchBlockParameters()) {
      final PsiType type = parameter.getType();
      if (type instanceof PsiDisjunctionType) {
        final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)type;
        out.addAll(disjunctionType.getDisjunctions());
      } else {
        out.add(type);
      }
    }
    return out;
  }

  private static class ExceptionsThrownVisitor extends JavaRecursiveElementWalkingVisitor {

    private final Set<PsiType> m_exceptionsThrown;

    private ExceptionsThrownVisitor(Set<PsiType> thrownTypes) {
      m_exceptionsThrown = thrownTypes;
    }

    @Override
    public void visitClass(PsiClass aClass) {}

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {}

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      collectExceptionsThrown(callExpression.resolveMethod(), m_exceptionsThrown);
    }

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception = statement.getException();
      if (exception == null) {
        return;
      }
      final PsiType type = exception.getType();
      if (type != null) {
        m_exceptionsThrown.add(type);
      }
    }

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      final Set<PsiType> exceptionsHandled = getExceptionTypesHandled(statement);

      for (PsiType resourceException : calculateExceptionsThrown(statement.getResourceList())) {
        if (!isExceptionHandled(exceptionsHandled, resourceException)) {
          m_exceptionsThrown.add(resourceException);
        }
      }
      for (PsiType tryException : calculateExceptionsThrown(statement.getTryBlock())) {
        if (!isExceptionHandled(exceptionsHandled, tryException)) {
          m_exceptionsThrown.add(tryException);
        }
      }
      calculateExceptionsThrown(statement.getFinallyBlock(), m_exceptionsThrown);
      for (PsiCodeBlock catchBlock : statement.getCatchBlocks()) {
        calculateExceptionsThrown(catchBlock, m_exceptionsThrown);
      }
    }

    private static boolean isExceptionHandled(Set<PsiType> exceptionsHandled, @NotNull PsiType thrownType) {
      if (exceptionsHandled.contains(thrownType)) {
        return true;
      }
      for (PsiType exceptionHandled : exceptionsHandled) {
        if (exceptionHandled.isAssignableFrom(thrownType)) {
          return true;
        }
      }
      return false;
    }
  }
}