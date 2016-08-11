/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* User: cdr
*/
abstract class FoldRegionsTree {
  private static final Key<Boolean> VISIBLE = Key.create("visible.fold.region");
  
  @NotNull private volatile CachedData myCachedData = new CachedData();

  //sorted using RangeMarker.BY_START_OFFSET comparator
  //i.e., first by start offset, then, if start offsets are equal, by end offset
  @NotNull
  private List<FoldRegion> myRegions = ContainerUtil.newArrayList();

  private static final Comparator<FoldRegion> BY_END_OFFSET = (r1, r2) -> {
    int end1 = r1.getEndOffset();
    int end2 = r2.getEndOffset();
    if (end1 < end2) return -1;
    if (end1 > end2) return 1;
    return 0;
  };
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
  
  void clear() {
    clearCachedValues();

    for (FoldRegion region : myRegions) {
      region.dispose();
    }

    myRegions = new ArrayList<>();
  }

  void clearCachedValues() {
    myCachedData = new CachedData();
  }

  protected abstract boolean isFoldingEnabled();

  void rebuild() {
    List<FoldRegion> topLevels = new ArrayList<>(myRegions.size() / 2);
    List<FoldRegion> visible = new ArrayList<>(myRegions.size());
    List<FoldRegion> allValid = new ArrayList<>(myRegions.size());
    
    THashMap<FoldRegion, FoldRegion> distinctRegions = new THashMap<>(myRegions.size(), OFFSET_BASED_HASHING_STRATEGY);
    for (FoldRegion region : myRegions) {
      if (!region.isValid()) {
        continue;
      }
      if (distinctRegions.contains(region)) {
        if (region.getUserData(VISIBLE) == null) {
          region.dispose();
          continue;
        }
        else {
          FoldRegion identicalRegion = distinctRegions.remove(region);
          identicalRegion.dispose();
        }
      }
      distinctRegions.put(region, region);
    }
    
    for (FoldRegion region : myRegions) {
      if (region.isValid()) {
        allValid.add(region);
      }
    }

    if (allValid.size() < myRegions.size()) {
      myRegions = allValid;
    }
    Collections.sort(myRegions, RangeMarker.BY_START_OFFSET); // the order could have changed due to document changes 

    FoldRegion currentCollapsed = null;
    for (FoldRegion region : myRegions) {
      if (!region.isExpanded()) {
        removeRegionsWithSameStartOffset(visible, region);
        removeRegionsWithSameStartOffset(topLevels, region);
      }

      if (currentCollapsed == null || !contains(currentCollapsed, region)) {
        visible.add(region);
        region.putUserData(VISIBLE, Boolean.TRUE);
        if (!region.isExpanded()) {
          currentCollapsed = region;
          topLevels.add(region);
        }
      }
      else {
        region.putUserData(VISIBLE, null);
      }
    }

    FoldRegion[] topLevelRegions = toFoldArray(topLevels);
    FoldRegion[] visibleRegions = toFoldArray(visible);

    Arrays.sort(topLevelRegions, BY_END_OFFSET);
    Arrays.sort(visibleRegions, BY_END_OFFSET_REVERSE);

    updateCachedOffsets(visibleRegions, topLevelRegions);
  }

  private static void removeRegionsWithSameStartOffset(List<FoldRegion> regions, FoldRegion region) {
    for (int i = regions.size() - 1; i >= 0 ; i--) {
      if (regions.get(i).getStartOffset() == region.getStartOffset()) {
        regions.remove(i);
      }
      else {
        break;
      }
    }
  }

  @NotNull
  private static FoldRegion[] toFoldArray(@NotNull List<FoldRegion> topLevels) {
    return topLevels.isEmpty() ? FoldRegion.EMPTY_ARRAY : topLevels.toArray(new FoldRegion[topLevels.size()]);
  }

  void updateCachedOffsets() {
    CachedData cachedData = myCachedData;
    updateCachedOffsets(cachedData.visibleRegions, cachedData.topLevelRegions);
  }
  
  private void updateCachedOffsets(FoldRegion[] visibleRegions, FoldRegion[] topLevelRegions) {
    if (!isFoldingEnabled()) {
      return;
    }
    if (visibleRegions == null) {
      rebuild();
      return;
    }
    
    Set<FoldRegion> distinctRegions = new THashSet<>(visibleRegions.length, OFFSET_BASED_HASHING_STRATEGY);

    for (FoldRegion foldRegion : visibleRegions) {
      if (!foldRegion.isValid() || !distinctRegions.add(foldRegion)) {
        rebuild();
        return;
      }
    }

    int length = topLevelRegions.length;
    int[] startOffsets = ArrayUtil.newIntArray(length);
    int[] endOffsets = ArrayUtil.newIntArray(length);
    int[] foldedLines = ArrayUtil.newIntArray(length);
    
    int sum = 0;
    for (int i = 0; i < length; i++) {
      FoldRegion region = topLevelRegions[i];
      startOffsets[i] = region.getStartOffset();
      endOffsets[i] = region.getEndOffset() - 1;
      Document document = region.getDocument();
      sum += document.getLineNumber(region.getEndOffset()) - document.getLineNumber(region.getStartOffset());
      foldedLines[i] = sum;
    }
    
    myCachedData = new CachedData(visibleRegions, topLevelRegions, startOffsets, endOffsets, foldedLines);
  }

  boolean addRegion(@NotNull FoldRegion range) {
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    int insertionIndex = myRegions.size();
    for (int i = 0; i < myRegions.size(); i++) {
      FoldRegion region = myRegions.get(i);
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
        else if (rEnd > end) {
          insertionIndex = Math.min(insertionIndex, i);
        }
      }
      else {
        insertionIndex = Math.min(insertionIndex, i);
        if (rStart > end) {
          break;
        }
        if (region.isValid() && rStart < end && end < rEnd) {
          return false;
        }
      }
    }

