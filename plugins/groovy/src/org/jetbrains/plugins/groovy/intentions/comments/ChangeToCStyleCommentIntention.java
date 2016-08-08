/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.intentions.comments;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.ArrayList;
import java.util.List;

public class ChangeToCStyleCommentIntention extends Intention {


  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new EndOfLineCommentPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element, Project project, Editor editor)
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

  @Nullable
  private PsiElement getNextNonWhiteSpace(PsiElement nextComment) {
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

  @Nullable
  private PsiElement getPrevNonWhiteSpace(PsiElement nextComment) {
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
