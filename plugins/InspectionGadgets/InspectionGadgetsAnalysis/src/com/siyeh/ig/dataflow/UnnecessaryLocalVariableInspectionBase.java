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

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Field;

public class UnnecessaryLocalVariableInspectionBase extends BaseInspection {
  private static final String VARIABLES_NEW = "m_ignoreAnnotatedVariablesNew";
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreImmediatelyReturnedVariables = false;

  @Deprecated
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreAnnotatedVariables = false;
  public boolean m_ignoreAnnotatedVariablesNew = true;

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, node, new DefaultJDOMExternalizer.JDOMFilter() {
      @Override
      public boolean isAccept(@NotNull Field field) {
        return !Comparing.equal(VARIABLES_NEW, field.getName());
      }
    });

    if (!m_ignoreAnnotatedVariablesNew) {
      final Element option = new Element("option");
      option.setAttribute("name", VARIABLES_NEW);
      option.setAttribute("value", Boolean.toString(m_ignoreAnnotatedVariablesNew));
      node.addContent(option);
    }
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

    @SuppressWarnings({"IfStatementWithIdenticalBranches"})
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
      final PsiExpression initializer = ParenthesesUtils.stripParentheses(variable.getInitializer());
      if (!(initializer instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)initializer;
      final PsiElement referent = reference.resolve();
      if (referent == null) {
        return false;
      }
      if (!(referent instanceof PsiLocalVariable || referent instanceof PsiParameter)) {
        return false;
      }
      if (!(referent instanceof PsiResourceVariable) && variable instanceof PsiResourceVariable) {
        return false;
      }
      final PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (containingScope == null) {
        return false;
      }
      if (!variable.hasModifierProperty(PsiModifier.FINAL) &&
          VariableAccessUtils.variableIsAssigned(variable, containingScope, false)) {
        return false;
      }
      final PsiVariable initialization = (PsiVariable)referent;
      if (!initialization.hasModifierProperty(PsiModifier.FINAL) &&
          VariableAccessUtils.variableIsAssigned(initialization, containingScope, false)) {
        return false;
      }

      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(containingScope.getProject()).getResolveHelper();
      final String initializationName = initialization.getName();

      final boolean finalVariableIntroduction = 
        !initialization.hasModifierProperty(PsiModifier.FINAL) && variable.hasModifierProperty(PsiModifier.FINAL) ||
        PsiUtil.isLanguageLevel8OrHigher(initialization) &&
        !HighlightControlFlowUtil.isEffectivelyFinal(initialization, containingScope, null) && 
        HighlightControlFlowUtil.isEffectivelyFinal(variable, containingScope, null);

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
      }

      return !TypeConversionUtil.boxingConversionApplicable(variable.getType(), initialization.getType());
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
      PsiStatement nextStatement = null;
      final PsiStatement[] statements = containingScope.getStatements();
      for (int i = 0; i < (statements.length - 1); i++) {
        if (statements[i].equals(declarationStatement)) {
          nextStatement = statements[i + 1];
          break;
        }
      }
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
      PsiStatement nextStatement = null;
      final PsiStatement[] statements = containingScope.getStatements();
      for (int i = 0; i < (statements.length - 1); i++) {
        if (statements[i].equals(declarationStatement)) {
          nextStatement = statements[i + 1];
          break;
        }
      }
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
      PsiStatement nextStatement = null;
      int followingStatementNumber = 0;
      final PsiStatement[] statements = containingScope.getStatements();
      for (int i = 0; i < (statements.length - 1); i++) {
        if (statements[i].equals(declarationStatement)) {
          nextStatement = statements[i + 1];
          followingStatementNumber = i + 2;
          break;
        }
      }
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
      for (int i = followingStatementNumber; i < statements.length; i++) {
        if (VariableAccessUtils.variableIsUsed(variable, statements[i])) {
          return false;
        }
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
      PsiStatement nextStatement = null;
      int followingStatementNumber = 0;
      final PsiStatement[] statements = containingScope.getStatements();
      for (int i = 0; i < (statements.length - 1); i++) {
        if (statements[i].equals(declarationStatement)) {
          nextStatement = statements[i + 1];
          followingStatementNumber = i + 2;
          break;
        }
      }
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
      for (int i = followingStatementNumber; i < statements.length; i++) {
        if (VariableAccessUtils.variableIsUsed(variable, statements[i])) {
          return false;
        }
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