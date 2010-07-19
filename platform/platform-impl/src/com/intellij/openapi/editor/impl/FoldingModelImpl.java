/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 4, 2002
 * Time: 8:27:13 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class FoldingModelImpl implements FoldingModelEx, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorFoldingModelImpl");
  private boolean myIsFoldingEnabled;
  private final EditorImpl myEditor;
  private final FoldRegionsTree myFoldTree;
  private TextAttributes myFoldTextAttributes;
  private boolean myIsBatchFoldingProcessing;
  private boolean myDoNotCollapseCaret;
  private boolean myFoldRegionsProcessed;

  private int mySavedCaretX;
  private int mySavedCaretY;
  private int mySavedCaretShift;
  private boolean myCaretPositionSaved;
  private final MultiMap<FoldingGroup, FoldRegion> myGroups = new MultiMap<FoldingGroup, FoldRegion>();
  private static final Comparator<FoldRegion> BY_END_OFFSET = new Comparator<FoldRegion>() {
    public int compare(FoldRegion r1, FoldRegion r2) {
      int end1 = r1.getEndOffset();
      int end2 = r2.getEndOffset();
      if (end1 < end2) return -1;
      if (end1 > end2) return 1;
      return 0;
    }
  };
  private static final Comparator<? super FoldRegion> BY_END_OFFSET_REVERSE = Collections.reverseOrder(BY_END_OFFSET);

  public FoldingModelImpl(EditorImpl editor) {
    myEditor = editor;
    myIsFoldingEnabled = true;
    myIsBatchFoldingProcessing = false;
    myDoNotCollapseCaret = false;
    myFoldTree = new FoldRegionsTree();
    myFoldRegionsProcessed = false;
    refreshSettings();
  }

  @NotNull
  public List<FoldRegion> getGroupedRegions(@NotNull FoldingGroup group) {
    return (List<FoldRegion>)myGroups.get(group);
  }

  @NotNull
  public FoldRegion getFirstRegion(@NotNull FoldingGroup group, FoldRegion child) {
    final List<FoldRegion> regions = getGroupedRegions(group);
    if (regions.isEmpty()) {
      final boolean inAll = Arrays.asList(getAllFoldRegions()).contains(child);
      final boolean inAllPlusInvalid = Arrays.asList(getAllFoldRegionsIncludingInvalid()).contains(child);
      throw new AssertionError("Folding group without children; the known child is in all: " + inAll + "; in all+invalid: " + inAllPlusInvalid);
    }

    FoldRegion main = regions.get(0);
    for (int i = 1; i < regions.size(); i++) {
      FoldRegion region = regions.get(i);
      if (main.getStartOffset() > region.getStartOffset()) {
        main = region;
      }
    }
    return main;
  }

  public int getEndOffset(@NotNull FoldingGroup group) {
    final List<FoldRegion> regions = getGroupedRegions(group);
    int endOffset = 0;
    for (FoldRegion region : regions) {
      if (region.isValid()) {
        endOffset = Math.max(endOffset, region.getEndOffset());
      }
    }
    return endOffset;
  }

  public void refreshSettings() {
    myFoldTextAttributes = myEditor.getColorsScheme().getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES);
  }

  public boolean isFoldingEnabled() {
    return myIsFoldingEnabled;
  }

  public boolean isOffsetCollapsed(int offset) {
    assertReadAccess();
    return getCollapsedRegionAtOffset(offset) != null;
  }

  private void assertIsDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditor.getComponent());
  }
  private static void assertReadAccess() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessAllowed();
  }

  public void setFoldingEnabled(boolean isEnabled) {
    assertIsDispatchThread();
    myIsFoldingEnabled = isEnabled;
  }

  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    FoldRegion region = createFoldRegion(startOffset, endOffset, placeholderText, null);
    return addFoldRegion(region) ? region : null;
  }

  public boolean addFoldRegion(@NotNull final FoldRegion region) {
    assertIsDispatchThread();
    if (isFoldingEnabled()) {
      if (!myIsBatchFoldingProcessing) {
        LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
        return false;
      }

      myFoldRegionsProcessed = true;
      if (myFoldTree.addRegion(region)) {
        final FoldingGroup group = region.getGroup();
        if (group != null) {
          myGroups.putValue(group, region);
        }
        return true;
      }
    }

    return false;
  }

  public void runBatchFoldingOperation(@NotNull Runnable operation) {
    runBatchFoldingOperation(operation, false);
  }

  private void runBatchFoldingOperation(final Runnable operation, final boolean dontCollapseCaret) {
    assertIsDispatchThread();
    boolean oldDontCollapseCaret = myDoNotCollapseCaret;
    myDoNotCollapseCaret |= dontCollapseCaret;
    boolean oldBatchFlag = myIsBatchFoldingProcessing;
    if (!oldBatchFlag) {
      mySavedCaretShift = myEditor.visibleLineNumberToYPosition(myEditor.getCaretModel().getVisualPosition().line) - myEditor.getScrollingModel().getVerticalScrollOffset();
    }

    myIsBatchFoldingProcessing = true;
    myFoldTree.myCachedLastIndex = -1;
    operation.run();
    myFoldTree.myCachedLastIndex = -1;

    if (!oldBatchFlag) {
      if (myFoldRegionsProcessed) {
        notifyBatchFoldingProcessingDone();
        myFoldRegionsProcessed = false;
      }
      myIsBatchFoldingProcessing = false;
    }
    myDoNotCollapseCaret = oldDontCollapseCaret;
  }

  public void runBatchFoldingOperationDoNotCollapseCaret(@NotNull final Runnable operation) {
    runBatchFoldingOperation(operation, true);
  }

  public void flushCaretShift() {
    mySavedCaretShift = -1;
  }

  @NotNull
  public FoldRegion[] getAllFoldRegions() {
    assertReadAccess();
    return myFoldTree.fetchAllRegions();
  }

  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  int getLastTopLevelIndexBefore (int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  public FoldRegion getFoldingPlaceholderAt(Point p) {
    assertReadAccess();
    LogicalPosition pos = myEditor.xyToLogicalPosition(p);
    int line = pos.line;

    if (line >= myEditor.getDocument().getLineCount()) return null;

    //leftmost folded block position
    if (myEditor.xyToVisualPosition(p).equals(myEditor.logicalToVisualPosition(pos))) return null;

    int offset = myEditor.logicalPositionToOffset(pos);

    return myFoldTree.fetchOutermost(offset);
  }

  public FoldRegion[] getAllFoldRegionsIncludingInvalid() {
    assertReadAccess();
    return myFoldTree.fetchAllRegionsIncludingInvalid();
  }

  public void removeFoldRegion(@NotNull final FoldRegion region) {
    assertIsDispatchThread();

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }

    region.setExpanded(true);
    final FoldingGroup group = region.getGroup();
    if (group != null) {
      myGroups.removeValue(group, region);
      }
    myFoldTree.removeRegion(region);
    myFoldRegionsProcessed = true;
  }

  public void expandFoldRegion(FoldRegion region) {
    assertIsDispatchThread();
    if (region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    if (myCaretPositionSaved) {
      int savedOffset = myEditor.logicalPositionToOffset(new LogicalPosition(mySavedCaretY, mySavedCaretX));

      FoldRegion[] allCollapsed = myFoldTree.fetchCollapsedAt(savedOffset);
      if (allCollapsed.length == 1 && allCollapsed[0] == region) {
        LogicalPosition pos = new LogicalPosition(mySavedCaretY, mySavedCaretX);
        myEditor.getCaretModel().moveToLogicalPosition(pos);
      }
    }

    myFoldRegionsProcessed = true;
    ((FoldRegionImpl) region).setExpandedInternal(true);
  }

  public void collapseFoldRegion(FoldRegion region) {
    assertIsDispatchThread();
    if (!region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();

    int caretOffset = myEditor.logicalPositionToOffset(caretPosition);

    if (myFoldTree.contains(region, caretOffset)) {
      if (myDoNotCollapseCaret) return;

      if (!myCaretPositionSaved) {
        mySavedCaretX = caretPosition.column;
        mySavedCaretY = caretPosition.line;
        myCaretPositionSaved = true;
      }
    }

    int selectionStart = myEditor.getSelectionModel().getSelectionStart();
    int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();

    if (myFoldTree.contains(region, selectionStart-1) || myFoldTree.contains(region, selectionEnd)) myEditor.getSelectionModel().removeSelection();

    myFoldRegionsProcessed = true;
    ((FoldRegionImpl) region).setExpandedInternal(false);
  }

  private void notifyBatchFoldingProcessingDone() {
    myFoldTree.rebuild();

    myEditor.updateCaretCursor();
    myEditor.recalculateSizeAndRepaint();
    if (myEditor.getGutterComponentEx().isFoldingOutlineShown()) {
      myEditor.getGutterComponentEx().repaint();
    }

    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
    int caretOffset = myEditor.logicalPositionToOffset(caretPosition);
    boolean hasBlockSelection = myEditor.getSelectionModel().hasBlockSelection();
    int selectionStart = myEditor.getSelectionModel().getSelectionStart();
    int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();

    int column = -1;
    int line = -1;

    FoldRegion collapsed = myFoldTree.fetchOutermost(caretOffset);
    if (myCaretPositionSaved) {
      int savedOffset = myEditor.logicalPositionToOffset(new LogicalPosition(mySavedCaretY, mySavedCaretX));
      FoldRegion collapsedAtSaved = myFoldTree.fetchOutermost(savedOffset);
      column = mySavedCaretX;
      line = collapsedAtSaved != null ? collapsedAtSaved.getDocument().getLineNumber(collapsedAtSaved.getStartOffset()) : mySavedCaretY;
    }

    if (collapsed != null && column == -1) {
      line = collapsed.getDocument().getLineNumber(collapsed.getStartOffset());
      column = myEditor.getCaretModel().getVisualPosition().column;
    }

    boolean oldCaretPositionSaved = myCaretPositionSaved;

    if (column != -1) {
      LogicalPosition log = new LogicalPosition(line, 0);
      VisualPosition vis = myEditor.logicalToVisualPosition(log);
      VisualPosition pos = new VisualPosition(vis.line, column);
      myEditor.getCaretModel().moveToVisualPosition(pos);
    } else {
      myEditor.getCaretModel().moveToLogicalPosition(caretPosition);
    }

    myCaretPositionSaved = oldCaretPositionSaved;

    if (!hasBlockSelection) {
      myEditor.getSelectionModel().setSelection(selectionStart, selectionEnd);
    }

    if (mySavedCaretShift > 0) {
      myEditor.getScrollingModel().disableAnimation();
      int scrollTo = myEditor.visibleLineNumberToYPosition(myEditor.getCaretModel().getVisualPosition().line) - mySavedCaretShift;
      myEditor.getScrollingModel().scrollVertically(scrollTo);
      myEditor.getScrollingModel().enableAnimation();
    }
  }

  public void rebuild() {
    myFoldTree.rebuild();
  }

  private void updateCachedOffsets() {
    myFoldTree.updateCachedOffsets();
  }

  public int getFoldedLinesCountBefore(int offset) {
    return myFoldTree.getFoldedLinesCountBefore(offset);
  }

  public FoldRegion[] fetchTopLevel() {
    return myFoldTree.fetchTopLevel();
  }

  public FoldRegion fetchOutermost(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  public FoldRegion[] fetchCollapsedAt(int offset) {
    return myFoldTree.fetchCollapsedAt(offset);
  }

  public boolean intersectsRegion (int startOffset, int endOffset) {
    return myFoldTree.intersectsRegion(startOffset, endOffset);
  }

  public FoldRegion[] fetchVisible() {
    return myFoldTree.fetchVisible();
  }

  public int getLastCollapsedRegionBefore(int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  public TextAttributes getPlaceholderAttributes() {
    return myFoldTextAttributes;
  }

  public void flushCaretPosition() {
    myCaretPositionSaved = false;
  }

  class FoldRegionsTree {
    private FoldRegion[] myCachedVisible;
    private FoldRegion[] myCachedTopLevelRegions;
    private int[] myCachedEndOffsets;
    private int[] myCachedStartOffsets;
    private int[] myCachedFoldedLines;
    private int myCachedLastIndex = -1;
    private ArrayList<FoldRegion> myRegions = CollectionFactory.arrayList();  //sorted in tree left-to-right topdown traversal order

    private void clear() {
      myCachedVisible = null;
      myCachedTopLevelRegions = null;
      myCachedEndOffsets = null;
      myCachedStartOffsets = null;
      myCachedFoldedLines = null;
      myRegions = new ArrayList<FoldRegion>();
    }

    private boolean isFoldingEnabled() {
      return FoldingModelImpl.this.isFoldingEnabled() && myCachedVisible != null;
    }

    void rebuild() {
      ArrayList<FoldRegion> topLevels = new ArrayList<FoldRegion>(myRegions.size() / 2);
      ArrayList<FoldRegion> visible = new ArrayList<FoldRegion>(myRegions.size());
      FoldRegion[] regions = myRegions.toArray(new FoldRegion[myRegions.size()]);
      FoldRegion currentToplevel = null;
      for (FoldRegion region : regions) {
        if (region.isValid()) {
          visible.add(region);
          if (!region.isExpanded()) {
            if (currentToplevel == null || currentToplevel.getEndOffset() < region.getStartOffset()) {
              currentToplevel = region;
              topLevels.add(region);
            }
          }
        }
      }

      myCachedTopLevelRegions = topLevels.isEmpty() ? FoldRegion.EMPTY_ARRAY : topLevels.toArray(new FoldRegion[topLevels.size()]);

      Arrays.sort(myCachedTopLevelRegions, BY_END_OFFSET);

      FoldRegion[] visibleArrayed = visible.toArray(new FoldRegion[visible.size()]);
      for (FoldRegion visibleRegion : visibleArrayed) {
        for (FoldRegion topLevelRegion : myCachedTopLevelRegions) {
          if (contains(topLevelRegion, visibleRegion)) {
            visible.remove(visibleRegion);
            break;
          }
        }
      }

      myCachedVisible = visible.isEmpty() ? FoldRegion.EMPTY_ARRAY : visible.toArray(new FoldRegion[visible.size()]);

      Arrays.sort(myCachedVisible, BY_END_OFFSET_REVERSE);

      updateCachedOffsets();
    }

    void updateCachedOffsets() {
      if (FoldingModelImpl.this.isFoldingEnabled()) {
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
          sum += region.getDocument().getLineNumber(region.getEndOffset()) - region.getDocument().getLineNumber(region.getStartOffset());
          myCachedFoldedLines[i] = sum;
        }
      }
    }

    boolean addRegion(FoldRegion range) {
      // During batchProcessing elements are inserted in ascending order, 
      // binary search find acceptable insertion place first time
      int fastIndex = myCachedLastIndex != -1 && myIsBatchFoldingProcessing? myCachedLastIndex + 1:Collections.binarySearch(myRegions, range, RangeMarker.BY_START_OFFSET);
      if (fastIndex < 0) fastIndex = -fastIndex - 1;

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

    FoldRegion fetchOutermost(int offset) {
      if (!isFoldingEnabled()) return null;

      final int[] starts = myCachedStartOffsets;
      final int[] ends = myCachedEndOffsets;

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
          return myCachedTopLevelRegions[i];
        }
      }

      return null;
    }

    FoldRegion[] fetchVisible() {
      if (!isFoldingEnabled()) return FoldRegion.EMPTY_ARRAY;
      return myCachedVisible;
    }

    FoldRegion[] fetchTopLevel() {
      if (!isFoldingEnabled()) return null;
      return myCachedTopLevelRegions;
    }

    private boolean contains(FoldRegion outer, FoldRegion inner) {
      return outer.getStartOffset() < inner.getStartOffset() && outer.getEndOffset() > inner.getStartOffset();
    }

    private boolean intersects(FoldRegion r1, FoldRegion r2) {
      final int s1 = r1.getStartOffset();
      final int s2 = r2.getStartOffset();
      final int e1 = r1.getEndOffset();
      final int e2 = r2.getEndOffset();
      return s1 == s2 && e1 == e2 || s1 < s2 && s2 < e1 && e1 < e2 || s2 < s1 && s1 < e2 && e2 < e1;
    }

    private boolean contains(FoldRegion region, int offset) {
      return region.getStartOffset() < offset && region.getEndOffset() > offset;
    }

    public FoldRegion[] fetchCollapsedAt(int offset) {
      if (!isFoldingEnabled()) return FoldRegion.EMPTY_ARRAY;
      ArrayList<FoldRegion> allCollapsed = new ArrayList<FoldRegion>();
      for (FoldRegion region : myRegions) {
        if (!region.isExpanded() && contains(region, offset)) {
          allCollapsed.add(region);
        }
      }

      return allCollapsed.toArray(new FoldRegion[allCollapsed.size()]);
    }

    boolean intersectsRegion(int startOffset, int endOffset) {
      if (!FoldingModelImpl.this.isFoldingEnabled()) return true;
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
      if (!isFoldingEnabled()) return FoldRegion.EMPTY_ARRAY;

      return myRegions.toArray(new FoldRegion[myRegions.size()]);
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
      if (!isFoldingEnabled()) return -1;

      int start = 0;
      int end = myCachedEndOffsets.length - 1;

      while (start <= end) {
        int i = (start + end) / 2;
        if (offset < myCachedEndOffsets[i]) {
          end = i - 1;
        } else if (offset > myCachedEndOffsets[i]) {
          start = i + 1;
        }
        else {
          return i;
        }
      }

      return end;

//      for (int i = 0; i < myCachedEndOffsets.length; i++) {
//        if (!myCachedTopLevelRegions[i].isValid()) continue;
//        int endOffset = myCachedEndOffsets[i];
//        if (endOffset > offset) break;
//        lastIndex = i;
//      }
//
//      return lastIndex;
    }

    public FoldRegion[] fetchAllRegionsIncludingInvalid() {
      if (!FoldingModelImpl.this.isFoldingEnabled()) return FoldRegion.EMPTY_ARRAY;

      return myRegions.toArray(new FoldRegion[myRegions.size()]);
    }
  }

  public void beforeDocumentChange(DocumentEvent event) {
  }

  public void documentChanged(DocumentEvent event) {
    if (((DocumentEx)event.getDocument()).isInBulkUpdate()) {
      myFoldTree.clear();
    } else {
      updateCachedOffsets();
    }
  }

  public int getPriority() {
    return 1;
  }

  public FoldRegion createFoldRegion(int startOffset, int endOffset, @NotNull String placeholder, FoldingGroup group) {
    return new FoldRegionImpl(myEditor, startOffset, endOffset, placeholder, group);
  }
}
