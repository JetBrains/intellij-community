// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.editorActions.TypedHandlerUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.INVALID_INSIDE_REFERENCE;

public final class GroovyTypedHandler extends TypedHandlerDelegate {
  private boolean myJavaLTTyped;

  @Override
  public @NotNull Result beforeCharTyped(final char c, final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file, final @NotNull FileType fileType) {
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

    if (c == '.' && file instanceof GroovyFile) {
      autoPopupMemberLookup(project, editor, null);
    }

    return Result.CONTINUE;
  }

  private static void autoPopupMemberLookup(Project project, final Editor editor, Condition<PsiFile> condition) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, condition);
  }


  @Override
  public @NotNull Result charTyped(final char c, final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    if (myJavaLTTyped) {
      myJavaLTTyped = false;
      TypedHandlerUtil.handleAfterGenericLT(editor, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, INVALID_INSIDE_REFERENCE);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  public static boolean isAfterClassLikeIdentifier(final int offset, final Editor editor) {
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;
    if (iterator.getStart() > 0) iterator.retreat();
    return TypedHandlerUtil.isClassLikeIdentifier(offset, editor, iterator, GroovyTokenTypes.mIDENT);
  }
}
