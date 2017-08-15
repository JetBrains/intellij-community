/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

abstract class FoldRegionsTree {
  private final RangeMarkerTree<FoldRegionImpl> myMarkerTree;
  @NotNull private volatile CachedData myCachedData = new CachedData();

  private static final Comparator<FoldRegion> BY_END_OFFSET = Comparator.comparingInt(RangeMarker::getEndOffset);
  private static final Comparator<? super FoldRegion> BY_END_OFFSET_REVERSE = Collections.reverseOrder(BY_END_OFFSET);

  private static final TObjectHashingStrategy<FoldRegion> OFFSET_BASED_HASHING_STRATEGY = new TObjectHashingStrategy<FoldRegion>() {
    @Override
    public int computeHashCode(FoldRegion o) {
      return o.getStartOffset() * 31 + o.getEndOffset();
    }

    @Override
    public boolean equals(FoldRegion o1, FoldRegion o2) {
      return o1.getStartOffset() == o2.getStartOffset() && o1.getEndOffset() == o2.getEndOffset();
    }
  };

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

  protected abstract boolean isFoldingEnabled();

  CachedData rebuild() {
    List<FoldRegion> visible = new ArrayList<>(myMarkerTree.size());

    List<FoldRegionImpl> duplicatesToKill = new ArrayList<>();
    SweepProcessor.Generator<FoldRegionImpl> generator = processor -> myMarkerTree.processOverlappingWith(0, Integer.MAX_VALUE, processor);
    // current regions which are nested with each other.
    // to determine visible regions (i.e. all regions which are not inside collapsed), for each endpoint
    // we should sort this list by inclusion (from the biggest to the smallest length), find the longest collapsed (with index i) and report all (0..i] as visible
    final List<FoldRegionImpl> nested = new ArrayList<>();
    SweepProcessor.sweep(generator, new SweepProcessor<FoldRegionImpl>() {
      int lastEnd = -1; // offset (with atStart==false) which was reported last by sweep

      @Override
      public boolean process(int offset, @NotNull FoldRegionImpl region, boolean atStart, @NotNull Collection<FoldRegionImpl> overlapping) {
        if (atStart) {
          if (!isNestedWithLast(nested, region)) {
            reportVisible(nested, lastEnd, visible, duplicatesToKill);
          }
          addToNested(nested, region);
        }
        else {
          if (lastEnd != offset) {
            reportVisible(nested, lastEnd, visible, duplicatesToKill);
            lastEnd = offset;
          }
        }
        return true;
      }
    });
    if (!nested.isEmpty()) {
      reportVisible(nested, nested.get(nested.size()-1).getEndOffset(), visible, duplicatesToKill);
    }

    for (FoldRegionImpl region : duplicatesToKill) {
      myMarkerTree.removeInterval(region);
    }

    FoldRegion[] visibleRegions = toFoldArray(visible);

    Arrays.sort(visibleRegions, BY_END_OFFSET_REVERSE);

    return updateCachedAndSortOffsets(visibleRegions);
  }

  private static boolean isNestedWithLast(List<FoldRegionImpl> nested, @NotNull FoldRegion region) {
    if (nested.isEmpty()) return true;
    FoldRegion last = nested.get(nested.size() - 1);
    return TextRange.containsRange(last, region) || TextRange.containsRange(region, last);
  }

  private static void addToNested(List<FoldRegionImpl> nested, @NotNull FoldRegionImpl region) {
    // maintain invariant: nested list should be sorted by inclusion: from biggest to smallest and then first expanded then collapsed
    for (int i=nested.size();i>=0;i--) {
      FoldRegion prev = i==0 ? null : nested.get(i - 1);
      if (prev == null ||
          TextRange.containsRange(prev, region) && (!TextRange.containsRange(region, prev) || prev.isExpanded() || !region.isExpanded())) {
        nested.add(i,region);
        break;
      }
    }
  }

