package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers.GrFixer;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.07.2008
 */
public class GrMissingIfStatement implements GrFixer {
  public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof GrIfStatement)) return;

    GrIfStatement ifStatement = (GrIfStatement) psiElement;
    final Document document = editor.getDocument();
    final GrStatement elseBranch = ifStatement.getElseBranch();
    final PsiElement elseElement = ifStatement.getElseKeyword();

    if (elseElement != null && (elseBranch == null || !(elseBranch instanceof GrBlockStatement) &&
            startLine(document, elseBranch) > startLine(document, elseElement))) {
      document.insertString(elseElement.getTextRange().getEndOffset(), "{}");
    }

    GrStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch instanceof GrBlockStatement) return;

    boolean transformingOneLiner = false;
    if (thenBranch != null && startLine(document, thenBranch) == startLine(document, ifStatement)) {
      if (ifStatement.getCondition() != null) {
        return;
      }
      transformingOneLiner = true;
    }

    final PsiElement rParenth = ifStatement.getRParenth();
    if (rParenth == null) return;

    if (elseBranch == null && !transformingOneLiner || thenBranch == null) {
      document.insertString(rParenth.getTextRange().getEndOffset(), "{}");
    } else {
      document.insertString(rParenth.getTextRange().getEndOffset(), "{");
      document.insertString(thenBranch.getTextRange().getEndOffset() + 1, "}");
    }
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
