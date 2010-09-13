/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates information to cache for the single visual line.
 */
@SuppressWarnings("unchecked")
class CacheEntry implements Comparable<CacheEntry>, Cloneable {

  private static final TIntObjectHashMap<FoldingData> DUMMY = new TIntObjectHashMap<FoldingData>();

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

  public boolean locked;

  private final List<CacheEntry> myCache;
  private final Editor myEditor;
  private final EditorTextRepresentationHelper myRepresentationHelper;

  /** Holds positions for the tabulation symbols on a target visual line sorted by offset in ascending order. */
  private List<TabData> myTabPositions = Collections.EMPTY_LIST;

  /** Holds information about single line fold regions representation data. */
  private TIntObjectHashMap<FoldingData> myFoldingData = DUMMY;

  CacheEntry(int visualLine, Editor editor, EditorTextRepresentationHelper representationHelper, List<CacheEntry> cache) {
    this.visualLine = visualLine;
    myEditor = editor;
    myRepresentationHelper = representationHelper;
    myCache = cache;
  }

  public void setLineStartPosition(@NotNull ProcessingContext context, int i) {
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

    if (i > 1 && (startOffset - myCache.get(i - 1).endOffset) > 1) {
      assert false;
    }
  }

  public void setLineEndPosition(@NotNull ProcessingContext context) {
    endOffset = context.offset;
    endLogicalLine = context.logicalLine;
    endLogicalColumn = context.logicalColumn;
    endVisualColumn = context.visualColumn;
    endSoftWrapLinesBefore = context.softWrapLinesBefore;
    endSoftWrapLinesCurrent = context.softWrapLinesCurrent;
    endSoftWrapColumnDiff = context.softWrapColumnDiff;
    endFoldedLines = context.foldedLines;
    endFoldingColumnDiff = context.foldingColumnDiff;
  }

  public ProcessingContext buildStartLineContext() {
    ProcessingContext result = new ProcessingContext(myEditor, myRepresentationHelper);
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

  public ProcessingContext buildEndLineContext() {
    ProcessingContext result = new ProcessingContext(myEditor, myRepresentationHelper);
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

  public void store(FoldRegion foldRegion, int startX) {
    store(new FoldingData(foldRegion, startX, myRepresentationHelper, myEditor), foldRegion.getStartOffset());
  }

  public void store(FoldingData foldData, int offset) {
    if (myFoldingData == DUMMY) {
      myFoldingData = new TIntObjectHashMap<FoldingData>();
    }
    myFoldingData.put(offset, foldData);
  }

  public TIntObjectHashMap<FoldingData> getFoldingData() {
    return myFoldingData;
  }

  public List<TabData> getTabData() {
    return myTabPositions;
  }

  public void storeTabData(ProcessingContext context) {
    storeTabData(new TabData(context));
  }

  public void storeTabData(TabData tabData) {
    if (myTabPositions == Collections.EMPTY_LIST) {
      myTabPositions = new ArrayList<TabData>();
    }
    myTabPositions.add(tabData);
  }

  public void advance(final int offsetDiff) {
    startOffset += offsetDiff;
    endOffset += offsetDiff;
    for (TabData tabData : myTabPositions) {
      tabData.offset += offsetDiff;
    }
    final TIntObjectHashMap<FoldingData> newFoldingData = new TIntObjectHashMap<FoldingData>();
    myFoldingData.forEachEntry(new TIntObjectProcedure<FoldingData>() {
      @Override
      public boolean execute(int offset, FoldingData foldingData) {
        newFoldingData.put(offset + offsetDiff, foldingData);
        return true;
      }
    });
    myFoldingData = newFoldingData;
  }

  @Override
  public int compareTo(CacheEntry e) {
    return visualLine - e.visualLine;
  }

  @Override
  public String toString() {
    return "visual line: " + visualLine + ", offsets: " + startOffset + "-" + endOffset;
  }

  @Override
  protected CacheEntry clone() {
    final CacheEntry result = new CacheEntry(visualLine, myEditor, myRepresentationHelper, myCache);

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
