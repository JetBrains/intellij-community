// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.IntPair;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

abstract class FoldRegionsTree {
  private final @NotNull RangeMarkerTree<? extends FoldRegionImpl> myMarkerTree;
  private volatile CachedData myCachedData;

  private static final Comparator<? super FoldRegion> BY_END_OFFSET = Comparator.comparingInt(region -> region.getEndOffset());
  private static final Comparator<? super FoldRegion> BY_END_OFFSET_REVERSE = Collections.reverseOrder(BY_END_OFFSET);

  FoldRegionsTree(@NotNull RangeMarkerTree<? extends FoldRegionImpl> markerTree) {
    myMarkerTree = markerTree;
  }

  void clear() {
    clearCachedValues();
    myMarkerTree.clear();
  }

  void clearCachedValues() {
    myCachedData = null;
  }

  void clearCachedInlayValues() {
    CachedData data = myCachedData;
    if (data != null) {
      myCachedData = data.clearCachedInlayValues();
    }
  }

  protected abstract boolean isFoldingEnabled();

  protected abstract boolean hasBlockInlays();

  protected abstract int getFoldedBlockInlaysHeight(int foldStartOffset, int foldEndOffset);

  protected abstract int getLineHeight();

  @NotNull CachedData rebuild() {
    List<FoldRegion> visible = new ArrayList<>(myMarkerTree.size());

    SweepProcessor.Generator<FoldRegionImpl> generator = processor -> myMarkerTree.processOverlappingWith(0, Integer.MAX_VALUE, processor);
    SweepProcessor.sweep(generator, new SweepProcessor<>() {
      FoldRegionImpl lastCollapsedRegion;

      @Override
      public boolean process(int offset,
                             @NotNull FoldRegionImpl region,
                             boolean atStart,
                             @NotNull Collection<? extends FoldRegionImpl> overlapping) {
        if (atStart) {
          if (lastCollapsedRegion == null || region.getEndOffset() > lastCollapsedRegion.getEndOffset() ||
              region instanceof CustomFoldRegion &&
              region.getStartOffset() == lastCollapsedRegion.getStartOffset() &&
              region.getEndOffset() == lastCollapsedRegion.getEndOffset()) {
            if (!region.isExpanded()) {
              hideContainedRegions(region);
              lastCollapsedRegion = region;
            }
            visible.add(region);
          }
        }
        return true;
      }

      private void hideContainedRegions(FoldRegion region) {
        for (int i = visible.size() - 1; i >= 0; i--) {
          if (region.getStartOffset() == visible.get(i).getStartOffset()) {
            visible.remove(i);
          }
          else {
            break;
          }
        }
      }
    });

    FoldRegion[] visibleRegions = toFoldArray(visible);

    Arrays.sort(visibleRegions, BY_END_OFFSET_REVERSE);

    return updateCachedAndSortOffsets(visibleRegions, true);
  }

  private static @NotNull FoldRegion @NotNull [] toFoldArray(@NotNull List<FoldRegion> topLevels) {
    return topLevels.isEmpty() ? FoldRegion.EMPTY_ARRAY : topLevels.toArray(FoldRegion.EMPTY_ARRAY);
  }

  void updateCachedOffsets() {
    if (isFoldingEnabled()) {
      CachedData data = myCachedData;
      if (data == null) {
        rebuild();
      }
      else {
        updateCachedAndSortOffsets(data.visibleRegions, false);
      }
    }
  }

