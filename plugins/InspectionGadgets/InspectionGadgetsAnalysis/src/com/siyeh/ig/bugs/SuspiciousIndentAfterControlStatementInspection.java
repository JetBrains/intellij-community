// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.util.Key;
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

    private static final Key<Integer> TAB_SIZE = new Key<>("TAB_SIZE");

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

    private boolean isWhitespaceSuspicious(PsiStatement statement, PsiStatement body) {
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
        final int lineBreakIndex = text.lastIndexOf('\n');
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
      final int index = text.lastIndexOf('\n');
      if (index < 0) {
        return false;
      }
      final PsiElement nextSibling = nextStatement.getPrevSibling();
      if (!(nextSibling instanceof PsiWhiteSpace)) {
        return false;
      }
      final String nextText = nextSibling.getText();
      final int nextIndex = nextText.lastIndexOf('\n');
      if (nextIndex < 0) {
        return false;
      }
      final int nextIndentValue = getIndent(nextText.substring(nextIndex + 1));
      final int indentValue = getIndent(text.substring(index + 1));
      if (lineBreakBeforeBody) {
        return nextIndentValue == indentValue;
      }
      else {
        return nextIndentValue > indentValue;
      }
    }

    private int getIndent(String indent) {
      int result = 0;
      for (int i = 0, length = indent.length(); i < length; i++) {
        final char c = indent.charAt(i);
        if (c == ' ') result++;
        else if (c == '\t') result += getTabSize();
        else throw new AssertionError();
      }
      return result;
    }

    private int getTabSize() {
      final PsiFile file = getCurrentFile();
      final Integer tabSize = file.getUserData(TAB_SIZE);
      return tabSize != null ? tabSize : CodeStyle.getIndentOptions(file).TAB_SIZE;
    }
  }
}
