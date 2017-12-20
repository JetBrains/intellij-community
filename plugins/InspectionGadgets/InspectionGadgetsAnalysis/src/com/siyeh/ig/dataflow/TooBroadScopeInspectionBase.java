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
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class TooBroadScopeInspectionBase extends BaseInspection {

  /**
   * @noinspection PublicField for externalization
   */
  public boolean m_allowConstructorAsInitializer = false;

  /**
   * @noinspection PublicField for externalization
   */
  public boolean m_onlyLookAtBlocks = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("too.broad.scope.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "TooBroadScope";
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel checkboxOptionsPanel = new MultipleCheckboxOptionsPanel(this);
    checkboxOptionsPanel.addCheckbox(InspectionGadgetsBundle.message("too.broad.scope.only.blocks.option"), "m_onlyLookAtBlocks");
    checkboxOptionsPanel.addCheckbox(InspectionGadgetsBundle.message("too.broad.scope.allow.option"), "m_allowConstructorAsInitializer");
    return checkboxOptionsPanel;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("too.broad.scope.problem.descriptor");
  }

  protected boolean isMoveable(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression == null) {
      return true;
    }
    if (PsiUtil.isConstantExpression(expression) || ExpressionUtils.isNullLiteral(expression)) {
      return true;
    }
    if (expression instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)expression;
      for (PsiExpression initializer : arrayInitializerExpression.getInitializers()) {
        if (!isMoveable(initializer)) {
          return false;
        }
      }
      return true;
    }
    if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
      if (arrayDimensions.length > 0) {
        for (PsiExpression arrayDimension : arrayDimensions) {
          if (!isMoveable(arrayDimension)) {
            return false;
          }
        }
        return true;
      }
      final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
      if (arrayInitializer != null) {
        final PsiExpression[] initializers = arrayInitializer.getInitializers();
        for (final PsiExpression initializerExpression : initializers) {
          if (!isMoveable(initializerExpression)) {
            return false;
          }
        }
        return true;
      }
      final PsiType type = newExpression.getType();
      if (type == null) {
        return false;
      }
      else if (!m_allowConstructorAsInitializer) {
        if (!isAllowedType(type)) {
          return false;
        }
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      for (final PsiExpression argumentExpression : expressions) {
        if (!isMoveable(argumentExpression)) {
          return false;
        }
      }
      return true;
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiExpression qualifier = referenceExpression.getQualifierExpression();
      if (!isMoveable(qualifier)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiClass) {
        return true;
      }
      if (!(target instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)target;
      if (!ClassUtils.isImmutable(variable.getType()) && !CollectionUtils.isEmptyArray(variable)) {
        return false;
      }
      if (variable.hasModifierProperty(PsiModifier.FINAL)) {
        return true;
      }
      final PsiElement context = PsiUtil.getVariableCodeBlock(variable, referenceExpression);
      return context != null && !(variable instanceof PsiField) &&
             HighlightControlFlowUtil.isEffectivelyFinal(variable, context, referenceExpression);
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (!isMoveable(operand)) {
          return false;
        }
      }
      return true;
    }
    if (expression instanceof PsiMethodCallExpression) {
      if (!isAllowedType(expression.getType())) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (!isAllowedMethod(method)) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null && !isMoveable(qualifierExpression)) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      for (PsiExpression argument : argumentList.getExpressions()){
        if (!isMoveable(argument)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean isAllowedMethod(PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null || !qualifiedName.startsWith("java.")) {
      return false;
    }
    final String methodName = method.getName();
    return !"now".equals(methodName) && !"currentTimeMillis".equals(methodName) &&
           !"nanoTime".equals(methodName) && !"waitFor".equals(methodName);
  }

  private static boolean isAllowedType(PsiType type) {
    if (ClassUtils.isImmutable(type)) {
      return true;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return isAllowedClass(aClass);
  }

  private static boolean isAllowedClass(@Nullable PsiClass aClass) {
    // allow some "safe" jdk types
    if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION) ||
        InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP)) {
      return true;
    }
    return aClass != null && aClass.isEnum();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TooBroadScopeVisitor();
  }

  private class TooBroadScopeVisitor extends BaseInspectionVisitor {

    TooBroadScopeVisitor() {}

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      if (!(variable instanceof PsiLocalVariable) || variable instanceof PsiResourceVariable) {
        return;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (!isMoveable(initializer)) {
        return;
      }
      final PsiElement variableScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiForStatement.class);
      final List<PsiReferenceExpression> references = VariableAccessUtils.findReferences(variable, variableScope);
      if (references.isEmpty() || variableScope == null) {
        return;
      }
      PsiElement commonParent = ScopeUtils.getCommonParent(references);
      if (commonParent == null) {
        return;
      }
      if (initializer != null) {
        commonParent = ScopeUtils.moveOutOfLoopsAndClasses(commonParent, variableScope);
        if (commonParent == null) {
          return;
        }
      }
      if (PsiTreeUtil.isAncestor(commonParent, variableScope, true)) {
        return;
      }
      if (PsiTreeUtil.isAncestor(variableScope, commonParent, true)) {
        registerVariableError(variable, variable);
        return;
      }
      if (m_onlyLookAtBlocks) {
        return;
      }
      if (commonParent instanceof PsiForStatement) {
        return;
      }
      final PsiElement referenceElement = references.get(0);
      final PsiElement blockChild = ScopeUtils.getChildWhichContainsElement(variableScope, referenceElement);
      if (blockChild == null) {
        return;
      }
      final PsiElement insertionPoint = ScopeUtils.findTighterDeclarationLocation(blockChild, variable);
      if (insertionPoint == null) {
        if (!(blockChild instanceof PsiExpressionStatement)) {
          return;
        }
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)blockChild;
        final PsiExpression expression = expressionStatement.getExpression();
        if (!(expression instanceof PsiAssignmentExpression)) {
          return;
        }
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
        final IElementType tokenType = assignmentExpression.getOperationTokenType();
        if (tokenType != JavaTokenType.EQ) {
          return;
        }
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!lhs.equals(referenceElement)) {
          return;
        }
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if (rhs != null && VariableAccessUtils.variableIsUsed(variable, rhs)) {
          return;
        }
      }
      if (insertionPoint != null && FileTypeUtils.isInServerPageFile(insertionPoint)) {
        PsiElement elementBefore = insertionPoint.getPrevSibling();
        elementBefore = PsiTreeUtil.skipWhitespacesBackward(elementBefore);
        if (elementBefore instanceof PsiDeclarationStatement) {
          final PsiElement variableParent = variable.getParent();
          if (elementBefore.equals(variableParent)) {
            return;
          }
        }
      }
      registerVariableError(variable, variable);
    }
  }
}
