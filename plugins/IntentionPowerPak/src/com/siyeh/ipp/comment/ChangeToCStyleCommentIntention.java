/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.comment;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ChangeToCStyleCommentIntention extends Intention {


  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new EndOfLineCommentPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiComment selectedComment = (PsiComment)element;
    PsiComment firstComment = selectedComment;

    while (true) {
      final PsiElement prevComment =
        PsiTreeUtil.skipSiblingsBackward(firstComment,
                                         new Class[]{
                                           PsiWhiteSpace.class});
      if (!isEndOfLineComment(prevComment)) {
        break;
      }
      firstComment = (PsiComment)prevComment;
    }
    final PsiManager manager = selectedComment.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    String text = getCommentContents(firstComment);
    final List<PsiElement> commentsToDelete = new ArrayList<PsiElement>();
    PsiElement nextComment = firstComment;
    while (true) {
      nextComment = PsiTreeUtil.skipSiblingsForward(nextComment,
                                                    new Class[]{
                                                      PsiWhiteSpace.class});
      if (!isEndOfLineComment(nextComment)) {
        break;
      }
      text += nextComment.getPrevSibling().getText() + "  " //to get the whitespace for proper spacing
              + getCommentContents((PsiComment)nextComment);
      commentsToDelete.add(nextComment);
    }
    final PsiComment newComment =
      factory.createCommentFromText("/*" + text + " */", selectedComment.getParent());
    final PsiElement insertedElement = firstComment.replace(newComment);
    final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
    codeStyleManager.reformat(insertedElement);
    for (PsiElement commentToDelete : commentsToDelete) {
      commentToDelete.delete();
    }
  }

  private boolean isEndOfLineComment(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    final PsiComment comment = (PsiComment)element;
    final IElementType tokenType = comment.getTokenType();
    return JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType);
  }

  private static String getCommentContents(PsiComment comment) {
    final String text = comment.getText();
    return text.substring(2);
  }
}