  // process and report regions which getEndOffset() == endOffset
  private static void reportVisible(List<FoldRegionImpl> nested,
                                    int endOffset,
                                    List<FoldRegion> outVisible,
                                    List<FoldRegionImpl> outDuplicatesToKill) {
    if (nested.isEmpty()) return;
    FoldRegion topCollapsed = null;

    int start;
    for (start = nested.size()-1; start>=0; start--) {
      FoldRegion region = nested.get(start);
      if (!region.isExpanded()) topCollapsed = region;

      if (endOffset != region.getEndOffset()) {
        break;
      }
    }

    if (start != nested.size()-1) {
      for (int i = start-1; i>=0; i--) {
        FoldRegion region = nested.get(i);
        if (!region.isExpanded()) topCollapsed = region;
      }
      boolean toReport = true;
      for (int i = start+1; i < nested.size(); i++) {
        FoldRegionImpl region = nested.get(i);
        FoldRegion next = i==nested.size()-1?null:nested.get(i+1);

        // there can be multiple regions with the same offsets, collapsed and expanded.
        // in that case dispose the expanded and preserve collapsed (to reduce flickering)
        if (next != null && Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(next, region) == 0) {
          // regions are sorted by expanded, so expanded are first
          outDuplicatesToKill.add(region);
          if (topCollapsed == region) {
            // should update topCollapsed or it will be disposed otherwise
            topCollapsed = next.isExpanded() ? null : next;
          }
          continue;
        }

        // report all these top regions as visible, until we met collapsed
        if (topCollapsed != null && topCollapsed != region) {
          // no, all these regions are inside collapsed, nothing to report
          toReport = false;
        }
        if (toReport) {
          outVisible.add(region);
          if (!region.isExpanded()) {
            toReport = false;
          }
        }
      }
      nested.subList(start+1, nested.size()).clear();
    }
  }

  @NotNull
  private static FoldRegion[] toFoldArray(@NotNull List<FoldRegion> topLevels) {
    return topLevels.isEmpty() ? FoldRegion.EMPTY_ARRAY : topLevels.toArray(new FoldRegion[topLevels.size()]);
  }

  void updateCachedOffsets() {
    CachedData cachedData = myCachedData;
    updateCachedAndSortOffsets(cachedData.visibleRegions);
  }
  
  private CachedData updateCachedAndSortOffsets(FoldRegion[] visibleRegions) {
    if (!isFoldingEnabled()) {
      return null;
    }
    if (visibleRegions == null) {
      return rebuild();
    }

    List<FoldRegion> topLevel = new ArrayList<>(visibleRegions.length/2);

    Set<FoldRegion> distinctRegions = new THashSet<>(visibleRegions.length, OFFSET_BASED_HASHING_STRATEGY);

    for (FoldRegion region : visibleRegions) {
      if (!region.isValid() || !distinctRegions.add(region)) {
        return rebuild();
      }
      if (!region.isExpanded()) {
        topLevel.add(region);
      }
    }
    FoldRegion[] topLevelRegions = topLevel.toArray(new FoldRegion[topLevel.size()]);
    Arrays.sort(topLevelRegions, BY_END_OFFSET);

    int[] startOffsets = ArrayUtil.newIntArray(topLevelRegions.length);
    int[] endOffsets = ArrayUtil.newIntArray(topLevelRegions.length);
    int[] foldedLines = ArrayUtil.newIntArray(topLevelRegions.length);
    
    int sum = 0;
    for (int i = 0; i < topLevelRegions.length; i++) {
      FoldRegion region = topLevelRegions[i];
      startOffsets[i] = region.getStartOffset();
      endOffsets[i] = region.getEndOffset() - 1;
      Document document = region.getDocument();
      sum += document.getLineNumber(region.getEndOffset()) - document.getLineNumber(region.getStartOffset());
      foldedLines[i] = sum;
    }

    CachedData data = new CachedData(visibleRegions, topLevelRegions, startOffsets, endOffsets, foldedLines);
    myCachedData = data;
    return data;
  }

  boolean checkIfValidToCreate(int start, int end) {
    // check that range doesn't strictly overlaps other regions and is distinct from everything else
    return myMarkerTree.processOverlappingWith(start, end, region->{
      int rStart = region.getStartOffset();
      int rEnd = region.getEndOffset();
      if (rStart < start) {
        if (region.isValid() && start < rEnd && rEnd < end) {
          return false;
        }
      }
      else if (rStart == start) {
        if (rEnd == end) {
          return false;
        }
      }
      else {
        if (rStart > end) {
          return true;
        }
        if (region.isValid() && rStart < end && end < rEnd) {
          return false;
        }
      }
      return true;
    });
  }

  private CachedData ensureAvailableData() {
    CachedData cachedData = myCachedData;
    if (!cachedData.isAvailable()) {
      return rebuild();
    }
    return cachedData;
  }

