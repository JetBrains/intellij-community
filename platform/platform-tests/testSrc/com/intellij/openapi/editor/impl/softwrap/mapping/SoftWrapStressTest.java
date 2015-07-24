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
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SoftWrapStressTest extends AbstractEditorTest {
  private static final int ITERATIONS = 10000;
  private static final Long SEED_OVERRIDE = null; // set non-null value to run with a specific seed
  
  private static final List<? extends Action> ourActions = Arrays.asList(new AddText("a"),
                                                                         new AddText("\n"),
                                                                         new AddText("\t"),
                                                                         new RemoveCharacter(),
                                                                         new MoveCharacter(),
                                                                         new AddFoldRegion(),
                                                                         new RemoveFoldRegion(),
                                                                         new CollapseFoldRegion(),
                                                                         new ExpandFoldRegion());
  
  private final Random myRandom = new Random() {{
    //noinspection ConstantConditions
    setSeed(mySeed = (SEED_OVERRIDE == null ? nextLong() : SEED_OVERRIDE)); 
  }};
  private long mySeed;
  
  public void testSoftWrapModelInternalCachesStayConsistentAfterDocumentAndFoldingChanges() {
    int i = 0;
    try {
      initText("");
      configureSoftWraps(10);
      for (i = 1; i <= ITERATIONS; i++) {
        doRandomAction();
        checkConsistency();
      }
    }
    catch (Throwable t) {
      throw new RuntimeException("Failed when run with seed=" + mySeed + " in iteration " + i, t);
    }
  }
  
  private void doRandomAction() {
    ourActions.get(myRandom.nextInt(ourActions.size())).perform(myEditor, myRandom);
  }
  
  private static void checkConsistency() {
    Set<Integer> softWrapOffsets = checkSoftWraps();
    List<CacheEntry> cache = ((SoftWrapModelImpl)myEditor.getSoftWrapModel()).getDataMapper().getCache();
    CacheEntry prevEntry = null;
    for (CacheEntry entry : cache) {
      boolean currentLineStartsWithSoftWrap = checkRelationBetweenNeighbourEntries(entry, prevEntry, softWrapOffsets);
      checkConsistencyWithinEntry(entry, currentLineStartsWithSoftWrap);
      checkConsistencyWithFoldingsAndTabs(entry);
      prevEntry = entry;
    }
    assertTrue(softWrapOffsets.isEmpty());
  }

  private static void checkConsistencyWithFoldingsAndTabs(CacheEntry entry) {
    Map<Integer, FoldRegion> actualFoldRegions = new HashMap<Integer, FoldRegion>();
    List<Integer> actualTabPositions = new ArrayList<Integer>();
    for (int i = entry.startOffset; i < entry.endOffset; i++) {
      FoldRegion region = myEditor.getFoldingModel().getCollapsedRegionAtOffset(i);
      if (region == null) {
        if (myEditor.getDocument().getCharsSequence().charAt(i) == '\t') {
          actualTabPositions.add(i);
        }
      }
      else {
        if (region.getStartOffset() == i) {
          actualFoldRegions.put(i, region);
        }
      }
    }
    final Map<Integer, FoldRegion> cachedFoldRegions = new HashMap<Integer, FoldRegion>();
    entry.getFoldingData().forEachEntry(new TIntObjectProcedure<FoldingData>() {
      @Override
      public boolean execute(int offset, FoldingData data) {
        cachedFoldRegions.put(offset, data.getFoldRegion());
        return true;
      }
    });
    assertEquals(actualFoldRegions, cachedFoldRegions);
    List<Integer> cachedTabPositions = new ArrayList<Integer>();
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
  private static Set<Integer> checkSoftWraps() {
    List<? extends SoftWrap> softWraps = myEditor.getSoftWrapModel().getSoftWrapsForRange(0, myEditor.getDocument().getTextLength());
    Set<Integer> softWrapOffsets = new HashSet<Integer>();
    int lastOffset = -1;
    for (SoftWrap softWrap : softWraps) {
      assertEquals(softWrap.getStart(), softWrap.getEnd());
      assertTrue(softWrap.getStart() > lastOffset);
      lastOffset = softWrap.getStart();
      softWrapOffsets.add(lastOffset);
    }
    return softWrapOffsets;
  }

  interface Action {
    void perform(Editor editor, Random random);
  }
  
  private static class AddText implements Action {
    private final String myText;

    AddText(String text) {
      myText = text;
    }
    
    @Override
    public void perform(Editor editor, Random random) {
      Document document = editor.getDocument();
      int offset = random.nextInt(document.getTextLength() + 1);
      document.insertString(offset, myText);
    }
  }
  
  private static class RemoveCharacter implements Action {
    @Override
    public void perform(Editor editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int offset = random.nextInt(textLength);
      document.deleteString(offset, offset + 1);
    }
  }
  
  private static class MoveCharacter implements Action {
    @Override
    public void perform(Editor editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int offset = random.nextInt(textLength);
      int targetOffset = random.nextInt(textLength + 1);
      if (targetOffset < offset || targetOffset > offset + 1) {
        ((DocumentEx)document).moveText(offset, offset + 1, targetOffset);
      }
    }
  }
  
  private static class AddFoldRegion implements Action {
    @Override
    public void perform(final Editor editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      final int startOffset = random.nextInt(textLength + 1);
      final int endOffset = random.nextInt(textLength + 1);
      if (startOffset == endOffset) return;
      final FoldingModel foldingModel = editor.getFoldingModel();
      foldingModel.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          foldingModel.addFoldRegion(Math.min(startOffset, endOffset), Math.max(startOffset, endOffset), ".");
        }
      });
    }
  }
  
  private static class RemoveFoldRegion implements Action {
    @Override
    public void perform(final Editor editor, Random random) {
      final FoldingModel foldingModel = editor.getFoldingModel();
      FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
      if (foldRegions.length == 0) return;
      final FoldRegion region = foldRegions[random.nextInt(foldRegions.length)];
      foldingModel.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          foldingModel.removeFoldRegion(region);
        }
      });
    }
  }
  
  private static class CollapseFoldRegion implements Action {
    @Override
    public void perform(final Editor editor, Random random) {
      final FoldingModel foldingModel = editor.getFoldingModel();
      FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
      if (foldRegions.length == 0) return;
      final FoldRegion region = foldRegions[random.nextInt(foldRegions.length)];
      foldingModel.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          region.setExpanded(false);
        }
      });
    }
  }
  
  private static class ExpandFoldRegion implements Action {
    @Override
    public void perform(final Editor editor, Random random) {
      final FoldingModel foldingModel = editor.getFoldingModel();
      FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
      if (foldRegions.length == 0) return;
      final FoldRegion region = foldRegions[random.nextInt(foldRegions.length)];
      foldingModel.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          region.setExpanded(true);
        }
      });
    }
  }
}
