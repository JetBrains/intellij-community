/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.text.CharArrayUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ChangeToEndOfLineCommentIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new CStyleCommentPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiComment comment = (PsiComment)element;
    final Project project = comment.getProject();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final PsiElement parent = comment.getParent();
    assert parent != null;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String commentText = comment.getText();
    final PsiElement whitespace = comment.getNextSibling();
    final String text = commentText.substring(2, commentText.length() - 2);
    final String[] lines = text.split("\n");

    int tabSize = getTabSize(comment);
    int firstLineStartColumn = getStartColumnNumber(comment, tabSize) + 2;
    trimLinesWithAlignment(lines, tabSize, firstLineStartColumn);

    for (int i = lines.length - 1; i >= 1; i--) {
      final PsiComment nextComment = factory.createCommentFromText("//" + lines[i], parent);
      parent.addAfter(nextComment, comment);
      if (whitespace != null) {
        final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(project);
        final PsiElement newWhiteSpace = parserFacade.createWhiteSpaceFromText(whitespace.getText());
        parent.addAfter(newWhiteSpace, comment);
      }
    }
    final PsiComment newComment = factory.createCommentFromText("//" + lines[0], parent);
    final PsiElement replacedComment = comment.replace(newComment);codeStyleManager.reformat(replacedComment);
  }

  private static int getTabSize(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file == null ? 1 : Math.max(1, CodeStyle.getIndentOptions(file).TAB_SIZE);
  }

  private static int getStartColumnNumber(@NotNull PsiElement element, int tabSize) {
    PsiFile file = element.getContainingFile();
    if (file == null) return 0;
    String text = file.getText();
    if (text == null) return 0;
    int elementOffset = element.getTextRange().getStartOffset();
    int lineStart = CharArrayUtil.shiftBackwardUntil(text, elementOffset - 1, "\n") + 1;
    int column = 0;
    for (int i = lineStart; i < elementOffset; i++) {
      column = nextColumn(column, text.charAt(i), tabSize);
    }
    return column;
  }

  private static void trimLinesWithAlignment(@NotNull String[] lines, int tabSize, int firstLineStartColumn) {
    if (lines.length == 1) {
      lines[0] = lines[0].trim();
    }
    else {
      int minIndent = firstLineStartColumn;
      for (int i = 1; i < lines.length; i++) {
        String line = lines[i];
        int column = 0;
        for (int j = 0; j < line.length(); j++) {
          char c = line.charAt(j);
          if (" \t".indexOf(c) == -1) break;
          column = nextColumn(column, c, tabSize);
        }
        if (column < minIndent) minIndent = column;
      }
      for (int i = 1; i < lines.length; i++) {
        String line = lines[i];
        int column = 0;
        int trimOffset = 0;
        for (; trimOffset < line.length(); trimOffset++) {
          column = nextColumn(column, line.charAt(trimOffset), tabSize);
          if (column > minIndent) break;
        }
        lines[i] = line.substring(trimOffset);
      }
    }
  }

  private static int nextColumn(int currentColumn, char c, int tabSize) {
    return c == '\t' ? ((currentColumn / tabSize) + 1) * tabSize : currentColumn + 1;
  }
}