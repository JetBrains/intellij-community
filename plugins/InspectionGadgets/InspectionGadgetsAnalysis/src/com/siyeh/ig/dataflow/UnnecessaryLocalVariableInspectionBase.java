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
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryLocalVariableInspectionBase extends BaseInspection {
  /**
   * @noinspection PublicField, WeakerAccess
   */
  public boolean m_ignoreImmediatelyReturnedVariables;

  /**
   * @noinspection PublicField
   */
  @Deprecated
  public boolean m_ignoreAnnotatedVariables;
  @SuppressWarnings("WeakerAccess")
  public boolean m_ignoreAnnotatedVariablesNew = true;

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    defaultWriteSettings(node, "m_ignoreAnnotatedVariablesNew");
    writeBooleanOption(node, "m_ignoreAnnotatedVariablesNew", true);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("redundant.local.variable.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("redundant.local.variable.ignore.option"),
                             "m_ignoreImmediatelyReturnedVariables");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("redundant.local.variable.annotation.option"),
                             "m_ignoreAnnotatedVariablesNew");
    return optionsPanel;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.local.variable.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryLocalVariableVisitor();
  }

  private class UnnecessaryLocalVariableVisitor extends BaseInspectionVisitor {

    @SuppressWarnings("IfStatementWithIdenticalBranches")
    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (m_ignoreAnnotatedVariablesNew) {
        final PsiModifierList list = variable.getModifierList();
        if (list != null && list.getAnnotations().length > 0) {
          return;
        }
      }
      if (isCopyVariable(variable)) {
        registerVariableError(variable);
      }
      else if (!m_ignoreImmediatelyReturnedVariables && isImmediatelyReturned(variable)) {
        registerVariableError(variable);
      }
      else if (!m_ignoreImmediatelyReturnedVariables && isImmediatelyThrown(variable)) {
        registerVariableError(variable);
      }
      else if (isImmediatelyAssigned(variable)) {
        registerVariableError(variable);
      }
      else if (isImmediatelyAssignedAsDeclaration(variable)) {
        registerVariableError(variable);
      }
    }

    private boolean isCopyVariable(PsiVariable variable) {
      PsiExpression initializer = ParenthesesUtils.stripParentheses(variable.getInitializer());
      if (initializer instanceof PsiTypeCastExpression) {
        PsiExpression operand = ((PsiTypeCastExpression)initializer).getOperand();
        if (operand instanceof PsiReferenceExpression && RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)initializer)) {
          initializer = operand;
        }
      }
      if (!(initializer instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)initializer;
      final PsiVariable initialization = ObjectUtils.tryCast(reference.resolve(), PsiVariable.class);
      if (initialization == null) {
        return false;
      }
      if (!(initialization instanceof PsiResourceVariable) && variable instanceof PsiResourceVariable) {
        return false;
      }
      if (!(initialization instanceof PsiLocalVariable || initialization instanceof PsiParameter)) {
        if (!isFinalChain(reference) || ReferencesSearch.search(variable).findAll().size() != 1) {
          // only warn when variable is referenced once, to avoid warning when a field is cached in local variable
          // as in e.g. gnu.trove.TObjectHash#forEach()
          return false;
        }
      }
      final PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (containingScope == null) {
        return false;
      }
      if (variableMayChange(containingScope, null, variable)) {
        return false;
      }
      if (variableMayChange(containingScope, PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression()), initialization)) {
        return false;
      }

      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(containingScope.getProject()).getResolveHelper();
      final String initializationName = initialization.getName();
      if (initializationName == null) {
        return false;
      }

      final boolean finalVariableIntroduction =
        !initialization.hasModifierProperty(PsiModifier.FINAL) && variable.hasModifierProperty(PsiModifier.FINAL) ||
        PsiUtil.isLanguageLevel8OrHigher(initialization) &&
        !HighlightControlFlowUtil.isEffectivelyFinal(initialization, containingScope, null) &&
        HighlightControlFlowUtil.isEffectivelyFinal(variable, containingScope, null);

      final PsiType variableType = variable.getType();
      final PsiType initializationType = initialization.getType();
      final boolean sameType = Comparing.equal(variableType, initializationType);
      for (PsiReference ref : ReferencesSearch.search(variable, new LocalSearchScope(containingScope))) {
        final PsiElement refElement = ref.getElement();
        if (finalVariableIntroduction) {
          final PsiElement element = PsiTreeUtil.getParentOfType(refElement, PsiClass.class, PsiLambdaExpression.class);
          if (element != null && PsiTreeUtil.isAncestor(containingScope, element, true)) {
            return false;
          }
        }

        if (resolveHelper.resolveReferencedVariable(initializationName, refElement) != initialization) {
          return false;
        }

        if (!sameType) {
          final PsiElement parent = refElement.getParent();
          if (parent instanceof PsiReferenceExpression) {
            final PsiElement resolve = ((PsiReferenceExpression)parent).resolve();
            if (resolve instanceof PsiMember &&
                ((PsiMember)resolve).hasModifierProperty(PsiModifier.PRIVATE)) {
              return false;
            }
          }
        }
      }

      return !TypeConversionUtil.boxingConversionApplicable(variableType, initializationType);
    }

    private boolean isFinalChain(PsiReferenceExpression reference) {
      while (true) {
        PsiElement element = reference.resolve();
        if (!(element instanceof PsiField)) return true;
        if (!((PsiField)element).hasModifierProperty(PsiModifier.FINAL)) return false;
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression());
        if (qualifier == null || qualifier instanceof PsiThisExpression) return true;
        if (!(qualifier instanceof PsiReferenceExpression)) return false;
        reference = (PsiReferenceExpression)qualifier;
      }
    }

    private boolean variableMayChange(PsiCodeBlock containingScope, PsiExpression qualifier, PsiVariable variable) {
      while (variable != null) {
        if (!variable.hasModifierProperty(PsiModifier.FINAL) &&
            VariableAccessUtils.variableIsAssigned(variable, containingScope, false)) {
          return true;
        }
        if (!(qualifier instanceof PsiReferenceExpression)) break;
        PsiReferenceExpression qualifierReference = (PsiReferenceExpression)qualifier;
        qualifier = PsiUtil.skipParenthesizedExprDown(qualifierReference.getQualifierExpression());
        variable = ObjectUtils.tryCast(qualifierReference.resolve(), PsiVariable.class);
      }
      return false;
    }

    private boolean isImmediatelyReturned(PsiVariable variable) {
      final PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
      if (containingScope == null) {
        return false;
      }
      final PsiElement parent = variable.getParent();
      if (!(parent instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)parent;
      final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(declarationStatement, PsiStatement.class);
      if (!(nextStatement instanceof PsiReturnStatement)) {
        return false;
      }
      final PsiReturnStatement returnStatement = (PsiReturnStatement)nextStatement;
      final PsiExpression returnValue = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
      if (!(returnValue instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)returnValue;
      final PsiElement referent = referenceExpression.resolve();
      if (referent == null || !referent.equals(variable)) {
        return false;
      }
      return !isVariableUsedInFollowingDeclarations(variable, declarationStatement);
    }

    private boolean isImmediatelyThrown(PsiVariable variable) {
      final PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
      if (containingScope == null) {
        return false;
      }
      final PsiElement parent = variable.getParent();
      if (!(parent instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)parent;
      final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(declarationStatement, PsiStatement.class);
      if (!(nextStatement instanceof PsiThrowStatement)) {
        return false;
      }
      final PsiThrowStatement throwStatement = (PsiThrowStatement)nextStatement;
      final PsiExpression returnValue = ParenthesesUtils.stripParentheses(throwStatement.getException());
      if (!(returnValue instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiElement referent = ((PsiReference)returnValue).resolve();
      if (referent == null || !referent.equals(variable)) {
        return false;
      }
      return !isVariableUsedInFollowingDeclarations(variable, declarationStatement);
    }

    private boolean isImmediatelyAssigned(PsiVariable variable) {
      final PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
      if (containingScope == null) {
        return false;
      }
      final PsiElement parent = variable.getParent();
      if (!(parent instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)parent;
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(declarationStatement, PsiStatement.class);
      if (!(nextStatement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)nextStatement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression)) {
        return false;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      if (tokenType != JavaTokenType.EQ) {
        return false;
      }
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(assignmentExpression.getRExpression());
      if (!(rhs instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)rhs;
      final PsiElement referent = reference.resolve();
      if (referent == null || !referent.equals(variable)) {
        return false;
      }
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (lhs instanceof PsiArrayAccessExpression) {
        return false;
      }
      if (isVariableUsedInFollowingDeclarations(variable, declarationStatement)) {
        return false;
      }
      nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      while (nextStatement != null) {
        if (VariableAccessUtils.variableIsUsed(variable, nextStatement)) {
          return false;
        }
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      }
      return true;
    }

    private boolean isImmediatelyAssignedAsDeclaration(PsiVariable variable) {
      final PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
      if (containingScope == null) {
        return false;
      }
      final PsiElement parent = variable.getParent();
      if (!(parent instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)parent;
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(declarationStatement, PsiStatement.class);
      if (nextStatement instanceof PsiDeclarationStatement) {
        boolean referenceFound = false;
        final PsiDeclarationStatement nextDeclarationStatement = (PsiDeclarationStatement)nextStatement;
        for (PsiElement declaration : nextDeclarationStatement.getDeclaredElements()) {
          if (!(declaration instanceof PsiVariable)) {
            continue;
          }
          final PsiVariable nextVariable = (PsiVariable)declaration;
          final PsiExpression initializer = ParenthesesUtils.stripParentheses(nextVariable.getInitializer());
          if (!referenceFound && initializer instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)initializer;
            final PsiElement referent = referenceExpression.resolve();
            if (variable.equals(referent)) {
              referenceFound = true;
              continue;
            }
          }
          if (VariableAccessUtils.variableIsUsed(variable, initializer)) {
            return false;
          }
        }
        if (!referenceFound) {
          return false;
        }
      }
      else if (nextStatement instanceof PsiTryStatement) {
        final PsiTryStatement tryStatement = (PsiTryStatement)nextStatement;
        final PsiResourceList resourceList = tryStatement.getResourceList();
        if (resourceList == null) {
          return false;
        }
        boolean referenceFound = false;
        for (PsiResourceListElement resource : resourceList) {
          if (resource instanceof PsiResourceVariable) {
            final PsiExpression initializer = ((PsiResourceVariable)resource).getInitializer();
            if (!referenceFound && initializer instanceof PsiReferenceExpression) {
              final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)initializer;
              final PsiElement referent = referenceExpression.resolve();
              if (variable.equals(referent)) {
                referenceFound = true;
                continue;
              }
            }
            if (VariableAccessUtils.variableIsUsed(variable, initializer)) {
              return false;
            }
          }
        }
        if (!referenceFound) {
          return false;
        }
        if (VariableAccessUtils.variableIsUsed(variable, tryStatement.getTryBlock()) ||
            VariableAccessUtils.variableIsUsed(variable, tryStatement.getFinallyBlock())) {
          return false;
        }
        for (PsiCatchSection section : tryStatement.getCatchSections()) {
          if (VariableAccessUtils.variableIsUsed(variable, section)) {
            return false;
          }
        }
      }
      else {
        return false;
      }
      if (isVariableUsedInFollowingDeclarations(variable, declarationStatement)) {
        return false;
      }
      nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      while (nextStatement != null) {
        if (VariableAccessUtils.variableIsUsed(variable, nextStatement)) {
          return false;
        }
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      }
      return true;
    }

    private boolean isVariableUsedInFollowingDeclarations(PsiVariable variable, PsiDeclarationStatement declarationStatement) {
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      if (declaredElements.length == 1) {
        return false;
      }
      boolean check = false;
      for (PsiElement declaredElement : declaredElements) {
        if (!check && variable.equals(declaredElement)) {
          check = true;
        } else {
          if (VariableAccessUtils.variableIsUsed(variable, declaredElement)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}