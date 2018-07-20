// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.simple.SimpleDiffChange;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffGutterRenderer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.MoveChangesLineStatusAction;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker.LocalRange;
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

public class SimpleLocalChangeListDiffViewer extends SimpleDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;
  @NotNull private final String myChangelistId;
  @NotNull private final String myChangelistName;

  private final boolean myAllowExcludeChangesFromCommit;

  public SimpleLocalChangeListDiffViewer(@NotNull DiffContext context,
                                         @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myLocalRequest = localRequest;
    myChangelistId = localRequest.getChangelistId();
    myChangelistName = localRequest.getChangelistName();

    MyTrackerListener trackerListener = new MyTrackerListener();
    MyLineStatusTrackerManagerListener lstmListener = new MyLineStatusTrackerManagerListener(trackerListener);

    LineStatusTrackerManager.getInstanceImpl(getProject()).addTrackerListener(lstmListener, this);

    PartialLocalLineStatusTracker tracker = getPartialTracker();
    if (tracker != null) tracker.addListener(trackerListener, this);

    DiffUtil.registerAction(new MoveSelectedChangesToAnotherChangelistAction(true), myPanel);
    DiffUtil.registerAction(new ExcludeSelectedChangesFromCommitAction(true), myPanel);

    myAllowExcludeChangesFromCommit = Boolean.TRUE.equals(context.getUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT));
  }

  @NotNull
  @Override
  public Project getProject() {
    //noinspection ConstantConditions
    return super.getProject();
  }

  @Nullable
  public PartialLocalLineStatusTracker getPartialTracker() {
    return ObjectUtils.tryCast(myLocalRequest.getLineStatusTracker(), PartialLocalLineStatusTracker.class);
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>(super.createEditorPopupActions());

    group.add(new MoveSelectedChangesToAnotherChangelistAction(false));
    group.add(new ExcludeSelectedChangesFromCommitAction(false));

    return group;
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    LineStatusTracker tracker = myLocalRequest.getLineStatusTracker();
    if (tracker instanceof SimpleLocalLineStatusTracker) {
      // partial changes are disabled for file (ex: it is marked as "unmodified")
      return super.performRediff(indicator);
    }

    PartialLocalLineStatusTracker partialTracker = ObjectUtils.tryCast(tracker, PartialLocalLineStatusTracker.class);
    if (partialTracker == null || getContent2().getDocument() != tracker.getDocument()) {
      return applyNotification(DiffNotifications.createError()); // DiffRequest is out of date
    }

    try {
      indicator.checkCanceled();

      TrackerData data = ReadAction.compute(() -> {
        boolean isReleased = partialTracker.isReleased();
        boolean isOperational = partialTracker.isOperational();
        List<String> affectedChangelistIds = partialTracker.getAffectedChangeListsIds();

        if (!isOperational) {
          return new TrackerData(isReleased, affectedChangelistIds, null);
        }

        List<LocalRange> ranges = partialTracker.getRanges();

        CharSequence localText = getContent2().getDocument().getImmutableCharSequence();
        CharSequence vcsText = getContent1().getDocument().getImmutableCharSequence();
        CharSequence trackerVcsText = partialTracker.getVcsDocument().getImmutableCharSequence();

        TrackerDiffData diffData = new TrackerDiffData(ranges, localText, vcsText, trackerVcsText);
        return new TrackerData(isReleased, affectedChangelistIds, diffData);
      });


      if (data.isReleased) {
        return applyNotification(DiffNotifications.createError()); // DiffRequest is out of date
      }

      if (data.diffData == null &&
          data.affectedChangelist.size() == 1 &&
          data.affectedChangelist.contains(myChangelistId)) {
        // tracker is waiting for initialisation
        // there are only one changelist, so it's safe to fallback to default logic
        Runnable callback = super.performRediff(indicator);
        return () -> {
          callback.run();
          getStatusPanel().setBusy(true);
        };
      }

      TrackerDiffData diffData = data.diffData;
      if (diffData == null || diffData.ranges == null) {
        scheduleRediff();
        throw new ProcessCanceledException();
      }

      return performRediffUsingPartialTracker(diffData.ranges, diffData.localText, diffData.vcsText, diffData.trackerVcsText, indicator);
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

  @NotNull
  private Runnable performRediffUsingPartialTracker(@NotNull List<LocalRange> ranges,
                                                    @NotNull CharSequence localText,
                                                    @NotNull CharSequence vcsText,
                                                    @NotNull CharSequence trackerVcsText,
                                                    @NotNull ProgressIndicator indicator) {
    if (!StringUtil.equals(trackerVcsText, vcsText)) {
      return applyNotification(DiffNotifications.createError()); // DiffRequest is out of date
    }

    if (myTextDiffProvider.isHighlightingDisabled()) {
      return apply(new CompareData(null, ranges.isEmpty()));
    }


    List<Range> linesRanges = ContainerUtil.map(ranges, range -> {
      return new Range(range.getVcsLine1(), range.getVcsLine2(), range.getLine1(), range.getLine2());
    });

    List<List<LineFragment>> newFragments = notNull(myTextDiffProvider.compare(vcsText, localText, linesRanges, indicator));

    boolean isContentsEqual = ranges.isEmpty();
    List<SimpleDiffChange> changes = new ArrayList<>();

    for (int i = 0; i < ranges.size(); i++) {
      LocalRange localRange = ranges.get(i);
      List<LineFragment> rangeFragments = newFragments.get(i);

      boolean isExcludedFromCommit = localRange.isExcludedFromCommit();
      boolean isFromActiveChangelist = localRange.getChangelistId().equals(myChangelistId);
      boolean isSkipped = !isFromActiveChangelist;
      boolean isExcluded = !isFromActiveChangelist || (myAllowExcludeChangesFromCommit && isExcludedFromCommit);

      changes.addAll(ContainerUtil.map(rangeFragments, fragment -> {
        return new MySimpleDiffChange(fragment, isExcluded, isSkipped, localRange.getChangelistId(), isExcludedFromCommit);
      }));
    }

    return apply(new CompareData(changes, isContentsEqual));
  }


  private class MySimpleDiffChange extends SimpleDiffChange {
    @NotNull private final String myChangelistId;
    private final boolean myIsExcludedFromCommit;

    public MySimpleDiffChange(@NotNull LineFragment fragment,
                              boolean isExcluded,
                              boolean isSkipped,
                              @NotNull String changelistId,
                              boolean isExcludedFromCommit) {
      super(SimpleLocalChangeListDiffViewer.this, fragment, isExcluded, isSkipped);
      myChangelistId = changelistId;
      myIsExcludedFromCommit = isExcludedFromCommit;
    }

    @NotNull
    private SimpleLocalChangeListDiffViewer getViewer() {
      return (SimpleLocalChangeListDiffViewer)myViewer;
    }

    public boolean isFromActiveChangelist() {
      return myChangelistId.equals(getViewer().myChangelistId);
    }

    public boolean isExcludedFromCommit() {
      return myIsExcludedFromCommit;
    }

    @Override
    protected void doInstallActionHighlighters() {
      super.doInstallActionHighlighters();
      if (myAllowExcludeChangesFromCommit && isFromActiveChangelist()) {
        myOperations.add(new ExcludeGutterOperation());
      }
    }

    private class ExcludeGutterOperation extends GutterOperation {
      public ExcludeGutterOperation() {
        super(Side.RIGHT);
      }

      @Override
      public GutterIconRenderer createRenderer() {
        if (!isFromActiveChangelist()) return null;

        Icon icon = myIsExcludedFromCommit ? AllIcons.Diff.GutterCheckBox : AllIcons.Diff.GutterCheckBoxSelected;
        return new DiffGutterRenderer(icon, "Include into commit") {
          @Override
          protected void performAction(AnActionEvent e) {
            if (!isValid()) return;

            PartialLocalLineStatusTracker tracker = getViewer().getPartialTracker();
            if (tracker == null) return;
            LocalRange range = tracker.getRangeForLine(getStartLine(Side.RIGHT));
            if (range == null) return;

            tracker.setExcludedFromCommit(range, !myIsExcludedFromCommit);

            getViewer().rediff();
          }
        };
      }
    }
  }


  private class MyTrackerListener extends PartialLocalLineStatusTracker.ListenerAdapter {
    @Override
    public void onBecomingValid(@NotNull PartialLocalLineStatusTracker tracker) {
      scheduleRediff();
    }

    @Override
    public void onChangeListMarkerChange(@NotNull PartialLocalLineStatusTracker tracker) {
      scheduleRediff();
    }

    @Override
    public void onExcludedFromCommitChange(@NotNull PartialLocalLineStatusTracker tracker) {
      scheduleRediff();
    }
  }

  private class MyLineStatusTrackerManagerListener extends LineStatusTrackerManager.ListenerAdapter {
    @NotNull private final PartialLocalLineStatusTracker.Listener myListener;

    public MyLineStatusTrackerManagerListener(@NotNull PartialLocalLineStatusTracker.Listener trackerListener) {
      myListener = trackerListener;
    }

    @Override
    public void onTrackerAdded(@NotNull LineStatusTracker<?> tracker) {
      if (tracker instanceof PartialLocalLineStatusTracker &&
          tracker.getVirtualFile().equals(myLocalRequest.getVirtualFile())) {
        ((PartialLocalLineStatusTracker)tracker).addListener(myListener, SimpleLocalChangeListDiffViewer.this);
        scheduleRediff();
      }
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

      PartialLocalLineStatusTracker tracker = getPartialTracker();
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (side == null || tracker == null) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setVisible(true);
        return;
      }

      List<MySimpleDiffChange> selectedChanges = getSelectedChanges(side);

      String text;
      if (!selectedChanges.isEmpty() && ContainerUtil.and(selectedChanges, change -> !change.isFromActiveChangelist())) {
        String shortChangeListName = StringUtil.trimMiddle(myChangelistName, 40);
        text = String.format("Move to '%s' Changelist", shortChangeListName);
      }
      else {
        text = ActionsBundle.message("action.ChangesView.Move.text");
      }

      e.getPresentation().setText(text);

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(!selectedChanges.isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PartialLocalLineStatusTracker tracker = getPartialTracker();
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (editor == null || side == null || tracker == null) return;

      List<MySimpleDiffChange> selectedChanges = getSelectedChanges(side);
      if (selectedChanges.isEmpty()) return;

      BitSet selectedLines = getLocalSelectedLines(selectedChanges);

      if (ContainerUtil.and(selectedChanges, change -> !change.isFromActiveChangelist())) {
        LocalChangeList changeList = ChangeListManager.getInstance(getProject()).getChangeList(myChangelistId);
        if (changeList != null) tracker.moveToChangelist(selectedLines, changeList);
      }
      else {
        MoveChangesLineStatusAction.moveToAnotherChangelist(tracker, selectedLines);
      }

      rediff();
    }

    @NotNull
    private List<MySimpleDiffChange> getSelectedChanges(@NotNull Side side) {
      return ContainerUtil.findAll(SimpleLocalChangeListDiffViewer.this.getSelectedChanges(side), MySimpleDiffChange.class);
    }
  }

  private class ExcludeSelectedChangesFromCommitAction extends DumbAwareAction {
    private final boolean myShortcut;

    public ExcludeSelectedChangesFromCommitAction(boolean shortcut) {
      myShortcut = shortcut;
      ActionUtil.copyFrom(this, "Vcs.Diff.ExcludeChangedLinesFromCommit");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!myAllowExcludeChangesFromCommit) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      if (myShortcut) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      PartialLocalLineStatusTracker tracker = getPartialTracker();
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (side == null || tracker == null) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setVisible(true);
        return;
      }

      List<MySimpleDiffChange> activeChanges = getActiveChanges(side);
      boolean hasExcluded = ContainerUtil.or(activeChanges, MySimpleDiffChange::isExcludedFromCommit);

      e.getPresentation().setText(activeChanges.isEmpty() || !hasExcluded ? "Exclude Lines from Commit" : "Include Lines into Commit");

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(!activeChanges.isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PartialLocalLineStatusTracker tracker = getPartialTracker();
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (editor == null || side == null || tracker == null) return;

      List<MySimpleDiffChange> activeChanges = getActiveChanges(side);
      if (activeChanges.isEmpty()) return;

      BitSet selectedLines = getLocalSelectedLines(activeChanges);

      boolean hasExcluded = ContainerUtil.or(activeChanges, MySimpleDiffChange::isExcludedFromCommit);
      tracker.setExcludedFromCommit(selectedLines, !hasExcluded);

      rediff();
    }

    @NotNull
    private List<MySimpleDiffChange> getActiveChanges(@NotNull Side side) {
      List<MySimpleDiffChange> selectedChanges = ContainerUtil.findAll(getSelectedChanges(side), MySimpleDiffChange.class);
      return ContainerUtil.filter(selectedChanges, MySimpleDiffChange::isFromActiveChangelist);
    }
  }

  @NotNull
  private static BitSet getLocalSelectedLines(@NotNull List<MySimpleDiffChange> changes) {
    BitSet selectedLines = new BitSet();
    for (SimpleDiffChange change : changes) {
      int startLine = change.getStartLine(Side.RIGHT);
      int endLine = change.getEndLine(Side.RIGHT);
      selectedLines.set(startLine, startLine == endLine ? startLine + 1 : endLine);
    }
    return selectedLines;
  }

  private static class TrackerData {
    private final boolean isReleased;
    @NotNull public final List<String> affectedChangelist;
    @Nullable public final TrackerDiffData diffData;

    public TrackerData(boolean isReleased,
                       @NotNull List<String> affectedChangelist,
                       @Nullable TrackerDiffData diffData) {
      this.isReleased = isReleased;
      this.affectedChangelist = affectedChangelist;
      this.diffData = diffData;
    }
  }

  private static class TrackerDiffData {
    @Nullable public final List<LocalRange> ranges;
    @NotNull public final CharSequence localText;
    @NotNull public final CharSequence vcsText;
    @NotNull public final CharSequence trackerVcsText;

    public TrackerDiffData(@Nullable List<LocalRange> ranges,
                           @NotNull CharSequence localText,
                           @NotNull CharSequence vcsText,
                           @NotNull CharSequence trackerVcsText) {
      this.ranges = ranges;
      this.localText = localText;
      this.vcsText = vcsText;
      this.trackerVcsText = trackerVcsText;
    }
  }
}
