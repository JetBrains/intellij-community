/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public class RollbackLineStatusAction extends LineStatusActionBase {
  @Override
  protected void doAction(@NotNull LineStatusTrackerBase<?> tracker, @NotNull Editor editor) {
    rollback(tracker, editor);
  }

  public static void rollback(@NotNull LineStatusTrackerBase<?> tracker, @NotNull Editor editor) {
    BitSet selectedLines = DiffUtil.getSelectedLines(editor);
    tracker.rollbackChanges(selectedLines);
  }

  public static void rollback(@NotNull LineStatusTrackerBase<?> tracker, @NotNull Range range, @Nullable Editor editor) {
    if (editor != null) DiffUtil.moveCaretToLineRangeIfNeeded(editor, range.getLine1(), range.getLine2());
    tracker.rollbackChanges(range);
  }
}
