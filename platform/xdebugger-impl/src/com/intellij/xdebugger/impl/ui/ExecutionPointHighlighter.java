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
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ExecutionPointHighlighter {
  private final Project myProject;
  private RangeHighlighter myRangeHighlighter;
  private Editor myEditor;
  private XSourcePosition mySourcePosition;
  private OpenFileDescriptor myOpenFileDescriptor;
  private boolean myUseSelection;
  private GutterIconRenderer myGutterIconRenderer;
  private static final Key<Boolean> EXECUTION_POINT_HIGHLIGHTER_KEY = Key.create("EXECUTION_POINT_HIGHLIGHTER_KEY");

  public ExecutionPointHighlighter(final Project project) {
    myProject = project;
  }

  public void show(final @NotNull XSourcePosition position, final boolean useSelection,
                   @Nullable final GutterIconRenderer gutterIconRenderer) {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
      public void run() {
        doShow(position, useSelection, gutterIconRenderer);
      }
    });
  }

  public void hide() {
    AppUIUtil.invokeOnEdt(new Runnable() {
      public void run() {
        doHide();
      }
    });
  }

  public void navigateTo() {
    if (myOpenFileDescriptor != null) {
      FileEditorManager.getInstance(myProject).openTextEditor(myOpenFileDescriptor, false);
    }
  }

  @Nullable
  public VirtualFile getCurrentFile() {
    return myOpenFileDescriptor != null ? myOpenFileDescriptor.getFile() : null;
  }

  public void update() {
    show(mySourcePosition, myUseSelection, myGutterIconRenderer);
  }

  public void updateGutterIcon(@NotNull final GutterIconRenderer renderer) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
      public void run() {
        if (myRangeHighlighter != null && myGutterIconRenderer != null) {
          myRangeHighlighter.setGutterIconRenderer(renderer);
        }
      }
    });
  }

  private void doShow(@NotNull XSourcePosition position, final boolean useSelection, @Nullable GutterIconRenderer renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeHighlighter();

    mySourcePosition = position;
    myEditor = openEditor();
    myUseSelection = useSelection;
    myGutterIconRenderer = renderer;
    if (myEditor != null) {
      addHighlighter();
    }
  }

  @Nullable
  private Editor openEditor() {
    VirtualFile file = mySourcePosition.getFile();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    int offset = mySourcePosition.getOffset();
    if (offset < 0 || offset >= document.getTextLength()) {
      myOpenFileDescriptor = new OpenFileDescriptor(myProject, file, mySourcePosition.getLine(), 0);
    }
    else {
      myOpenFileDescriptor = new OpenFileDescriptor(myProject, file, offset);
    }
    return FileEditorManager.getInstance(myProject).openTextEditor(myOpenFileDescriptor, false);
  }

  private void doHide() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeHighlighter();
    myOpenFileDescriptor = null;
    myEditor = null;
  }

  private void removeHighlighter() {
    if (myUseSelection && myEditor != null) {
      myEditor.getSelectionModel().removeSelection();
    }
    if (myRangeHighlighter == null || myEditor == null) return;

    myRangeHighlighter.dispose();
    myRangeHighlighter = null;
  }

  private void addHighlighter() {
    int line = mySourcePosition.getLine();
    Document document = myEditor.getDocument();
    if (line >= document.getLineCount()) return;

    if (myUseSelection) {
      myEditor.getSelectionModel().setSelection(document.getLineStartOffset(line), document.getLineEndOffset(line) + document.getLineSeparatorLength(line));
      return;
    }

    if (myRangeHighlighter != null) return;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myRangeHighlighter = myEditor.getMarkupModel().addLineHighlighter(line, DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                                                                      scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES));
    myRangeHighlighter.putUserData(EXECUTION_POINT_HIGHLIGHTER_KEY, true);
    myRangeHighlighter.setGutterIconRenderer(myGutterIconRenderer);
  }
}
