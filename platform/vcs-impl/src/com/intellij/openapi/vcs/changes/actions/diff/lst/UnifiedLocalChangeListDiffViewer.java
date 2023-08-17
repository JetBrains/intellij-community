// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.codeWithMe.ClientId;
import com.intellij.diff.DiffContext;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.fragmented.*;
import com.intellij.diff.util.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LineFragmentData;
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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.ex.DocumentTrackerKt.countAffectedVisibleChanges;
import static com.intellij.openapi.vcs.ex.LineStatusClientIdTrackerKt.createClientIdGutterIconRenderer;

public class UnifiedLocalChangeListDiffViewer extends UnifiedDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;

  private final boolean myAllowExcludeChangesFromCommit;

  private final LocalTrackerDiffUtil.LocalTrackerActionProvider myTrackerActionProvider;
  private final LocalTrackerDiffUtil.ExcludeAllCheckboxPanel myExcludeAllCheckboxPanel;
  private final GutterCheckboxMouseMotionListener myGutterCheckboxMouseMotionListener;

  private final @NotNull List<RangeHighlighter> myHighlighters = new ArrayList<>();

  public UnifiedLocalChangeListDiffViewer(@NotNull DiffContext context,
                                          @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myLocalRequest = localRequest;

    myAllowExcludeChangesFromCommit = DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context);
    myTrackerActionProvider = new MyLocalTrackerActionProvider(this, localRequest, myAllowExcludeChangesFromCommit);
    myExcludeAllCheckboxPanel = new LocalTrackerDiffUtil.ExcludeAllCheckboxPanel(this, getEditor());
    myExcludeAllCheckboxPanel.init(myLocalRequest, myAllowExcludeChangesFromCommit);

    LocalTrackerDiffUtil.installTrackerListener(this, myLocalRequest);

    myGutterCheckboxMouseMotionListener = new GutterCheckboxMouseMotionListener();
    myGutterCheckboxMouseMotionListener.install();

    for (AnAction action : LocalTrackerDiffUtil.createTrackerShortcutOnlyActions(myTrackerActionProvider)) {
      DiffUtil.registerAction(action, myPanel);
    }
  }

  @Nullable
  @Override
  protected JComponent createTitles() {
    JComponent titles = super.createTitles();

    BorderLayoutPanel titleWithCheckbox = JBUI.Panels.simplePanel();
    if (titles != null) titleWithCheckbox.addToCenter(titles);
    titleWithCheckbox.addToLeft(myExcludeAllCheckboxPanel);
    return titleWithCheckbox;
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
  protected UnifiedDiffChangeUi createUi(@NotNull UnifiedDiffChange change) {
    if (change instanceof MyUnifiedDiffChange) return new MyUnifiedDiffChangeUi(this, (MyUnifiedDiffChange)change);
    return super.createUi(change);
  }

  @Override
  @Nullable
  protected @Nls String getStatusTextMessage() {
    List<UnifiedDiffChange> allChanges = getDiffChanges();
    if (myAllowExcludeChangesFromCommit && allChanges != null) {
      int totalCount = 0;
      int includedIntoCommitCount = 0;
      int excludedCount = 0;

      for (UnifiedDiffChange change : allChanges) {
        RangeExclusionState exclusionState;
        if (change instanceof MyUnifiedDiffChange myChange) {
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
      myAllowExcludeChangesFromCommit,
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
                         @NotNull List<ToggleableLineRange> toggleableLineRanges) {
      @NotNull List<LineFragment> fragments = new ArrayList<>();
      @NotNull List<LineFragmentData> fragmentsData = new ArrayList<>();

      for (ToggleableLineRange range : toggleableLineRanges) {
        List<LineFragment> rangeFragments = range.getFragments();
        fragments.addAll(rangeFragments);
        fragmentsData.addAll(Collections.nCopies(rangeFragments.size(), range.getFragmentData()));
      }

      UnifiedFragmentBuilder builder = ReadAction.compute(() -> {
        myIndicator.checkCanceled();
        return new MyUnifiedFragmentBuilder(fragments, fragmentsData, myDocument1, myDocument2).exec();
      });

      Runnable applyChanges = apply(builder, texts, myIndicator);
      Runnable applyGutterExcludeOperations = applyGutterOperations(builder, toggleableLineRanges);

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
      return applyErrorNotification();
    }
  }

  private class MyUnifiedFragmentBuilder extends UnifiedFragmentBuilder {
    @NotNull private final List<LineFragmentData> myFragmentsData;

    MyUnifiedFragmentBuilder(@NotNull List<? extends LineFragment> fragments,
                             @NotNull List<LineFragmentData> fragmentsData,
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
      LineFragment fragment = getFragments().get(fragmentIndex);
      LineFragmentData data = myFragmentsData.get(fragmentIndex);
      boolean isSkipped = data.isSkipped();
      boolean isExcluded = data.isExcluded(myAllowExcludeChangesFromCommit);
      return new MyUnifiedDiffChange(blockStart, insertedStart, blockEnd, fragment, isExcluded, isSkipped,
                                     data.getChangelistId(), data.isPartiallyExcluded(), data.getExclusionState());
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

    for (RangeHighlighter operation : myHighlighters) {
      operation.dispose();
    }
    myHighlighters.clear();

    myGutterCheckboxMouseMotionListener.destroyHoverHighlighter();
  }

  private @NotNull Runnable applyGutterOperations(@NotNull UnifiedFragmentBuilder builder,
                                                  @NotNull List<ToggleableLineRange> toggleableLineRanges) {
    return () -> {
      if (myAllowExcludeChangesFromCommit) {
        for (ToggleableLineRange toggleableLineRange : toggleableLineRanges) {
          myHighlighters.addAll(createGutterToggleRenderers(builder, toggleableLineRange));
        }
      }

      for (ToggleableLineRange range : toggleableLineRanges) {
        ContainerUtil.addIfNotNull(myHighlighters, createClientIdHighlighter(builder, range));
      }

      if (!myHighlighters.isEmpty()) {
        getEditor().getGutterComponentEx().revalidateMarkup();
      }
    };
  }

  private @NotNull List<RangeHighlighter> createGutterToggleRenderers(@NotNull UnifiedFragmentBuilder builder,
                                                                      @NotNull ToggleableLineRange toggleableLineRange) {
    LineFragmentData fragmentData = toggleableLineRange.getFragmentData();
    if (!fragmentData.isFromActiveChangelist()) return Collections.emptyList();

    List<RangeHighlighter> result = new ArrayList<>();
    RangeExclusionState exclusionState = fragmentData.getExclusionState();
    if (fragmentData.isPartiallyExcluded()) {
      RangeExclusionState.Partial partialExclusionState = (RangeExclusionState.Partial)exclusionState;
      Range lineRange = toggleableLineRange.getLineRange();

      partialExclusionState.iterateDeletionOffsets((start, end, isIncluded) -> {
        for (int i = start; i < end; i++) {
          result.add(createLineCheckboxToggleHighlighter(builder, i + lineRange.start1, Side.LEFT, !isIncluded));
        }
        return null;
      });
      partialExclusionState.iterateAdditionOffsets((start, end, isIncluded) -> {
        for (int i = start; i < end; i++) {
          result.add(createLineCheckboxToggleHighlighter(builder, i + lineRange.start2, Side.RIGHT, !isIncluded));
        }
        return null;
      });
    }
    else {
      result.add(createBlockCheckboxToggleHighlighter(builder, toggleableLineRange));
    }

    if (LocalTrackerDiffUtil.shouldShowToggleAreaThumb(toggleableLineRange)) {
      ContainerUtil.addIfNotNull(result, createToggleAreaThumb(builder, toggleableLineRange));
    }
    return result;
  }

  @NotNull
  private RangeHighlighter createBlockCheckboxToggleHighlighter(@NotNull UnifiedFragmentBuilder builder,
                                                                @NotNull ToggleableLineRange toggleableLineRange) {
    Side side = Side.RIGHT;
    int line = LocalTrackerDiffUtil.getSingleCheckBoxLine(toggleableLineRange, side);
    boolean isExcludedFromCommit = toggleableLineRange.getFragmentData().getExclusionState() instanceof RangeExclusionState.Excluded;

    LineNumberConvertor lineConvertor = side.select(builder.getConvertor1(), builder.getConvertor2());
    int editorLine = lineConvertor.convertApproximateInv(line);

    return LocalTrackerDiffUtil.createCheckboxToggle(getEditor(), editorLine, isExcludedFromCommit, () -> {
      LocalTrackerDiffUtil.toggleBlockExclusion(myTrackerActionProvider, line, isExcludedFromCommit);
    });
  }

  @NotNull
  private RangeHighlighter createLineCheckboxToggleHighlighter(@NotNull UnifiedFragmentBuilder builder,
                                                               int line, @NotNull Side side, boolean isExcludedFromCommit) {
    LineNumberConvertor lineConvertor = side.select(builder.getConvertor1(), builder.getConvertor2());
    int editorLine = lineConvertor.convertApproximateInv(line);

    return LocalTrackerDiffUtil.createCheckboxToggle(getEditor(), editorLine, isExcludedFromCommit, () -> {
      LocalTrackerDiffUtil.toggleLinePartialExclusion(myTrackerActionProvider, line, side, isExcludedFromCommit);
    });
  }

  @Nullable
  private RangeHighlighter createToggleAreaThumb(@NotNull UnifiedFragmentBuilder builder,
                                                 @NotNull ToggleableLineRange toggleableLineRange) {
    Range lineRange = toggleableLineRange.getLineRange();
    int line1 = builder.getConvertor1().convertApproximateInv(lineRange.start1);
    int line2 = builder.getConvertor2().convertApproximateInv(lineRange.end2);
    if (line1 < 0 || line2 < 0 || line2 <= line1 || line2 > DiffUtil.getLineCount(myDocument)) {
      LOG.warn("Failed to show toggle area thumb");
      return null;
    }
    boolean isExcludedFromCommit = toggleableLineRange.getFragmentData().getExclusionState() instanceof RangeExclusionState.Excluded;
    return LocalTrackerDiffUtil.createToggleAreaThumb(getEditor(), line1, line2, () -> {
      LocalTrackerDiffUtil.toggleBlockExclusion(myTrackerActionProvider, lineRange.start1, isExcludedFromCommit);
    });
  }

  private @Nullable RangeHighlighter createClientIdHighlighter(@NotNull UnifiedFragmentBuilder builder,
                                                               @NotNull ToggleableLineRange range) {
    List<ClientId> clientIds = range.getFragmentData().getClientIds();
    if (clientIds.isEmpty()) return null;

    GutterIconRenderer iconRenderer = createClientIdGutterIconRenderer(myLocalRequest.getProject(), clientIds);
    if (iconRenderer == null) return null;

    Range lineRange = range.getLineRange();
    int line1 = builder.getConvertor1().convertApproximateInv(lineRange.start1);
    int line2 = builder.getConvertor2().convertApproximateInv(lineRange.end2);
    if (line1 < 0 || line2 < 0 || line2 <= line1 || line2 > DiffUtil.getLineCount(myDocument)) {
      LOG.warn("Failed to show toggle area thumb");
      return null;
    }

    EditorEx editor = getEditor();
    TextRange textRange = DiffUtil.getLinesRange(editor.getDocument(), line1, line2);
    return editor.getMarkupModel()
      .addRangeHighlighterAndChangeAttributes(null,
                                              textRange.getStartOffset(), textRange.getEndOffset(),
                                              DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                              HighlighterTargetArea.LINES_IN_RANGE,
                                              false, rangeHighlighterEx -> {
          rangeHighlighterEx.setGreedyToLeft(true);
          rangeHighlighterEx.setGreedyToRight(true);

          rangeHighlighterEx.setGutterIconRenderer(iconRenderer);
        });
  }

  private static final class MyUnifiedDiffChange extends UnifiedDiffChange {
    private final @NotNull @NonNls String myChangelistId;
    private final boolean myIsPartiallyExcluded;
    private final @NotNull RangeExclusionState myExclusionState;

    private MyUnifiedDiffChange(int blockStart,
                                int insertedStart,
                                int blockEnd,
                                @NotNull LineFragment lineFragment,
                                boolean isExcluded,
                                boolean isSkipped,
                                @NotNull @NonNls String changelistId,
                                boolean isPartiallyExcluded,
                                @NotNull RangeExclusionState exclusionState) {
      super(blockStart, insertedStart, blockEnd, lineFragment, isExcluded, isSkipped);
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
    public void installHighlighter() {
      if (getChange().isPartiallyExcluded() && getViewer().myAllowExcludeChangesFromCommit) {
        assert myHighlighters.isEmpty() && myOperations.isEmpty();

        int deletionStart = myChange.getDeletedRange().start;
        int additionStart = myChange.getInsertedRange().start;

        RangeExclusionState.Partial exclusionState = (RangeExclusionState.Partial)getChange().getExclusionState();
        exclusionState.iterateDeletionOffsets((start, end, isIncluded) -> {
          myHighlighters.addAll(
            new DiffDrawUtil.LineHighlighterBuilder(myEditor,
                                                    start + deletionStart,
                                                    end + deletionStart,
                                                    TextDiffType.DELETED)
              .withExcludedInEditor(myChange.isSkipped())
              .withExcludedInGutter(!isIncluded)
              .done());
          return null;
        });
        exclusionState.iterateAdditionOffsets((start, end, isIncluded) -> {
          myHighlighters.addAll(
            new DiffDrawUtil.LineHighlighterBuilder(myEditor,
                                                    start + additionStart,
                                                    end + additionStart,
                                                    TextDiffType.INSERTED)
              .withExcludedInEditor(myChange.isSkipped())
              .withExcludedInGutter(!isIncluded)
              .done());
          return null;
        });

        // do not draw ">>"
        // doInstallActionHighlighters();
      }
      else {
        super.installHighlighter();
      }
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
                                                               it.getChangelistId(),
                                                               it.getExclusionState()))
        .toList();
    }

    @Override
    public @Nullable SelectedTrackerLine getSelectedTrackerLines(@NotNull AnActionEvent e) {
      if (!myViewer.isContentGood()) return null;

      BitSet deletions = new BitSet();
      BitSet additions = new BitSet();
      DiffUtil.getSelectedLines(myViewer.getEditor()).stream().forEach(line -> {
        int line1 = myViewer.transferLineFromOnesideStrict(Side.LEFT, line);
        if (line1 != -1) deletions.set(line1);
        int line2 = myViewer.transferLineFromOnesideStrict(Side.RIGHT, line);
        if (line2 != -1) additions.set(line2);
      });

      return new SelectedTrackerLine(deletions, additions);
    }
  }

  private class GutterCheckboxMouseMotionListener {
    private @Nullable RangeHighlighter myHighlighter;

    public void install() {
      MyGutterMouseListener listener = new MyGutterMouseListener();
      myEditor.getGutterComponentEx().addMouseListener(listener);
      myEditor.getGutterComponentEx().addMouseMotionListener(listener);
    }

    public void destroyHoverHighlighter() {
      if (myHighlighter != null) {
        myHighlighter.dispose();
        myHighlighter = null;
      }
    }

    private void updateHoverHighlighter(int editorLine) {
      List<UnifiedDiffChange> changes = getDiffChanges();
      if (changes == null) {
        destroyHoverHighlighter();
        return;
      }

      MyUnifiedDiffChange change = ObjectUtils.tryCast(ContainerUtil.find(changes, it -> it.getLine1() <= editorLine &&
                                                                                         it.getLine2() > editorLine),
                                                       MyUnifiedDiffChange.class);
      if (change == null ||
          change.isPartiallyExcluded() ||
          !myLocalRequest.getChangelistId().equals(change.getChangelistId())) {
        destroyHoverHighlighter();
        return;
      }

      int rightLine = transferLineFromOnesideStrict(Side.RIGHT, editorLine);
      int leftLine = transferLineFromOnesideStrict(Side.LEFT, editorLine);

      int line;
      Side side;
      if (rightLine != -1) {
        line = rightLine;
        side = Side.RIGHT;
      }
      else if (leftLine != -1) {
        line = leftLine;
        side = Side.LEFT;
      }
      else {
        destroyHoverHighlighter();
        return;
      }

      if (LocalTrackerDiffUtil.hasIconHighlighters(myProject, myEditor, editorLine)) {
        if (myHighlighter != null && myEditor.getDocument().getLineNumber(myHighlighter.getStartOffset()) != editorLine) {
          destroyHoverHighlighter();
        }
        return;
      }

      destroyHoverHighlighter();

      boolean isExcludedFromCommit = change.getExclusionState() instanceof RangeExclusionState.Excluded;
      myHighlighter = LocalTrackerDiffUtil.createCheckboxToggle(myEditor, editorLine, isExcludedFromCommit, () -> {
        LocalTrackerDiffUtil.toggleLinePartialExclusion(myTrackerActionProvider, line, side, isExcludedFromCommit);
        destroyHoverHighlighter();
      });
    }

    private class MyGutterMouseListener extends MouseAdapter {
      @Override
      public void mouseMoved(MouseEvent e) {
        if (!myAllowExcludeChangesFromCommit) {
          destroyHoverHighlighter();
          return;
        }

        EditorGutterComponentEx gutter = myEditor.getGutterComponentEx();
        int xOffset = DiffUtil.isMirrored(myEditor) ? gutter.getWidth() - e.getX() : e.getX();
        if (xOffset < gutter.getIconAreaOffset() || xOffset > gutter.getIconAreaOffset() + gutter.getIconsAreaWidth()) {
          destroyHoverHighlighter();
          return;
        }

        LogicalPosition position = myEditor.xyToLogicalPosition(e.getPoint());
        updateHoverHighlighter(position.line);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        destroyHoverHighlighter();
      }
    }
  }
}
