/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LazyRangeMarkerFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class LazyRangeMarkerFactoryImpl extends LazyRangeMarkerFactory {
  private final Project myProject;

  public LazyRangeMarkerFactoryImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final VirtualFile file, final int offset) {
    return ReadAction.compute(() -> DocumentImpl.createRangeMarkerForVirtualFile(file, offset, offset, -1, -1, -1, -1, false));
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final VirtualFile file, final int line, final int column, final boolean persistent) {
    return ReadAction.compute(() -> {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        int myTabSize = CodeStyleFacade.getInstance(myProject).getTabSize(file.getFileType());
        final int offset = calculateOffset(document, line, column, myTabSize);
        return DocumentImpl.createRangeMarkerForVirtualFile(file, offset, offset, line, column, line, column, persistent);
      }

      return DocumentImpl.createRangeMarkerForVirtualFile(file, 0, 0, line, column, line, column, persistent);
    });
  }


  private static int calculateOffset(@NotNull Document document,
                                     final int line,
                                     final int column,
                                     int tabSize) {
    int offset;
    if (0 <= line && line < document.getLineCount()) {
      final int lineStart = document.getLineStartOffset(line);
      final int lineEnd = document.getLineEndOffset(line);
      final CharSequence docText = document.getCharsSequence();

      offset = lineStart;
      int col = 0;
      while (offset < lineEnd && col < column) {
        col += docText.charAt(offset) == '\t' ? tabSize : 1;
        offset++;
      }
    }
    else {
      offset = document.getTextLength();
    }
    return offset;
  }
}
