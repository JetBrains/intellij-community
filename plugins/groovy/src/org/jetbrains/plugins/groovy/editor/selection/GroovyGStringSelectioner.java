/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyGStringSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    PsiElement parent = e.getParent();
    return parent instanceof GrStringInjection || parent instanceof GrString || parent instanceof GrStringContent;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    final List<TextRange> ranges = ContainerUtil.newArrayList();
    final PsiElement parent = e.getParent();

    if (parent instanceof GrStringContent && parent.getParent() instanceof GrString) {
      TextRange range = getLineTextRange(parent, cursorOffset);
      ranges.add(range);
    }
    else if (parent instanceof GrString) {
      PsiElement firstChild = parent.getFirstChild();
      PsiElement lastChild = parent.getLastChild();
      if (firstChild.getNode().getElementType() == GroovyTokenTypes.mGSTRING_BEGIN) {
        firstChild = firstChild.getNextSibling();
      }
      if (lastChild.getNode().getElementType() == GroovyTokenTypes.mGSTRING_END) {
        lastChild = lastChild.getPrevSibling();
      }
      if (firstChild != null && lastChild != null) {
        TextRange range = new TextRange(firstChild.getTextOffset(), lastChild.getTextOffset() + lastChild.getTextLength());
        ranges.add(range);
      }

      ranges.add(parent.getTextRange());
    }
    else if (parent instanceof GrStringInjection) {
      if (e instanceof GrReferenceExpression) {
        List<TextRange> r = new ArrayList<>(2);
        SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, r);
        for (TextRange textRange : r) {
          if (editorText.charAt(textRange.getStartOffset()) == '$') {
            textRange = new TextRange(textRange.getStartOffset() + 1, textRange.getEndOffset());
          }
          ranges.add(textRange);
        }
      }
      ranges.add(parent.getTextRange());
      ranges.add(e.getTextRange());
    }

    return ranges;
  }

  @NotNull
  private static TextRange getLineTextRange(PsiElement e, int cursorOffset) {
    assert e.getParent() instanceof GrString;

    PsiElement next = e;
    int startOffset = cursorOffset;
    int endOffset = cursorOffset;
    if (e.getNode().getElementType() == GroovyTokenTypes.mGSTRING_CONTENT) {
      final String text = e.getText();
      int cur;
      int index = -1;
      while (true) {
        cur = text.indexOf('\n', index + 1);
        if (cur < 0 || cur + e.getTextOffset() > cursorOffset) break;
        index = cur;
      }
      if (index >= 0) {
        startOffset = e.getTextOffset() + index + 1;
      }

      index = text.indexOf('\n', cursorOffset - e.getTextOffset());
      if (index >= 0) {
        endOffset = e.getTextOffset() + index + 1;
      }
    }

    if (startOffset == cursorOffset) {
      do {
        if (next == null) break;
        final ASTNode node = next.getNode();
        if (node == null) break;
        final IElementType type = node.getElementType();
        if (type == GroovyTokenTypes.mGSTRING_BEGIN) {
          startOffset = next.getTextRange().getEndOffset();
          break;
        }
        if (type == GroovyElementTypes.GSTRING_CONTENT) {
          final String text;
          if (startOffset == cursorOffset && next.getTextRange().contains(cursorOffset)) {
            text = next.getText().substring(0, startOffset - next.getTextOffset());
          }
          else {
            text = next.getText();
          }

          final int i = text.lastIndexOf('\n');
          if (i >= 0) {
            startOffset = next.getTextOffset() + i + 1;
            break;
          }
        }
        startOffset = next.getTextOffset();
        next = next.getPrevSibling();
      }
      while (true);
    }

    if (endOffset == cursorOffset) {
      next = e;
      do {
        if (next == null) break;
        final ASTNode node = next.getNode();
        if (node == null) break;

        final IElementType type = node.getElementType();
        if (type == GroovyTokenTypes.mGSTRING_END) {
          endOffset = next.getTextOffset();
          break;
        }
        if (type == GroovyElementTypes.GSTRING_CONTENT) {
          final String text;
          final int offset;
          if (endOffset == cursorOffset && next.getTextRange().contains(cursorOffset)) {
            offset = endOffset - next.getTextOffset();
            text = next.getText().substring(offset);
          }
          else {
            offset = 0;
            text = next.getText();
          }
          final int i = text.indexOf('\n');
          if (i >= 0) {
            endOffset = next.getTextOffset() + offset + i;
            break;
          }
        }
        endOffset = next.getTextOffset() + next.getTextLength();
        next = next.getNextSibling();
      }
      while (true);
    }

    return new TextRange(startOffset, endOffset);
  }
}
