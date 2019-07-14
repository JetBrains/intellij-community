// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.simple.SimpleDiffChange;
import com.intellij.diff.tools.simple.SimpleDiffChangeUi;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffGutterRenderer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ex.*;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.*;

public class SimpleLocalChangeListDiffViewer extends SimpleDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;
  @NotNull private final String myChangelistId;
  @NotNull private final String myChangelistName;

  private ExcludeAllCheckboxPanel myExcludeAllCheckboxPanel;

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
  protected List<JComponent> createTitles() {
    List<JComponent> titles = DiffUtil.createTextTitles(myRequest, getEditors());
    assert titles.size() == 2;

    myExcludeAllCheckboxPanel = new ExcludeAllCheckboxPanel();
    JPanel titleWithCheckbox = JBUI.Panels.simplePanel(titles.get(1)).addToLeft(myExcludeAllCheckboxPanel);

    return DiffUtil.createSyncHeightComponents(Arrays.asList(titles.get(0), titleWithCheckbox));
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>(super.createEditorPopupActions());

    group.add(new MoveSelectedChangesToAnotherChangelistAction());
    group.add(new ExcludeSelectedChangesFromCommitAction());
    group.add(new IncludeOnlySelectedChangesIntoCommitAction());

    return group;
  }

  @NotNull
  @Override
  protected SimpleDiffChangeUi createUi(@NotNull SimpleDiffChange change) {
    if (change instanceof MySimpleDiffChange) return new MySimpleDiffChangeUi(this, (MySimpleDiffChange)change);
    return super.createUi(change);
  }

  @NotNull
  private Runnable superComputeDifferences(@NotNull ProgressIndicator indicator) {
    return super.computeDifferences(indicator);
  }

  @Override
  @NotNull
  protected Runnable computeDifferences(@NotNull ProgressIndicator indicator) {
    return LocalTrackerDiffUtil.computeDifferences(
      myLocalRequest.getLineStatusTracker(),
      getContent1().getDocument(),
      getContent2().getDocument(),
      myChangelistId,
      myTextDiffProvider,
      indicator,
      new MyLocalTrackerDiffHandler(indicator)
    );
  }

  private class MyLocalTrackerDiffHandler implements LocalTrackerDiffUtil.LocalTrackerDiffHandler {
    @NotNull private final ProgressIndicator myIndicator;

    private MyLocalTrackerDiffHandler(@NotNull ProgressIndicator indicator) {
      myIndicator = indicator;
    }

    @NotNull
    @Override
    public Runnable done(boolean isContentsEqual,
                         @NotNull List<? extends LineFragment> fragments,
                         @NotNull List<LocalTrackerDiffUtil.LineFragmentData> fragmentsData) {
      List<SimpleDiffChange> changes = new ArrayList<>();

      for (int i = 0; i < fragments.size(); i++) {
        LineFragment fragment = fragments.get(i);
        LocalTrackerDiffUtil.LineFragmentData data = fragmentsData.get(i);

        boolean isExcludedFromCommit = data.isExcluded();
        boolean isFromActiveChangelist = data.getChangelistId().equals(myChangelistId);
        boolean isSkipped = !isFromActiveChangelist;
        boolean isExcluded = !isFromActiveChangelist ||
                             (myAllowExcludeChangesFromCommit && isExcludedFromCommit);

        changes.add(new MySimpleDiffChange(changes.size(), fragment, isExcluded, isSkipped,
                                           data.getChangelistId(), isFromActiveChangelist,
                                           isExcludedFromCommit));
      }

      return apply(changes, isContentsEqual);
    }

    @NotNull
    @Override
    public Runnable retryLater() {
      scheduleRediff();
      throw new ProcessCanceledException();
    }

    @NotNull
    @Override
    public Runnable fallback() {
      return superComputeDifferences(myIndicator);
    }

    @NotNull
    @Override
    public Runnable fallbackWithProgress() {
      Runnable callback = superComputeDifferences(myIndicator);
      return () -> {
        callback.run();
        getStatusPanel().setBusy(true);
      };
    }

    @NotNull
    @Override
    public Runnable error() {
      return applyNotification(DiffNotifications.createError());
    }
  }

  @Override
  protected void onAfterRediff() {
    super.onAfterRediff();
    myExcludeAllCheckboxPanel.refresh();
  }

  private static class MySimpleDiffChange extends SimpleDiffChange {
    @NotNull private final String myChangelistId;
    private final boolean myIsFromActiveChangelist;
    private final boolean myIsExcludedFromCommit;

    MySimpleDiffChange(int index,
                       @NotNull LineFragment fragment,
                       boolean isExcluded,
                       boolean isSkipped,
                       @NotNull String changelistId,
                       boolean isFromActiveChangelist,
                       boolean isExcludedFromCommit) {
      super(index, fragment, isExcluded, isSkipped);
      myChangelistId = changelistId;
      myIsFromActiveChangelist = isFromActiveChangelist;
      myIsExcludedFromCommit = isExcludedFromCommit;
    }

    @NotNull
    public String getChangelistId() {
      return myChangelistId;
    }

    public boolean isFromActiveChangelist() {
      return myIsFromActiveChangelist;
    }

    public boolean isExcludedFromCommit() {
      return myIsExcludedFromCommit;
    }
  }

  private static class MySimpleDiffChangeUi extends SimpleDiffChangeUi {
    private MySimpleDiffChangeUi(@NotNull SimpleLocalChangeListDiffViewer viewer, @NotNull MySimpleDiffChange change) {
      super(viewer, change);
    }

    @NotNull
    private SimpleLocalChangeListDiffViewer getViewer() {
      return (SimpleLocalChangeListDiffViewer)myViewer;
    }

    @NotNull
    private MySimpleDiffChange getChange() {
      return ((MySimpleDiffChange)myChange);
    }

    @Override
    protected void doInstallActionHighlighters() {
      super.doInstallActionHighlighters();
      if (getViewer().myAllowExcludeChangesFromCommit && getChange().isFromActiveChangelist()) {
        myOperations.add(new ExcludeGutterOperation());
      }
    }

    private class ExcludeGutterOperation extends GutterOperation {
      ExcludeGutterOperation() {
        super(Side.RIGHT);
      }

      @Override
      public GutterIconRenderer createRenderer() {
        if (!getChange().isFromActiveChangelist()) return null;

        final boolean isExcludedFromCommit = getChange().isExcludedFromCommit();
        Icon icon = isExcludedFromCommit ? AllIcons.Diff.GutterCheckBox : AllIcons.Diff.GutterCheckBoxSelected;
        return new DiffGutterRenderer(icon, "Include into commit") {
          @Override
          protected void handleMouseClick() {
            if (!myChange.isValid()) return;

            PartialLocalLineStatusTracker tracker = getViewer().getPartialTracker();
            if (tracker == null) return;
            LocalRange range = tracker.getRangeForLine(myChange.getStartLine(Side.RIGHT));
            if (range == null) return;

            tracker.setExcludedFromCommit(range, !isExcludedFromCommit);

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

    MyLineStatusTrackerManagerListener(@NotNull PartialLocalLineStatusTracker.Listener trackerListener) {
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

  private class MoveSelectedChangesToAnotherChangelistAction extends MySelectedChangesActionBase {
    MoveSelectedChangesToAnotherChangelistAction() {
      super(false);
      copyShortcutFrom(ActionManager.getInstance().getAction("Vcs.MoveChangedLinesToChangelist"));
    }

    @NotNull
    @Override
    protected String getText(@NotNull List<? extends MySimpleDiffChange> selectedChanges) {
      if (!selectedChanges.isEmpty() && ContainerUtil.and(selectedChanges, change -> !change.isFromActiveChangelist())) {
        String shortChangeListName = StringUtil.trimMiddle(myChangelistName, 40);
        return String.format("Move to '%s' Changelist", StringUtil.escapeMnemonics(shortChangeListName));
      }
      else {
        return ActionsBundle.message("action.ChangesView.Move.text");
      }
    }

    @Override
    protected void doPerform(@NotNull AnActionEvent e,
                             @NotNull PartialLocalLineStatusTracker tracker,
                             @NotNull List<? extends MySimpleDiffChange> selectedChanges) {
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
  }

  private class ExcludeSelectedChangesFromCommitAction extends MySelectedChangesActionBase {
    ExcludeSelectedChangesFromCommitAction() {
      super(true);
      ActionUtil.copyFrom(this, "Vcs.Diff.ExcludeChangedLinesFromCommit");
    }

    @NotNull
    @Override
    protected String getText(@NotNull List<? extends MySimpleDiffChange> selectedChanges) {
      boolean hasExcluded = ContainerUtil.or(selectedChanges, MySimpleDiffChange::isExcludedFromCommit);
      return !selectedChanges.isEmpty() && !hasExcluded ? "Exclude Lines from Commit" : "Include Lines into Commit";
    }

    @Override
    protected void doPerform(@NotNull AnActionEvent e,
                             @NotNull PartialLocalLineStatusTracker tracker,
                             @NotNull List<? extends MySimpleDiffChange> selectedChanges) {
      BitSet selectedLines = getLocalSelectedLines(selectedChanges);

      boolean hasExcluded = ContainerUtil.or(selectedChanges, MySimpleDiffChange::isExcludedFromCommit);
      tracker.setExcludedFromCommit(selectedLines, !hasExcluded);

      rediff();
    }
  }

  private class IncludeOnlySelectedChangesIntoCommitAction extends MySelectedChangesActionBase {
    IncludeOnlySelectedChangesIntoCommitAction() {
      super(true);
      ActionUtil.copyFrom(this, "Vcs.Diff.IncludeOnlyChangedLinesIntoCommit");
    }

    @Override
    protected void doPerform(@NotNull AnActionEvent e,
                             @NotNull PartialLocalLineStatusTracker tracker,
                             @NotNull List<? extends MySimpleDiffChange> selectedChanges) {
      BitSet selectedLines = getLocalSelectedLines(selectedChanges);

      tracker.setExcludedFromCommit(myChangelistId, true);
      tracker.setExcludedFromCommit(selectedLines, false);

      rediff();
    }
  }

  private abstract class MySelectedChangesActionBase extends DumbAwareAction {
    private final boolean myActiveChangelistOnly;

    MySelectedChangesActionBase(boolean activeChangelistOnly) {
      myActiveChangelistOnly = activeChangelistOnly;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myActiveChangelistOnly && !myAllowExcludeChangesFromCommit) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      if (DiffUtil.isFromShortcut(e)) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      PartialLocalLineStatusTracker tracker = getPartialTracker();
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (side == null || tracker == null) {
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(false);
        e.getPresentation().setText(getText(Collections.emptyList()));
        return;
      }

      List<MySimpleDiffChange> selectedChanges = getSelectedChanges(side);
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(!selectedChanges.isEmpty());
      e.getPresentation().setText(getText(selectedChanges));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PartialLocalLineStatusTracker tracker = getPartialTracker();
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (editor == null || side == null || tracker == null) return;

      List<MySimpleDiffChange> selectedChanges = getSelectedChanges(side);
      if (selectedChanges.isEmpty()) return;

      doPerform(e, tracker, selectedChanges);
    }

    @NotNull
    private List<MySimpleDiffChange> getSelectedChanges(@NotNull Side side) {
      List<MySimpleDiffChange> changes = ContainerUtil.findAll(SimpleLocalChangeListDiffViewer.this.getSelectedChanges(side),
                                                               MySimpleDiffChange.class);
      if (myActiveChangelistOnly) {
        return ContainerUtil.filter(changes, MySimpleDiffChange::isFromActiveChangelist);
      }
      else {
        return changes;
      }
    }

    @NotNull
    protected String getText(@NotNull List<? extends MySimpleDiffChange> selectedChanges) {
      return getTemplatePresentation().getText();
    }

    @CalledWithWriteLock
    protected abstract void doPerform(@NotNull AnActionEvent e,
                                      @NotNull PartialLocalLineStatusTracker tracker,
                                      @NotNull List<? extends MySimpleDiffChange> selectedChanges);
  }

  @NotNull
  private static BitSet getLocalSelectedLines(@NotNull List<? extends MySimpleDiffChange> changes) {
    BitSet selectedLines = new BitSet();
    for (SimpleDiffChange change : changes) {
      int startLine = change.getStartLine(Side.RIGHT);
      int endLine = change.getEndLine(Side.RIGHT);
      selectedLines.set(startLine, startLine == endLine ? startLine + 1 : endLine);
    }
    return selectedLines;
  }

  private class ExcludeAllCheckboxPanel extends JPanel {
    private final InplaceButton myCheckbox;

    private ExcludeAllCheckboxPanel() {
      myCheckbox = new InplaceButton(null, AllIcons.Diff.GutterCheckBox, e -> toggleState());
      myCheckbox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myCheckbox.setVisible(false);
      add(myCheckbox);

      getEditor2().getGutterComponentEx().addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          ApplicationManager.getApplication().invokeLater(() -> updateLayout(), ModalityState.any());
        }
      });
    }

    @Override
    public void doLayout() {
      Dimension size = myCheckbox.getPreferredSize();
      EditorGutterComponentEx gutter = getEditor2().getGutterComponentEx();
      int y = (getHeight() - size.height) / 2;
      int x = gutter.getIconAreaOffset() + 2; // "+2" from EditorGutterComponentImpl.processIconsRow
      myCheckbox.setBounds(Math.min(getWidth() - AllIcons.Diff.GutterCheckBox.getIconWidth(), x), Math.max(0, y), size.width, size.height);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = myCheckbox.getPreferredSize();
      EditorGutterComponentEx gutter = getEditor2().getGutterComponentEx();
      int gutterWidth = gutter.getLineMarkerFreePaintersAreaOffset();
      return new Dimension(Math.max(gutterWidth + JBUIScale.scale(2), size.width), size.height);
    }

    private void updateLayout() {
      invalidate();
      myPanel.validate();
      myPanel.repaint();
    }

    private void toggleState() {
      PartialLocalLineStatusTracker tracker = getPartialTracker();
      if (tracker != null && tracker.isValid()) {
        ExclusionState exclusionState = tracker.getExcludedFromCommitState(myChangelistId);
        getPartialTracker().setExcludedFromCommit(myChangelistId, exclusionState == ExclusionState.ALL_INCLUDED);
        refresh();
        rediff();
      }
    }

    public void refresh() {
      Icon icon = getIcon();
      if (icon != null) {
        myCheckbox.setIcon(icon);
        myCheckbox.setVisible(true);
      }
      else {
        myCheckbox.setVisible(false);
      }
    }

    @Nullable
    private Icon getIcon() {
      if (!myAllowExcludeChangesFromCommit) return null;

      PartialLocalLineStatusTracker tracker = getPartialTracker();
      if (tracker == null || !tracker.isValid()) return null;

      ExclusionState exclusionState = tracker.getExcludedFromCommitState(myChangelistId);
      switch (exclusionState) {
        case ALL_INCLUDED:
          return AllIcons.Diff.GutterCheckBoxSelected;
        case ALL_EXCLUDED:
          return AllIcons.Diff.GutterCheckBox;
        case PARTIALLY:
          return AllIcons.Diff.GutterCheckBoxIndeterminate;
        case NO_CHANGES:
        default:
          return null;
      }
    }
  }
}