  private @NotNull CachedData updateCachedAndSortOffsets(@NotNull FoldRegion @NotNull [] visibleRegions, boolean fromRebuild) {
    List<FoldRegion> topLevel = new ArrayList<>(visibleRegions.length/2);

    for (FoldRegion region : visibleRegions) {
      if (!region.isValid()) {
        if (fromRebuild) {
          throw new RuntimeExceptionWithAttachments("FoldRegionsTree.rebuild() failed",
                                                    new Attachment("visibleRegions.txt", Arrays.toString(visibleRegions)));
        }
        return rebuild();
      }
      if (!region.isExpanded()) {
        topLevel.add(region);
      }
    }
    FoldRegion[] topLevelRegions = topLevel.toArray(FoldRegion.EMPTY_ARRAY);
    Arrays.sort(topLevelRegions, BY_END_OFFSET);

    int[] startOffsets = ArrayUtil.newIntArray(topLevelRegions.length);
    int[] endOffsets = ArrayUtil.newIntArray(topLevelRegions.length);
    int[] foldedLines = ArrayUtil.newIntArray(topLevelRegions.length);
    int[] customYAdjustment = ArrayUtil.newIntArray(topLevelRegions.length);

    int foldedLinesSum = 0;
    int currentCustomYAdjustment = 0;
    int lineHeight = getLineHeight();
    for (int i = 0; i < topLevelRegions.length; i++) {
      FoldRegion region = topLevelRegions[i];
      startOffsets[i] = region.getStartOffset();
      endOffsets[i] = region.getEndOffset() - 1;
      Document document = region.getDocument();
      foldedLinesSum += document.getLineNumber(region.getEndOffset()) - document.getLineNumber(region.getStartOffset());
      foldedLines[i] = foldedLinesSum;
      if (region instanceof CustomFoldRegion) {
        currentCustomYAdjustment += ((CustomFoldRegion)region).getHeightInPixels() - lineHeight;
      }
      customYAdjustment[i] = currentCustomYAdjustment;
    }

    CachedData data = new CachedData(visibleRegions, topLevelRegions, startOffsets, endOffsets, foldedLines, customYAdjustment, computeTopFoldedInlaysHeight(topLevelRegions, startOffsets, endOffsets));
    myCachedData = data;
    return data;
  }

  boolean checkIfValidToCreate(int start, int end, boolean custom, FoldRegion toIgnore) {
    // check that range doesn't strictly overlap other regions and is distinct from everything else
    // notes specific to 'custom' fold regions:
    // * we do allow two otherwise identical regions one of which is 'custom' and the other is not - the custom one always takes preference
    //   over 'normal' one in terms of visibility
    // * regions 'touching' on one of the boundaries are allowed only if both regions are 'normal', or 'custom' region fully contains
    //   the 'normal' one - otherwise, fold region marker would need to be displayed in 'custom' region's area of the gutter, and that's not
    //   currently supported
    int length = end - start;
    return myMarkerTree.processOverlappingWith(start, end, region -> {
      if (region == toIgnore || !region.isValid()) {
        return true;
      }

      int rStart = region.getStartOffset();
      int rEnd = region.getEndOffset();
      int rLength = rEnd - rStart;
      boolean rCustom = region instanceof CustomFoldRegion;

      int overlapLength = Math.min(end, rEnd) - Math.max(start, rStart);
      if (overlapLength == 0) {
        return !(custom || rCustom);
      }
      if (overlapLength < length && overlapLength < rLength) {
        return false;
      }
      if (length == rLength){
        return custom != rCustom;
      }
      return start != rStart && end != rEnd || (length < rLength ? (!custom || rCustom) : (!rCustom || custom));
    });
  }

  private @Nullable CachedData ensureAvailableDataIfPossible() {
    CachedData cachedData = myCachedData;
    if (cachedData == null && ApplicationManager.getApplication().isDispatchThread()) {
      return rebuild();
    }
    return cachedData;
  }

  @Nullable
  FoldRegion fetchOutermost(int offset) {
    if (!isFoldingEnabled()) {
      return null;
    }
    CachedData cachedData = ensureAvailableDataIfPossible();
    if (cachedData == null) {
      return null;
    }

    int[] starts = cachedData.topStartOffsets;
    int[] ends = cachedData.topEndOffsets;
    int i = ObjectUtils.binarySearch(0, ends.length, mid-> ends[mid] < offset ? -1 : starts[mid] > offset ? 1 : 0);
    return i < 0 ? null : cachedData.topLevelRegions[i];
  }

  @NotNull FoldRegion @Nullable [] fetchVisible() {
    if (!isFoldingEnabled()) {
      return null;
    }
    CachedData cachedData = ensureAvailableDataIfPossible();
    if (cachedData == null) {
      return null;
    }

    return cachedData.visibleRegions;
  }

  @NotNull FoldRegion @Nullable [] fetchTopLevel() {
    if (!isFoldingEnabled()) {
      return null;
    }
    CachedData cachedData = ensureAvailableDataIfPossible();
    if (cachedData == null) {
      return null;
    }
    return cachedData.topLevelRegions;
  }

