/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

/**
 * @author irengrig
 *         author: lesya
 */
@SuppressWarnings({"MethodMayBeStatic", "FieldAccessedSynchronizedAndUnsynchronized"})
public class LineStatusTracker extends LineStatusTrackerBase {
  public enum Mode {DEFAULT, SMART, SILENT}

  private static final Key<JPanel> PANEL_KEY = new Key<>("LineStatusTracker.CanNotCalculateDiffPanel");

  @NotNull private final VirtualFile myVirtualFile;

  @NotNull private final FileEditorManager myFileEditorManager;
  @NotNull private final VcsDirtyScopeManager myVcsDirtyScopeManager;

  @NotNull private Mode myMode;

  private LineStatusTracker(@NotNull final Project project,
                            @NotNull final Document document,
                            @NotNull final VirtualFile virtualFile,
                            @NotNull final Mode mode) {
    super(project, document);
    myVirtualFile = virtualFile;
    myMode = mode;

    myFileEditorManager = FileEditorManager.getInstance(project);
    myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
  }

  public static LineStatusTracker createOn(@NotNull VirtualFile virtualFile,
                                           @NotNull Document document,
                                           @NotNull Project project,
                                           @NotNull Mode mode) {
    return new LineStatusTracker(project, document, virtualFile, mode);
  }

  @NotNull
  @Override
  public Project getProject() {
    //noinspection ConstantConditions
    return super.getProject();
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @NotNull
  @CalledInAwt
  public Mode getMode() {
    return myMode;
  }

  @CalledInAwt
  public boolean isAvailableAt(@NotNull Editor editor) {
    return myMode != Mode.SILENT && editor.getSettings().isLineMarkerAreaShown() && !DiffUtil.isDiffEditor(editor);
  }

  @CalledInAwt
  public void setMode(@NotNull Mode mode) {
    if (myMode == mode) return;
    myMode = mode;

    reinstallRanges();
  }

  @Override
  @CalledInAwt
  protected boolean isDetectWhitespaceChangedLines() {
    return myMode == Mode.SMART;
  }

  @Override
  @CalledInAwt
  protected void installNotification(@NotNull String text) {
    final FileEditor[] editors = myFileEditorManager.getAllEditors(myVirtualFile);
    for (FileEditor editor : editors) {
      JPanel panel = editor.getUserData(PANEL_KEY);
      if (panel == null) {
        final JPanel newPanel = new EditorNotificationPanel().text(text);
        editor.putUserData(PANEL_KEY, newPanel);
        myFileEditorManager.addTopComponent(editor, newPanel);
      }
    }
  }

  @Override
  @CalledInAwt
  protected void destroyNotification() {
    final FileEditor[] editors = myFileEditorManager.getEditors(myVirtualFile);
    for (FileEditor editor : editors) {
      final JPanel panel = editor.getUserData(PANEL_KEY);
      if (panel != null) {
        myFileEditorManager.removeTopComponent(editor, panel);
        editor.putUserData(PANEL_KEY, null);
      }
    }
  }

  @Override
  @CalledInAwt
  protected void createHighlighter(@NotNull Range range) {
    myApplication.assertIsDispatchThread();

    if (range.getHighlighter() != null) {
      LOG.error("Multiple highlighters registered for the same Range");
      return;
    }

    if (myMode == Mode.SILENT) return;

    int first =
      range.getLine1() >= getLineCount(myDocument) ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getLine1());
    int second =
      range.getLine2() >= getLineCount(myDocument) ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getLine2());

    MarkupModel markupModel = DocumentMarkupModel.forDocument(myDocument, myProject, true);

    RangeHighlighter highlighter = LineStatusMarkerRenderer.createRangeHighlighter(range, new TextRange(first, second), markupModel);
    highlighter.setLineMarkerRenderer(LineStatusMarkerRenderer.createRenderer(range, (editor) -> {
      return new LineStatusTrackerDrawing.MyLineStatusMarkerPopup(this, editor, range);
    }));

    highlighter.setEditorFilter(MarkupEditorFilterFactory.createIsNotDiffFilter());

    range.setHighlighter(highlighter);
  }

  @Override
  @CalledInAwt
  protected void fireFileUnchanged() {
    if (GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
      // later to avoid saving inside document change event processing.
      TransactionGuard.getInstance().submitTransactionLater(getProject(), () -> {
        FileDocumentManager.getInstance().saveDocument(myDocument);
        List<Range> ranges = getRanges();
        if (ranges == null || ranges.isEmpty()) {
          // file was modified, and now it's not -> dirty local change
          myVcsDirtyScopeManager.fileDirty(myVirtualFile);
        }
      });
    }
  }

  @Override
  protected void doRollbackRange(@NotNull Range range) {
    super.doRollbackRange(range);
    markLinesUnchanged(range.getLine1(), range.getLine1() + range.getVcsLine2() - range.getVcsLine1());
  }

  private void markLinesUnchanged(int startLine, int endLine) {
    if (myDocument.getTextLength() == 0) return; // empty document has no lines
    if (startLine == endLine) return;
    ((DocumentImpl)myDocument).clearLineModificationFlags(startLine, endLine);
  }
}
