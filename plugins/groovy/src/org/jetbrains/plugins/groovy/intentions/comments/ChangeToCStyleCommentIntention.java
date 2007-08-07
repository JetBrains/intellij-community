package org.jetbrains.plugins.groovy.intentions.comments;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.ArrayList;
import java.util.List;

public class ChangeToCStyleCommentIntention extends Intention {


  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new EndOfLineCommentPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
      throws IncorrectOperationException {
    final PsiComment selectedComment = (PsiComment) element;
    PsiComment firstComment = selectedComment;

    while (true) {
      final PsiElement prevComment =
          getPrevNonWhiteSpace(firstComment);
      if (!isEndOfLineComment(prevComment)) {
        break;
      }
      firstComment = (PsiComment) prevComment;
    }
    final PsiManager manager = selectedComment.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    String text = getCommentContents(firstComment);
    final List<PsiElement> commentsToDelete = new ArrayList<PsiElement>();
    PsiElement nextComment = firstComment;
    while (true) {
      nextComment = getNextNonWhiteSpace(nextComment);
      if (!isEndOfLineComment(nextComment)) {
        break;
      }
      text += nextComment.getPrevSibling().getText() + "  " //to get the whitespace for proper spacing
          + getCommentContents((PsiComment) nextComment);
      commentsToDelete.add(nextComment);
    }
    final PsiComment newComment =
        factory.createCommentFromText("/*" + text + " */", selectedComment.getParent());
    final PsiElement insertedElement = firstComment.replace(newComment);
    final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
    for (PsiElement commentToDelete : commentsToDelete) {
      commentToDelete.delete();
    }
  }

  @Nullable
  private PsiElement getNextNonWhiteSpace(PsiElement nextComment) {
    PsiElement elementToCheck = nextComment;
    while(true)
    {
      final PsiElement sibling = elementToCheck.getNextSibling();
      if(sibling == null)
      {
        return null;
      }
      if(sibling.getText().trim().replace("\n", "").length() == 0)
      {
         elementToCheck = sibling;
      }
      else
      {
        return sibling;
      }
    }
  }

  @Nullable
  private PsiElement getPrevNonWhiteSpace(PsiElement nextComment) {
    PsiElement elementToCheck = nextComment;
    while(true)
    {
      final PsiElement sibling = elementToCheck.getPrevSibling();
      if(sibling == null)
      {
        return null;
      }
      if(sibling.getText().trim().replace("\n", "").length() == 0)
      {
         elementToCheck = sibling;
      }
      else
      {
        return sibling;
      }
    }
  }

  private boolean isEndOfLineComment(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    final PsiComment comment = (PsiComment) element;
    final IElementType tokenType = comment.getTokenType();
    return GroovyTokenTypes.mSL_COMMENT.equals(tokenType);
  }

  private static String getCommentContents(PsiComment comment) {
    final String text = comment.getText();
    return text.substring(2);
  }
}
