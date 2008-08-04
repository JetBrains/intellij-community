package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.08.2008
 */
public class GrLiteralFixer implements GroovyFixer {
  public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement)
          throws IncorrectOperationException {

    if (psiElement.getNode().getElementType() == GroovyTokenTypes.mWRONG_STRING_LITERAL &&
            !StringUtil.endsWithChar(psiElement.getText(), '\'')) {
      editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\'");
    } else if ( psiElement.getNode().getElementType() == GroovyTokenTypes.mWRONG_GSTRING_LITERAL &&
            !StringUtil.endsWithChar(psiElement.getText(), '\"')) {
      editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"");
    } else if (psiElement instanceof GrString && !StringUtil.endsWithChar(psiElement.getText(), '\"')){
      editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"");
    }
  }
}
