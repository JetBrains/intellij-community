/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

/**
 * @author Tagir Valeev
 */
public class BlockUtils {

  /**
   * Adds new statements before given anchor statement creating a new code block, if necessary
   *
   * @param anchor  existing statement
   * @param newStatements  the new statements which should be added before the existing one
   * @return last added physical statement
   */
  public static PsiStatement addBefore(PsiStatement anchor, PsiStatement... newStatements) {
    if (newStatements.length == 0) throw new IllegalArgumentException();
    PsiElement oldStatement = anchor;
    PsiElement parent = oldStatement.getParent();
    while (parent instanceof PsiLabeledStatement) {
      oldStatement = parent;
      parent = oldStatement.getParent();
    }
    if (newStatements.length == 1 && oldStatement instanceof PsiEmptyStatement) {
      return (PsiStatement)oldStatement.replace(newStatements[0]);
    }
    PsiElement result = null;
    if (parent instanceof PsiCodeBlock) {
      for (PsiStatement statement : newStatements) {
        result = parent.addBefore(statement, oldStatement);
      }
    }
    else {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
      final PsiBlockStatement newBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", oldStatement);
      final PsiElement codeBlock = newBlockStatement.getCodeBlock();
      for (PsiStatement newStatement : newStatements) {
        codeBlock.add(newStatement);
      }
      codeBlock.add(oldStatement);
      final PsiStatement[] statements = ((PsiBlockStatement)oldStatement.replace(newBlockStatement)).getCodeBlock().getStatements();
      result = statements[statements.length - 2];
    }
    return (PsiStatement)result;
  }

  /**
   * Add new statement after given anchor statement creating code block, if necessary
   *
   * @param anchor existing statement
   * @param newStatement a new statement which should be added after an existing one
   * @return added physical statement
   */
  public static PsiStatement addAfter(PsiStatement anchor, PsiStatement newStatement) {
    PsiElement oldStatement = anchor;
    PsiElement parent = oldStatement.getParent();
    while (parent instanceof PsiLabeledStatement) {
      oldStatement = parent;
      parent = oldStatement.getParent();
    }
    final PsiElement result;
    if (parent instanceof PsiCodeBlock) {
      result = parent.addAfter(newStatement, oldStatement);
    }
    else {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
      final PsiBlockStatement newBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", oldStatement);
      final PsiElement codeBlock = newBlockStatement.getCodeBlock();
      codeBlock.add(oldStatement);
      codeBlock.add(newStatement);
      result = ((PsiBlockStatement)oldStatement.replace(newBlockStatement)).getCodeBlock().getStatements()[1];
    }
    return (PsiStatement)result;
  }
}
