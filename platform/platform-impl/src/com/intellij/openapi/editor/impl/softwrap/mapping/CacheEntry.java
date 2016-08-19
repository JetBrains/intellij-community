/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.Ref;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates information to cache for the single visual line.
 */
@SuppressWarnings("unchecked")
class CacheEntry implements Comparable<CacheEntry>, Cloneable {

  private static final TIntObjectHashMap<FoldingData> DUMMY = new TIntObjectHashMap<>();

  public int visualLine;

  public int startLogicalLine;
  public int startLogicalColumn;
  public int startOffset;
  public int startSoftWrapLinesBefore;
  public int startSoftWrapLinesCurrent;
  public int startSoftWrapColumnDiff;
  public int startFoldedLines;
  public int startFoldingColumnDiff;

  public int endOffset;
  public int endLogicalLine;
  public int endLogicalColumn;
  public int endVisualColumn;
  public int endSoftWrapLinesBefore;
  public int endSoftWrapLinesCurrent;
  public int endSoftWrapColumnDiff;
  public int endFoldedLines;
  public int endFoldingColumnDiff;

  private final Editor myEditor;

  /** Holds positions for the tabulation symbols on a target visual line sorted by offset in ascending order. */
  private List<TabData> myTabPositions = Collections.EMPTY_LIST;

  /** Holds information about single line fold regions representation data. */
  private TIntObjectHashMap<FoldingData> myFoldingData = DUMMY;

  CacheEntry(int visualLine, @NotNull Editor editor) {
    this.visualLine = visualLine;
    myEditor = editor;
  }

  public void setLineStartPosition(@NotNull EditorPosition context) {
    assert context.visualColumn == 0;
    startLogicalLine = context.logicalLine;
    startLogicalColumn = context.logicalColumn;
    visualLine = context.visualLine;
    startOffset = context.offset;
    startSoftWrapLinesBefore = context.softWrapLinesBefore;
    startSoftWrapLinesCurrent = context.softWrapLinesCurrent;
    startSoftWrapColumnDiff = context.softWrapColumnDiff;
    startFoldedLines = context.foldedLines;
    startFoldingColumnDiff = context.foldingColumnDiff;
  }

  public void setLineEndPosition(@NotNull EditorPosition position) {
    endOffset = position.offset;
    endLogicalLine = position.logicalLine;
    endLogicalColumn = position.logicalColumn;
    endVisualColumn = position.visualColumn;
    endSoftWrapLinesBefore = position.softWrapLinesBefore;
    endSoftWrapLinesCurrent = position.softWrapLinesCurrent;
    endSoftWrapColumnDiff = position.softWrapColumnDiff;
    endFoldedLines = position.foldedLines;
    endFoldingColumnDiff = position.foldingColumnDiff;
  }

  public EditorPosition buildStartLinePosition() {
    EditorPosition result = new EditorPosition(myEditor);
    result.logicalLine = startLogicalLine;
    result.logicalColumn = startLogicalColumn;
    result.offset = startOffset;
    result.visualLine = visualLine;
    result.visualColumn = 0;
    result.softWrapLinesBefore = startSoftWrapLinesBefore;
    result.softWrapLinesCurrent = startSoftWrapLinesCurrent;
    result.softWrapColumnDiff = startSoftWrapColumnDiff;
    result.foldedLines = startFoldedLines;
    result.foldingColumnDiff = startFoldingColumnDiff;
    return result;
  }

  public EditorPosition buildEndLinePosition() {
    EditorPosition result = new EditorPosition(myEditor);
    result.logicalLine = endLogicalLine;
    result.logicalColumn = endLogicalColumn;
    result.offset = endOffset;
    result.visualLine = visualLine;
    result.visualColumn = endVisualColumn;
    result.softWrapLinesBefore = endSoftWrapLinesBefore;
    result.softWrapLinesCurrent = endSoftWrapLinesCurrent;
    result.softWrapColumnDiff = endSoftWrapColumnDiff;
    result.foldedLines = endFoldedLines;
    result.foldingColumnDiff = endFoldingColumnDiff;
    return result;
  }

  /**
   * Removes data for all tabs and fold regions that start at or after the given offset.
   * 
   * @param offset  target offset
   */
  public void removeAllDataAtOrAfter(final int offset) {
    if (myFoldingData != DUMMY && !myFoldingData.isEmpty()) {
      myFoldingData.retainEntries(new TIntObjectProcedure<FoldingData>() {
        @Override
        public boolean execute(int a, FoldingData b) {
          return a < offset;
        }
      });
    }
    int i;
    for (i = 0; i < myTabPositions.size(); i++) {
      if (myTabPositions.get(i).offset >= offset) {
        break;
      }
    }
    myTabPositions.subList(i, myTabPositions.size()).clear();
  }
  
