/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;

import java.util.List;

/**
 * @author ilyas
 */
public class GroovyBlockStatementsSelectioner extends GroovyBasicSelectioner {

  public boolean canSelect(PsiElement e) {
    return e instanceof GrCodeBlock;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    if (e instanceof GrCodeBlock) {
      GrCodeBlock block = ((GrCodeBlock)e);
      int startOffset = findOpeningBrace(block);
      int endOffset = findClosingBrace(block, startOffset);
      TextRange range = new TextRange(startOffset, endOffset);
      result.addAll(expandToWholeLine(editorText, range));
    }
    return result;
  }

  private static int findOpeningBrace(GrCodeBlock block) {
    PsiElement lbrace = block.getLBrace();
    if (lbrace == null) return block.getTextRange().getStartOffset();

    while (isWhiteSpace(lbrace.getNextSibling())) {
      lbrace = lbrace.getNextSibling();
    }
    return lbrace.getTextRange().getEndOffset();
  }

  private static int findClosingBrace(GrCodeBlock block, int startOffset) {
    PsiElement rbrace = block.getRBrace();
    if (rbrace == null) return block.getTextRange().getEndOffset();

    while (isWhiteSpace(rbrace.getPrevSibling()) && rbrace.getPrevSibling().getTextRange().getStartOffset() > startOffset) {
      rbrace = rbrace.getPrevSibling();
    }

    return rbrace.getTextRange().getStartOffset();
  }

  private static boolean isWhiteSpace(PsiElement element) {
    return element != null && GroovyTokenTypes.WHITE_SPACES_SET.contains(element.getNode().getElementType());
  }
}
