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
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.List;

public class MoveChangesLineStatusAction extends LineStatusActionBase {
  @Override
  protected boolean isEnabled(@NotNull LineStatusTrackerBase<?> tracker, @NotNull Editor editor) {
    return tracker instanceof PartialLocalLineStatusTracker;
  }

  @Override
  protected void doAction(@NotNull LineStatusTrackerBase<?> tracker, @NotNull Editor editor) {
    moveToAnotherChangelist((PartialLocalLineStatusTracker)tracker, editor);
  }

  public static void moveToAnotherChangelist(@NotNull PartialLocalLineStatusTracker tracker, @NotNull Editor editor) {
    Project project = tracker.getProject();
    BitSet selectedLines = DiffUtil.getSelectedLines(editor);

    List<PartialLocalLineStatusTracker.LocalRange> ranges = tracker.getRangesForLines(selectedLines);
    if (ranges == null || ranges.isEmpty()) return;

    LocalChangeList selectedList = askTargetChangelist(project);
    if (selectedList == null) return;

    tracker.moveToChangelist(selectedLines, selectedList);
  }

  public static void moveToAnotherChangelist(@NotNull PartialLocalLineStatusTracker tracker, @NotNull Range range) {
    Project project = tracker.getProject();

    LocalChangeList selectedList = askTargetChangelist(project);
    if (selectedList == null) return;

    tracker.moveToChangelist(range, selectedList);
  }

  @Nullable
  private static LocalChangeList askTargetChangelist(Project project) {
    ChangeListChooser chooser = new ChangeListChooser(project,
                                                      ChangeListManager.getInstance(project).getChangeListsCopy(),
                                                      null,
                                                      ActionsBundle.message("action.ChangesView.Move.text"),
                                                      null);
    chooser.show();

    return chooser.getSelectedList();
  }
}
