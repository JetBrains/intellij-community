// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.comments;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.ArrayList;
import java.util.List;

public final class ChangeToCStyleCommentIntention extends GrPsiUpdateIntention {


  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new EndOfLineCommentPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
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
    final JavaPsiFacade manager = JavaPsiFacade.getInstance(selectedComment.getProject());
    final PsiElementFactory factory = manager.getElementFactory();
    String text = getCommentContents(firstComment);
    final List<PsiElement> commentsToDelete = new ArrayList<>();
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
    firstComment.replace(newComment);
    for (PsiElement commentToDelete : commentsToDelete) {
      commentToDelete.delete();
    }
  }

  private static @Nullable PsiElement getNextNonWhiteSpace(PsiElement nextComment) {
    PsiElement elementToCheck = nextComment;
    while (true) {
      final PsiElement sibling = elementToCheck.getNextSibling();
      if (sibling == null) {
        return null;
      }
      if (sibling.getText().trim().replace("\n", "").isEmpty()) {
        elementToCheck = sibling;
      } else {
        return sibling;
      }
    }
  }

  private static @Nullable PsiElement getPrevNonWhiteSpace(PsiElement nextComment) {
    PsiElement elementToCheck = nextComment;
    while (true) {
      final PsiElement sibling = elementToCheck.getPrevSibling();
      if (sibling == null) {
        return null;
      }
      if (sibling.getText().trim().replace("\n", "").isEmpty()) {
        elementToCheck = sibling;
      } else {
        return sibling;
      }
    }
  }

  private static boolean isEndOfLineComment(PsiElement element) {
    if (!(element instanceof PsiComment comment)) {
      return false;
    }
    final IElementType tokenType = comment.getTokenType();
    return GroovyTokenTypes.mSL_COMMENT.equals(tokenType);
  }

  private static String getCommentContents(PsiComment comment) {
    final String text = comment.getText();
    return text.substring(2);
  }
}
