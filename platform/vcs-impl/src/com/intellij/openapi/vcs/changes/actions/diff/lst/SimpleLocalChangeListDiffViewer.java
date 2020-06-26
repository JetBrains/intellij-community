// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.simple.SimpleDiffChange;
import com.intellij.diff.tools.simple.SimpleDiffChangeUi;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffGutterOperation;
import com.intellij.diff.util.DiffGutterRenderer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LocalTrackerChange;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimpleLocalChangeListDiffViewer extends SimpleDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;

  private final boolean myAllowExcludeChangesFromCommit;

  private final LocalTrackerDiffUtil.LocalTrackerActionProvider myTrackerActionProvider;
  private LocalTrackerDiffUtil.ExcludeAllCheckboxPanel myExcludeAllCheckboxPanel;


  public SimpleLocalChangeListDiffViewer(@NotNull DiffContext context,
                                         @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myLocalRequest = localRequest;

    myAllowExcludeChangesFromCommit = DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context);
    myTrackerActionProvider = new MyLocalTrackerActionProvider(this, localRequest, myAllowExcludeChangesFromCommit);
    myExcludeAllCheckboxPanel.init(myLocalRequest, myAllowExcludeChangesFromCommit);

    LocalTrackerDiffUtil.installTrackerListener(this, myLocalRequest);
  }

  @NotNull
  @Override
  protected List<JComponent> createTitles() {
    List<JComponent> titles = DiffUtil.createTextTitles(myRequest, getEditors());
    assert titles.size() == 2;

    myExcludeAllCheckboxPanel = new LocalTrackerDiffUtil.ExcludeAllCheckboxPanel(this, getEditor2());

    BorderLayoutPanel titleWithCheckbox = JBUI.Panels.simplePanel();
    if (titles.get(1) != null) titleWithCheckbox.addToCenter(titles.get(1));
    titleWithCheckbox.addToLeft(myExcludeAllCheckboxPanel);

    return DiffUtil.createSyncHeightComponents(Arrays.asList(titles.get(0), titleWithCheckbox));
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>(super.createEditorPopupActions());
    group.addAll(LocalTrackerDiffUtil.createTrackerActions(myTrackerActionProvider));
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
      myLocalRequest.getChangelistId(),
      myTextDiffProvider,
      indicator,
      new MyLocalTrackerDiffHandler(indicator)
    );
  }

  private final class MyLocalTrackerDiffHandler implements LocalTrackerDiffUtil.LocalTrackerDiffHandler {
    @NotNull private final ProgressIndicator myIndicator;

    private MyLocalTrackerDiffHandler(@NotNull ProgressIndicator indicator) {
      myIndicator = indicator;
    }

    @NotNull
    @Override
    public Runnable done(boolean isContentsEqual,
                         CharSequence @NotNull [] texts,
                         @NotNull List<? extends LineFragment> fragments,
                         @NotNull List<LocalTrackerDiffUtil.LineFragmentData> fragmentsData) {
      List<SimpleDiffChange> changes = new ArrayList<>();

      for (int i = 0; i < fragments.size(); i++) {
        LineFragment fragment = fragments.get(i);
        LocalTrackerDiffUtil.LineFragmentData data = fragmentsData.get(i);

        boolean isExcludedFromCommit = data.isExcludedFromCommit();
        boolean isFromActiveChangelist = data.isFromActiveChangelist();
        boolean isSkipped = data.isSkipped();
        boolean isExcluded = data.isExcluded(myAllowExcludeChangesFromCommit);

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

  private static final class MySimpleDiffChangeUi extends SimpleDiffChangeUi {
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
      if (getViewer().myAllowExcludeChangesFromCommit) {
        ContainerUtil.addIfNotNull(myOperations, createExcludeGutterOperation());
      }
    }

    @Nullable
    private DiffGutterOperation createExcludeGutterOperation() {
      if (!getChange().isFromActiveChangelist()) return null;

      final boolean isExcludedFromCommit = getChange().isExcludedFromCommit();
      Icon icon = isExcludedFromCommit ? AllIcons.Diff.GutterCheckBox : AllIcons.Diff.GutterCheckBoxSelected;

      return createOperation(Side.RIGHT, (ctrlPressed, shiftPressed, altPressed) -> {
        return new DiffGutterRenderer(icon, DiffBundle.message("action.presentation.diff.include.into.commit.text")) {
          @Override
          protected void handleMouseClick() {
            if (!myChange.isValid()) return;

            int line = myChange.getStartLine(Side.RIGHT);
            LocalTrackerDiffUtil.toggleRangeAtLine(getViewer().myTrackerActionProvider, line, isExcludedFromCommit);
          }
        };
      });
    }
  }


  private static final class MyLocalTrackerActionProvider extends LocalTrackerDiffUtil.LocalTrackerActionProvider {
    @NotNull private final SimpleLocalChangeListDiffViewer myViewer;

    private MyLocalTrackerActionProvider(@NotNull SimpleLocalChangeListDiffViewer viewer,
                                         @NotNull LocalChangeListDiffRequest localRequest,
                                         boolean allowExcludeChangesFromCommit) {
      super(viewer, localRequest, allowExcludeChangesFromCommit);
      myViewer = viewer;
    }

    @Nullable
    @Override
    public List<LocalTrackerChange> getSelectedTrackerChanges(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(myViewer.getEditors(), editor);
      if (side == null) return null;

      return StreamEx.of(myViewer.getSelectedChanges(side))
        .select(MySimpleDiffChange.class)
        .map(it -> new LocalTrackerChange(it.getStartLine(Side.RIGHT),
                                          it.getEndLine(Side.RIGHT),
                                          it.myChangelistId,
                                          it.myIsExcludedFromCommit))
        .toList();
    }
  }
}
