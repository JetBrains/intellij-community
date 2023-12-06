// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

abstract class FoldRegionsTree {
  private final RangeMarkerTree<FoldRegionImpl> myMarkerTree;
  private volatile @NotNull CachedData myCachedData = new CachedData();

  private static final Comparator<FoldRegion> BY_END_OFFSET = Comparator.comparingInt(RangeMarker::getEndOffset);
  private static final Comparator<? super FoldRegion> BY_END_OFFSET_REVERSE = Collections.reverseOrder(BY_END_OFFSET);

  FoldRegionsTree(@NotNull RangeMarkerTree<FoldRegionImpl> markerTree) {
    myMarkerTree = markerTree;
  }

  void clear() {
    clearCachedValues();
    myMarkerTree.clear();
  }

  void clearCachedValues() {
    myCachedData = new CachedData();
  }

  void clearCachedInlayValues() {
    myCachedData.topFoldedInlaysHeightValid = false;
  }

  protected abstract boolean isFoldingEnabled();

  protected abstract boolean hasBlockInlays();

  protected abstract int getFoldedBlockInlaysHeight(int foldStartOffset, int foldEndOffset);

  protected abstract int getLineHeight();

  CachedData rebuild() {
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

  private static FoldRegion @NotNull [] toFoldArray(@NotNull List<FoldRegion> topLevels) {
    return topLevels.isEmpty() ? FoldRegion.EMPTY_ARRAY : topLevels.toArray(FoldRegion.EMPTY_ARRAY);
  }

  void updateCachedOffsets() {
    CachedData cachedData = myCachedData;
    updateCachedAndSortOffsets(cachedData.visibleRegions, false);
  }

  private CachedData updateCachedAndSortOffsets(FoldRegion @Nullable [] visibleRegions, boolean fromRebuild) {
    if (!isFoldingEnabled()) {
      return null;
    }
    if (visibleRegions == null) {
      return rebuild();
    }

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

    CachedData data = new CachedData(visibleRegions, topLevelRegions, startOffsets, endOffsets, foldedLines, customYAdjustment);
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
      if (region == toIgnore || !region.isValid()) return true;

      int rStart = region.getStartOffset();
      int rEnd = region.getEndOffset();
      int rLength = rEnd - rStart;
      boolean rCustom = region instanceof CustomFoldRegion;

      int overlapLength = Math.min(end, rEnd) - Math.max(start, rStart);
      if (overlapLength == 0) {
        return !(custom || rCustom);
      }
      else if (overlapLength < length && overlapLength < rLength) {
        return false;
      }
      else if (length == rLength){
        return custom != rCustom;
      }
      else {
        return start != rStart && end != rEnd || (length < rLength ? (!custom || rCustom) : (!rCustom || custom));
      }
    });
  }

  private CachedData ensureAvailableData() {
    CachedData cachedData = myCachedData;
    if (!cachedData.isAvailable() && ApplicationManager.getApplication().isDispatchThread()) {
      return rebuild();
    }
    return cachedData;
  }

  @Nullable
  FoldRegion fetchOutermost(int offset) {
    if (!isFoldingEnabled()) return null;
    CachedData cachedData = ensureAvailableData();

    int[] starts = cachedData.topStartOffsets;
    int[] ends = cachedData.topEndOffsets;
    if (starts == null || ends == null) {
      return null;
    }

    int i = ObjectUtils.binarySearch(0, ends.length, mid-> ends[mid] < offset ? -1 : starts[mid] > offset ? 1 : 0);
    return i < 0 ? null : cachedData.topLevelRegions[i];
  }

  FoldRegion @Nullable [] fetchVisible() {
    if (!isFoldingEnabled()) return null;
    CachedData cachedData = ensureAvailableData();

    return cachedData.visibleRegions;
  }

  FoldRegion @Nullable [] fetchTopLevel() {
    if (!isFoldingEnabled()) return null;
    CachedData cachedData = ensureAvailableData();
    return cachedData.topLevelRegions;
  }

  static boolean containsStrict(@NotNull FoldRegion region, int offset) {
    return region.getStartOffset() < offset && offset < region.getEndOffset();
  }

  FoldRegion @NotNull [] fetchCollapsedAt(int offset) {
    if (!isFoldingEnabled()) return FoldRegion.EMPTY_ARRAY;
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
    if (!isFoldingEnabled()) return true;
    return !myMarkerTree.processAll(region -> {
      boolean contains1 = containsStrict(region, startOffset);
      boolean contains2 = containsStrict(region, endOffset);
      return contains1 == contains2;
    });
  }

  FoldRegion @NotNull [] fetchAllRegions() {
    if (!isFoldingEnabled()) return FoldRegion.EMPTY_ARRAY;
    List<FoldRegion> regions = new ArrayList<>();
    myMarkerTree.processOverlappingWith(0, Integer.MAX_VALUE, new CommonProcessors.CollectProcessor<>(regions));
    return toFoldArray(regions);
  }


  List<FoldRegion> fetchOverlapping(int startOffset, int endOffset) {
    if (!isFoldingEnabled()) return Collections.emptyList();
    List<FoldRegion> regions = new ArrayList<>();
    myMarkerTree.processOverlappingWith(startOffset, endOffset, new CommonProcessors.CollectProcessor<>(regions));
    return regions;
  }

  private void forEach(@NotNull Consumer<? super FoldRegion> consumer) {
    myMarkerTree.processAll(region -> { consumer.consume(region); return true; });
  }

  int getFoldedLinesCountBefore(int offset) {
    if (!isFoldingEnabled()) return 0;
    CachedData cachedData = ensureAvailableData();
    int idx = getLastTopLevelIndexBefore(cachedData, offset);
    if (idx == -1) return 0;
    assert cachedData.topFoldedLines != null;
    return cachedData.topFoldedLines[idx];
  }

  int getTotalNumberOfFoldedLines() {
    if (!isFoldingEnabled()) return 0;
    CachedData cachedData = ensureAvailableData();
    int[] foldedLines = cachedData.topFoldedLines;

    if (foldedLines == null || foldedLines.length == 0) return 0;
    return foldedLines[foldedLines.length - 1];
  }

  int getHeightOfFoldedBlockInlaysBefore(int idx) {
    if (!isFoldingEnabled()) return 0;
    CachedData cachedData = ensureAvailableData();
    if (idx == -1) return 0;
    cachedData.ensureInlayDataAvailable();
    int[] topFoldedInlaysHeight = cachedData.topFoldedInlaysHeight;
    return topFoldedInlaysHeight == null ? 0 : topFoldedInlaysHeight[idx];
  }

  int getTotalHeightOfFoldedBlockInlays() {
    if (!isFoldingEnabled()) return 0;
    CachedData cachedData = ensureAvailableData();
    cachedData.ensureInlayDataAvailable();
    int[] foldedInlaysHeight = cachedData.topFoldedInlaysHeight;
    return foldedInlaysHeight == null || foldedInlaysHeight.length == 0 ? 0 : foldedInlaysHeight[foldedInlaysHeight.length - 1];
  }

  /**
   * @return (prevAdjustment, curAdjustment)
   */
  @NotNull
  IntPair getCustomRegionsYAdjustment(int offset, int idx) {
    if (!isFoldingEnabled()) return new IntPair(0,0);
    CachedData cachedData = ensureAvailableData();
    int prevAdjustment = idx == -1 ? 0 : cachedData.topCustomYAdjustment[idx];
    int curAdjustment = idx + 1 < cachedData.topStartOffsets.length && cachedData.topStartOffsets[idx + 1] == offset
                        ? cachedData.topCustomYAdjustment[idx + 1] - prevAdjustment : 0;
    return new IntPair(prevAdjustment, curAdjustment);
  }

  int getLastTopLevelIndexBefore(int offset) {
    if (!isFoldingEnabled()) return -1;
    CachedData cachedData = ensureAvailableData();
    return getLastTopLevelIndexBefore(cachedData, offset);
  }

  private static int getLastTopLevelIndexBefore(@NotNull CachedData cachedData, int offset) {
    int[] endOffsets = cachedData.topEndOffsets;

    if (endOffsets == null) return -1;

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
    forEach(region -> ((FoldRegionImpl)region).resetDocumentRegionChanged());
  }

  private final class CachedData {
    private final FoldRegion[] visibleRegions;  // all foldings outside collapsed regions
    private final FoldRegion[] topLevelRegions; // all visible regions which are collapsed
    private final int[] topStartOffsets;
    private final int[] topEndOffsets;
    private final int[] topFoldedLines;
    private final int[] topCustomYAdjustment;
    private int[] topFoldedInlaysHeight;
    private boolean topFoldedInlaysHeightValid;

    private CachedData() {
      visibleRegions = null;
      topLevelRegions = null;
      topStartOffsets = null;
      topEndOffsets = null;
      topFoldedLines = null;
      topCustomYAdjustment = null;
    }

    private CachedData(FoldRegion @NotNull [] visibleRegions,
                       FoldRegion @NotNull [] topLevelRegions,
                       int @NotNull [] topStartOffsets,
                       int @NotNull [] topEndOffsets,
                       int @NotNull [] topFoldedLines,
                       int @NotNull [] topCustomYAdjustment) {
      this.visibleRegions = visibleRegions;
      this.topLevelRegions = topLevelRegions;
      this.topStartOffsets = topStartOffsets;
      this.topEndOffsets = topEndOffsets;
      this.topFoldedLines = topFoldedLines;
      this.topCustomYAdjustment = topCustomYAdjustment;
      ensureInlayDataAvailable();
    }

    private boolean isAvailable() {
      return visibleRegions != null;
    }

    private void ensureInlayDataAvailable() {
      if (topFoldedInlaysHeightValid || !ApplicationManager.getApplication().isDispatchThread()) return;
      topFoldedInlaysHeightValid = true;
      if (hasBlockInlays()) {
        int count = topLevelRegions.length;
        topFoldedInlaysHeight = ArrayUtil.newIntArray(count);
        int inlaysHeightSum = 0;
        for (int i = 0; i < count; i++) {
          inlaysHeightSum += getFoldedBlockInlaysHeight(topStartOffsets[i], topEndOffsets[i] + 1);
          topFoldedInlaysHeight[i] = inlaysHeightSum;
        }
      }
      else {
        topFoldedInlaysHeight = null;
      }
    }
  }
}
