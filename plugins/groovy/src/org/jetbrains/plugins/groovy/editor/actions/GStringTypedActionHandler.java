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

package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Maxim.Medvedev
 */
public class GStringTypedActionHandler extends TypedHandlerDelegate {
  @NotNull
  @Override
  public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (c != '{' || !HandlerUtils.canBeInvoked(editor, project)) {
      return Result.CONTINUE;
    }

    if (!(file instanceof GroovyFile)) return Result.CONTINUE;

    int caret = editor.getCaretModel().getOffset();
    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    if (caret < 1) return Result.CONTINUE;

    HighlighterIterator iterator = highlighter.createIterator(caret - 1);
    if (iterator.getTokenType() != GroovyTokenTypes.mLCURLY) return Result.CONTINUE;
    iterator.retreat();
    if (iterator.atEnd() || iterator.getTokenType() != GroovyTokenTypes.mDOLLAR) return Result.CONTINUE;
    iterator.advance();
    if (iterator.atEnd()) return Result.CONTINUE;
    iterator.advance();
    if (iterator.getTokenType() != GroovyTokenTypes.mGSTRING_BEGIN) return Result.CONTINUE;

    editor.getDocument().insertString(caret, "}");
    return Result.STOP;
  }
}
