/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class MergeNestedTryStatementsIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new NestedTryStatementsPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiTryStatement tryStatement1 = (PsiTryStatement)element.getParent();
    final StringBuilder newTryStatement = new StringBuilder("try (");
    final PsiResourceList list1 = tryStatement1.getResourceList();
    boolean semicolon = false;
    if (list1 != null) {
      final List<PsiResourceVariable> variables1 = list1.getResourceVariables();
      for (PsiResourceVariable variable : variables1) {
        if (semicolon) {
          newTryStatement.append(';');
        }
        else {
          semicolon = true;
        }
        newTryStatement.append(variable.getText());
      }
    }
    final PsiCodeBlock tryBlock1 = tryStatement1.getTryBlock();
    if (tryBlock1 == null) {
      return;
    }
    final PsiStatement[] statements = tryBlock1.getStatements();
    final PsiTryStatement tryStatement2 = (PsiTryStatement)statements[0];
    final PsiResourceList list2 = tryStatement2.getResourceList();
    if (list2 == null) {
      return;
    }
    final List<PsiResourceVariable> variables2 = list2.getResourceVariables();
    for (PsiResourceVariable variable : variables2) {
      if (semicolon) {
        newTryStatement.append(';');
      }
      else {
        semicolon = true;
      }
      newTryStatement.append(variable.getText());
    }
    newTryStatement.append(")");
    final PsiCodeBlock tryBlock2 = tryStatement2.getTryBlock();
    if (tryBlock2 == null) {
      return;
    }
    newTryStatement.append(tryBlock2.getText());
    final PsiCatchSection[] catchSections = tryStatement1.getCatchSections();
    for (PsiCatchSection section : catchSections) {
      newTryStatement.append(section.getText());
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiStatement newStatement = factory.createStatementFromText(newTryStatement.toString(), element);
    tryStatement1.replace(newStatement);
  }
}
