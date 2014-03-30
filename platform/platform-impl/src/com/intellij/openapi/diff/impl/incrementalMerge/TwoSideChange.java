package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TwoSideChange<T extends TwoSideChange.SideChange> extends ChangeSide implements DiffRangeMarker.RangeInvalidListener {
  @NotNull protected final MergeList myMergeList;
  @NotNull protected DiffRangeMarker myBaseRangeMarker;
  protected T myLeftChange;
  protected T myRightChange;
  @NotNull protected final ChangeHighlighterHolder myCommonHighlighterHolder;

  protected TwoSideChange(@NotNull TextRange baseRange,
                          @NotNull MergeList mergeList,
                          @NotNull ChangeHighlighterHolder highlighterHolder) {
    myBaseRangeMarker = new DiffRangeMarker((DocumentEx)mergeList.getBaseDocument(), baseRange, this);
    myMergeList = mergeList;
    myCommonHighlighterHolder = highlighterHolder;
  }

  @NotNull
  public ChangeHighlighterHolder getHighlighterHolder() {
    return myCommonHighlighterHolder;
  }

  @NotNull
  public DiffRangeMarker getRange() {
    return myBaseRangeMarker;
  }

  @Nullable
  public Change getLeftChange() {
    return myLeftChange;
  }

  @Nullable
  public Change getRightChange() {
    return myRightChange;
  }

  public void setRange(@NotNull DiffRangeMarker range) {
    myBaseRangeMarker = range;
  }

  @Nullable
  T getOtherChange(@NotNull T change) {
    if (change == myLeftChange) {
      return myRightChange;
    }
    else if (change == myRightChange) {
      return myLeftChange;
    }
    else {
      throw new IllegalStateException("Unexpected change: " + change);
    }
  }

  public void removeOtherChange(@NotNull T change) {
    if (change == myLeftChange) {
      myRightChange = null;
    }
    else if (change == myRightChange) {
      myLeftChange = null;
    }
    else {
      throw new IllegalStateException("Unexpected change: " + change);
    }
  }

  public void conflictRemoved() {
    removeHighlighters(myLeftChange);
    removeHighlighters(myRightChange);
    myCommonHighlighterHolder.removeHighlighters();
    myMergeList.removeChanges(myLeftChange, myRightChange);
    myBaseRangeMarker.removeListener(this);
  }

  private static <T extends SideChange> void removeHighlighters(@Nullable T change) {
    if (change != null) {
      change.getOriginalSide().getHighlighterHolder().removeHighlighters();
    }
  }

  @NotNull
  public Document getOriginalDocument(FragmentSide mergeSide) {
    return myMergeList.getChanges(mergeSide).getDocument(MergeList.BRANCH_SIDE);
  }

  public void onRangeInvalidated() {
    conflictRemoved();
  }

  @NotNull
  public MergeList getMergeList() {
    return myMergeList;
  }

  protected static abstract class SideChange<V extends TwoSideChange> extends Change implements DiffRangeMarker.RangeInvalidListener {
    protected V myTwoSideChange;
    @NotNull protected final ChangeList myChangeList;

    protected SimpleChangeSide myOriginalSide;
    @NotNull protected ChangeType myType;

    protected SideChange(@NotNull V twoSideChange,
                         @NotNull ChangeList changeList,
                         @NotNull ChangeType type,
                         @NotNull FragmentSide mergeSide,
                         @NotNull TextRange versionRange) {
      myTwoSideChange = twoSideChange;
      myChangeList = changeList;
      myOriginalSide =
        new SimpleChangeSide(mergeSide, new DiffRangeMarker((DocumentEx)twoSideChange.getOriginalDocument(mergeSide), versionRange, this));
      myType = type;
    }

    @NotNull
    public ChangeType getType() {
      return myType;
    }

    public SimpleChangeSide getOriginalSide() {
      return myOriginalSide;
    }

    protected void markApplied() {
      myType = ChangeType.deriveApplied(myType);
      myChangeList.apply(this);

      myOriginalSide.getHighlighterHolder().updateHighlighter(myOriginalSide, myType);
      myOriginalSide.getHighlighterHolder().setActions(new AnAction[0]);

      // display, what one side of the conflict was resolved to
      myTwoSideChange.getHighlighterHolder().updateHighlighter(myTwoSideChange, myType);
    }

    public ChangeList getChangeList() {
      return myTwoSideChange.getMergeList().getChanges(myOriginalSide.getFragmentSide());
    }

    @Override
    protected void changeSide(ChangeSide sideToChange, DiffRangeMarker newRange) {
      myTwoSideChange.setRange(newRange);
    }

    @NotNull
    @Override
    public ChangeSide getChangeSide(@NotNull FragmentSide side) {
      return isBranch(side) ? myOriginalSide : myTwoSideChange;
    }

    protected static boolean isBranch(@NotNull FragmentSide side) {
      return MergeList.BRANCH_SIDE == side;
    }

    protected void removeFromList() {
      myTwoSideChange.conflictRemoved();
      myTwoSideChange = null;
    }

    public boolean isValid() {
      return myTwoSideChange != null;
    }

    public void onRemovedFromList() {
      myOriginalSide.getRange().removeListener(this);
      myTwoSideChange = null;
      myOriginalSide = null;
    }

    public void onRangeInvalidated() {
      removeFromList();
    }
  }
}
