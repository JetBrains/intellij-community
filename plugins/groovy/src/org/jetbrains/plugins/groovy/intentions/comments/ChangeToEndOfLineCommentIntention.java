package org.jetbrains.plugins.groovy.intentions.comments;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;

public class ChangeToEndOfLineCommentIntention extends Intention {

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new CStyleCommentPredicate();
  }

  public void processIntention(PsiElement element)
      throws IncorrectOperationException {
    final PsiComment comment = (PsiComment) element;
    final PsiManager manager = comment.getManager();
    final PsiElement parent = comment.getParent();
    assert parent != null;
    final PsiElementFactory factory = manager.getElementFactory();
    final String commentText = comment.getText();
    final PsiElement whitespace = comment.getNextSibling();
    final String text = commentText.substring(2, commentText.length() - 2);
    final String[] lines = text.split("\n");
    for (int i = lines.length - 1; i >= 1; i--) {
      final PsiComment nextComment =
          factory.createCommentFromText("//" + lines[i].trim() + '\n',
              parent);
      parent.addAfter(nextComment, comment);
     /* if (whitespace != null) {
        final PsiElement newWhiteSpace =
            factory.createWhiteSpaceFromText(whitespace.getText());
        parent.addAfter(newWhiteSpace, comment);
      }  */
    }
    final PsiComment newComment =
        factory.createCommentFromText("//" + lines[0], parent);
    comment.replace(newComment);
  }
}
