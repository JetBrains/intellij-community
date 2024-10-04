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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.List;

import static java.util.Collections.singletonList;

@ApiStatus.Internal
public class MoveChangesLineStatusAction extends LineStatusActionBase {
  @Override
  protected boolean isEnabled(@NotNull LineStatusTrackerI<?> tracker, @NotNull Editor editor) {
    return tracker instanceof PartialLocalLineStatusTracker;
  }

  @Override
  protected void doAction(@NotNull LineStatusTrackerI<?> tracker, @NotNull Editor editor, @Nullable Range range) {
    moveToAnotherChangelist((PartialLocalLineStatusTracker)tracker, editor);
  }

  public static void moveToAnotherChangelist(@NotNull PartialLocalLineStatusTracker tracker, @NotNull Editor editor) {
    moveToAnotherChangelist(tracker, DiffUtil.getSelectedLines(editor));
  }

  public static void moveToAnotherChangelist(@NotNull PartialLocalLineStatusTracker tracker, @NotNull BitSet selectedLines) {
    Project project = tracker.getProject();

    List<LocalRange> ranges = tracker.getRangesForLines(selectedLines);
    if (ranges == null || ranges.isEmpty()) return;

    LocalChangeList targetList = MoveChangesToAnotherListAction.askTargetChangelist(project, ranges, tracker);
    if (targetList == null) return;

    tracker.moveToChangelist(selectedLines, targetList);
  }

  public static void moveToAnotherChangelist(@NotNull PartialLocalLineStatusTracker tracker, @NotNull LocalRange range) {
    Project project = tracker.getProject();

    LocalChangeList targetList = MoveChangesToAnotherListAction.askTargetChangelist(project, singletonList(range), tracker);
    if (targetList == null) return;

    tracker.moveToChangelist(range, targetList);
  }
}
