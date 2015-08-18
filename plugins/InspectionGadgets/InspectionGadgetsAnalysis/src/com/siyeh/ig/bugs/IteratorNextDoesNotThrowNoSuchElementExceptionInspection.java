/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.IteratorUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IteratorNextDoesNotThrowNoSuchElementExceptionInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "IteratorNextCanNotThrowNoSuchElementException";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "iterator.next.does.not.throw.nosuchelementexception.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "iterator.next.does.not.throw.nosuchelementexception.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IteratorNextDoesNotThrowNoSuchElementExceptionVisitor();
  }

  private static class IteratorNextDoesNotThrowNoSuchElementExceptionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (!MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_ITERATOR, null,
                                     HardcodedMethodConstants.NEXT)) {
        return;
      }
      for (final PsiType exception : ExceptionUtil.getThrownExceptions(method)) {
        if (exception.equalsToText(
          "java.util.NoSuchElementException")) {
          return;
        }
      }
      if (IteratorUtils.containsCallToIteratorNext(method, null, false)) {
        return;
      }
      final CalledMethodsVisitor visitor = new CalledMethodsVisitor();
      method.accept(visitor);
      if (visitor.isNoSuchElementExceptionThrown()) {
        return;
      }
      registerMethodError(method);
    }
  }

  private static class CalledMethodsVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private boolean noSuchElementExceptionThrown;

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      if (noSuchElementExceptionThrown) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiElement method = methodExpression.resolve();
      if (method == null) {
        return;
      }
      final List<PsiClassType> exceptions = ExceptionUtil.getThrownExceptions(method);
      for (final PsiType exception : exceptions) {
        if (exception.equalsToText(
          "java.util.NoSuchElementException")) {
          noSuchElementExceptionThrown = true;
        }
      }
    }

    boolean isNoSuchElementExceptionThrown() {
      return noSuchElementExceptionThrown;
    }
  }
}