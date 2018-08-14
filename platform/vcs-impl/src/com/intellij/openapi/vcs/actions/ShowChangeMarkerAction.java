// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ShowChangeMarkerAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Data data = getDataFromContext(e);
    boolean isEnabled = data != null && data.tracker.isValid() && data.tracker.isAvailableAt(data.editor) &&
                        getTargetRange(data.tracker, data.editor) != null;

    e.getPresentation().setEnabled(isEnabled);
    e.getPresentation().setVisible(data != null || e.isFromActionToolbar());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Data data = getDataFromContext(e);
    if (data == null) return;

    Range targetRange = getTargetRange(data.tracker, data.editor);
    if (targetRange == null) return;

    moveToRange(data.tracker, data.editor, targetRange);
  }

  @Nullable
  private static Data getDataFromContext(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return null;

    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
    if (tracker == null) return null;

    return new Data(tracker, editor);
  }


  protected void moveToRange(@NotNull LineStatusTracker<?> tracker, @NotNull Editor editor, @NotNull Range range) {
    tracker.scrollAndShowHint(range, editor);
  }

  @Nullable
  private Range getTargetRange(@NotNull LineStatusTracker<?> tracker, @NotNull Editor editor) {
    int line = editor.getCaretModel().getLogicalPosition().line;
    return getTargetRange(tracker, line);
  }

  @Nullable
  protected abstract Range getTargetRange(@NotNull LineStatusTracker<?> tracker, int line);


  public static class Next extends ShowChangeMarkerAction {
    @Override
    protected Range getTargetRange(@NotNull LineStatusTracker<?> tracker, int line) {
      return tracker.getNextRange(line);
    }
  }

  public static class Prev extends ShowChangeMarkerAction {
    @Override
    protected Range getTargetRange(@NotNull LineStatusTracker<?> tracker, int line) {
      return tracker.getPrevRange(line);
    }
  }

  public static class Current extends ShowChangeMarkerAction {
    @Override
    protected Range getTargetRange(@NotNull LineStatusTracker<?> tracker, int line) {
      return tracker.getRangeForLine(line);
    }

    @Override
    protected void moveToRange(@NotNull LineStatusTracker<?> tracker, @NotNull Editor editor, @NotNull Range range) {
      tracker.showHint(range, editor);
    }
  }


  private static class Data {
    @NotNull private final LineStatusTracker<?> tracker;
    @NotNull private final Editor editor;

    public Data(@NotNull LineStatusTracker<?> tracker, @NotNull Editor editor) {
      this.tracker = tracker;
      this.editor = editor;
    }
  }
}
