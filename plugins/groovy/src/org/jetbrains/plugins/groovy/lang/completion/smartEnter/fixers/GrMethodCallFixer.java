package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

/**
 * User: Dmitry.Krasilschikov
 * Date: 05.08.2008
 */

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;

public class GrMethodCallFixer implements GroovyFixer {
  public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    GrArgumentList args = null;
    if (psiElement instanceof GrMethodCallExpression) {
      args = ((GrMethodCallExpression) psiElement).getArgumentList();
    } else if (psiElement instanceof PsiNewExpression) {
      args = ((GrNewExpression) psiElement).getArgumentList();
    }

    if (args == null) return;

    PsiElement parenth = args.getLastChild();

    if (parenth == null || !")".equals(parenth.getText())) {
      int endOffset = -1;
      PsiElement child = args.getFirstChild();
      while (child != null) {
        if (child instanceof PsiErrorElement) {
          final PsiErrorElement errorElement = (PsiErrorElement) child;
          if (errorElement.getErrorDescription().indexOf("')'") >= 0) {
            endOffset = errorElement.getTextRange().getStartOffset();
            break;
          }
        }
        child = child.getNextSibling();
      }

      if (endOffset == -1) {
        endOffset = args.getTextRange().getEndOffset();
      }

      final GrExpression[] params = args.getExpressionArguments();
      if (params.length > 0 && startLine(editor, args) != startLine(editor, params[0])) {
        endOffset = args.getTextRange().getStartOffset() + 1;
      }

      endOffset = CharArrayUtil.shiftBackward(editor.getDocument().getCharsSequence(), endOffset - 1, " \t\n") + 1;
      editor.getDocument().insertString(endOffset, ")");
    }
  }

  private int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}

