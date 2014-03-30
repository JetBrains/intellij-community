/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WhileCanBeForeachInspectionBase extends BaseInspection {
  @Nullable
  public static PsiStatement getPreviousStatement(PsiElement context) {
    final PsiElement prevStatement = PsiTreeUtil.skipSiblingsBackward(context, PsiWhiteSpace.class, PsiComment.class);
    if (!(prevStatement instanceof PsiStatement)) {
      return null;
    }
    return (PsiStatement)prevStatement;
  }

  @Override
  @NotNull
  public String getID() {
    return "WhileLoopReplaceableByForEach";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("while.can.be.foreach.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("while.can.be.foreach.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WhileCanBeForeachVisitor();
  }

  private static class WhileCanBeForeachVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement whileStatement) {
      super.visitWhileStatement(whileStatement);
      if (!PsiUtil.isLanguageLevel5OrHigher(whileStatement)) {
        return;
      }
      if (!isCollectionLoopStatement(whileStatement)) {
        return;
      }
      registerStatementError(whileStatement);
    }

    private static boolean isCollectionLoopStatement(PsiWhileStatement whileStatement) {
      final PsiStatement initialization = getPreviousStatement(whileStatement);
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)declaredElement;
      if (!TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_UTIL_ITERATOR, "java.util.ListIterator")) {
        return false;
      }
      final PsiExpression initialValue = variable.getInitializer();
      if (initialValue == null) {
        return false;
      }
      if (!(initialValue instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression initialCall = (PsiMethodCallExpression)initialValue;
      final PsiExpressionList argumentList = initialCall.getArgumentList();
      final PsiExpression[] argument = argumentList.getExpressions();
      if (argument.length != 0) {
        return false;
      }
      final PsiReferenceExpression initialMethodExpression = initialCall.getMethodExpression();
      @NonNls final String initialCallName = initialMethodExpression.getReferenceName();
      if (!"iterator".equals(initialCallName) && !"listIterator".equals(initialCallName)) {
        return false;
      }
      final PsiExpression qualifier = initialMethodExpression.getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        return false;
      }
      final PsiClass qualifierClass;
      if (qualifier != null) {
        final PsiType qualifierType = qualifier.getType();
        if (!(qualifierType instanceof PsiClassType)) {
          return false;
        }
        qualifierClass = ((PsiClassType)qualifierType).resolve();
      }
      else {
        qualifierClass = PsiTreeUtil.getParentOfType(whileStatement, PsiClass.class);
      }
      if (qualifierClass == null) {
        return false;
      }
      if (!InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_LANG_ITERABLE)) {
        return false;
      }
      final PsiExpression condition = whileStatement.getCondition();
      if (!isHasNextCalled(variable, condition)) {
        return false;
      }
      final PsiStatement body = whileStatement.getBody();
      if (body == null) {
        return false;
      }
      if (calculateCallsToIteratorNext(variable, body) != 1) {
        return false;
      }
      if (isIteratorRemoveCalled(variable, body)) {
        return false;
      }
      //noinspection SimplifiableIfStatement
      if (isIteratorHasNextCalled(variable, body)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, body)) {
        return false;
      }
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, body)) {
        return false;
      }
      PsiElement nextSibling = whileStatement.getNextSibling();
      while (nextSibling != null) {
        if (VariableAccessUtils.variableValueIsUsed(variable, nextSibling)) {
          return false;
        }
        nextSibling = nextSibling.getNextSibling();
      }
      return true;
    }

    private static boolean isHasNextCalled(PsiVariable iterator, PsiExpression condition) {
      if (!(condition instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression call = (PsiMethodCallExpression)condition;
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return false;
      }
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.HAS_NEXT.equals(methodName)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return true;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      return iterator.equals(target);
    }

    private static int calculateCallsToIteratorNext(PsiVariable iterator, PsiElement context) {
      final NumberCallsToIteratorNextVisitor visitor = new NumberCallsToIteratorNextVisitor(iterator);
      context.accept(visitor);
      return visitor.getNumCallsToIteratorNext();
    }

    private static boolean isIteratorRemoveCalled(PsiVariable iterator, PsiElement context) {
      final IteratorMethodCallVisitor visitor = new IteratorMethodCallVisitor(iterator);
      context.accept(visitor);
      return visitor.isMethodCalled();
    }

    private static boolean isIteratorHasNextCalled(PsiVariable iterator, PsiElement context) {
      final IteratorHasNextVisitor visitor = new IteratorHasNextVisitor(iterator);
      context.accept(visitor);
      return visitor.isHasNextCalled();
    }
  }

  private static class NumberCallsToIteratorNextVisitor extends JavaRecursiveElementVisitor {

    private int numCallsToIteratorNext = 0;
    private final PsiVariable iterator;

    private NumberCallsToIteratorNextVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression callExpression) {
      super.visitMethodCallExpression(callExpression);
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.NEXT.equals(methodName)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (!iterator.equals(target)) {
        return;
      }
      numCallsToIteratorNext++;
    }

    public int getNumCallsToIteratorNext() {
      return numCallsToIteratorNext;
    }
  }

  private static class IteratorMethodCallVisitor extends JavaRecursiveElementVisitor {

    private boolean methodCalled = false;
    private final PsiVariable iterator;

    IteratorMethodCallVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!methodCalled) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (methodCalled) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (HardcodedMethodConstants.NEXT.equals(name)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (iterator.equals(target)) {
        methodCalled = true;
      }
    }

    public boolean isMethodCalled() {
      return methodCalled;
    }
  }

  private static class IteratorHasNextVisitor extends JavaRecursiveElementVisitor {

    private boolean hasNextCalled = false;
    private final PsiVariable iterator;

    private IteratorHasNextVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!hasNextCalled) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.HAS_NEXT.equals(name)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (iterator.equals(target)) {
        hasNextCalled = true;
      }
    }

    public boolean isHasNextCalled() {
      return hasNextCalled;
    }
  }
}
