/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorStressTest;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SoftWrapStressTest extends EditorStressTest {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("editor.new.rendering").setValue(false);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Registry.get("editor.new.rendering").setValue(true);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected void checkConsistency(Editor editor) {
    Set<Integer> softWrapOffsets = checkSoftWraps(editor);
    List<CacheEntry> cache = ((SoftWrapModelImpl)editor.getSoftWrapModel()).getDataMapper().getCache();
    CacheEntry prevEntry = null;
    for (CacheEntry entry : cache) {
      boolean currentLineStartsWithSoftWrap = checkRelationBetweenNeighbourEntries(entry, prevEntry, softWrapOffsets);
      checkConsistencyWithinEntry(entry, currentLineStartsWithSoftWrap);
      checkConsistencyWithFoldingsAndTabs(editor, entry);
      prevEntry = entry;
    }
    assertTrue(softWrapOffsets.isEmpty());
  }

  private static void checkConsistencyWithFoldingsAndTabs(Editor editor, CacheEntry entry) {
    Map<Integer, FoldRegion> actualFoldRegions = new HashMap<>();
    List<Integer> actualTabPositions = new ArrayList<>();
    for (int i = entry.startOffset; i < entry.endOffset; i++) {
      FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(i);
      if (region == null) {
        if (editor.getDocument().getCharsSequence().charAt(i) == '\t') {
          actualTabPositions.add(i);
        }
      }
      else {
        if (region.getStartOffset() == i) {
          actualFoldRegions.put(i, region);
        }
      }
    }
    final Map<Integer, FoldRegion> cachedFoldRegions = new HashMap<>();
    entry.getFoldingData().forEachEntry((offset, data) -> {
      cachedFoldRegions.put(offset, data.getFoldRegion());
      return true;
    });
    assertEquals(actualFoldRegions, cachedFoldRegions);
    List<Integer> cachedTabPositions = new ArrayList<>();
    for (TabData tabData : entry.getTabData()) {
      cachedTabPositions.add(tabData.offset);
    }
    assertEquals(actualTabPositions, cachedTabPositions);
  }

  private static void checkConsistencyWithinEntry(CacheEntry entry, boolean currentLineStartsWithSoftWrap) {
    assertEquals(new VisualPosition(entry.visualLine, 0),
                 new LogicalPosition(entry.startLogicalLine, entry.startLogicalColumn, 
                                     entry.startSoftWrapLinesBefore, entry.startSoftWrapLinesCurrent, entry.startSoftWrapColumnDiff, 
                                     entry.startFoldedLines, entry.startFoldingColumnDiff).toVisualPosition());
    assertEquals(new VisualPosition(entry.visualLine, entry.endVisualColumn),
                 new LogicalPosition(entry.endLogicalLine, entry.endLogicalColumn, 
                                     entry.endSoftWrapLinesBefore, entry.endSoftWrapLinesCurrent, entry.endSoftWrapColumnDiff, 
                                     entry.endFoldedLines, entry.endFoldingColumnDiff).toVisualPosition());
    assertTrue(entry.endOffset > entry.startOffset);
    if (!currentLineStartsWithSoftWrap) {
      assertEquals(0, entry.endSoftWrapColumnDiff);
    }
    if (entry.getFoldingData().isEmpty()) {
      assertTrue(entry.endLogicalLine == entry.startLogicalLine);
      assertTrue(entry.endLogicalColumn > entry.startLogicalColumn);
      assertTrue(entry.endSoftWrapLinesBefore == entry.startSoftWrapLinesBefore);
      assertTrue(entry.endFoldedLines == entry.startFoldedLines);
      assertTrue(entry.endFoldingColumnDiff == entry.startFoldingColumnDiff);
      assertTrue(entry.endSoftWrapColumnDiff >= entry.startSoftWrapColumnDiff);
    }
    else {
      assertTrue(entry.endLogicalLine >= entry.startLogicalLine);
      assertTrue(entry.endSoftWrapLinesBefore >= entry.startSoftWrapLinesBefore);
      assertTrue(entry.endFoldedLines >= entry.startFoldedLines);
    }
  }

  private static boolean checkRelationBetweenNeighbourEntries(CacheEntry entry, CacheEntry prevEntry, Set<Integer> softWrapOffsets) {
    if (prevEntry == null) {
      assertEquals(0, entry.startLogicalColumn);
      assertEquals(0, entry.startSoftWrapLinesBefore);
      assertEquals(0, entry.startSoftWrapLinesCurrent);
      assertEquals(0, entry.startSoftWrapColumnDiff);
      assertEquals(0, entry.startFoldedLines);
      assertEquals(0, entry.startFoldingColumnDiff);
      return false;
    }
    else {
      if (entry.startOffset == prevEntry.endOffset) {
        assertEquals(prevEntry.visualLine + 1, entry.visualLine);
        assertEquals(prevEntry.endLogicalLine, entry.startLogicalLine);
        assertEquals(prevEntry.endLogicalColumn, entry.startLogicalColumn);
        assertEquals(prevEntry.endSoftWrapLinesBefore, entry.startSoftWrapLinesBefore);
        assertEquals(prevEntry.endSoftWrapLinesCurrent + 1, entry.startSoftWrapLinesCurrent);
        assertEquals(prevEntry.endSoftWrapColumnDiff - prevEntry.endVisualColumn, entry.startSoftWrapColumnDiff);
        assertEquals(prevEntry.endFoldedLines, entry.startFoldedLines);
        assertEquals(prevEntry.endFoldingColumnDiff, entry.startFoldingColumnDiff);
        
        assertTrue(softWrapOffsets.remove(entry.startOffset));
        return true;
      }
      else {
        assertTrue(entry.visualLine > prevEntry.visualLine);
        assertTrue(entry.startOffset > prevEntry.endOffset);
        if (entry.visualLine == prevEntry.visualLine + 1) {
          assertEquals(prevEntry.endLogicalLine + 1, entry.startLogicalLine);
        }
        else {
          assertTrue(entry.startLogicalLine > prevEntry.endLogicalLine);
        }
        assertEquals(0, entry.startLogicalColumn);
        assertEquals(prevEntry.endSoftWrapLinesBefore + prevEntry.endSoftWrapLinesCurrent, entry.startSoftWrapLinesBefore);
        assertEquals(0, entry.startSoftWrapLinesCurrent);
        assertEquals(0, entry.startSoftWrapColumnDiff);
        assertEquals(prevEntry.endFoldedLines, entry.startFoldedLines);
        assertEquals(0, entry.startFoldingColumnDiff);
        return false;
      }
    }
  }

  @NotNull
  private static Set<Integer> checkSoftWraps(Editor editor) {
    List<? extends SoftWrap> softWraps = editor.getSoftWrapModel().getSoftWrapsForRange(0, editor.getDocument().getTextLength());
    Set<Integer> softWrapOffsets = new HashSet<>();
    int lastOffset = -1;
    for (SoftWrap softWrap : softWraps) {
      assertEquals(softWrap.getStart(), softWrap.getEnd());
      assertTrue(softWrap.getStart() > lastOffset);
      lastOffset = softWrap.getStart();
      softWrapOffsets.add(lastOffset);
    }
    return softWrapOffsets;
  }
}
