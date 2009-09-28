package org.jetbrains.plugins.groovy.intentions.comments;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.utils.TreeUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

class CStyleCommentPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    if (element instanceof PsiDocComment) {
      return false;
    }
    final PsiComment comment = (PsiComment) element;
    final IElementType type = comment.getTokenType();
    if (!GroovyTokenTypes.mML_COMMENT.equals(type)) {
      return false;
    }
    final PsiElement sibling = TreeUtil.getNextLeaf(comment);
    if(sibling == null)
    {
      return true;
    }
    if (!(isWhitespace(sibling))) {
      return false;
    }
    final String whitespaceText = sibling.getText();
    return whitespaceText.indexOf((int) '\n') >= 0 ||
        whitespaceText.indexOf((int) '\r') >= 0;
  }

  private static boolean isWhitespace(PsiElement element) {
    return element.getText().replace("\n", "").trim().length() == 0;
  }
}
