/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class MoveCommentToSeparateLineIntention extends Intention {

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new CommentOnLineWithSourcePredicate();
  }

  public void processIntention(@NotNull PsiElement element) {
    final PsiComment comment = (PsiComment)element;
    final PsiWhiteSpace whitespace;
    while (true) {
      final PsiElement prevLeaf = PsiTreeUtil.prevLeaf(element);
      if (prevLeaf == null || prevLeaf instanceof PsiWhiteSpace && prevLeaf.getText().indexOf('\n') >= 0) {
        whitespace = (PsiWhiteSpace)prevLeaf;
        break;
      }
      element = prevLeaf;
    }
    final PsiElement anchor = element;

    final Project project = comment.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(comment.getContainingFile());
    if (document == null) {
      return;
    }
    final String newline;
    if (whitespace == null) {
      newline = "\n";
    }
    else {
      final String text = whitespace.getText();
      newline = text.substring(text.lastIndexOf('\n'));
    }
    final PsiElement prev = PsiTreeUtil.prevLeaf(comment);
    final TextRange commentRange = comment.getTextRange();
    final int deleteOffset = prev instanceof PsiWhiteSpace ? prev.getTextRange().getStartOffset() : commentRange.getStartOffset();
    document.deleteString(deleteOffset, commentRange.getEndOffset());

    final int offset = anchor.getTextRange().getStartOffset();
    document.insertString(offset, newline);
    document.insertString(offset, comment.getText());
    scrollToVisible(project, offset);
  }

  private static void scrollToVisible(Project project, int offset) {
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) {
      return;
    }
    final LogicalPosition position = editor.offsetToLogicalPosition(offset);
    editor.getScrollingModel().scrollTo(position, ScrollType.MAKE_VISIBLE);
  }
}