  @Nullable
  public FoldingData getFoldingData(@NotNull final FoldRegion region) {
    FoldingData candidate = myFoldingData.get(region.getStartOffset());
    if (candidate != null) {
      return candidate;
    }
    
    // Folding implementation is known to postpone actual fold region offsets update on document change, i.e. it performs
    // fold data caching with its further replace by up-to-date info. Hence, there is a possible case that soft wraps processing
    // advances fold region offset but folding model still provides old cached values. Hence, we're trying to match exact given
    // fold region against the cached data here.
    final Ref<FoldingData> result = new Ref<>();
    myFoldingData.forEachValue(new TObjectProcedure<FoldingData>() {
      @Override
      public boolean execute(FoldingData data) {
        if (data.getFoldRegion().equals(region)) {
          result.set(data);
          return false;
        }
        return true;
      }
    });
    return result.get();
  }
  
  public void store(FoldingData foldData, int offset) {
    if (myFoldingData == DUMMY) {
      myFoldingData = new TIntObjectHashMap<>();
    }
    myFoldingData.put(offset, foldData);
  }

  public List<TabData> getTabData() {
    return myTabPositions;
  }

  public void storeTabData(TabData tabData) {
    if (myTabPositions == Collections.EMPTY_LIST) {
      myTabPositions = new ArrayList<>();
    }
    myTabPositions.add(tabData);
  }

  @SuppressWarnings("ForLoopReplaceableByForEach")
  public void advance(final int offsetDiff) {
    startOffset += offsetDiff;
    endOffset += offsetDiff;

    // 'For-each' loop is not used here because this code is called quite often and profile shows the Iterator usage here
    // produces performance drawback. 
    for (int i = 0; i < myTabPositions.size(); i++) {
      myTabPositions.get(i).offset += offsetDiff;
    }

    if (myFoldingData.isEmpty()) {
      return;
    }
    
    final TIntObjectHashMap<FoldingData> newFoldingData = new TIntObjectHashMap<>(myFoldingData.size());
    myFoldingData.forEachEntry(new TIntObjectProcedure<FoldingData>() {
      @Override
      public boolean execute(int offset, FoldingData foldingData) {
        newFoldingData.put(offset + offsetDiff, foldingData);
        return true;
      }
    });
    myFoldingData = newFoldingData;
  }

  @TestOnly
  public TIntObjectHashMap<FoldingData> getFoldingData() {
    return myFoldingData;
  }

  @Override
  public int compareTo(CacheEntry e) {
    return visualLine - e.visualLine;
  }

  @Override
  public String toString() {
    return String.format(
      "%d - visual line: %d, offsets: %d-%d, logical lines: %d-%d, logical columns: %d-%d, end visual column: %d, "
      + "fold regions: %s, tab data: %s",
      System.identityHashCode(this), visualLine, startOffset, endOffset, startLogicalLine, endLogicalLine, startLogicalColumn,
      endLogicalColumn, endVisualColumn, Arrays.toString(myFoldingData.getValues()), myTabPositions
    );
  }

  @Override
  protected CacheEntry clone() {
    final CacheEntry result = new CacheEntry(visualLine, myEditor);

    result.startLogicalLine = startLogicalLine;
    result.startLogicalColumn = startLogicalColumn;
    result.startOffset = startOffset;
    result.startSoftWrapLinesBefore = startSoftWrapLinesBefore;
    result.startSoftWrapLinesCurrent = startSoftWrapLinesCurrent;
    result.startSoftWrapColumnDiff = startSoftWrapColumnDiff;
    result.startFoldedLines = startFoldedLines;
    result.startFoldingColumnDiff = startFoldingColumnDiff;

    result.endOffset = endOffset;
    result.endLogicalLine = endLogicalLine;
    result.endLogicalColumn = endLogicalColumn;
    result.endVisualColumn = endVisualColumn;
    result.endSoftWrapLinesBefore = endSoftWrapLinesBefore;
    result.endSoftWrapLinesCurrent = endSoftWrapLinesCurrent;
    result.endSoftWrapColumnDiff = endSoftWrapColumnDiff;
    result.endFoldedLines = endFoldedLines;
    result.endFoldingColumnDiff = endFoldingColumnDiff;

    myFoldingData.forEachEntry(new TIntObjectProcedure<FoldingData>() {
      @Override
      public boolean execute(int offset, FoldingData foldData) {
        result.store(foldData, offset);
        return true;
      }
    });
    for (TabData tabPosition : myTabPositions) {
      result.storeTabData(tabPosition);
    }

    return result;
  }
}
