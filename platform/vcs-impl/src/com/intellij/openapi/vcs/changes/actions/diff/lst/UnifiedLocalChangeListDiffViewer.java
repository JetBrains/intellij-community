// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.fragmented.UnifiedDiffChange;
import com.intellij.diff.tools.fragmented.UnifiedDiffChangeUi;
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer;
import com.intellij.diff.tools.fragmented.UnifiedFragmentBuilder;
import com.intellij.diff.util.DiffGutterOperation;
import com.intellij.diff.util.DiffGutterRenderer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class UnifiedLocalChangeListDiffViewer extends UnifiedDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;

  private final boolean myAllowExcludeChangesFromCommit;

  private final LocalTrackerDiffUtil.LocalTrackerActionProvider myTrackerActionProvider;
  private LocalTrackerDiffUtil.ExcludeAllCheckboxPanel myExcludeAllCheckboxPanel;

  public UnifiedLocalChangeListDiffViewer(@NotNull DiffContext context,
                                          @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myLocalRequest = localRequest;

    myAllowExcludeChangesFromCommit = DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context);
    myTrackerActionProvider = new MyLocalTrackerActionProvider(this, localRequest, myAllowExcludeChangesFromCommit);
    myExcludeAllCheckboxPanel.init(myLocalRequest, myAllowExcludeChangesFromCommit);

    LocalTrackerDiffUtil.installTrackerListener(this, myLocalRequest);
  }

  @Nullable
  @Override
  protected JComponent createTitles() {
    JComponent titles = super.createTitles();

    myExcludeAllCheckboxPanel = new LocalTrackerDiffUtil.ExcludeAllCheckboxPanel(this, getEditor());

    BorderLayoutPanel titleWithCheckbox = JBUI.Panels.simplePanel();
    if (titles != null) titleWithCheckbox.addToCenter(titles);
    titleWithCheckbox.addToLeft(myExcludeAllCheckboxPanel);
    return titleWithCheckbox;
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
  protected UnifiedDiffChangeUi createUi(@NotNull UnifiedDiffChange change) {
    if (change instanceof MyUnifiedDiffChange) return new MyUnifiedDiffChangeUi(this, (MyUnifiedDiffChange)change);
    return super.createUi(change);
  }

  @NotNull
  private Runnable superComputeDifferences(@NotNull ProgressIndicator indicator) {
    return super.computeDifferences(indicator);
  }

  @NotNull
  @Override
  protected Runnable computeDifferences(@NotNull ProgressIndicator indicator) {
    Document document1 = getContent1().getDocument();
    Document document2 = getContent2().getDocument();

    return LocalTrackerDiffUtil.computeDifferences(
      myLocalRequest.getLineStatusTracker(),
      document1,
      document2,
      myLocalRequest.getChangelistId(),
      myTextDiffProvider,
      indicator,
      new MyLocalTrackerDiffHandler(document1, document2, indicator));
  }

  private final class MyLocalTrackerDiffHandler implements LocalTrackerDiffUtil.LocalTrackerDiffHandler {
    @NotNull private final Document myDocument1;
    @NotNull private final Document myDocument2;
    @NotNull private final ProgressIndicator myIndicator;

    private MyLocalTrackerDiffHandler(@NotNull Document document1,
                                      @NotNull Document document2,
                                      @NotNull ProgressIndicator indicator) {
      myDocument1 = document1;
      myDocument2 = document2;
      myIndicator = indicator;
    }

    @NotNull
    @Override
    public Runnable done(boolean isContentsEqual,
                         CharSequence @NotNull [] texts,
                         @NotNull List<? extends LineFragment> fragments,
                         @NotNull List<LocalTrackerDiffUtil.LineFragmentData> fragmentsData) {
      UnifiedFragmentBuilder builder = ReadAction.compute(() -> {
        myIndicator.checkCanceled();
        return new MyUnifiedFragmentBuilder(fragments, fragmentsData, myDocument1, myDocument2).exec();
      });

      return apply(builder, texts, myIndicator);
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
      return applyErrorNotification();
    }
  }

  private class MyUnifiedFragmentBuilder extends UnifiedFragmentBuilder {
    @NotNull private final List<LocalTrackerDiffUtil.LineFragmentData> myFragmentsData;

    MyUnifiedFragmentBuilder(@NotNull List<? extends LineFragment> fragments,
                             @NotNull List<LocalTrackerDiffUtil.LineFragmentData> fragmentsData,
                             @NotNull Document document1,
                             @NotNull Document document2) {
      super(fragments, document1, document2, myMasterSide);
      myFragmentsData = fragmentsData;
    }

    @NotNull
    @Override
    protected UnifiedDiffChange createDiffChange(int blockStart,
                                                 int insertedStart,
                                                 int blockEnd,
                                                 int fragmentIndex) {
      LineFragment fragment = myFragments.get(fragmentIndex);
      LocalTrackerDiffUtil.LineFragmentData data = myFragmentsData.get(fragmentIndex);
      boolean isSkipped = data.isSkipped();
      boolean isExcluded = data.isExcluded(myAllowExcludeChangesFromCommit);
      return new MyUnifiedDiffChange(blockStart, insertedStart, blockEnd, fragment, isExcluded, isSkipped,
                                     data.getChangelistId(), data.isFromActiveChangelist(), data.isExcludedFromCommit());
    }
  }

  @Override
  protected void onAfterRediff() {
    super.onAfterRediff();
    myExcludeAllCheckboxPanel.refresh();
  }

  private static final class MyUnifiedDiffChange extends UnifiedDiffChange {
    @NotNull private final String myChangelistId;
    private final boolean myIsFromActiveChangelist;
    private final boolean myIsExcludedFromCommit;

    private MyUnifiedDiffChange(int blockStart,
                                int insertedStart,
                                int blockEnd,
                                @NotNull LineFragment lineFragment,
                                boolean isExcluded,
                                boolean isSkipped,
                                @NotNull String changelistId,
                                boolean isFromActiveChangelist,
                                boolean isExcludedFromCommit) {
      super(blockStart, insertedStart, blockEnd, lineFragment, isExcluded, isSkipped);
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

  private static final class MyUnifiedDiffChangeUi extends UnifiedDiffChangeUi {
    private MyUnifiedDiffChangeUi(@NotNull UnifiedLocalChangeListDiffViewer viewer,
                                  @NotNull MyUnifiedDiffChange change) {
      super(viewer, change);
    }

    @NotNull
    private UnifiedLocalChangeListDiffViewer getViewer() {
      return (UnifiedLocalChangeListDiffViewer)myViewer;
    }

    @NotNull
    private MyUnifiedDiffChange getChange() {
      return ((MyUnifiedDiffChange)myChange);
    }

    @Override
    protected void doInstallActionHighlighters() {
      if (getViewer().myAllowExcludeChangesFromCommit) {
        ContainerUtil.addIfNotNull(myOperations, createExcludeOperation());
      }
      super.doInstallActionHighlighters();
    }

    @Nullable
    private DiffGutterOperation createExcludeOperation() {
      if (!getChange().isFromActiveChangelist()) return null;

      return createOperation(() -> {
        final boolean isExcludedFromCommit = getChange().isExcludedFromCommit();
        Icon icon = isExcludedFromCommit ? AllIcons.Diff.GutterCheckBox : AllIcons.Diff.GutterCheckBoxSelected;
        return new DiffGutterRenderer(icon, DiffBundle.message("action.presentation.diff.include.into.commit.text")) {
          @Override
          protected void handleMouseClick() {
            if (!getViewer().isContentGood()) return;

            int line = myViewer.transferLineFromOneside(Side.RIGHT, myChange.getLine1());
            LocalTrackerDiffUtil.toggleRangeAtLine(getViewer().myTrackerActionProvider, line, isExcludedFromCommit);
          }
        };
      });
    }
  }

  private static final class MyLocalTrackerActionProvider extends LocalTrackerDiffUtil.LocalTrackerActionProvider {
    @NotNull private final UnifiedLocalChangeListDiffViewer myViewer;

    private MyLocalTrackerActionProvider(@NotNull UnifiedLocalChangeListDiffViewer viewer,
                                         @NotNull LocalChangeListDiffRequest localRequest,
                                         boolean allowExcludeChangesFromCommit) {
      super(viewer, localRequest, allowExcludeChangesFromCommit);
      myViewer = viewer;
    }

    @Nullable
    @Override
    public List<LocalTrackerDiffUtil.LocalTrackerChange> getSelectedTrackerChanges(@NotNull AnActionEvent e) {
      if (!myViewer.isContentGood()) return null;

      return StreamEx.of(myViewer.getSelectedChanges())
        .select(MyUnifiedDiffChange.class)
        .map(it -> new LocalTrackerDiffUtil.LocalTrackerChange(myViewer.transferLineFromOneside(Side.RIGHT, it.getLine1()),
                                                               myViewer.transferLineFromOneside(Side.RIGHT, it.getLine2()),
                                                               it.myChangelistId,
                                                               it.myIsExcludedFromCommit))
        .toList();
    }
  }
}
