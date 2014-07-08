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
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

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

  private final AtomicBoolean updateRequested = new AtomicBoolean();

  public ExecutionPointHighlighter(final Project project) {
    myProject = project;
  }

  public void show(final @NotNull XSourcePosition position, final boolean useSelection,
                   @Nullable final GutterIconRenderer gutterIconRenderer) {
    updateRequested.set(false);
    AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
      @Override
      public void run() {
        updateRequested.set(false);

        mySourcePosition = position;

        clearDescriptor();
        myOpenFileDescriptor = XSourcePositionImpl.createOpenFileDescriptor(myProject, position);
        //see IDEA-125645 and IDEA-63459
        //myOpenFileDescriptor.setUseCurrentWindow(true);

        myGutterIconRenderer = gutterIconRenderer;
        myUseSelection = useSelection;

        doShow();
      }
    });
  }

  public void hide() {
    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
      public void run() {
        updateRequested.set(false);

        removeHighlighter();
        clearDescriptor();
        myEditor = null;
        myGutterIconRenderer = null;
      }
    });
  }

  private void clearDescriptor() {
    if (myOpenFileDescriptor != null) {
      myOpenFileDescriptor.dispose();
      myOpenFileDescriptor = null;
    }
  }

  public void navigateTo() {
    if (myOpenFileDescriptor != null) {
      myOpenFileDescriptor.navigateInEditor(myProject, true);
    }
  }

  @Nullable
  public VirtualFile getCurrentFile() {
    return myOpenFileDescriptor != null ? myOpenFileDescriptor.getFile() : null;
  }

  public void update() {
    if (updateRequested.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (updateRequested.compareAndSet(true, false)) {
            doShow();
          }
        }
      }, myProject.getDisposed());
    }
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

  private void doShow() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    removeHighlighter();

    myEditor = myOpenFileDescriptor == null ? null : XDebuggerUtilImpl.createEditor(myOpenFileDescriptor);
    if (myEditor != null) {
      addHighlighter();
    }
  }

  private void removeHighlighter() {
    if (myUseSelection && myEditor != null) {
      myEditor.getSelectionModel().removeSelection();
    }

    if (myRangeHighlighter != null) {
      myRangeHighlighter.dispose();
      myRangeHighlighter = null;
    }
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