  @Nullable
  FoldRegion fetchOutermost(int offset) {
    if (!isFoldingEnabled()) return null;
    CachedData cachedData = ensureAvailableData();

    final int[] starts = cachedData.topStartOffsets;
    final int[] ends = cachedData.topEndOffsets;
    if (starts == null || ends == null) {
      return null;
    }

    int start = 0;
    int end = ends.length - 1;

    while (start <= end) {
      int i = (start + end) / 2;
      if (offset < starts[i]) {
        end = i - 1;
      }
      else if (offset > ends[i]) {
        start = i + 1;
      }
      else {
        return cachedData.topLevelRegions[i];
      }
    }

    return null;
  }

  FoldRegion[] fetchVisible() {
    if (!isFoldingEnabled()) return null;
    CachedData cachedData = ensureAvailableData();

    return cachedData.visibleRegions;
  }

  @Nullable
  FoldRegion[] fetchTopLevel() {
    if (!isFoldingEnabled()) return null;
    CachedData cachedData = ensureAvailableData();
    return cachedData.topLevelRegions;
  }

  static boolean containsStrict(FoldRegion region, int offset) {
    return region.getStartOffset() < offset && offset < region.getEndOffset();
  }

  @NotNull
  FoldRegion[] fetchCollapsedAt(int offset) {
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

  @NotNull
  FoldRegion[] fetchAllRegions() {
    if (!isFoldingEnabled()) return FoldRegion.EMPTY_ARRAY;
    List<FoldRegion> regions = new ArrayList<>();
    myMarkerTree.processOverlappingWith(0, Integer.MAX_VALUE, new CommonProcessors.CollectProcessor<>(regions));
    return toFoldArray(regions);
  }

  private void forEach(@NotNull Consumer<? super FoldRegion> consumer) {
    myMarkerTree.processAll(region -> { consumer.consume(region); return true; });
  }

  int getFoldedLinesCountBefore(int offset) {
    if (!isFoldingEnabled()) return 0;
    CachedData cachedData = ensureAvailableData();
    int idx = getLastTopLevelIndexBefore(cachedData, offset);
    if (idx == -1) return 0;
    return cachedData.topFoldedLines[idx];
  }

  int getTotalNumberOfFoldedLines() {
    if (!isFoldingEnabled()) return 0;
    CachedData cachedData = ensureAvailableData();
    int[] foldedLines = cachedData.topFoldedLines;

    if (foldedLines == null || foldedLines.length == 0) return 0;
    return foldedLines[foldedLines.length - 1];
  }

  int getLastTopLevelIndexBefore(int offset) {
    if (!isFoldingEnabled()) return -1;
    CachedData cachedData = ensureAvailableData();
    return getLastTopLevelIndexBefore(cachedData, offset);
  }
  
  private static int getLastTopLevelIndexBefore(CachedData cachedData, int offset) {
    int[] endOffsets = cachedData.topEndOffsets;

    if (endOffsets == null) return -1;

    offset--; // end offsets are decremented in cache
    int start = 0;
    int end = endOffsets.length - 1;

    while (start <= end) {
      int i = (start + end) / 2;
      if (offset < endOffsets[i]) {
        end = i - 1;
      } else if (offset > endOffsets[i]) {
        start = i + 1;
      }
      else {
        return i;
      }
    }

    return end;
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

  private static class CachedData {
    private final FoldRegion[] visibleRegions;  // all foldings outside collapsed regions
    private final FoldRegion[] topLevelRegions; // all visible regions which are collapsed
    private final int[] topStartOffsets;
    private final int[] topEndOffsets;
    private final int[] topFoldedLines;

    private CachedData() {
      visibleRegions = null;
      topLevelRegions = null;
      topStartOffsets = null;
      topEndOffsets = null;
      topFoldedLines = null;
    }

    private CachedData(@NotNull FoldRegion[] visibleRegions,
                       @NotNull FoldRegion[] topLevelRegions,
                       @NotNull int[] topStartOffsets,
                       @NotNull int[] topEndOffsets,
                       @NotNull int[] topFoldedLines) {
      this.visibleRegions = visibleRegions;
      this.topLevelRegions = topLevelRegions;
      this.topStartOffsets = topStartOffsets;
      this.topEndOffsets = topEndOffsets;
      this.topFoldedLines = topFoldedLines;
    }

    private boolean isAvailable() {
      return visibleRegions != null;
    }
  }
}
