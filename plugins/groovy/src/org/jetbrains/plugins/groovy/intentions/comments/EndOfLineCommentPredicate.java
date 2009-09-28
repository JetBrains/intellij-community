package org.jetbrains.plugins.groovy.intentions.comments;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

class EndOfLineCommentPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    if (element instanceof PsiDocComment) {
      return false;
    }
    final PsiComment comment = (PsiComment) element;
    final IElementType type = comment.getTokenType();
    return GroovyTokenTypes.mSL_COMMENT.equals(type);
  }
}
