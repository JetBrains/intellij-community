/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyStatementSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof GrExpression && PsiUtil.isExpressionStatement(e) ||
           (e instanceof GrStatement && !(e instanceof GrExpression)) ||
           e.getNode().getElementType() == GroovyTokenTypes.mSEMI;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    TextRange originalRange;

    PsiElement first;
    PsiElement last;
    if (e instanceof GrStatement) {
      first = e;
      PsiElement next = e.getNextSibling();
      next = skipWhitespacesForward(next);
      if (next != null && next.getNode().getElementType() == GroovyTokenTypes.mSEMI) {
        originalRange = new TextRange(e.getTextRange().getStartOffset(), next.getTextRange().getEndOffset());
        last = next;
      }
      else {
        originalRange = e.getTextRange();
        last = e;
      }
    }

    else {
      last = e;
      PsiElement prev = e.getPrevSibling();
      prev = skipWhitespaceBack(prev);
      if (prev instanceof GrStatement) {
        originalRange = new TextRange(prev.getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
        first = prev;
      }
      else {
        originalRange = e.getTextRange();
        first = e;
      }
    }


    final List<TextRange> ranges = ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, originalRange);


    final TextRange blockRange = inferBlockRange(first, last);

    if (!blockRange.equals(originalRange)) {
      ranges.addAll(ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, blockRange, true));
    }

    return ranges;
  }

  private static TextRange inferBlockRange(PsiElement first, PsiElement last) {
    while (true) {
      PsiElement prev = first.getPrevSibling();

      prev = skipWhitespaceBack(prev);
      if (isOneLineFeed(prev)) prev = prev.getPrevSibling();
      prev = skipWhitespaceBack(prev);
      if (prev != null && prev.getNode().getElementType() == GroovyTokenTypes.mSEMI || prev instanceof GrStatement) {
        first = prev;
      }
      else {
        break;
      }
    }

    while (true) {
      PsiElement next = last.getNextSibling();

      next = skipWhitespacesForward(next);
      if (isOneLineFeed(next)) next = next.getNextSibling();
      next = skipWhitespacesForward(next);
      if (next != null && next.getNode().getElementType() == GroovyTokenTypes.mSEMI || next instanceof GrStatement) {
        last = next;
      }
      else {
        break;
      }
    }

    return new TextRange(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
  }

  private static PsiElement skipWhitespacesForward(PsiElement next) {
    while (isSpaceWithoutLineFeed(next)) next = next.getNextSibling();
    return next;
  }

  private static PsiElement skipWhitespaceBack(PsiElement prev) {
    while (isSpaceWithoutLineFeed(prev)) prev = prev.getPrevSibling();
    return prev;
  }

  private static boolean isOneLineFeed(PsiElement e) {
    if (e == null) return false;
    if (!PsiImplUtil.isWhiteSpaceOrNls(e)) return false;

    final String text = e.getText();
    final int i = text.indexOf('\n');
    return i >= 0 && i == text.lastIndexOf('\n');
  }

  private static boolean isSpaceWithoutLineFeed(PsiElement e) {
    return e instanceof PsiWhiteSpace && e.getText().indexOf('\n') == -1;
  }
}
