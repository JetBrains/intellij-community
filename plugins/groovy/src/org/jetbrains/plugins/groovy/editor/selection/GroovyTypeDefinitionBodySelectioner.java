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
package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.List;

public class GroovyTypeDefinitionBodySelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof GrTypeDefinitionBody;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    if (e instanceof GrTypeDefinitionBody) {
      GrTypeDefinitionBody block = ((GrTypeDefinitionBody)e);
      int startOffset = findOpeningBrace(block);
      int endOffset = findClosingBrace(block, startOffset);
      TextRange range = new TextRange(startOffset, endOffset);
      result.addAll(expandToWholeLine(editorText, range));
    }
    return result;
  }

  private static int findOpeningBrace(GrTypeDefinitionBody block) {
    PsiElement lbrace = block.getLBrace();
    if (lbrace == null) return block.getTextRange().getStartOffset();

    while (PsiImplUtil.isWhiteSpaceOrNls(lbrace.getNextSibling())) {
      lbrace = lbrace.getNextSibling();
    }
    return lbrace.getTextRange().getEndOffset();
  }

  private static int findClosingBrace(GrTypeDefinitionBody block, int startOffset) {
    PsiElement rbrace = block.getRBrace();
    if (rbrace == null) return block.getTextRange().getEndOffset();

    while (PsiImplUtil.isWhiteSpaceOrNls(rbrace.getPrevSibling()) && rbrace.getPrevSibling().getTextRange().getStartOffset() > startOffset) {
      rbrace = rbrace.getPrevSibling();
    }

    return rbrace.getTextRange().getStartOffset();
  }
}
