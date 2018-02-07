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
package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.codeInsight.editorActions.TypedHandlerUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author peter
 */
public class GroovyBackspaceHandler extends BackspaceHandlerDelegate {
  private boolean myToDeleteGt;

  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset() - 1;
    myToDeleteGt = c =='<' && file instanceof GroovyFile && GroovyTypedHandler.isAfterClassLikeIdentifier(offset, editor);
  }

  @Override
  public boolean charDeleted(final char c, @NotNull final PsiFile file, @NotNull final Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    final CharSequence chars = editor.getDocument().getCharsSequence();
    if (editor.getDocument().getTextLength() <= offset) return false; //virtual space after end of file

    char c1 = chars.charAt(offset);
    if (c == '<' && myToDeleteGt) {
      if (c1 != '>') return true;
      TypedHandlerUtil.handleGenericLTDeletion(editor, offset, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, GroovyTypedHandler.INVALID_INSIDE_REFERENCE);
      return true;
    }
    return false;
  }
}
