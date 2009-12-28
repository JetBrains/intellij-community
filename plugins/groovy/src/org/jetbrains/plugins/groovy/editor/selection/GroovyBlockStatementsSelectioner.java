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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
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
      GrCodeBlock block = ((GrCodeBlock) e);
      GrStatement[] statements = block.getStatements();

      if (statements.length > 0) {
        int startOffset = statements[0].getTextRange().getStartOffset();
        int endOffset = statements[statements.length - 1].getTextRange().getEndOffset();
        TextRange range = new TextRange(startOffset, endOffset);
        result.add(range);
      }
    }
    return result;
  }

}
