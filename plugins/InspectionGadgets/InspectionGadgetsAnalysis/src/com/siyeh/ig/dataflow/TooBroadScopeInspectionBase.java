/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.intellij.psi.util.FileTypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

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
    if (expression == null) {
      return true;
    }
    if (PsiUtil.isConstantExpression(expression) || ExpressionUtils.isNullLiteral(expression)) {
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
      boolean result = true;
      if (arrayInitializer != null) {
        final PsiExpression[] initializers = arrayInitializer.getInitializers();
        for (final PsiExpression initializerExpression : initializers) {
          result &= isMoveable(initializerExpression);
        }
      }
      else if (!m_allowConstructorAsInitializer) {
        final PsiType type = newExpression.getType();
        if (!ClassUtils.isImmutable(type)) {
          return false;
        }
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return result;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      for (final PsiExpression argumentExpression : expressions) {
        result &= isMoveable(argumentExpression);
      }
      return result;
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return false;
      }
      final PsiField field = (PsiField)target;
      if (ExpressionUtils.isConstant(field)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TooBroadScopeVisitor();
  }

  private class TooBroadScopeVisitor extends BaseInspectionVisitor {

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
      if (variableScope == null) {
        return;
      }
      final Query<PsiReference> query = ReferencesSearch.search(variable, variable.getUseScope());
      final Collection<PsiReference> referencesCollection = query.findAll();
      final int size = referencesCollection.size();
      if (size == 0) {
        return;
      }
      final PsiElement[] referenceElements = new PsiElement[referencesCollection.size()];
      int index = 0;
      for (PsiReference reference : referencesCollection) {
        final PsiElement referenceElement = reference.getElement();
        referenceElements[index] = referenceElement;
        index++;
      }
      PsiElement commonParent = ScopeUtils.getCommonParent(referenceElements);
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
      final PsiElement referenceElement = referenceElements[0];
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
        elementBefore = PsiTreeUtil.skipSiblingsBackward(elementBefore, PsiWhiteSpace.class);
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
