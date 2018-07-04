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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.editorActions.TypedHandlerUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author peter
 */
public class GroovyTypedHandler extends TypedHandlerDelegate {
  static final TokenSet INVALID_INSIDE_REFERENCE = TokenSet.create(GroovyTokenTypes.mSEMI, GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY);
  private boolean myJavaLTTyped;

  @NotNull
  @Override
  public Result beforeCharTyped(final char c, @NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final FileType fileType) {
    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    myJavaLTTyped = '<' == c &&
                    file instanceof GroovyFile &&
                    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                    isAfterClassLikeIdentifier(offsetBefore, editor);

    if ('>' == c) {
      if (file instanceof GroovyFile && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
        if (TypedHandlerUtil.handleGenericGT(editor, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, INVALID_INSIDE_REFERENCE)) return Result.STOP;
      }
    }

    if (c == '@' && file instanceof GroovyFile) {
      autoPopupMemberLookup(project, editor, file12 -> {
        int offset = editor.getCaretModel().getOffset();

        PsiElement lastElement = file12.findElementAt(offset - 1);
        if (lastElement == null) return false;

        final PsiElement prevSibling = PsiTreeUtil.prevVisibleLeaf(lastElement);
        return prevSibling != null && ".".equals(prevSibling.getText());
      });
    }

    if (c == '&' && file instanceof GroovyFile) {
      autoPopupMemberLookup(project, editor, file1 -> {
        int offset = editor.getCaretModel().getOffset();

        PsiElement lastElement = file1.findElementAt(offset - 1);
        return lastElement != null && ".&".equals(lastElement.getText());
      });
    }


    return Result.CONTINUE;
  }

  private static void autoPopupMemberLookup(Project project, final Editor editor, Condition<PsiFile> condition) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, condition);
  }


  @NotNull
  @Override
  public Result charTyped(final char c, @NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (myJavaLTTyped) {
      myJavaLTTyped = false;
      TypedHandlerUtil.handleAfterGenericLT(editor, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, INVALID_INSIDE_REFERENCE);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  public static boolean isAfterClassLikeIdentifier(final int offset, final Editor editor) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;
    if (iterator.getStart() > 0) iterator.retreat();
    return TypedHandlerUtil.isClassLikeIdentifier(offset, editor, iterator, GroovyTokenTypes.mIDENT);
  }
}