  static boolean containsStrict(@NotNull FoldRegion region, int offset) {
    return region.getStartOffset() < offset && offset < region.getEndOffset();
  }

  @NotNull FoldRegion @NotNull [] fetchCollapsedAt(int offset) {
    if (!isFoldingEnabled()) {
      return FoldRegion.EMPTY_ARRAY;
    }
    List<FoldRegion> allCollapsed = new ArrayList<>();
    myMarkerTree.processContaining(offset, region->{
      if (!region.isExpanded() && containsStrict(region, offset)) {
        allCollapsed.add(region);
      }
      return true;
    });
    return toFoldArray(allCollapsed);
  }

  boolean intersectsRegion(int startOffset, int endOffset) {
    if (!isFoldingEnabled()) {
      return true;
    }
    return !myMarkerTree.processAll(region -> {
      boolean contains1 = containsStrict(region, startOffset);
      boolean contains2 = containsStrict(region, endOffset);
      return contains1 == contains2;
    });
  }

  @NotNull FoldRegion @NotNull [] fetchAllRegions() {
    if (!isFoldingEnabled()) {
      return FoldRegion.EMPTY_ARRAY;
    }
    List<FoldRegion> regions = new ArrayList<>();
    myMarkerTree.processOverlappingWith(0, Integer.MAX_VALUE, new CommonProcessors.CollectProcessor<>(regions));
    return toFoldArray(regions);
  }


  @NotNull List<FoldRegion> fetchOverlapping(int startOffset, int endOffset) {
    if (!isFoldingEnabled()) {
      return Collections.emptyList();
    }
    List<FoldRegion> regions = new ArrayList<>();
    myMarkerTree.processOverlappingWith(startOffset, endOffset, new CommonProcessors.CollectProcessor<>(regions));
    return regions;
  }

  int getFoldedLinesCountBefore(int offset) {
    if (!isFoldingEnabled()) {
      return 0;
    }
    CachedData cachedData = ensureAvailableDataIfPossible();
    if (cachedData == null) {
      return 0;
    }
    int idx = getLastTopLevelIndexBefore(cachedData, offset);
    if (idx == -1) {
      return 0;
    }
    return cachedData.topFoldedLines[idx];
  }

  int getTotalNumberOfFoldedLines() {
    if (!isFoldingEnabled()) {
      return 0;
    }
    CachedData cachedData = ensureAvailableDataIfPossible();
    if (cachedData == null) {
      return 0;
    }
    int[] foldedLines = cachedData.topFoldedLines;
    if (foldedLines.length == 0) {
      return 0;
    }
    return foldedLines[foldedLines.length - 1];
  }

  int getHeightOfFoldedBlockInlaysBefore(int idx) {
    if (!isFoldingEnabled() || idx == -1) {
      return 0;
    }
    CachedData cachedData = ensureInlayDataAvailableIfPossible();
    if (cachedData == null) {
      return 0;
    }
    int[] topFoldedInlaysHeight = cachedData.topFoldedInlaysHeight;
    return topFoldedInlaysHeight.length <= idx || topFoldedInlaysHeight == CachedData.NOT_YET_COMPUTED ? 0 : topFoldedInlaysHeight[idx];
  }

  int getTotalHeightOfFoldedBlockInlays() {
    if (!isFoldingEnabled()) {
      return 0;
    }
    CachedData cachedData = ensureInlayDataAvailableIfPossible();
    if (cachedData == null) {
      return 0;
    }
    int[] foldedInlaysHeight = cachedData.topFoldedInlaysHeight;
    return foldedInlaysHeight.length == 0 || foldedInlaysHeight == CachedData.NOT_YET_COMPUTED ? 0 : foldedInlaysHeight[foldedInlaysHeight.length - 1];
  }

  /**
   * @return (prevAdjustment, curAdjustment)
   */
  @NotNull
  IntPair getCustomRegionsYAdjustment(int offset, int idx) {
    if (!isFoldingEnabled()) {
      return new IntPair(0,0);
    }
    CachedData cachedData = ensureAvailableDataIfPossible();
    if (cachedData == null) {
      return new IntPair(0,0);
    }
    int prevAdjustment = idx == -1 ? 0 : cachedData.topCustomYAdjustment[idx];
    int curAdjustment = idx + 1 < cachedData.topStartOffsets.length && cachedData.topStartOffsets[idx + 1] == offset
                        ? cachedData.topCustomYAdjustment[idx + 1] - prevAdjustment : 0;
    return new IntPair(prevAdjustment, curAdjustment);
  }

