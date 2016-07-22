/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ChangeToCStyleCommentIntention extends Intention {

  private static final Class<PsiWhiteSpace>[] WHITESPACE_CLASS =
    new Class[]{PsiWhiteSpace.class};

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new EndOfLineCommentPredicate();
  }

  @Override
  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    PsiComment firstComment = (PsiComment)element;
    while (true) {
      final PsiElement prevComment =
        PsiTreeUtil.skipSiblingsBackward(firstComment,
                                         WHITESPACE_CLASS);
      if (!isEndOfLineComment(prevComment)) {
        break;
      }
      assert prevComment != null;
      firstComment = (PsiComment)prevComment;
    }
    final JavaPsiFacade psiFacade =
      JavaPsiFacade.getInstance(element.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final List<PsiComment> multiLineComments = new ArrayList<>();
    PsiElement nextComment = firstComment;
    String whiteSpace = null;
    while (true) {
      nextComment = PsiTreeUtil.skipSiblingsForward(nextComment,
                                                    WHITESPACE_CLASS);
      if (!isEndOfLineComment(nextComment)) {
        break;
      }
      assert nextComment != null;
      if (whiteSpace == null) {
        final PsiElement prevSibling = nextComment.getPrevSibling();
        assert prevSibling != null;
        final String text = prevSibling.getText();
        whiteSpace = getIndent(text);
      }
      multiLineComments.add((PsiComment)nextComment);
    }
    final String newCommentString;
    if (multiLineComments.isEmpty()) {
      final String text = getCommentContents(firstComment);
      newCommentString = "/* " + text + " */";
    }
    else {
      final StringBuilder text = new StringBuilder();
      text.append("/*\n");
      text.append(whiteSpace);
      text.append(getCommentContents(firstComment));
      for (PsiComment multiLineComment : multiLineComments) {
        text.append('\n');
        text.append(whiteSpace);
        text.append(getCommentContents(multiLineComment));
      }
      text.append('\n');
      text.append(whiteSpace);
      text.append("*/");
      newCommentString = text.toString();
    }
    final PsiComment newComment =
      factory.createCommentFromText(newCommentString, element);
    firstComment.replace(newComment);
    for (PsiElement commentToDelete : multiLineComments) {
      commentToDelete.delete();
    }
  }

  private static String getIndent(String whitespace) {
    for (int i = whitespace.length() - 1; i >= 0; i--) {
      final char c = whitespace.charAt(i);
      if (c == '\n') {
        if (i == whitespace.length() - 1) {
          return "";
        }
        return whitespace.substring(i + 1);
      }
    }
    return whitespace;
  }

  private static boolean isEndOfLineComment(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    final PsiComment comment = (PsiComment)element;
    final IElementType tokenType = comment.getTokenType();
    return JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType);
  }

  private static String getCommentContents(@NotNull PsiComment comment) {
    final String text = comment.getText();
    return StringUtil.replace(text.substring(2), "*/", "* /").trim();
  }
}
