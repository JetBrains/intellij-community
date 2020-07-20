// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class SuspiciousIndentAfterControlStatementInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("suspicious.indent.after.control.statement.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousIndentAfterControlStatementVisitor();
  }

  private static class SuspiciousIndentAfterControlStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      super.visitForStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement elseStatement = statement.getElseBranch();
      if (elseStatement instanceof PsiBlockStatement || elseStatement instanceof PsiIfStatement) {
        return;
      }
      else if (elseStatement == null) {
        final PsiStatement thenStatement = statement.getThenBranch();
        if (thenStatement instanceof PsiBlockStatement || thenStatement == null || !isWhitespaceSuspicious(statement, thenStatement)) {
          return;
        }
      }
      else {
        if (!isWhitespaceSuspicious(statement, elseStatement)) {
          return;
        }
      }
      final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      if (nextStatement == null) {
        return;
      }
      registerStatementError(nextStatement);
    }

    private void checkLoopStatement(PsiLoopStatement statement) {
      final PsiStatement body = statement.getBody();
      if (body instanceof PsiBlockStatement || body == null) {
        return;
      }
      if (!isWhitespaceSuspicious(statement, body)) {
        return;
      }
      final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      if (nextStatement == null) {
        return;
      }
      registerStatementError(nextStatement);
    }

    private static boolean isWhitespaceSuspicious(PsiStatement statement, PsiStatement body) {
      final boolean lineBreakBeforeBody;
      PsiElement prevSibling = body.getPrevSibling();
      if (!(prevSibling instanceof PsiWhiteSpace)) {
        lineBreakBeforeBody = false;
        prevSibling = statement.getPrevSibling();
        if (!(prevSibling instanceof PsiWhiteSpace)) {
          return false;
        }
      }
      else {
        final String text = prevSibling.getText();
        final int lineBreakIndex = getLineBreakIndex(text);
        if (lineBreakIndex < 0) {
          lineBreakBeforeBody = false;
          prevSibling = statement.getPrevSibling();
          if (!(prevSibling instanceof PsiWhiteSpace)) {
            return false;
          }
        }
        else {
          lineBreakBeforeBody = true;
        }
      }
      final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      if (nextStatement == null) {
        return false;
      }
      final String text = prevSibling.getText();
      final int index = getLineBreakIndex(text);
      if (index < 0) {
        return false;
      }
      final PsiElement nextSibling = nextStatement.getPrevSibling();
      if (!(nextSibling instanceof PsiWhiteSpace)) {
        return false;
      }
      final String nextText = nextSibling.getText();
      final int nextIndex = getLineBreakIndex(nextText);
      if (nextIndex < 0) {
        return false;
      }
      final String nextIndent = nextText.substring(nextIndex + 1);
      final String indent = text.substring(index + 1);
      if (lineBreakBeforeBody) {
        return indent.equals(nextIndent);
      }
      else {
        return !indent.equals(nextIndent);
      }
    }

    private static int getLineBreakIndex(String text) {
      final int newLineIndex1 = text.lastIndexOf('\n');
      final int carriageReturnIndex1 = text.lastIndexOf('\r');
      return Math.max(newLineIndex1, carriageReturnIndex1);
    }
  }
}
