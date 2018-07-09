/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  public void update(AnActionEvent e) {
    Data data = getDataFromContext(e);
    boolean isEnabled = data != null && data.tracker.isValid() && data.tracker.isAvailableAt(data.editor) &&
                        getTargetRange(data.tracker, data.editor) != null;

    e.getPresentation().setEnabled(isEnabled);
    e.getPresentation().setVisible(data != null || e.isFromActionToolbar());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Data data = getDataFromContext(e);
    if (data == null) return;

    Range targetRange = getTargetRange(data.tracker, data.editor);
    if (targetRange == null) return;

    moveToRange(data.tracker, data.editor, targetRange);
  }

  @Nullable
  private static Data getDataFromContext(AnActionEvent e) {
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
    protected Range getTargetRange(@NotNull LineStatusTracker<?> tracker, int line) {
      return tracker.getNextRange(line);
    }
  }

  public static class Prev extends ShowChangeMarkerAction {
    protected Range getTargetRange(@NotNull LineStatusTracker<?> tracker, int line) {
      return tracker.getPrevRange(line);
    }
  }

  public static class Current extends ShowChangeMarkerAction {
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