    myRegions.add(insertionIndex, range);
    return true;
  }

  @Nullable
  FoldRegion fetchOutermost(int offset) {
    CachedData cachedData = myCachedData;
    if (cachedData.isUnavailable()) return null;

    final int[] starts = cachedData.startOffsets;
    final int[] ends = cachedData.endOffsets;
    if (starts == null || ends == null) {
      return null;
    }

    int start = 0;
    int end = ends.length - 1;

    while (start <= end) {
      int i = (start + end) / 2;
      if (offset < starts[i]) {
        end = i - 1;
      } else if (offset > ends[i]) {
        start = i + 1;
      }
      else {
        return cachedData.topLevelRegions[i];
      }
    }

    return null;
  }

  FoldRegion[] fetchVisible() {
    CachedData cachedData = myCachedData;
    return cachedData.isUnavailable() ? FoldRegion.EMPTY_ARRAY : cachedData.visibleRegions;
  }

  @Nullable
  FoldRegion[] fetchTopLevel() {
    CachedData cachedData = myCachedData;
    return cachedData.isUnavailable() ? null : cachedData.topLevelRegions;
  }

  private static boolean contains(FoldRegion outer, FoldRegion inner) {
    return outer.getStartOffset() <= inner.getStartOffset() && outer.getEndOffset() >= inner.getEndOffset();
  }

  static boolean contains(FoldRegion region, int offset) {
    return region.getStartOffset() < offset && region.getEndOffset() > offset;
  }

  public FoldRegion[] fetchCollapsedAt(int offset) {
    if (myCachedData.isUnavailable()) return FoldRegion.EMPTY_ARRAY;
    ArrayList<FoldRegion> allCollapsed = new ArrayList<>();
    for (FoldRegion region : myRegions) {
      if (!region.isExpanded() && contains(region, offset)) {
        allCollapsed.add(region);
      }
    }

    return toFoldArray(allCollapsed);
  }

  boolean intersectsRegion(int startOffset, int endOffset) {
    if (!isFoldingEnabled()) return true;
    for (FoldRegion region : myRegions) {
      boolean contains1 = contains(region, startOffset);
      boolean contains2 = contains(region, endOffset);
      if (contains1 != contains2) {
        return true;
      }
    }
    return false;
  }

  FoldRegion[] fetchAllRegions() {
    if (myCachedData.isUnavailable()) return FoldRegion.EMPTY_ARRAY;

    return toFoldArray(myRegions);
  }

  void removeRegion(@NotNull FoldRegion range) {
    myRegions.remove(range);
  }

  int getFoldedLinesCountBefore(int offset) {
    CachedData snapshot = myCachedData;
    int idx = getLastTopLevelIndexBefore(snapshot, offset);
    if (idx == -1) return 0;
    return snapshot.foldedLines[idx];
  }

  int getTotalNumberOfFoldedLines() {
    CachedData snapshot = myCachedData;
    int[] foldedLines = snapshot.foldedLines;
    if (snapshot.isUnavailable() || foldedLines == null || foldedLines.length == 0) return 0;
    return foldedLines[foldedLines.length - 1];
  }

  public int getLastTopLevelIndexBefore(int offset) {
    return getLastTopLevelIndexBefore(myCachedData, offset);
  }
  
  private static int getLastTopLevelIndexBefore(CachedData snapshot, int offset) {
    int[] endOffsets = snapshot.endOffsets;
    if (snapshot.isUnavailable() || endOffsets == null) return -1;

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
  public FoldRegion getRegionAt(int startOffset, int endOffset) {
    int index = Collections.binarySearch(myRegions, new DummyFoldRegion(startOffset, endOffset), RangeMarker.BY_START_OFFSET);
    return index < 0 ? null : myRegions.get(index);
  }

  void clearDocumentRangesModificationStatus() {
    for (FoldRegion region : myRegions) {
      if (region instanceof FoldRegionImpl) {
        ((FoldRegionImpl)region).resetDocumentRegionChanged();
      }
    }
  }

  private class CachedData implements Cloneable {
    private final FoldRegion[] visibleRegions;
    private final FoldRegion[] topLevelRegions;
    private final int[] startOffsets;
    private final int[] endOffsets;
    private final int[] foldedLines;

    private CachedData() {
      this.visibleRegions = null;
      this.topLevelRegions = null;
      this.startOffsets = null;
      this.endOffsets = null;
      this.foldedLines = null;
    }

    private CachedData(FoldRegion[] visibleRegions, FoldRegion[] topLevelRegions, int[] startOffsets, int[] endOffsets, int[] foldedLines) {
      this.visibleRegions = visibleRegions;
      this.topLevelRegions = topLevelRegions;
      this.startOffsets = startOffsets;
      this.endOffsets = endOffsets;
      this.foldedLines = foldedLines;
    }

    private boolean isUnavailable() {
      return !isFoldingEnabled() || visibleRegions == null;
    }
  }

  private static class DummyFoldRegion implements FoldRegion {
    private final int myStartOffset;
    private final int myEndOffset;

    private DummyFoldRegion(int startOffset, int endOffset) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
    }

    @Override
    public boolean isExpanded() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setExpanded(boolean expanded) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String getPlaceholderText() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Editor getEditor() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public FoldingGroup getGroup() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean shouldNeverExpand() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Document getDocument() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getStartOffset() {
      return myStartOffset;
    }

    @Override
    public int getEndOffset() {
      return myEndOffset;
    }

    @Override
    public boolean isValid() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setGreedyToLeft(boolean greedy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setGreedyToRight(boolean greedy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGreedyToRight() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGreedyToLeft() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      throw new UnsupportedOperationException();
    }
  }
}
