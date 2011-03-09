/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.editor.actions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.JavaTypedHandler;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author peter
 */
public class GroovyTypedHandler extends TypedHandlerDelegate {
  static final TokenSet INVALID_INSIDE_REFERENCE = TokenSet.create(GroovyTokenTypes.mSEMI, GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY);
  private boolean myJavaLTTyped;

  public Result beforeCharTyped(final char c, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    myJavaLTTyped = '<' == c &&
                    file instanceof GroovyFile &&
                    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                    isAfterClassLikeIdentifier(offsetBefore, editor);

    if ('>' == c) {
      if (file instanceof GroovyFile && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
        if (JavaTypedHandler.handleJavaGT(editor, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, INVALID_INSIDE_REFERENCE)) return Result.STOP;
      }
    }

    return Result.CONTINUE;
  }

  public Result charTyped(final char c, final Project project, final Editor editor, final PsiFile file) {
    if (myJavaLTTyped) {
      myJavaLTTyped = false;
      JavaTypedHandler.handleAfterJavaLT(editor, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, INVALID_INSIDE_REFERENCE);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  public static boolean isAfterClassLikeIdentifier(final int offset, final Editor editor) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;
    if (iterator.getStart() > 0) iterator.retreat();
    return JavaTypedHandler.isClassLikeIdentifier(offset, editor, iterator, GroovyTokenTypes.mIDENT);
  }


}
