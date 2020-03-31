/*
 * Copyright 2011-2017 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class MismatchedStringBuilderQueryUpdateInspection extends BaseInspection {

  @NonNls
  static final Set<String> returnSelfNames =
    ContainerUtil.set("append", "appendCodePoint", "delete", "deleteCharAt", "insert", "replace", "reverse");

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "MismatchedQueryAndUpdateOfStringBuilder";
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final boolean updated = ((Boolean)infos[0]).booleanValue();
    final PsiType type = (PsiType)infos[1]; //"StringBuilder";
    if (updated) {
      return InspectionGadgetsBundle.message("mismatched.string.builder.updated.problem.descriptor", type.getPresentableText());
    }
    else {
      return InspectionGadgetsBundle.message("mismatched.string.builder.queried.problem.descriptor", type.getPresentableText());
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MismatchedQueryAndUpdateOfStringBuilderVisitor();
  }

  private static class MismatchedQueryAndUpdateOfStringBuilderVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
      if (!checkVariable(field, containingClass)) {
        return;
      }
      final boolean queried = isStringBuilderQueried(field, containingClass);
      final boolean updated = isStringBuilderUpdated(field, containingClass);
      if (queried == updated || UnusedSymbolUtil.isImplicitWrite(field)) {
        return;
      }
      registerFieldError(field, Boolean.valueOf(updated), field.getType());
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (!checkVariable(variable, codeBlock)) {
        return;
      }
      final boolean queried = isStringBuilderQueried(variable, codeBlock);
      final boolean updated = isStringBuilderUpdated(variable, codeBlock);
      if (queried == updated) {
        return;
      }
      registerVariableError(variable, Boolean.valueOf(updated), variable.getType());
    }

    private static boolean checkVariable(PsiVariable variable, PsiElement context) {
      if (context == null) {
        return false;
      }
      if (!TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
        return false;
      }
      if (!(PsiUtil.skipParenthesizedExprDown(variable.getInitializer()) instanceof PsiNewExpression)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsReturned(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context)) {
        return false;
      }
      return !VariableAccessUtils.variableIsUsedInArrayInitializer(variable, context);
    }

    private static boolean isStringBuilderUpdated(PsiVariable variable, PsiElement context) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null && !ConstructionUtils.isEmptyStringBuilderInitializer(initializer)) {
        return true;
      }
      final StringBuilderUpdateCalledVisitor visitor = new StringBuilderUpdateCalledVisitor(variable);
      context.accept(visitor);
      return visitor.isUpdated();
    }

    private static boolean isStringBuilderQueried(PsiVariable variable, PsiElement context) {
      final StringBuilderQueryCalledVisitor visitor = new StringBuilderQueryCalledVisitor(variable);
      context.accept(visitor);
      return visitor.isQueried();
    }
  }

  private static class StringBuilderUpdateCalledVisitor extends JavaRecursiveElementWalkingVisitor {
    @NonNls
    private static final Set<String> updateNames =
      ContainerUtil.set("append", "appendCodePoint", "delete", "deleteCharAt", "insert", "replace", "reverse", "setCharAt", "setLength");

    private final PsiVariable variable;
    private boolean updated;

    StringBuilderUpdateCalledVisitor(PsiVariable variable) {
      this.variable = variable;
    }

    public boolean isUpdated() {
      return updated;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (updated) return;
      super.visitMethodCallExpression(expression);
      checkReferenceExpression(expression.getMethodExpression());
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      if (updated) return;
      super.visitMethodReferenceExpression(expression);
      checkReferenceExpression(expression);
    }

    private void checkReferenceExpression(PsiReferenceExpression methodExpression) {
      final String name = methodExpression.getReferenceName();
      if (!updateNames.contains(name)) return;
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (hasReferenceToVariable(variable, qualifierExpression)) {
        updated = true;
      }
    }
  }

  private static class StringBuilderQueryCalledVisitor extends JavaRecursiveElementWalkingVisitor {
    @NonNls
    private static final Set<String> queryNames = ContainerUtil
      .set("toString", "indexOf", "lastIndexOf", "capacity", "charAt", "codePointAt", "codePointBefore", "codePointCount", "equals",
           "getChars", "hashCode", "length", "offsetByCodePoints", "subSequence", "substring");

    private final PsiVariable variable;
    private boolean queried;

    StringBuilderQueryCalledVisitor(PsiVariable variable) {
      this.variable = variable;
    }

    public boolean isQueried() {
      return queried;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (queried) return;
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (queried) return;
      super.visitReferenceExpression(expression);
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (!(parent instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (!JavaTokenType.PLUS.equals(tokenType)) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!variable.equals(target)) {
        return;
      }
      final PsiType type = polyadicExpression.getType();
      if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      queried = true;
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      if (queried) return;
      super.visitMethodReferenceExpression(expression);
      final String name = expression.getReferenceName();
      if (!queryNames.contains(name) && !returnSelfNames.contains(name)) return;
      if (PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(expression))) return;
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (hasReferenceToVariable(variable, qualifierExpression)) {
        queried = true;
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (queried) return;
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (!queryNames.contains(name)) {
        if (returnSelfNames.contains(name) && hasReferenceToVariable(variable, qualifierExpression) && isVariableValueUsed(expression)) {
          queried = true;
        }
        return;
      }
      if (hasReferenceToVariable(variable, qualifierExpression)) {
        PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiStatement.class, PsiLambdaExpression.class);
        if (parent instanceof PsiStatement &&
            !SideEffectChecker.mayHaveSideEffects(
              parent, e -> e instanceof PsiMethodCallExpression && isSideEffectFreeBuilderMethodCall((PsiMethodCallExpression)e))) {
          return;
        }
        queried = true;
      }
    }

    /**
     * @param call call to check
     * @return true if method call has no side effect except possible modification of the current StringBuilder
     */
    private boolean isSideEffectFreeBuilderMethodCall(PsiMethodCallExpression call) {
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      return !"getChars".equals(methodExpression.getReferenceName()) &&
             ExpressionUtils.isReferenceTo(methodExpression.getQualifierExpression(), variable);
    }

    private static boolean isVariableValueUsed(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)parent;
        return isVariableValueUsed(parenthesizedExpression);
      }
      if (parent instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
        return isVariableValueUsed(typeCastExpression);
      }
      if (parent instanceof PsiReturnStatement) {
        return true;
      }
      if (parent instanceof PsiExpressionList) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiMethodCallExpression) {
          return true;
        }
      }
      else if (parent instanceof PsiArrayInitializerExpression) {
        return true;
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
        final PsiExpression rhs = assignmentExpression.getRExpression();
        return expression.equals(rhs);
      }
      else if (parent instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)parent;
        final PsiExpression initializer = variable.getInitializer();
        return expression.equals(initializer);
      }
      return false;
    }
  }

  static boolean hasReferenceToVariable(PsiVariable variable, PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      return referenceExpression.isReferenceTo(variable);
    }
    else if (element instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)element;
      final PsiExpression expression = parenthesizedExpression.getExpression();
      return hasReferenceToVariable(variable, expression);
    }
    else if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (returnSelfNames.contains(name)) {
        return hasReferenceToVariable(variable, methodExpression.getQualifierExpression());
      }
    }
    else if (element instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (hasReferenceToVariable(variable, thenExpression)) {
        return true;
      }
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      return hasReferenceToVariable(variable, elseExpression);
    }
    return false;
  }
}
