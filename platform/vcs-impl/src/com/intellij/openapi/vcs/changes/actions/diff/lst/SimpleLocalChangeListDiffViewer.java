// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.simple.SimpleDiffChange;
import com.intellij.diff.tools.simple.SimpleDiffChangeUi;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LineFragmentData;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LocalTrackerChange;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.SelectedTrackerLine;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.ToggleableLineRange;
import com.intellij.openapi.vcs.ex.RangeExclusionState;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

import static com.intellij.openapi.vcs.ex.DocumentTrackerKt.countAffectedVisibleChanges;

public class SimpleLocalChangeListDiffViewer extends SimpleDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;

  private final boolean myAllowExcludeChangesFromCommit;

  private final LocalTrackerDiffUtil.LocalTrackerActionProvider myTrackerActionProvider;
  private final LocalTrackerDiffUtil.ExcludeAllCheckboxPanel myExcludeAllCheckboxPanel;
  private final GutterCheckboxMouseMotionListener myGutterCheckboxMouseMotionListener;

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

    myGutterCheckboxMouseMotionListener = new GutterCheckboxMouseMotionListener();
    myGutterCheckboxMouseMotionListener.install();

    for (AnAction action : LocalTrackerDiffUtil.createTrackerShortcutOnlyActions(myTrackerActionProvider)) {
      DiffUtil.registerAction(action, myPanel);
    }
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
    group.addAll(LocalTrackerDiffUtil.createTrackerEditorPopupActions(myTrackerActionProvider));
    return group;
  }

  @NotNull
  @Override
  protected SimpleDiffChangeUi createUi(@NotNull SimpleDiffChange change) {
    if (change instanceof MySimpleDiffChange) return new MySimpleDiffChangeUi(this, (MySimpleDiffChange)change);
    return super.createUi(change);
  }

  @Override
  @Nullable
  protected @Nls String getStatusTextMessage() {
    if (myAllowExcludeChangesFromCommit) {
      int totalCount = 0;
      int includedIntoCommitCount = 0;
      int excludedCount = 0;

      for (SimpleDiffChange change : getDiffChanges()) {
        RangeExclusionState exclusionState;
        if (change instanceof MySimpleDiffChange myChange) {
          exclusionState = myChange.getExclusionState();
        }
        else {
          exclusionState = RangeExclusionState.Included.INSTANCE;
        }

        totalCount += countAffectedVisibleChanges(exclusionState, false);
        if (change.isSkipped()) {
          excludedCount += countAffectedVisibleChanges(exclusionState, false);
        }
        else {
          includedIntoCommitCount += countAffectedVisibleChanges(exclusionState, true);
        }
      }

      return LocalTrackerDiffUtil.getStatusText(totalCount, includedIntoCommitCount, excludedCount, myModel.isContentsEqual());
    }
    return super.getStatusTextMessage();
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
      myAllowExcludeChangesFromCommit,
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
                                             data.getChangelistId(), data.isPartiallyExcluded(), data.getExclusionState()));
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

    myGutterCheckboxMouseMotionListener.destroyHoverHighlighter();
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
    RangeExclusionState exclusionState = fragmentData.getExclusionState();
    if (fragmentData.isPartiallyExcluded()) {
      RangeExclusionState.Partial partialExclusionState = (RangeExclusionState.Partial)exclusionState;
      Range lineRange = toggleableLineRange.getLineRange();

      partialExclusionState.iterateDeletionOffsets((start, end, isIncluded) -> {
        for (int i = start; i < end; i++) {
          result.add(createLineCheckboxToggleHighlighter(i + lineRange.start1, Side.LEFT, !isIncluded));
        }
        return null;
      });
      partialExclusionState.iterateAdditionOffsets((start, end, isIncluded) -> {
        for (int i = start; i < end; i++) {
          result.add(createLineCheckboxToggleHighlighter(i + lineRange.start2, Side.RIGHT, !isIncluded));
        }
        return null;
      });
    }
    else {
      result.add(createBlockCheckboxToggleHighlighter(toggleableLineRange));
    }

    if (LocalTrackerDiffUtil.shouldShowToggleAreaThumb(toggleableLineRange)) {
      result.add(createToggleAreaThumb(toggleableLineRange, Side.LEFT));
      result.add(createToggleAreaThumb(toggleableLineRange, Side.RIGHT));
    }

    return result;
  }

  @NotNull
  private RangeHighlighter createBlockCheckboxToggleHighlighter(@NotNull ToggleableLineRange toggleableLineRange) {
    Side side = Side.RIGHT;
    int line = LocalTrackerDiffUtil.getSingleCheckBoxLine(toggleableLineRange, side);
    boolean isExcludedFromCommit = toggleableLineRange.getFragmentData().getExclusionState() instanceof RangeExclusionState.Excluded;

    return LocalTrackerDiffUtil.createCheckboxToggle(getEditor(side), line, isExcludedFromCommit, () -> {
      LocalTrackerDiffUtil.toggleBlockExclusion(myTrackerActionProvider, line, isExcludedFromCommit);
    });
  }

  @NotNull
  private RangeHighlighter createLineCheckboxToggleHighlighter(int line, @NotNull Side side, boolean isExcludedFromCommit) {
    return LocalTrackerDiffUtil.createCheckboxToggle(getEditor(side), line, isExcludedFromCommit, () -> {
      LocalTrackerDiffUtil.toggleLinePartialExclusion(myTrackerActionProvider, line, side, isExcludedFromCommit);
    });
  }

  @NotNull
  private RangeHighlighter createToggleAreaThumb(@NotNull ToggleableLineRange toggleableLineRange, @NotNull Side side) {
    EditorEx editor = getEditor(side);
    Range lineRange = toggleableLineRange.getLineRange();
    int line1 = side.select(lineRange.start1, lineRange.start2);
    int line2 = side.select(lineRange.end1, lineRange.end2);
    boolean isExcludedFromCommit = toggleableLineRange.getFragmentData().getExclusionState() instanceof RangeExclusionState.Excluded;
    return LocalTrackerDiffUtil.createToggleAreaThumb(editor, line1, line2, () -> {
      LocalTrackerDiffUtil.toggleBlockExclusion(myTrackerActionProvider, lineRange.start1, isExcludedFromCommit);
    });
  }

  public static class MySimpleDiffChange extends SimpleDiffChange {
    private final @NotNull @NonNls String myChangelistId;
    private final boolean myIsPartiallyExcluded;
    private final @NotNull RangeExclusionState myExclusionState;

    MySimpleDiffChange(int index,
                       @NotNull LineFragment fragment,
                       boolean isExcluded,
                       boolean isSkipped,
                       @NotNull @NonNls String changelistId,
                       boolean isPartiallyExcluded,
                       @NotNull RangeExclusionState exclusionState) {
      super(index, fragment, isExcluded, isSkipped);
      myChangelistId = changelistId;
      myIsPartiallyExcluded = isPartiallyExcluded;
      myExclusionState = exclusionState;
    }

    public @NotNull @NonNls String getChangelistId() {
      return myChangelistId;
    }

    public boolean isPartiallyExcluded() {
      return myIsPartiallyExcluded;
    }

    public @NotNull RangeExclusionState getExclusionState() {
      return myExclusionState;
    }
  }

  private static final class MySimpleDiffChangeUi extends SimpleDiffChangeUi {
    private MySimpleDiffChangeUi(@NotNull SimpleLocalChangeListDiffViewer viewer,
                                 @NotNull MySimpleDiffChange change) {
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
    public void installHighlighter(@Nullable SimpleDiffChange previousChange) {
      if (getChange().isPartiallyExcluded() && getViewer().myAllowExcludeChangesFromCommit) {
        assert myHighlighters.isEmpty() && myOperations.isEmpty();

        int changeStart1 = getChange().getStartLine(Side.LEFT);
        int changeStart2 = getChange().getStartLine(Side.RIGHT);

        RangeExclusionState.Partial exclusionState = (RangeExclusionState.Partial)getChange().getExclusionState();
        exclusionState.iterateDeletionOffsets((start, end, isIncluded) -> {
          myHighlighters.addAll(
            new DiffDrawUtil.LineHighlighterBuilder(myViewer.getEditor(Side.LEFT),
                                                    start + changeStart1,
                                                    end + changeStart1,
                                                    TextDiffType.DELETED)
              .withExcludedInEditor(myChange.isSkipped())
              .withExcludedInGutter(!isIncluded)
              .withAlignedSides(myViewer.needAlignChanges())
              .done());
          return null;
        });
        exclusionState.iterateAdditionOffsets((start, end, isIncluded) -> {
          myHighlighters.addAll(
            new DiffDrawUtil.LineHighlighterBuilder(myViewer.getEditor(Side.RIGHT),
                                                    start + changeStart2,
                                                    end + changeStart2,
                                                    TextDiffType.INSERTED)
              .withExcludedInEditor(myChange.isSkipped())
              .withExcludedInGutter(!isIncluded)
              .withAlignedSides(myViewer.needAlignChanges())
              .done());
          return null;
        });

        if (exclusionState.getDeletionsCount() == 0) {
          myHighlighters.addAll(
            new DiffDrawUtil.LineHighlighterBuilder(myViewer.getEditor(Side.LEFT),
                                                    changeStart1,
                                                    changeStart1,
                                                    TextDiffType.INSERTED)
              .withExcludedInEditor(myChange.isSkipped())
              .withExcludedInGutter(false)
              .withAlignedSides(myViewer.needAlignChanges())
              .done());
        }
        if (exclusionState.getAdditionsCount() == 0) {
          myHighlighters.addAll(
            new DiffDrawUtil.LineHighlighterBuilder(myViewer.getEditor(Side.RIGHT),
                                                    changeStart2,
                                                    changeStart2,
                                                    TextDiffType.DELETED)
              .withExcludedInEditor(myChange.isSkipped())
              .withExcludedInGutter(false)
              .withAlignedSides(myViewer.needAlignChanges())
              .done());
        }

        // do not draw ">>"
        // doInstallActionHighlighters();
      }
      else {
        super.installHighlighter(previousChange);
      }
    }

    @Override
    public boolean drawDivider(DiffDividerDrawUtil.DividerPaintable.@NotNull Handler handler) {
      if (getChange().isPartiallyExcluded() && getViewer().myAllowExcludeChangesFromCommit) {
        int startLine1 = myChange.getStartLine(Side.LEFT);
        int endLine1 = myChange.getEndLine(Side.LEFT);
        int startLine2 = myChange.getStartLine(Side.RIGHT);
        int endLine2 = myChange.getEndLine(Side.RIGHT);

        if (myViewer.needAlignChanges()) {
          if (startLine1 != endLine1) {
            if (!handler.processAligned(startLine1, endLine1, startLine2, startLine2, TextDiffType.DELETED)) {
              return false;
            }
          }
          if (startLine2 != endLine2) {
            if (!handler.processAligned(endLine1, endLine1, startLine2, endLine2, TextDiffType.INSERTED)) {
              return false;
            }
          }
          return true;
        }
        else {
          if (startLine1 != endLine1) {
            if (!handler.processExcludable(startLine1, endLine1, startLine2, startLine2, TextDiffType.DELETED,
                                           myChange.isExcluded(), myChange.isSkipped())) {
              return false;
            }
          }
          if (startLine2 != endLine2) {
            if (!handler.processExcludable(endLine1, endLine1, startLine2, endLine2, TextDiffType.INSERTED,
                                           myChange.isExcluded(), myChange.isSkipped())) {
              return false;
            }
          }
          return true;
        }
      }
      else {
        return super.drawDivider(handler);
      }
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
                                          it.getExclusionState()))
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

  private class GutterCheckboxMouseMotionListener {
    private @Nullable RangeHighlighter myHighlighter;

    public void install() {
      for (Side side : Side.values()) {
        MyGutterMouseListener listener = new MyGutterMouseListener(side);
        getEditor(side).getGutterComponentEx().addMouseListener(listener);
        getEditor(side).getGutterComponentEx().addMouseMotionListener(listener);
      }
    }

    public void destroyHoverHighlighter() {
      if (myHighlighter != null) {
        myHighlighter.dispose();
        myHighlighter = null;
      }
    }

    private void updateHoverHighlighter(@NotNull Side side, int line) {
      MySimpleDiffChange change = ObjectUtils.tryCast(ContainerUtil.find(getDiffChanges(), it -> it.getStartLine(side) <= line &&
                                                                                                 it.getEndLine(side) > line),
                                                      MySimpleDiffChange.class);
      if (change == null ||
          change.isPartiallyExcluded() ||
          !myLocalRequest.getChangelistId().equals(change.getChangelistId())) {
        destroyHoverHighlighter();
        return;
      }

      EditorEx editor = getEditor(side);
      if (LocalTrackerDiffUtil.hasIconHighlighters(myProject, editor, line)) {
        if (myHighlighter != null && editor.getDocument().getLineNumber(myHighlighter.getStartOffset()) != line) {
          destroyHoverHighlighter();
        }
        return;
      }

      destroyHoverHighlighter();

      boolean isExcludedFromCommit = change.getExclusionState() instanceof RangeExclusionState.Excluded;
      myHighlighter = LocalTrackerDiffUtil.createCheckboxToggle(editor, line, isExcludedFromCommit, () -> {
        LocalTrackerDiffUtil.toggleLinePartialExclusion(myTrackerActionProvider, line, side, isExcludedFromCommit);
        destroyHoverHighlighter();
      });
    }

    private class MyGutterMouseListener extends MouseAdapter {
      private final Side mySide;

      MyGutterMouseListener(@NotNull Side side) {
        mySide = side;
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        if (!myAllowExcludeChangesFromCommit) {
          destroyHoverHighlighter();
          return;
        }

        EditorEx editor = getEditor(mySide);
        EditorGutterComponentEx gutter = editor.getGutterComponentEx();
        int xOffset = DiffUtil.isMirrored(editor) ? gutter.getWidth() - e.getX() : e.getX();
        if (xOffset < gutter.getIconAreaOffset() || xOffset > gutter.getIconAreaOffset() + gutter.getIconsAreaWidth()) {
          destroyHoverHighlighter();
          return;
        }

        LogicalPosition position = editor.xyToLogicalPosition(e.getPoint());
        updateHoverHighlighter(mySide, position.line);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        destroyHoverHighlighter();
      }
    }
  }
}
