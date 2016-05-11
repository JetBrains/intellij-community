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

import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.actions.ShowNextChangeMarkerAction;
import com.intellij.openapi.vcs.actions.ShowPrevChangeMarkerAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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

    @Override
    protected boolean isShowInnerDifferences() {
      return VcsApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES;
    }

    @NotNull
    @Override
    protected ActionToolbar buildToolbar(@Nullable Point mousePosition, @NotNull Disposable parentDisposable) {
      final DefaultActionGroup group = new DefaultActionGroup();

      final ShowPrevChangeMarkerAction localShowPrevAction = new ShowPrevChangeMarkerAction(myTracker.getPrevRange(myRange), myTracker, myEditor);
      final ShowNextChangeMarkerAction localShowNextAction = new ShowNextChangeMarkerAction(myTracker.getNextRange(myRange), myTracker, myEditor);
      final RollbackLineStatusRangeAction rollback = new RollbackLineStatusRangeAction(myTracker, myRange, myEditor);
      final ShowLineStatusRangeDiffAction showDiff = new ShowLineStatusRangeDiffAction(myTracker, myRange, myEditor);
      final CopyLineStatusRangeAction copyRange = new CopyLineStatusRangeAction(myTracker, myRange);
      final ToggleByWordDiffAction toggleWordDiff = new ToggleByWordDiffAction(myRange, myEditor, myTracker, mousePosition);

      group.add(localShowPrevAction);
      group.add(localShowNextAction);
      group.add(rollback);
      group.add(showDiff);
      group.add(copyRange);
      group.add(toggleWordDiff);

      JComponent editorComponent = myEditor.getComponent();
      DiffUtil.registerAction(localShowPrevAction, editorComponent);
      DiffUtil.registerAction(localShowNextAction, editorComponent);
      DiffUtil.registerAction(rollback, editorComponent);
      DiffUtil.registerAction(showDiff, editorComponent);
      DiffUtil.registerAction(copyRange, editorComponent);

      final List<AnAction> actionList = ActionUtil.getActions(editorComponent);
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          actionList.remove(localShowPrevAction);
          actionList.remove(localShowNextAction);
          actionList.remove(rollback);
          actionList.remove(showDiff);
          actionList.remove(copyRange);
        }
      });

      return ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, group, true);
    }

    @NotNull
    @Override
    protected FileType getFileType() {
      return myTracker.getVirtualFile().getFileType();
    }
  }

  private static class ToggleByWordDiffAction extends ToggleAction implements DumbAware {
    @NotNull private final Range myRange;
    @NotNull private final Editor myEditor;
    @NotNull private final LineStatusTracker myTracker;
    @Nullable private final Point myMousePosition;

    public ToggleByWordDiffAction(@NotNull Range range,
                                  @NotNull Editor editor,
                                  @NotNull LineStatusTracker tracker,
                                  @Nullable Point mousePosition) {
      super("Show Detailed Differences", null, AllIcons.Actions.PreviewDetails);
      myRange = range;
      myEditor = editor;
      myTracker = tracker;
      myMousePosition = mousePosition;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return VcsApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      VcsApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES = state;
      new MyLineStatusMarkerPopup(myTracker, myEditor, myRange).showHintAt(myMousePosition);
    }
  }
}
