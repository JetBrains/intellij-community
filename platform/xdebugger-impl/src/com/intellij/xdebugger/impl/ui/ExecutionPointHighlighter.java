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
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  private boolean myNotTopFrame;
  private GutterIconRenderer myGutterIconRenderer;
  public static final Key<Boolean> EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY = Key.create("EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY");

  private final AtomicBoolean updateRequested = new AtomicBoolean();

  public ExecutionPointHighlighter(@NotNull Project project) {
    myProject = project;

    // Update highlighter colors if global color schema was changed
    project.getMessageBus().connect().subscribe(EditorColorsManager.TOPIC, scheme -> update(false));
  }

  public void show(final @NotNull XSourcePosition position, final boolean notTopFrame,
                   @Nullable final GutterIconRenderer gutterIconRenderer) {
    updateRequested.set(false);
    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
      updateRequested.set(false);

      mySourcePosition = position;

      clearDescriptor();
      myOpenFileDescriptor = XSourcePositionImpl.createOpenFileDescriptor(myProject, position);
      if (!XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isScrollToCenter()) {
        myOpenFileDescriptor.setScrollType(notTopFrame ? ScrollType.CENTER : ScrollType.MAKE_VISIBLE);
      }
      //see IDEA-125645 and IDEA-63459
      //myOpenFileDescriptor.setUseCurrentWindow(true);

      myGutterIconRenderer = gutterIconRenderer;
      myNotTopFrame = notTopFrame;

      doShow(true);
    });
  }

  public void hide() {
    AppUIUtil.invokeOnEdt(() -> {
      updateRequested.set(false);

      removeHighlighter();
      clearDescriptor();
      myEditor = null;
      myGutterIconRenderer = null;
    });
  }

  private void clearDescriptor() {
    if (myOpenFileDescriptor != null) {
      myOpenFileDescriptor.dispose();
      myOpenFileDescriptor = null;
    }
  }

  public void navigateTo() {
    if (myOpenFileDescriptor != null && myOpenFileDescriptor.getFile().isValid()) {
      myOpenFileDescriptor.navigateInEditor(myProject, true);
    }
  }

  @Nullable
  public VirtualFile getCurrentFile() {
    return myOpenFileDescriptor != null ? myOpenFileDescriptor.getFile() : null;
  }

  public void update(final boolean navigate) {
    if (updateRequested.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (updateRequested.compareAndSet(true, false)) {
          doShow(navigate);
        }
      }, myProject.getDisposed());
    }
  }

  public void updateGutterIcon(@Nullable final GutterIconRenderer renderer) {
    AppUIUtil.invokeOnEdt(() -> {
      if (myRangeHighlighter != null && myGutterIconRenderer != null) {
        myRangeHighlighter.setGutterIconRenderer(renderer);
      }
    });
  }

  private void doShow(boolean navigate) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    removeHighlighter();


    OpenFileDescriptor fileDescriptor = myOpenFileDescriptor;
    if (!navigate && myOpenFileDescriptor != null) {
      fileDescriptor = new OpenFileDescriptor(myProject, myOpenFileDescriptor.getFile());
    }
    myEditor = null;
    if (fileDescriptor != null) {
      if (!navigate) {
        FileEditor editor = FileEditorManager.getInstance(fileDescriptor.getProject()).getSelectedEditor(fileDescriptor.getFile());
        if (editor instanceof TextEditor) {
          myEditor = ((TextEditor)editor).getEditor();
        }
      }
      if (myEditor == null) {
        myEditor = XDebuggerUtilImpl.createEditor(fileDescriptor);
      }
    }
    if (myEditor != null) {
      addHighlighter();
    }
  }

  private void removeHighlighter() {
    if (myEditor != null) {
      adjustCounter(myEditor, -1);
    }

    //if (myNotTopFrame && myEditor != null) {
    //  myEditor.getSelectionModel().removeSelection();
    //}

    if (myRangeHighlighter != null) {
      myRangeHighlighter.dispose();
      myRangeHighlighter = null;
    }
  }

  private void addHighlighter() {
    adjustCounter(myEditor, 1);
    int line = mySourcePosition.getLine();
    Document document = myEditor.getDocument();
    if (line < 0 || line >= document.getLineCount()) return;

    //if (myNotTopFrame) {
    //  myEditor.getSelectionModel().setSelection(document.getLineStartOffset(line), document.getLineEndOffset(line) + document.getLineSeparatorLength(line));
    //  return;
    //}

    if (myRangeHighlighter != null) return;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = myNotTopFrame ? scheme.getAttributes(DebuggerColors.NOT_TOP_FRAME_ATTRIBUTES)
                                              : scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES);
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);
    if (mySourcePosition instanceof HighlighterProvider) {
      TextRange range = ((HighlighterProvider)mySourcePosition).getHighlightRange();
      if (range != null) {
        TextRange lineRange = DocumentUtil.getLineTextRange(document, line);
        range = range.intersection(lineRange);
        if (range != null && !range.isEmpty() && !range.equals(lineRange)) {
          myRangeHighlighter = markupModel.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(),
                                                               DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER, attributes,
                                                               HighlighterTargetArea.EXACT_RANGE);
        }
      }
    }
    if (myRangeHighlighter == null) {
      myRangeHighlighter = markupModel.addLineHighlighter(line, DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER, attributes);
    }
    myRangeHighlighter.putUserData(EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY, !myNotTopFrame);
    myRangeHighlighter.setEditorFilter(MarkupEditorFilterFactory.createIsNotDiffFilter());
    myRangeHighlighter.setGutterIconRenderer(myGutterIconRenderer);
  }

  public boolean isFullLineHighlighter() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myRangeHighlighter != null && myRangeHighlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE;
  }

  private static void adjustCounter(@NotNull final Editor editor, final int increment) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    // need to always invoke later to maintain order of increment/decrement
    SwingUtilities.invokeLater(() -> {
      JComponent component = editor.getComponent();
      Object o = component.getClientProperty(EditorImpl.IGNORE_MOUSE_TRACKING);
      Integer value = ((o instanceof Integer) ? (Integer)o : 0) + increment;
      component.putClientProperty(EditorImpl.IGNORE_MOUSE_TRACKING, value > 0 ? value : null);
    });
  }

  public interface HighlighterProvider {
    @Nullable
    TextRange getHighlightRange();
  }
}