  int getLastTopLevelIndexBefore(int offset) {
    if (!isFoldingEnabled()) {
      return -1;
    }
    CachedData cachedData = ensureAvailableDataIfPossible();
    if (cachedData == null) {
      return -1;
    }
    return getLastTopLevelIndexBefore(cachedData, offset);
  }

  private static int getLastTopLevelIndexBefore(@NotNull CachedData cachedData, int offset) {
    int[] endOffsets = cachedData.topEndOffsets;
    offset--; // end offsets are decremented in cache
    int i = Arrays.binarySearch(endOffsets, offset);
    return i < 0 ? - i - 2 : i;
  }

  @Nullable
  FoldRegion getRegionAt(int startOffset, int endOffset) {
    FoldRegionImpl[] found = {null};
    myMarkerTree.processOverlappingWith(startOffset, endOffset, region -> {
      if (region.getStartOffset() == startOffset && region.getEndOffset() == endOffset) {
        found[0] = region;
        return false;
      }
      return true;
    });
    return found[0];
  }

  void clearDocumentRangesModificationStatus() {
    myMarkerTree.processAll(region -> { region.resetDocumentRegionChanged(); return true; });
  }

  /**
   * @param visibleRegions  all foldings outside collapsed regions
   * @param topLevelRegions all visible regions which are collapsed
   * @param topFoldedInlaysHeight {@link #NOT_YET_COMPUTED} means this field is invalid, all other values mean it's computed and valid
   */
  private record CachedData(@NotNull FoldRegion @NotNull [] visibleRegions,
                            @NotNull FoldRegion @NotNull [] topLevelRegions,
                            int @NotNull [] topStartOffsets,
                            int @NotNull [] topEndOffsets,
                            int @NotNull [] topFoldedLines,
                            int @NotNull [] topCustomYAdjustment,
                            int @NotNull [] topFoldedInlaysHeight) {
    private static final int[] NOT_YET_COMPUTED = new int[1]; // do not inline, needs unique identity
    private @NotNull CachedData clearCachedInlayValues() {
      return topFoldedInlaysHeight == NOT_YET_COMPUTED ? this :
        new CachedData(visibleRegions, topLevelRegions, topStartOffsets, topEndOffsets, topFoldedLines, topCustomYAdjustment, NOT_YET_COMPUTED);
    }
  }
  private @Nullable CachedData ensureInlayDataAvailableIfPossible() {
    CachedData data = ensureAvailableDataIfPossible();
    if (data == null) {
      return null;
    }
    if (data.topFoldedInlaysHeight != CachedData.NOT_YET_COMPUTED || !ApplicationManager.getApplication().isDispatchThread()) {
      return data;
    }
    int[] topFoldedInlaysHeight = computeTopFoldedInlaysHeight(data.topLevelRegions, data.topStartOffsets, data.topEndOffsets);
    CachedData newData = new CachedData(data.visibleRegions, data.topLevelRegions, data.topStartOffsets, data.topEndOffsets,
                                        data.topFoldedLines, data.topCustomYAdjustment, topFoldedInlaysHeight);
    myCachedData = newData;
    return newData;
  }

  private int @NotNull [] computeTopFoldedInlaysHeight(@NotNull FoldRegion[] topLevelRegions,
                                                       int @NotNull [] topStartOffsets,
                                                       int @NotNull [] topEndOffsets) {
    int[] topFoldedInlaysHeight;
    int count = hasBlockInlays() ? topLevelRegions.length : 0;
    topFoldedInlaysHeight = ArrayUtil.newIntArray(count);
    int inlaysHeightSum = 0;
    for (int i = 0; i < count; i++) {
      inlaysHeightSum += getFoldedBlockInlaysHeight(topStartOffsets[i], topEndOffsets[i] + 1);
      topFoldedInlaysHeight[i] = inlaysHeightSum;
    }
    return topFoldedInlaysHeight;
  }
}
