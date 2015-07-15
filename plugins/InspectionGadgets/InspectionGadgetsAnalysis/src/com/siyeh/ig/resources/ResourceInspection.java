/*
 * Copyright 2008-2014 Bas Leijdekkers
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
package com.siyeh.ig.resources;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ResourceInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean insideTryAllowed = false;

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("allow.resource.to.be.opened.inside.a.try.block"),
                                          this, "insideTryAllowed");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message("resource.opened.not.closed.problem.descriptor", text);
  }

  @Override
  public final BaseInspectionVisitor buildVisitor() {
    return new ResourceVisitor();
  }

  private class ResourceVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isNotSafelyClosedResource(expression)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isNotSafelyClosedResource(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }

    private boolean isNotSafelyClosedResource(PsiExpression expression) {
      if (!isResourceCreation(expression)) {
        return false;
      }
      final PsiVariable boundVariable = getVariable(expression);
      return !(boundVariable instanceof PsiResourceVariable) &&
             !isSafelyClosed(boundVariable, expression, insideTryAllowed) &&
             !isResourceFactoryClosed(expression, insideTryAllowed) &&
             !isResourceEscapingFromMethod(boundVariable, expression);
    }
  }

  protected abstract boolean isResourceCreation(PsiExpression expression);

  protected boolean isResourceFactoryClosed(PsiExpression expression, boolean insideTryAllowed) {
    return false;
  }

  @Nullable
  public static PsiVariable getVariable(@NotNull PsiExpression expression) {
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      final PsiExpression lhs = assignment.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement referent = referenceExpression.resolve();
      if (!(referent instanceof PsiVariable)) {
        return null;
      }
      return (PsiVariable)referent;
    }
    else if (parent instanceof PsiVariable) {
      return (PsiVariable)parent;
    }
    else {
      return null;
    }
  }

  private static boolean isSafelyClosed(@Nullable PsiVariable variable, PsiElement context, boolean insideTryAllowed) {
    if (variable == null) {
      return false;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(context, PsiStatement.class);
    if (statement == null) {
      return false;
    }
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (insideTryAllowed) {
      PsiStatement parentStatement = PsiTreeUtil.getParentOfType(statement, PsiStatement.class);
      while (parentStatement != null && !(parentStatement instanceof PsiTryStatement)) {
        parentStatement = PsiTreeUtil.getParentOfType(parentStatement, PsiStatement.class);
      }
      if (parentStatement != null) {
        final PsiTryStatement tryStatement = (PsiTryStatement)parentStatement;
        if (isResourceClosedInFinally(tryStatement, variable)) {
          return true;
        }
      }
    }
    while (nextStatement != null && !isSignificant(nextStatement)) {
      nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
    }
    while (nextStatement == null) {
      statement = PsiTreeUtil.getParentOfType(statement, PsiStatement.class, true);
      if (statement == null) {
        return false;
      }
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        statement = (PsiStatement)parent;
      }
      nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    }
    if (!(nextStatement instanceof PsiTryStatement)) {
      // exception in next statement can prevent closing of the resource
      return isResourceClose(nextStatement, variable);
    }
    final PsiTryStatement tryStatement = (PsiTryStatement)nextStatement;
    if (isResourceClosedInFinally(tryStatement, variable)) {
      return true;
    }
    return isResourceClose(nextStatement, variable);
  }

  private static boolean isSignificant(@NotNull PsiStatement statement) {
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.TRUE);
    statement.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        result.set(Boolean.FALSE);
        stopWalking();
      }
    });
    return !result.get().booleanValue();
  }

  protected static boolean isResourceClosedInFinally(@NotNull PsiTryStatement tryStatement, @NotNull PsiVariable variable) {
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock == null) {
      return false;
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return false;
    }
    final CloseVisitor visitor = new CloseVisitor(variable);
    finallyBlock.accept(visitor);
    return visitor.containsClose();
  }

  private static boolean isResourceClose(PsiStatement statement, PsiVariable variable) {
    if (statement instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      return isResourceClose(methodCallExpression, variable);
    }
    else if (statement instanceof PsiTryStatement) {
      final PsiTryStatement tryStatement = (PsiTryStatement)statement;
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) {
        return false;
      }
      final PsiStatement[] innerStatements = tryBlock.getStatements();
      if (innerStatements.length == 0) {
        return false;
      }
      if (isResourceClose(innerStatements[0], variable)) {
        return true;
      }
    }
    else if (statement instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      final PsiExpression condition = ifStatement.getCondition();
      if (!(condition instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.NE != tokenType) {
        return false;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return false;
      }
      if (PsiType.NULL.equals(lhs.getType())) {
        if (!(rhs instanceof PsiReferenceExpression)) {
          return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)rhs;
        final PsiElement target = referenceExpression.resolve();
        if (!variable.equals(target)) {
          return false;
        }
      }
      else if (PsiType.NULL.equals(rhs.getType())) {
        if (!(lhs instanceof PsiReferenceExpression)) {
          return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (!variable.equals(target)) {
          return false;
        }
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      return isResourceClose(thenBranch, variable);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiStatement[] statements = codeBlock.getStatements();
      return statements.length != 0 && isResourceClose(statements[0], variable);
    }
    return false;
  }

  private static boolean isResourceClose(PsiMethodCallExpression call, PsiVariable resource) {
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
      return false;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReference reference = (PsiReference)qualifier;
    final PsiElement referent = reference.resolve();
    return referent != null && referent.equals(resource);
  }

  public static boolean isResourceEscapingFromMethod(PsiVariable boundVariable, PsiExpression resourceCreationExpression) {
    if (resourceCreationExpression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)resourceCreationExpression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiField) {
          final PsiField field = (PsiField)target;
          final String fieldName = field.getName();
          if ("out".equals(fieldName) || "err".equals(fieldName)) {
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "java.lang.System".equals(containingClass.getQualifiedName())) {
              return true;
            }
          }
        }
      }
    }
    PsiElement parent = ParenthesesUtils.getParentSkipParentheses(resourceCreationExpression);
    if (parent instanceof PsiConditionalExpression) {
      parent = ParenthesesUtils.getParentSkipParentheses(parent);
    }
    if (parent instanceof PsiReturnStatement) {
      return true;
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      if (ParenthesesUtils.stripParentheses(assignmentExpression.getRExpression()) != resourceCreationExpression) {
        return true; // non-sensical code
      }
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(assignmentExpression.getLExpression());
      if (lhs instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiField) {
          return true;
        }
      }
    }
    else if (parent instanceof PsiExpressionList) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiAnonymousClass) {
        grandParent = grandParent.getParent();
      }
      if (grandParent instanceof PsiCallExpression) {
        return true;
      }
    }
    if (boundVariable == null) {
      return false;
    }
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(resourceCreationExpression, PsiCodeBlock.class, true, PsiMember.class);
    if (codeBlock == null) {
      return false;
    }
    final EscapeVisitor visitor = new EscapeVisitor(boundVariable);
    codeBlock.accept(visitor);
    return visitor.isEscaped();
  }

  private static class CloseVisitor extends JavaRecursiveElementVisitor {

    private boolean containsClose = false;
    private final PsiVariable resource;
    private final String resourceName;

    private CloseVisitor(PsiVariable resource) {
      this.resource = resource;
      this.resourceName = resource.getName();
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!containsClose) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression call) {
      if (containsClose) {
        return;
      }
      super.visitMethodCallExpression(call);
      if (!isResourceClose(call, resource)) {
        return;
      }
      containsClose = true;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
      // check if resource is closed in a method like IOUtils.silentClose()
      super.visitReferenceExpression(referenceExpression);
      if (containsClose) {
        return;
      }
      final String text = referenceExpression.getText();
      if (text == null || !text.equals(resourceName)) {
        return;
      }
      final PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      final PsiExpressionList argumentList = (PsiExpressionList)parent;
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiElement target = referenceExpression.resolve();
      if (target == null || !target.equals(resource)) {
        return;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiCodeBlock codeBlock = method.getBody();
      if (codeBlock == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length != 1) {
        return;
      }
      final PsiParameter parameter = parameters[0];
      final PsiStatement[] statements = codeBlock.getStatements();
      for (PsiStatement statement : statements) {
        if (isResourceClose(statement, parameter)) {
          containsClose = true;
          return;
        }
      }
    }

    public boolean containsClose() {
      return containsClose;
    }
  }

  private static class EscapeVisitor extends JavaRecursiveElementVisitor {

    private final PsiVariable boundVariable;
    private boolean escaped = false;

    public EscapeVisitor(@NotNull PsiVariable boundVariable) {
      this.boundVariable = boundVariable;
    }

    @Override
    public void visitAnonymousClass(PsiAnonymousClass aClass) {}

    @Override
    public void visitElement(PsiElement element) {
      if (escaped) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      final PsiExpression value = PsiUtil.deparenthesizeExpression(statement.getReturnValue());
      if (!(value instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)value;
      final PsiElement target = referenceExpression.resolve();
      if (boundVariable.equals(target)) {
        escaped = true;
      }
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      final PsiExpression rhs = PsiUtil.deparenthesizeExpression(expression.getRExpression());
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)rhs;
      final PsiElement target = referenceExpression.resolve();
      if (!boundVariable.equals(target)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.deparenthesizeExpression(expression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression lReferenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement lTarget = lReferenceExpression.resolve();
      if (lTarget instanceof PsiField) {
        escaped = true;
      }
    }

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      for (PsiExpression expression : expressions) {
        final PsiExpression expression1 = PsiUtil.deparenthesizeExpression(expression);
        if (!(expression1 instanceof PsiReferenceExpression)) {
          continue;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression1;
        final PsiElement target = referenceExpression.resolve();
        if (boundVariable.equals(target)) {
          escaped = true;
          break;
        }
      }
    }

    public boolean isEscaped() {
      return escaped;
    }
  }
}
