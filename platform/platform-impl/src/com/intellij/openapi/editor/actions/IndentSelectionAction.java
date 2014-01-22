/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 10:29:01 PM
 */
package com.intellij.openapi.editor.actions;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentStrategy;
import com.intellij.openapi.editor.LanguageIndentStrategy;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IndentSelectionAction extends EditorAction {
  public IndentSelectionAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (isEnabled(editor, dataContext)) {
        indentSelection(editor, project);
      }
    }
  }

  @Override
  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    presentation.setEnabled(originalIsEnabled(editor, true));
  }

  @Override
  public void updateForKeyboardAccess(Editor editor, Presentation presentation, DataContext dataContext) {
    presentation.setEnabled(isEnabled(editor, dataContext));
  }

  protected boolean isEnabled(Editor editor, DataContext dataContext) {
    return originalIsEnabled(editor, true);
  }

  protected static boolean originalIsEnabled(Editor editor, boolean wantSelection) {
    return (!wantSelection || hasSuitableSelection(editor)) && !editor.isOneLineMode();
  }

  /**
   * Returns true if there is a selection in the editor and it spans multiple lines or the whole single line (potentially without leading and
   * trailing whitespaces).
   */
  private static boolean hasSuitableSelection(Editor editor) {
    if (!editor.getSelectionModel().hasSelection()) {
      return false;
    }
    Document document = editor.getDocument();
    int selectionStart = editor.getSelectionModel().getSelectionStart();
    int selectionEnd = editor.getSelectionModel().getSelectionEnd();
    int selectionLineStart = document.getLineNumber(selectionStart);
    int selectionLineEnd = document.getLineNumber(selectionEnd);
    if (selectionLineStart != selectionLineEnd) {
      return true;
    }
    int lineStart = document.getLineStartOffset(selectionLineStart);
    int lineEnd = document.getLineEndOffset(selectionLineEnd);
    return (selectionStart <= lineStart || CharArrayUtil.containsOnlyWhiteSpaces(document.getCharsSequence().subSequence(lineStart, selectionStart)))
           && (selectionEnd >= lineEnd || CharArrayUtil.containsOnlyWhiteSpaces(document.getCharsSequence().subSequence(selectionEnd, lineEnd)));
  }

  private static void indentSelection(Editor editor, Project project) {
    int oldSelectionStart = editor.getSelectionModel().getSelectionStart();
    int oldSelectionEnd = editor.getSelectionModel().getSelectionEnd();
    if(!editor.getSelectionModel().hasSelection()) {
      oldSelectionStart = editor.getCaretModel().getOffset();
      oldSelectionEnd = oldSelectionStart;
    }

    Document document = editor.getDocument();
    int startIndex = document.getLineNumber(oldSelectionStart);
    if(startIndex == -1) {
      startIndex = document.getLineCount() - 1;
    }
    int endIndex = document.getLineNumber(oldSelectionEnd);
    if(endIndex > 0 && document.getLineStartOffset(endIndex) == oldSelectionEnd && editor.getSelectionModel().hasSelection()) {
      endIndex --;
    }
    if(endIndex == -1) {
      endIndex = document.getLineCount() - 1;
    }
    
    VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
    final FileType fileType = vFile == null ? null : vFile.getFileType();
    int blockIndent = CodeStyleFacade.getInstance(project).getIndentSize(fileType);
    doIndent(endIndex, startIndex, document, project, editor, blockIndent);
  }

  static void doIndent(final int endIndex, final int startIndex, final Document document, final Project project, final Editor editor,
                               final int blockIndent) {
    boolean bulkMode = endIndex - startIndex > 50;
    if (bulkMode) ((DocumentEx)document).setInBulkUpdate(true);

    try {
      List<Integer> nonModifiableLines = new ArrayList<Integer>();
      if (project != null) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        IndentStrategy indentStrategy = LanguageIndentStrategy.getIndentStrategy(file);
        if (!LanguageIndentStrategy.isDefault(indentStrategy)) {
          for (int i = startIndex; i <= endIndex; i++) {
            if (!canIndent(document, file, i, indentStrategy)) {
              nonModifiableLines.add(i);
            }
          }
        }
      }
      for(int i=startIndex; i<=endIndex; i++) {
        if (!nonModifiableLines.contains(i)) {
          EditorActionUtil.indentLine(project, editor, i, blockIndent);
        }
      }
    }
    finally {
      if (bulkMode) ((DocumentEx)document).setInBulkUpdate(false);
    }
  }

  static boolean canIndent(Document document, PsiFile file, int line, @NotNull IndentStrategy indentStrategy) {
    int offset = document.getLineStartOffset(line);
    if (file != null) {
      PsiElement element = file.findElementAt(offset);
      if (element != null) {
        return indentStrategy.canIndent(element);
      }
    }
    return true;
  }
}
