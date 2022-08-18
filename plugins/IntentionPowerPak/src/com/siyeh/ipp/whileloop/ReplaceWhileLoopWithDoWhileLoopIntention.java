// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.whileloop;

import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceWhileLoopWithDoWhileLoopIntention extends Intention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.while.loop.with.do.while.loop.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return IntentionPowerPackBundle.message("replace.while.loop.with.do.while.loop.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new WhileLoopPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiWhileStatement whileStatement = (PsiWhileStatement)element.getParent();
    if (whileStatement == null) {
      return;
    }
    final PsiStatement body = whileStatement.getBody();
    final PsiExpression condition = whileStatement.getCondition();
    final boolean infiniteLoop = BoolUtils.isTrue(condition);
    @NonNls final StringBuilder doWhileStatementText = new StringBuilder();
    CommentTracker tracker = new CommentTracker();
    if (!infiniteLoop) {
      doWhileStatementText.append("if(");
      if (condition != null) {
        doWhileStatementText.append(tracker.text(condition));
      }
      doWhileStatementText.append(") {\n");
    }
    if (body instanceof PsiBlockStatement) {
      doWhileStatementText.append("do {");
      final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        for (int i = 1; i < children.length - 1; i++) {
          final PsiElement child = children[i];
          doWhileStatementText.append(tracker.text(child));
        }
      }
      doWhileStatementText.append('}');
    }
    else if (body != null) {
      doWhileStatementText.append("do ").append(tracker.text(body)).append('\n');
    }
    doWhileStatementText.append("while(");
    if (condition != null) {
      doWhileStatementText.append(tracker.text(condition));
    }
    doWhileStatementText.append(");");
    if (!infiniteLoop) {
      doWhileStatementText.append("\n}");
    }
    PsiReplacementUtil.replaceStatement(whileStatement, doWhileStatementText.toString(), tracker);
  }
}
