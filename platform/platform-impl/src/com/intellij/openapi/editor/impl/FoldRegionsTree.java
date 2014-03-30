/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* User: cdr
*/
abstract class FoldRegionsTree {

  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  public static final boolean DEBUG = Boolean.getBoolean("idea.editor.debug.folding");
  
  private FoldRegion[] myCachedVisible;
  private FoldRegion[] myCachedTopLevelRegions;
  private int[] myCachedEndOffsets;
  private int[] myCachedStartOffsets;
  private int[] myCachedFoldedLines;
  int myCachedLastIndex = -1;
  private ArrayList<FoldRegion> myRegions = ContainerUtil.newArrayList();  //sorted in tree left-to-right topdown traversal order
  private static final Comparator<FoldRegion> BY_END_OFFSET = new Comparator<FoldRegion>() {
    @Override
    public int compare(FoldRegion r1, FoldRegion r2) {
      int end1 = r1.getEndOffset();
      int end2 = r2.getEndOffset();
      if (end1 < end2) return -1;
      if (end1 > end2) return 1;
      return 0;
    }
  };
  private static final Comparator<? super FoldRegion> BY_END_OFFSET_REVERSE = Collections.reverseOrder(BY_END_OFFSET);

  void clear() {
    myCachedVisible = null;
    myCachedTopLevelRegions = null;
    myCachedEndOffsets = null;
    myCachedStartOffsets = null;
    myCachedFoldedLines = null;

    if (myRegions != null) {
      for (FoldRegion region : myRegions) {
        region.dispose();
      }
    }

    myRegions = new ArrayList<FoldRegion>();
  }

  private boolean isFoldingEnabledAndUpToDate() {
    return isFoldingEnabled() && myCachedVisible != null;
  }

  protected abstract boolean isFoldingEnabled();

  protected abstract boolean isBatchFoldingProcessing();

  void rebuild() {
    ArrayList<FoldRegion> topLevels = new ArrayList<FoldRegion>(myRegions.size() / 2);
    ArrayList<FoldRegion> visible = new ArrayList<FoldRegion>(myRegions.size());
    FoldRegion[] regions = toFoldArray(myRegions);
    FoldRegion currentCollapsed = null;
    for (FoldRegion region : regions) {
      if (!region.isValid()) {
        continue;
      }
      
      if (currentCollapsed == null || !contains(currentCollapsed, region)) {
        visible.add(region);
      }
      
      if (!region.isExpanded()) {
        if (currentCollapsed == null || currentCollapsed.getEndOffset() < region.getStartOffset()) {
          currentCollapsed = region;
          topLevels.add(region);
        }
      }
    }

    myCachedTopLevelRegions = toFoldArray(topLevels);
    myCachedVisible = toFoldArray(visible);

    Arrays.sort(myCachedTopLevelRegions, BY_END_OFFSET);
    Arrays.sort(myCachedVisible, BY_END_OFFSET_REVERSE);

    updateCachedOffsets();
  }

  @NotNull
  private static FoldRegion[] toFoldArray(@NotNull List<FoldRegion> topLevels) {
    return topLevels.isEmpty() ? FoldRegion.EMPTY_ARRAY : topLevels.toArray(new FoldRegion[topLevels.size()]);
  }

  void updateCachedOffsets() {
    if (!isFoldingEnabled()) {
      return;
    }
    if (myCachedVisible == null) {
      rebuild();
      return;
    }

    for (FoldRegion foldRegion : myCachedVisible) {
      if (!foldRegion.isValid()) {
        rebuild();
        return;
      }
    }

    int length = myCachedTopLevelRegions.length;
    if (myCachedEndOffsets == null || myCachedEndOffsets.length != length) {
      if (length != 0) {
        myCachedEndOffsets = new int[length];
        myCachedStartOffsets = new int[length];
        myCachedFoldedLines = new int[length];
      }
      else {
        myCachedEndOffsets = ArrayUtil.EMPTY_INT_ARRAY;
        myCachedStartOffsets = ArrayUtil.EMPTY_INT_ARRAY;
        myCachedFoldedLines = ArrayUtil.EMPTY_INT_ARRAY;
      }
    }

    int sum = 0;
    for (int i = 0; i < length; i++) {
      FoldRegion region = myCachedTopLevelRegions[i];
      myCachedStartOffsets[i] = region.getStartOffset();
      myCachedEndOffsets[i] = region.getEndOffset() - 1;
      Document document = region.getDocument();
      sum += document.getLineNumber(region.getEndOffset()) - document.getLineNumber(region.getStartOffset());
      myCachedFoldedLines[i] = sum;
    }
  }

