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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InstanceofChainInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInstanceofOnLibraryClasses = false;

  @Override
  @NotNull
  public String getID() {
    return "ChainOfInstanceofChecks";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("chain.of.instanceof.checks.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("chain.of.instanceof.checks.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("ignore.instanceof.on.library.classes"), this,
                                          "ignoreInstanceofOnLibraryClasses");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofChainVisitor();
  }

  private class InstanceofChainVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
      super.visitIfStatement(ifStatement);
      final PsiElement parent = ifStatement.getParent();
      if (parent instanceof PsiIfStatement) {
        final PsiIfStatement parentStatement = (PsiIfStatement)parent;
        final PsiStatement elseBranch = parentStatement.getElseBranch();
        if (ifStatement.equals(elseBranch)) {
          return;
        }
      }
      final PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(ifStatement, PsiStatement.class);
      if (previousStatement instanceof PsiIfStatement) {
        final PsiIfStatement previousIfStatement = (PsiIfStatement)previousStatement;
        if (isInstanceofCheck(previousIfStatement.getCondition())) {
          return;
        }
      }
      int numChecks = 0;
      PsiIfStatement branch = ifStatement;
      while (true) {
        final PsiExpression condition = branch.getCondition();
        if (!isInstanceofCheck(condition)) {
          if (numChecks > 1) {
            break;
          }
          return;
        }
        numChecks++;
        final PsiStatement elseBranch = branch.getElseBranch();
        if (elseBranch instanceof PsiIfStatement) {
          branch = (PsiIfStatement)elseBranch;
        }
        else if (elseBranch == null) {
          final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(branch, PsiStatement.class);
          if (!(nextStatement instanceof PsiIfStatement)) {
            break;
          }
          branch = (PsiIfStatement)nextStatement;
        }
        else {
          break;
        }
      }
      if (numChecks < 2) {
        return;
      }
      registerStatementError(ifStatement);
    }

    private boolean isInstanceofCheck(PsiExpression condition) {
      while (true) {
        if (condition == null) {
          return false;
        }
        else if (condition instanceof PsiInstanceOfExpression) {
          if (ignoreInstanceofOnLibraryClasses) {
            final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)condition;
            if (isInstanceofOnLibraryClass(instanceOfExpression)) {
              return false;
            }
          }
          return true;
        }
        else if (condition instanceof PsiPolyadicExpression) {
          final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
          final PsiExpression[] operands = polyadicExpression.getOperands();
          for (PsiExpression operand : operands) {
            if (!isInstanceofCheck(operand)) {
              return false;
            }
          }
          return true;
        }
        else if (condition instanceof PsiParenthesizedExpression) {
          final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)condition;
          condition = parenthesizedExpression.getExpression();
          continue;
        }
        else if (condition instanceof PsiPrefixExpression) {
          final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)condition;
          condition = prefixExpression.getOperand();
          continue;
        }
        else if (condition instanceof PsiPostfixExpression) {
          final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)condition;
          condition = postfixExpression.getOperand();
          continue;
        }
        return false;
      }
    }

    private boolean isInstanceofOnLibraryClass(PsiInstanceOfExpression instanceOfExpression) {
      final PsiTypeElement checkType = instanceOfExpression.getCheckType();
      if (checkType == null) {
        return false;
      }
      final PsiType type = checkType.getType();
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      return LibraryUtil.classIsInLibrary(aClass);
    }
  }
}