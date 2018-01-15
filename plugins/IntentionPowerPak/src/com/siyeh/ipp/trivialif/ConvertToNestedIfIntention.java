/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.trivialif;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public class ConvertToNestedIfIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {

      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiReturnStatement)) {
          return false;
        }
        final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
        final PsiExpression returnValue = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
        if (!(returnValue instanceof PsiPolyadicExpression)) {
          return false;
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)returnValue;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        return tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR;
      }
    };
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null || ErrorUtil.containsDeepError(returnValue)) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    final String newStatementText = buildIf(returnValue, true, tracker, new StringBuilder()).toString();
    final Project project = returnStatement.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiBlockStatement blockStatement = (PsiBlockStatement)elementFactory.createStatementFromText("{" + newStatementText + "}", returnStatement);
    final PsiElement parent = returnStatement.getParent();
    for (PsiStatement st : blockStatement.getCodeBlock().getStatements()) {
      CodeStyleManager.getInstance(project).reformat(parent.addBefore(st, returnStatement));
    }
    PsiReplacementUtil.replaceStatement(returnStatement, "return false;", tracker);
  }

  private static StringBuilder buildIf(@Nullable PsiExpression expression,
                                       boolean top,
                                       CommentTracker tracker,
                                       StringBuilder out) {
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          buildIf(operand, false, tracker, out);
        }
        if (top && !StringUtil.endsWith(out, "return true;")) {
          out.append("return true;");
        }
        return out;
      }
      else if (top && JavaTokenType.OROR.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          buildIf(operand, false, tracker, out);
          if (!StringUtil.endsWith(out, "return true;")) {
            out.append("return true;");
          }
        }
        return out;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      buildIf(parenthesizedExpression.getExpression(), top, tracker, out);
      return out;
    }
    if (expression != null) {
      out.append("if(").append(tracker.text(expression)).append(")");
    }
    return out;
  }
}