  boolean addRegion(FoldRegion range) {
    // During batchProcessing elements are inserted in ascending order,
    // binary search find acceptable insertion place first time
    final boolean canUseCachedValue;
    if (myCachedLastIndex >= myRegions.size()) {
      // todo this happens after removeRegion()... myCachedListIndex must die!
      canUseCachedValue = false;
    }
    else {
      canUseCachedValue =
        myCachedLastIndex != -1 && isBatchFoldingProcessing() && myRegions.get(myCachedLastIndex).getStartOffset() <= range.getStartOffset();
    }
    int fastIndex = canUseCachedValue ? myCachedLastIndex + 1 : Collections.binarySearch(myRegions, range, RangeMarker.BY_START_OFFSET);
    if (fastIndex < 0) fastIndex = -fastIndex - 1;
    
    // There is a possible case that given range is the first at the current batch iteration. It's also possible that it
    // range with the same bounds is already registered (e.g. particular range is registered during 'build initial fold regions' phase
    // and given range has the same offsets but different 'expanded' status.
    // We explicitly check for such situation, remove existing region and add the given one instead.
    if (fastIndex < myRegions.size()) {
      FoldRegion foldRegion = myRegions.get(fastIndex);
      if (TextRange.areSegmentsEqual(foldRegion, range)) {
        removeRegion(foldRegion);
        return addRegion(range);
      } 
    } 
    
    for (int i = fastIndex - 1; i >=0; --i) {
      final FoldRegion region = myRegions.get(i);
      if (region.getEndOffset() < range.getStartOffset()) break;
      if (region.isValid() && intersects(region, range)) {
        return false;
      }
    }

    for (int i = fastIndex; i < myRegions.size(); i++) {
      final FoldRegion region = myRegions.get(i);

      if (range.getStartOffset() < region.getStartOffset() ||
          range.getStartOffset() == region.getStartOffset() && range.getEndOffset() > region.getEndOffset()) {
        for (int j = i + 1; j < myRegions.size(); j++) {
          final FoldRegion next = myRegions.get(j);
          if (next.getEndOffset() >= range.getEndOffset() && next.isValid()) {
            if (next.getStartOffset() < range.getStartOffset()) {
              return false;
            }
            else {
              break;
            }
          }
        }

        myRegions.add(myCachedLastIndex = i, range);
        return true;
      }
    }
    myRegions.add(myCachedLastIndex = myRegions.size(),range);
    return true;
  }

  @Nullable
  FoldRegion fetchOutermost(int offset) {
    if (!isFoldingEnabledAndUpToDate()) return null;

    final int[] starts = myCachedStartOffsets;
    final int[] ends = myCachedEndOffsets;
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
        // We encountered situation when cached data is inconsistent. It's not clear what produced that, so, the following was done:
        //     1. Corresponding check was added and cached data is rebuilt in case of inconsistency;
        //     2. Debug asserts are activated if dedicated flag is on (it's off by default);
        if (myCachedStartOffsets[i] != myCachedTopLevelRegions[i].getStartOffset()) {
          if (DEBUG) {
            assert false :
              "inconsistent cached fold data detected. Start offsets: " + Arrays.toString(myCachedStartOffsets) 
              + ", end offsets: " + Arrays.toString(myCachedEndOffsets) + ", top regions: " + Arrays.toString(myCachedTopLevelRegions)
              + ", visible regions: " + Arrays.toString(myCachedVisible);
          }
          rebuild();
          return fetchOutermost(offset);
        }
        return myCachedTopLevelRegions[i];
      }
    }

    return null;
  }

  FoldRegion[] fetchVisible() {
    if (!isFoldingEnabledAndUpToDate()) return FoldRegion.EMPTY_ARRAY;
    return myCachedVisible;
  }

  @Nullable
  FoldRegion[] fetchTopLevel() {
    if (!isFoldingEnabledAndUpToDate()) return null;
    return myCachedTopLevelRegions;
  }

  private static boolean contains(FoldRegion outer, FoldRegion inner) {
    return outer.getStartOffset() < inner.getStartOffset() && outer.getEndOffset() > inner.getStartOffset();
  }

  private static boolean intersects(FoldRegion r1, FoldRegion r2) {
    final int s1 = r1.getStartOffset();
    final int s2 = r2.getStartOffset();
    final int e1 = r1.getEndOffset();
    final int e2 = r2.getEndOffset();
    return s1 == s2 && e1 == e2 || s1 < s2 && s2 < e1 && e1 < e2 || s2 < s1 && s1 < e2 && e2 < e1;
  }

  static boolean contains(FoldRegion region, int offset) {
    return region.getStartOffset() < offset && region.getEndOffset() > offset;
  }

  public FoldRegion[] fetchCollapsedAt(int offset) {
    if (!isFoldingEnabledAndUpToDate()) return FoldRegion.EMPTY_ARRAY;
    ArrayList<FoldRegion> allCollapsed = new ArrayList<FoldRegion>();
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
    if (!isFoldingEnabledAndUpToDate()) return FoldRegion.EMPTY_ARRAY;

    return toFoldArray(myRegions);
  }

  void removeRegion(FoldRegion range) {
    myRegions.remove(range);
  }

  int getFoldedLinesCountBefore(int offset) {
    int idx = getLastTopLevelIndexBefore(offset);
    if (idx == -1) return 0;
    return myCachedFoldedLines[idx];
  }

  public int getLastTopLevelIndexBefore(int offset) {
    int[] endOffsets = myCachedEndOffsets;
    if (!isFoldingEnabledAndUpToDate() || endOffsets == null) return -1;

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
}
