/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vcs.actions.ShowNextChangeMarkerAction;
import com.intellij.openapi.vcs.actions.ShowPrevChangeMarkerAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class LineStatusTrackerDrawing {
  private LineStatusTrackerDrawing() {
  }

  public static void moveToRange(Range range, Editor editor, LineStatusTracker tracker) {
    new MyLineStatusMarkerPopup(tracker, editor, range).scrollAndShow();
  }

  public static void showHint(Range range, Editor editor, LineStatusTracker tracker) {
    new MyLineStatusMarkerPopup(tracker, editor, range).showAfterScroll();
  }

  public static class MyLineStatusMarkerPopup extends LineStatusMarkerPopup {
    @NotNull private final LineStatusTracker myTracker;

    public MyLineStatusMarkerPopup(@NotNull LineStatusTracker tracker,
                                   @NotNull Editor editor,
                                   @NotNull Range range) {
      super(tracker, editor, range);
      myTracker = tracker;
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions(@Nullable Point mousePosition) {
      List<AnAction> actions = new ArrayList<>();
      actions.add(new ShowPrevChangeMarkerAction(myTracker.getPrevRange(myRange), myTracker, myEditor));
      actions.add(new ShowNextChangeMarkerAction(myTracker.getNextRange(myRange), myTracker, myEditor));
      actions.add(new RollbackLineStatusRangeAction(myTracker, myRange, myEditor));
      actions.add(new ShowLineStatusRangeDiffAction(myTracker, myRange, myEditor));
      actions.add(new CopyLineStatusRangeAction(myTracker, myRange));
      actions.add(new ToggleByWordDiffAction(myRange, myEditor, myTracker, mousePosition));
      return actions;
    }

    @NotNull
    @Override
    protected FileType getFileType() {
      return myTracker.getVirtualFile().getFileType();
    }
  }

  private static class ToggleByWordDiffAction extends LineStatusMarkerPopup.ToggleByWordDiffActionBase {
    @NotNull private final Range myRange;
    @NotNull private final Editor myEditor;
    @NotNull private final LineStatusTracker myTracker;
    @Nullable private final Point myMousePosition;

    public ToggleByWordDiffAction(@NotNull Range range,
                                  @NotNull Editor editor,
                                  @NotNull LineStatusTracker tracker,
                                  @Nullable Point mousePosition) {
      myRange = range;
      myEditor = editor;
      myTracker = tracker;
      myMousePosition = mousePosition;
    }

    @Override
    protected void reshowPopup() {
      new MyLineStatusMarkerPopup(myTracker, myEditor, myRange).showHintAt(myMousePosition);
    }
  }
}
