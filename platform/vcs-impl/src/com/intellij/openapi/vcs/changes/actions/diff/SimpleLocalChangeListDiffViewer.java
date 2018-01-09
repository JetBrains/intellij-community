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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffContext;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.simple.SimpleDiffChange;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ex.MoveChangesLineStatusAction;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker.LocalRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

public class SimpleLocalChangeListDiffViewer extends SimpleDiffViewer {
  @NotNull private final String myChangelistId;
  @NotNull private final PartialLocalLineStatusTracker myTracker;

  public SimpleLocalChangeListDiffViewer(@NotNull DiffContext context,
                                         @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myChangelistId = localRequest.getChangelistId();
    myTracker = (PartialLocalLineStatusTracker)notNull(localRequest.getLineStatusTracker());

    myTracker.addListener(new MyTrackerListener(), this);

    DiffUtil.registerAction(new MoveSelectedChangesToAnotherChangelistAction(true), myPanel);
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>(super.createEditorPopupActions());

    group.add(new MoveSelectedChangesToAnotherChangelistAction(false));

    return group;
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    if (getContent2().getDocument() != myTracker.getDocument()) {
      return applyNotification(DiffNotifications.createError()); // DiffRequest is out of date
    }

    try {
      indicator.checkCanceled();

      TrackerData data = ReadAction.compute(() -> {
        boolean isReleased = myTracker.isReleased();
        boolean isOperational = myTracker.isOperational();
        List<String> affectedChangelistIds = myTracker.getAffectedChangeListsIds();

        List<LocalRange> ranges = null;
        CharSequence localText = null;
        CharSequence vcsText = null;
        CharSequence trackerVcsText = null;
        if (isOperational) {
          ranges = myTracker.getRanges();

          localText = getContent2().getDocument().getImmutableCharSequence();
          vcsText = getContent1().getDocument().getImmutableCharSequence();
          trackerVcsText = myTracker.getVcsDocument().getImmutableCharSequence();
        }

        return new TrackerData(isReleased, isOperational, affectedChangelistIds, ranges, localText, vcsText, trackerVcsText);
      });


      if (data.isReleased) {
        return applyNotification(DiffNotifications.createError()); // DiffRequest is out of date
      }

      if (!data.isOperational &&
          data.affectedChangelist.size() == 1 &&
          data.affectedChangelist.contains(myChangelistId)) {
        // tracker is waiting for initialisation
        // there are only one changelist, so it's safe to fallback to default logic
        return super.performRediff(indicator);
      }

      if (data.ranges == null) {
        scheduleRediff();
        throw new ProcessCanceledException();
      }


      assert data.localText != null;
      assert data.vcsText != null;
      assert data.trackerVcsText != null;

      if (!StringUtil.equals(data.trackerVcsText, data.vcsText)) {
        return applyNotification(DiffNotifications.createError()); // DiffRequest is out of date
      }

      if (myTextDiffProvider.isHighlightingDisabled()) {
        return apply(new CompareData(null, null, data.ranges.isEmpty()));
      }

      List<Range> linesRanges = ContainerUtil.map(data.ranges, range -> {
        return new Range(range.getVcsLine1(), range.getVcsLine2(), range.getLine1(), range.getLine2());
      });

      List<List<LineFragment>> newFragments = notNull(myTextDiffProvider.compare(data.vcsText, data.localText, linesRanges, indicator));

      boolean isContentsEqual = data.ranges.isEmpty();
      BitSet areResolved = new BitSet();
      List<LineFragment> fragments = new ArrayList<>();

      for (int i = 0; i < data.ranges.size(); i++) {
        PartialLocalLineStatusTracker.LocalRange localRange = data.ranges.get(i);
        List<LineFragment> rangeFragments = newFragments.get(i);

        boolean isResolved = !localRange.getChangelistId().equals(myChangelistId);
        areResolved.set(fragments.size(), fragments.size() + newFragments.size(), isResolved);

        fragments.addAll(rangeFragments);
      }

      return apply(new CompareData(fragments, areResolved, isContentsEqual));
    }
    catch (DiffTooBigException e) {
      return applyNotification(DiffNotifications.createDiffTooBig());
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(DiffNotifications.createError());
    }
  }

  private class MyTrackerListener implements PartialLocalLineStatusTracker.Listener {
    @Override
    public void onBecomingValid() {
      scheduleRediff();
    }

    @Override
    public void onChangelistsChange() {
      scheduleRediff();
    }
  }

  private class MoveSelectedChangesToAnotherChangelistAction extends SelectedChangesActionBase {
    public MoveSelectedChangesToAnotherChangelistAction(boolean shortcut) {
      super(shortcut);
    }

    @Override
    protected boolean isVisible(@NotNull Side side) {
      return true;
    }

    @NotNull
    @Override
    protected String getText(@NotNull Side side) {
      return ActionsBundle.actionText(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST);
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull Side side) {
      return AllIcons.Actions.MoveToAnotherChangelist;
    }

    @Override
    protected void doPerform(@NotNull AnActionEvent e, @NotNull Side side, @NotNull List<SimpleDiffChange> changes) {
      BitSet selectedLines = new BitSet();
      for (SimpleDiffChange change : changes) {
        selectedLines.set(change.getStartLine(side), change.getEndLine(side));
      }

      MoveChangesLineStatusAction.moveToAnotherChangelist(myTracker, selectedLines);
    }
  }

  private static class TrackerData {
    private final boolean isReleased;
    public final boolean isOperational;
    @NotNull public final List<String> affectedChangelist;
    @Nullable public final List<LocalRange> ranges;
    @Nullable public final CharSequence localText;
    @Nullable public final CharSequence vcsText;
    @Nullable public final CharSequence trackerVcsText;

    public TrackerData(boolean isReleased,
                       boolean isOperational,
                       @NotNull List<String> affectedChangelist,
                       @Nullable List<LocalRange> ranges,
                       @Nullable CharSequence localText,
                       @Nullable CharSequence vcsText,
                       @Nullable CharSequence trackerVcsText) {
      this.isReleased = isReleased;
      this.isOperational = isOperational;
      this.affectedChangelist = affectedChangelist;
      this.ranges = ranges;
      this.localText = localText;
      this.vcsText = vcsText;
      this.trackerVcsText = trackerVcsText;
    }
  }
}
