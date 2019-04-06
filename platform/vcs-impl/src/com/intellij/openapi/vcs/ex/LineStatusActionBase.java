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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public abstract class LineStatusActionBase extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (project == null || editor == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
    if (tracker == null || !tracker.isValid() || !tracker.isAvailableAt(editor) || !isEnabled(tracker, editor)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    if (!isSomeChangeSelected(editor, tracker)) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
    assert tracker != null;

    doAction(tracker, editor);
  }

  private static boolean isSomeChangeSelected(@NotNull Editor editor, @NotNull LineStatusTrackerI<?> tracker) {
    return DiffUtil.isSomeRangeSelected(editor, lines -> !ContainerUtil.isEmpty(tracker.getRangesForLines(lines)));
  }

  protected boolean isEnabled(@NotNull LineStatusTrackerI<?> tracker, @NotNull Editor editor) {
    return true;
  }

  protected abstract void doAction(@NotNull LineStatusTrackerI<?> tracker, @NotNull Editor editor);
}
