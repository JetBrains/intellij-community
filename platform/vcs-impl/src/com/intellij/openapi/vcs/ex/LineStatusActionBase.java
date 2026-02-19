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
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class LineStatusActionBase extends DumbAwareAction {
  public static final DataKey<Integer> SELECTED_OFFSET_KEY = DataKey.create("VCS_LINE_MARKER_SELECTED_OFFSET_KEY");

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    Integer selectedOffset = e.getData(SELECTED_OFFSET_KEY);
    if (project == null || editor == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
    if (tracker == null || !tracker.isValid() || !tracker.isAvailableAt(editor) || !isEnabled(tracker, editor)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    boolean isEnabled;
    if (selectedOffset != null) {
      int line = editor.getDocument().getLineNumber(selectedOffset);
      isEnabled = tracker.getRangeForLine(line) != null;
    }
    else {
      isEnabled = isSomeChangeSelected(editor, tracker);
    }

    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(isEnabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    Integer selectedOffset = e.getData(SELECTED_OFFSET_KEY);
    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
    assert tracker != null;

    Range range = null;
    if (selectedOffset != null) {
      int line = editor.getDocument().getLineNumber(selectedOffset);
      range = tracker.getRangeForLine(line);
    }

    doAction(tracker, editor, range);
  }

  private static boolean isSomeChangeSelected(@NotNull Editor editor, @NotNull LineStatusTrackerI<?> tracker) {
    return DiffUtil.isSomeRangeSelected(editor, lines -> !ContainerUtil.isEmpty(tracker.getRangesForLines(lines)));
  }

  protected boolean isEnabled(@NotNull LineStatusTrackerI<?> tracker, @NotNull Editor editor) {
    return true;
  }

  protected abstract void doAction(@NotNull LineStatusTrackerI<?> tracker, @NotNull Editor editor, @Nullable Range range);
}
