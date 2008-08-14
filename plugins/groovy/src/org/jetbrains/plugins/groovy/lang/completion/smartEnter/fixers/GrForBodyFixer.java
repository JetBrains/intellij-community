package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.08.2008
 */
public class GrForBodyFixer implements GrFixer{
   public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    GrForStatement forStatement = getForStatementParent(psiElement);
    if (forStatement == null) return;

    final Document doc = editor.getDocument();

    PsiElement body = forStatement.getBody();
    if (body instanceof GrBlockStatement) return;
    if (body != null && startLine(doc, body) == startLine(doc, forStatement)) return;

    PsiElement eltToInsertAfter = forStatement.getRParenth();
    String text = "{}";
    if (eltToInsertAfter == null) {
      eltToInsertAfter = forStatement;
      text = "){}";
    }
    doc.insertString(eltToInsertAfter.getTextRange().getEndOffset(), text);
  }

  @Nullable
  private static GrForStatement getForStatementParent(PsiElement psiElement) {
    GrForStatement statement = PsiTreeUtil.getParentOfType(psiElement, GrForStatement.class);
    if (statement == null) return null;

    //GrStatement init = statement.getInitialization();
    //GrStatement update = statement.getUpdate();
    //GrExpression check = statement.getCondition();
    //
    //return isValidChild(init, psiElement) || isValidChild(update, psiElement) || isValidChild(check, psiElement) ? statement : null;
    return statement;
  }

  private static boolean isValidChild(PsiElement ancestor, PsiElement psiElement) {
    if (ancestor != null) {
      if (PsiTreeUtil.isAncestor(ancestor, psiElement, false)) {
        if (PsiTreeUtil.hasErrorElements(ancestor)) return false;
        return true;
      }
    }

    return false;
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
