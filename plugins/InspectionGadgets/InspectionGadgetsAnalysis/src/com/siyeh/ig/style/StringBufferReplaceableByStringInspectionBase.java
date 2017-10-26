/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringBufferReplaceableByStringInspectionBase extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiElement element = (PsiElement)infos[0];
    if (element instanceof PsiNewExpression) {
      return InspectionGadgetsBundle.message("new.string.buffer.replaceable.by.string.problem.descriptor");
    }
    final String typeText = ((PsiType)infos[1]).getPresentableText();
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.problem.descriptor", typeText);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferReplaceableByStringVisitor();
  }

  private static class StringBufferReplaceableByStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiType type = variable.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type) &&
          !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type)) {
        return;
      }
      final PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
      if (!isNewStringBufferOrStringBuilder(initializer)) {
        return;
      }
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (codeBlock == null) {
        return;
      }
      final ReplaceableByStringVisitor visitor = new ReplaceableByStringVisitor(variable);
      codeBlock.accept(visitor);
      if (!visitor.isReplaceable()) {
        return;
      }
      registerVariableError(variable, variable, type);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type) &&
          !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type)) {
        return;
      }
      final PsiExpression completeExpression = getCompleteExpression(expression);
      if (completeExpression == null) {
        return;
      }
      registerNewExpressionError(expression, expression, type);
    }

    private static boolean isNewStringBufferOrStringBuilder(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PsiNewExpression) {
        return true;
      }
      if (!isAppendCall(expression) || !(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      return isNewStringBufferOrStringBuilder(qualifier);
    }
  }

  static boolean isAppendCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"append".equals(methodName)) {
      return false;
    }
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 3) {
      return arguments[0].getType() instanceof PsiArrayType &&
             PsiType.INT.equals(arguments[1].getType()) && PsiType.INT.equals(arguments[2].getType());
    }
    return arguments.length == 1;
  }

  private static boolean isToStringCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"toString".equals(methodName)) {
      return false;
    }
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    return arguments.length == 0;
  }

  @Nullable
  static PsiExpression getCompleteExpression(PsiElement element) {
    PsiElement completeExpression = element;
    boolean found = false;
    while (true) {
      final PsiElement parent = completeExpression.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        break;
      }
      final PsiElement grandParent = parent.getParent();
      if (isToStringCall(grandParent)) {
        found = true;
      } else if (!isAppendCall(grandParent)) {
        return null;
      }
      completeExpression = grandParent;
      if (found) {
        return (PsiExpression)completeExpression;
      }
    }
    return null;
  }

  private static class ReplaceableByStringVisitor extends JavaRecursiveElementWalkingVisitor {

    private final PsiElement myParent;
    private final PsiVariable myVariable;
    private boolean myReplaceable = true;
    private boolean myPossibleSideEffect;
    private boolean myToStringFound;

    ReplaceableByStringVisitor(@NotNull PsiVariable variable) {
      myVariable = variable;
      myParent = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiIfStatement.class, PsiLoopStatement.class);
    }

    public boolean isReplaceable() {
      return myReplaceable && myToStringFound;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (!myReplaceable) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getTextOffset() > myVariable.getTextOffset() && !myToStringFound) {
        myPossibleSideEffect = true;
      }
    }

    @Override
    public void visitUnaryExpression(PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      if (expression.getTextOffset() > myVariable.getTextOffset() && !myToStringFound) {
        myPossibleSideEffect = true;
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (expression.getTextOffset() < myVariable.getTextOffset() || myToStringFound) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        myPossibleSideEffect = true;
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        myPossibleSideEffect = true;
        return;
      }
      final String name = aClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(name) ||
        CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(name)) {
        return;
      }
      if (isArgumentOfStringBuilderMethod(expression)) {
        return;
      }
      myPossibleSideEffect = true;
    }

    private boolean isArgumentOfStringBuilderMethod(PsiMethodCallExpression expression) {
      final PsiExpressionList parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, true, PsiStatement.class);
      if (parent == null) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        return isCallToStringBuilderMethod(methodCallExpression) || isArgumentOfStringBuilderMethod(methodCallExpression);
      }
      if (grandParent instanceof PsiNewExpression) {
        final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(grandParent, PsiLocalVariable.class, true, PsiExpressionList.class);
        if (!myVariable.equals(variable)) {
          return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression)grandParent;
        final PsiMethod constructor = newExpression.resolveMethod();
        if (constructor == null) {
          return false;
        }
        final PsiClass aClass = constructor.getContainingClass();
        if (aClass == null) {
          return false;
        }
        final String name = aClass.getQualifiedName();
        return CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(name) ||
               CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(name);
      }
      return false;
    }

    private boolean isCallToStringBuilderMethod(PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      while (qualifier instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)qualifier;
        final PsiReferenceExpression methodExpression1 = callExpression.getMethodExpression();
        qualifier = methodExpression1.getQualifierExpression();
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (!myVariable.equals(target)) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String name1 = aClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(name1) ||
             CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(name1);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (!myReplaceable || expression.getTextOffset() < myVariable.getTextOffset()) {
        return;
      }
      super.visitReferenceExpression(expression);
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier != null) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!myVariable.equals(target)) {
        return;
      }
      if (myToStringFound) {
        myReplaceable = false;
        return;
      }
      final PsiElement element = PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class, PsiIfStatement.class, PsiLoopStatement.class);
      if (!myParent.equals(element)) {
        myReplaceable = false;
        return;
      }
      PsiElement parent = expression.getParent();
      while (true) {
        if (!(parent instanceof PsiReferenceExpression)) {
          myReplaceable = false;
          return;
        }
        final PsiElement grandParent = parent.getParent();
        if (!isAppendCall(grandParent)) {
          if (!isToStringCall(grandParent)) {
            myReplaceable = false;
            return;
          }
          myToStringFound = true;
          return;
        }
        if (myPossibleSideEffect) {
          myReplaceable = false;
          return;
        }
        parent = grandParent.getParent();
        if (parent instanceof PsiExpressionStatement) {
          return;
        }
      }
    }
  }
}
