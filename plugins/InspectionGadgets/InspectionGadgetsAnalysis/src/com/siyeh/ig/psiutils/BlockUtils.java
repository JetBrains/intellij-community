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
   * Add new statement before given anchor statement creating code block, if necessary
   *
   * @param anchor existing statement
   * @param newStatement a new statement which should be added before an existing one
   * @return added physical statement
   */
  public static PsiStatement addBefore(PsiStatement anchor, PsiStatement newStatement) {
    PsiElement oldStatement = anchor;
    PsiElement parent = oldStatement.getParent();
    while (parent instanceof PsiLabeledStatement) {
      oldStatement = parent;
      parent = oldStatement.getParent();
    }
    final PsiElement result;
    if (parent instanceof PsiCodeBlock) {
      result = parent.addBefore(newStatement, oldStatement);
    }
    else {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
      final PsiBlockStatement newBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", oldStatement);
      final PsiElement codeBlock = newBlockStatement.getCodeBlock();
      codeBlock.add(newStatement);
      codeBlock.add(oldStatement);
      result = ((PsiBlockStatement)oldStatement.replace(newBlockStatement)).getCodeBlock().getStatements()[0];
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
