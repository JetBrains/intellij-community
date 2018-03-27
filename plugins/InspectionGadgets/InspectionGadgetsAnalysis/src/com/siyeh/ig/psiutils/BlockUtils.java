/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      oldStatement = expandSingleStatementToBlockStatement(oldStatement);
      parent = oldStatement.getParent();
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
      oldStatement = expandSingleStatementToBlockStatement(oldStatement);
      parent = oldStatement.getParent();
    }
    return (PsiStatement)parent.addAfter(newStatement, oldStatement);
  }

  /**
   * Replaces a statement with a block statement with the statement inside it. As usual the block statement contains a code block, which
   * will contain the specified statement.
   *
   * @param statement  the statement to replace
   * @return a new statement equivalent to the argument, but inside a block
   */
  public static <T extends PsiStatement> T expandSingleStatementToBlockStatement(@NotNull T statement) {
    if (statement instanceof PsiBlockStatement) {
      return statement;
    }
    final PsiBlockStatement blockStatement = (PsiBlockStatement)
      JavaPsiFacade.getElementFactory(statement.getProject()).createStatementFromText("{}", statement);
    blockStatement.getCodeBlock().add(statement);
    final PsiBlockStatement result = (PsiBlockStatement)statement.replace(blockStatement);

    final PsiElement sibling = result.getNextSibling();
    if (sibling instanceof PsiWhiteSpace && PsiUtil.isJavaToken(sibling.getNextSibling(), JavaTokenType.ELSE_KEYWORD)) {
      sibling.delete();
    }
    //noinspection unchecked
    return (T)result.getCodeBlock().getStatements()[0];
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
