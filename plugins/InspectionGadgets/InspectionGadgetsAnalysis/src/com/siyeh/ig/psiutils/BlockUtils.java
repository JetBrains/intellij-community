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
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    PsiStatement oldStatement = anchor;
    PsiElement parent = oldStatement.getParent();
    while (parent instanceof PsiLabeledStatement) {
      oldStatement = (PsiStatement)parent;
      parent = oldStatement.getParent();
    }
    if (newStatements.length == 1 && oldStatement instanceof PsiEmptyStatement) {
      return (PsiStatement)oldStatement.replace(newStatements[0]);
    }
    if (!(parent instanceof PsiCodeBlock)) {
      final PsiBlockStatement block = expandSingleStatementToBlockStatement(oldStatement);
      final PsiCodeBlock codeBlock = (PsiCodeBlock)block.getFirstChild();
      parent = codeBlock;
      oldStatement = codeBlock.getStatements()[0];
    }
    PsiElement result = null;
    for (PsiStatement statement : newStatements) {
      result = parent.addBefore(statement, oldStatement);
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
    PsiStatement oldStatement = anchor;
    PsiElement parent = oldStatement.getParent();
    while (parent instanceof PsiLabeledStatement) {
      oldStatement = (PsiStatement)parent;
      parent = oldStatement.getParent();
    }
    if (!(parent instanceof PsiCodeBlock)) {
      final PsiBlockStatement block = expandSingleStatementToBlockStatement(oldStatement);
      final PsiCodeBlock codeBlock = (PsiCodeBlock)block.getFirstChild();
      parent = codeBlock;
      oldStatement = codeBlock.getStatements()[0];
    }
    return (PsiStatement)parent.addAfter(newStatement, oldStatement);
  }

  public static PsiBlockStatement expandSingleStatementToBlockStatement(@NotNull PsiStatement body) {
    if (body instanceof PsiBlockStatement) {
      return (PsiBlockStatement)body;
    }
    final PsiBlockStatement blockStatement = (PsiBlockStatement)
      JavaPsiFacade.getElementFactory(body.getProject()).createStatementFromText("{}", body);
    if (!(body instanceof PsiEmptyStatement)) {
      blockStatement.getFirstChild().add(body);
    }
    final PsiBlockStatement result = (PsiBlockStatement)body.replace(blockStatement);
    final PsiElement sibling = result.getNextSibling();
    if (sibling instanceof PsiWhiteSpace && PsiUtil.isJavaToken(sibling.getNextSibling(), JavaTokenType.ELSE_KEYWORD)) {
      sibling.delete();
    }
    return result;
  }

  @Nullable
  public static PsiElement getBody(PsiElement element) {
    if (element instanceof PsiLoopStatement) {
      final PsiStatement loopBody = ((PsiLoopStatement)element).getBody();
      return loopBody instanceof PsiBlockStatement ? ((PsiBlockStatement)loopBody).getCodeBlock() : loopBody;
    }
    else if (element instanceof PsiParameterListOwner) {
      return ((PsiParameterListOwner)element).getBody();
    }
    else if (element instanceof PsiSynchronizedStatement) {
      return ((PsiSynchronizedStatement)element).getBody();
    }
    else if (element instanceof PsiSwitchStatement) {
      return ((PsiSwitchStatement)element).getBody();
    }
    else if (element instanceof PsiClassInitializer) {
      return ((PsiClassInitializer)element).getBody();
    }
    else if (element instanceof PsiCatchSection) {
      return ((PsiCatchSection)element).getCatchBlock();
    }
    throw new AssertionError("can't get body from " + element);
  }
}
