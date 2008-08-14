package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.08.2008
 */
public class GrWhileBodyFixer implements GrFixer{
  public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof GrWhileStatement)) return;
    GrWhileStatement whileStatement = (GrWhileStatement) psiElement;

    final Document doc = editor.getDocument();

    PsiElement body = whileStatement.getBody();
    if (body instanceof GrBlockStatement) return;
    if (body != null && startLine(doc, body) == startLine(doc, whileStatement) && whileStatement.getCondition() != null) return;

    final PsiElement rParenth = whileStatement.getRParenth();
    assert rParenth != null;

    doc.insertString(rParenth.getTextRange().getEndOffset(), "{}");
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
