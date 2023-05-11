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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LineFragmentData;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LocalTrackerChange;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.SelectedTrackerLine;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.ToggleableLineRange;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class SimpleLocalChangeListDiffViewer extends SimpleDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;

  private final boolean myAllowExcludeChangesFromCommit;

  private final LocalTrackerDiffUtil.LocalTrackerActionProvider myTrackerActionProvider;
  private final LocalTrackerDiffUtil.ExcludeAllCheckboxPanel myExcludeAllCheckboxPanel;

  private final @NotNull List<RangeHighlighter> myToggleExclusionsHighlighters = new ArrayList<>();

  public SimpleLocalChangeListDiffViewer(@NotNull DiffContext context,
                                         @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myLocalRequest = localRequest;

    myAllowExcludeChangesFromCommit = DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context);
    myTrackerActionProvider = new MyLocalTrackerActionProvider(this, localRequest, myAllowExcludeChangesFromCommit);
    myExcludeAllCheckboxPanel = new LocalTrackerDiffUtil.ExcludeAllCheckboxPanel(this, getEditor2());
    myExcludeAllCheckboxPanel.init(myLocalRequest, myAllowExcludeChangesFromCommit);

    LocalTrackerDiffUtil.installTrackerListener(this, myLocalRequest);
  }

  @NotNull
  @Override
  protected List<JComponent> createTitles() {
    List<JComponent> titles = DiffUtil.createTextTitles(this, myRequest, getEditors());
    assert titles.size() == 2;

    BorderLayoutPanel titleWithCheckbox = JBUI.Panels.simplePanel();
    if (titles.get(1) != null) titleWithCheckbox.addToCenter(titles.get(1));
    titleWithCheckbox.addToLeft(myExcludeAllCheckboxPanel);

    return Arrays.asList(titles.get(0), titleWithCheckbox);
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
                         @NotNull List<ToggleableLineRange> toggleableLineRanges) {
      List<SimpleDiffChange> changes = new ArrayList<>();

      for (ToggleableLineRange toggleableLineRange : toggleableLineRanges) {
        LineFragmentData data = toggleableLineRange.getFragmentData();
        boolean isSkipped = data.isSkipped();
        boolean isExcluded = data.isExcluded(myAllowExcludeChangesFromCommit);

        for (LineFragment fragment : toggleableLineRange.getFragments()) {
          changes.add(new MySimpleDiffChange(changes.size(), fragment, isExcluded, isSkipped,
                                             data.getChangelistId(), data.isFromActiveChangelist(),
                                             data.isExcludedFromCommit(), data.isPartiallyExcluded()));
        }
      }

      Runnable applyChanges = apply(changes, isContentsEqual);
      Runnable applyGutterExcludeOperations = applyGutterOperations(toggleableLineRanges);

      return () -> {
        applyChanges.run();
        applyGutterExcludeOperations.run();
      };
    }

    @NotNull
    @Override
    public Runnable retryLater() {
      ApplicationManager.getApplication().invokeLater(() -> scheduleRediff());
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

  @Override
  protected void clearDiffPresentation() {
    super.clearDiffPresentation();

    for (RangeHighlighter operation : myToggleExclusionsHighlighters) {
      operation.dispose();
    }
    myToggleExclusionsHighlighters.clear();
  }

  private @NotNull Runnable applyGutterOperations(@NotNull List<ToggleableLineRange> toggleableLineRanges) {
    if (!myAllowExcludeChangesFromCommit) return EmptyRunnable.INSTANCE;

    return () -> {
      for (ToggleableLineRange toggleableLineRange : toggleableLineRanges) {
        myToggleExclusionsHighlighters.addAll(createGutterToggleRenderers(toggleableLineRange));
      }
      getEditor1().getGutterComponentEx().revalidateMarkup();
      getEditor2().getGutterComponentEx().revalidateMarkup();
    };
  }

  private @NotNull List<RangeHighlighter> createGutterToggleRenderers(@NotNull ToggleableLineRange toggleableLineRange) {
    LineFragmentData fragmentData = toggleableLineRange.getFragmentData();
    if (!fragmentData.isFromActiveChangelist()) return Collections.emptyList();

    List<RangeHighlighter> result = new ArrayList<>();
    result.add(createCheckboxToggleHighlighter(toggleableLineRange));
    if (LocalTrackerDiffUtil.shouldShowToggleAreaThumb(toggleableLineRange)) {
      result.add(createToggleAreaThumb(toggleableLineRange, Side.LEFT));
      result.add(createToggleAreaThumb(toggleableLineRange, Side.RIGHT));
    }

    return result;
  }

  @NotNull
  private RangeHighlighter createCheckboxToggleHighlighter(@NotNull ToggleableLineRange toggleableLineRange) {
    Range lineRange = toggleableLineRange.getLineRange();
    LineFragmentData fragmentData = toggleableLineRange.getFragmentData();
    LineFragment firstFragment = ContainerUtil.getFirstItem(toggleableLineRange.getFragments());

    Side side = ObjectUtils.chooseNotNull(fragmentData.getPartialExclusionSide(), Side.RIGHT);
    EditorEx editor = getEditor(side);
    int line = firstFragment != null ? side.getStartLine(firstFragment)
                                     : side.select(lineRange.getVcsLine1(), lineRange.getLine1());
    int offset = DiffGutterOperation.lineToOffset(editor, line);

    Icon icon = fragmentData.isExcludedFromCommit() ? AllIcons.Diff.GutterCheckBox : AllIcons.Diff.GutterCheckBoxSelected;
    RangeHighlighter checkboxHighlighter = editor.getMarkupModel().addRangeHighlighter(null, offset, offset,
                                                                                       HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                                       HighlighterTargetArea.LINES_IN_RANGE);
    checkboxHighlighter.setGutterIconRenderer(
      new DiffGutterRenderer(icon, DiffBundle.message("action.presentation.diff.include.into.commit.text")) {
        @Override
        protected void handleMouseClick() {
          LocalTrackerDiffUtil.toggleRangeAtLine(myTrackerActionProvider, line, fragmentData);
        }
      });

    return checkboxHighlighter;
  }

  @NotNull
  private RangeHighlighter createToggleAreaThumb(@NotNull ToggleableLineRange toggleableLineRange, @NotNull Side side) {
    EditorEx editor = getEditor(side);
    Range lineRange = toggleableLineRange.getLineRange();
    int line1 = side.select(lineRange.getVcsLine1(), lineRange.getLine1());
    int line2 = side.select(lineRange.getVcsLine2(), lineRange.getLine2());
    return LocalTrackerDiffUtil.createToggleAreaThumb(editor, line1, line2);
  }

  private static class MySimpleDiffChange extends SimpleDiffChange {
    @NotNull private final String myChangelistId;
    private final boolean myIsFromActiveChangelist;
    private final boolean myIsExcludedFromCommit;
    private final boolean myIsPartiallyExcluded;

    MySimpleDiffChange(int index,
                       @NotNull LineFragment fragment,
                       boolean isExcluded,
                       boolean isSkipped,
                       @NotNull String changelistId,
                       boolean isFromActiveChangelist,
                       boolean isExcludedFromCommit,
                       boolean isPartiallyExcluded) {
      super(index, fragment, isExcluded, isSkipped);
      myChangelistId = changelistId;
      myIsFromActiveChangelist = isFromActiveChangelist;
      myIsExcludedFromCommit = isExcludedFromCommit;
      myIsPartiallyExcluded = isPartiallyExcluded;
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

    private boolean isPartiallyExcluded() {
      return myIsPartiallyExcluded;
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
      if (getChange().isPartiallyExcluded()) return; // do not draw multiple ">>"
      super.doInstallActionHighlighters();
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
                                          it.getChangelistId(),
                                          it.isExcludedFromCommit()))
        .toList();
    }

    @Override
    public @Nullable SelectedTrackerLine getSelectedTrackerLines(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(myViewer.getEditors(), editor);
      if (editor == null || side == null) return null;

      BitSet selectedLines = DiffUtil.getSelectedLines(editor);
      if (side.isLeft()) {
        return new SelectedTrackerLine(selectedLines, null);
      }
      else {
        return new SelectedTrackerLine(null, selectedLines);
      }
    }
  }
}
