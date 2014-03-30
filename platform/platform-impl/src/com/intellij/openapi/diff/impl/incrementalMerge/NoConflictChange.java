package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public class NoConflictChange extends TwoSideChange.SideChange<MergeNoConflict> {
  private static final Logger LOG = Logger.getInstance(NoConflictChange.class);

  private boolean myApplied;

  public NoConflictChange(@NotNull MergeNoConflict twoSideChange,
                          @NotNull FragmentSide mergeSide,
                          @NotNull TextRange baseRange,
                          @NotNull TextRange versionRange,
                          @NotNull ChangeList changeList) {
    super(twoSideChange, changeList, ChangeType.fromRanges(baseRange, versionRange), mergeSide, versionRange);
  }

  @Override
  public void onApplied() {
    markApplied();

    NoConflictChange otherChange = myTwoSideChange.getOtherChange(this);
    LOG.assertTrue(otherChange != null, String.format("Other change is null. This change: %s Merge conflict: %s", this, myTwoSideChange));
    otherChange.markApplied();
  }

  @Override
  protected void markApplied() {
    if (!myApplied) {
      myApplied = true;
      super.markApplied();
    }
  }
}
