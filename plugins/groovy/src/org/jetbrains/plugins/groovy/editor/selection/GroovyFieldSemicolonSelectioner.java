/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyFieldSemicolonSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof GrVariableDeclaration;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    PsiElement next = e.getNextSibling();
    while (next != null &&
           next.getNode() != null &&
           TokenSets.WHITE_SPACES_SET.contains(next.getNode().getElementType()) &&
           !next.textContains('\n')) {
      next = next.getNextSibling();
    }

    final List<TextRange> ranges = new ArrayList<TextRange>();
    ranges.add(e.getTextRange());
    if (next != null && next.getNode().getElementType() == GroovyTokenTypes.mSEMI) {
      ranges.add(new TextRange(e.getTextRange().getStartOffset(), next.getTextRange().getEndOffset()));
    }
    return ranges;
  }
}
