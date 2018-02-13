// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ex.MoveChangesLineStatusAction;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker.LocalRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

public class SimpleLocalChangeListDiffViewer extends SimpleDiffViewer {
  @NotNull private final String myChangelistId;
  @NotNull private final String myChangelistName;
  @NotNull private final PartialLocalLineStatusTracker myTracker;

  public SimpleLocalChangeListDiffViewer(@NotNull DiffContext context,
                                         @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myChangelistId = localRequest.getChangelistId();
    myChangelistName = localRequest.getChangelistName();
    myTracker = (PartialLocalLineStatusTracker)notNull(localRequest.getLineStatusTracker());

    myTracker.addListener(new MyTrackerListener(), this);

    DiffUtil.registerAction(new MoveSelectedChangesToAnotherChangelistAction(true), myPanel);
  }

  @NotNull
  @Override
  public Project getProject() {
    //noinspection ConstantConditions
    return super.getProject();
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
      BitSet areSkipped = new BitSet();
      List<LineFragment> fragments = new ArrayList<>();

      for (int i = 0; i < data.ranges.size(); i++) {
        PartialLocalLineStatusTracker.LocalRange localRange = data.ranges.get(i);
        List<LineFragment> rangeFragments = newFragments.get(i);

        boolean isSkipped = !localRange.getChangelistId().equals(myChangelistId);
        areSkipped.set(fragments.size(), fragments.size() + newFragments.size(), isSkipped);

        fragments.addAll(rangeFragments);
      }

      return apply(new CompareData(fragments, areSkipped, isContentsEqual));
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
    public void onChangeListsChange() {
    }

    @Override
    public void onChangeListMarkerChange() {
      scheduleRediff();
    }
  }

  private class MoveSelectedChangesToAnotherChangelistAction extends DumbAwareAction {
    private final boolean myShortcut;

    public MoveSelectedChangesToAnotherChangelistAction(boolean shortcut) {
      myShortcut = shortcut;
      copyShortcutFrom(ActionManager.getInstance().getAction("Vcs.MoveChangedLinesToChangelist"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myShortcut) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (side == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      List<SimpleDiffChange> selectedChanges = getSelectedChanges(side);

      String text;
      if (!selectedChanges.isEmpty() && ContainerUtil.and(selectedChanges, change -> change.isSkipped())) {
        String shortChangeListName = StringUtil.trimMiddle(myChangelistName, 40);
        text = String.format("Move to '%s' Changelist", shortChangeListName);
      }
      else {
        text = ActionsBundle.message("action.ChangesView.Move.text");
      }

      e.getPresentation().setText(text);
      e.getPresentation().setIcon(AllIcons.Actions.MoveToAnotherChangelist);

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(!selectedChanges.isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (editor == null || side == null) return;

      List<SimpleDiffChange> selectedChanges = getSelectedChanges(side);
      if (selectedChanges.isEmpty()) return;

      BitSet selectedLines = new BitSet();
      for (SimpleDiffChange change : selectedChanges) {
        selectedLines.set(change.getStartLine(side), change.getEndLine(side));
      }

      if (ContainerUtil.and(selectedChanges, change -> change.isSkipped())) {
        LocalChangeList changeList = ChangeListManager.getInstance(getProject()).getChangeList(myChangelistId);
        if (changeList != null) myTracker.moveToChangelist(selectedLines, changeList);
      }
      else {
        MoveChangesLineStatusAction.moveToAnotherChangelist(myTracker, selectedLines);
      }

      rediff();
    }

    @NotNull
    private List<SimpleDiffChange> getSelectedChanges(@NotNull Side side) {
      EditorEx editor = getEditor(side);
      BitSet lines = DiffUtil.getSelectedLines(editor);

      return ContainerUtil.filter(getDiffChanges(), change -> {
        int line1 = change.getStartLine(side);
        int line2 = change.getEndLine(side);

        return DiffUtil.isSelectedByLine(lines, line1, line2);
      });
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
