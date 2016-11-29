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

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Every document that is exposed to end-user via IJ editor has a number of various dimensions ({@link LogicalPosition logical}
 * and {@link VisualPosition visual} positions, {@link Document#getCharsSequence() text offset} etc. It's very important to be
 * able to map one dimension to another and do that effectively.
 * 
 * This class is implemented using the following principles:
 * <pre>
 * <ul>
 *   <li>it caches document dimension for starts of all visual lines;</li>
 *   <li>
 *        every time document position mapping should be performed this mapper chooses target visual line to process
 *        and calculates target position from its start (note that we can optimize that in order to choose calculation
 *        direction - 'from start' or 'from end');
 *   </li>
 *   <li>
 *        information about target visual lines is updated incrementally on document changes, i.e. we're trying to reduce
 *        calculations number as much as possible;
 *   </li>
 * </ul>
 * </pre>
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 10:24:47 AM
 */
public class CachingSoftWrapDataMapper implements SoftWrapAwareDocumentParsingListener, Dumpable {
  
  private static final Logger LOG = Logger.getInstance("#" + CachingSoftWrapDataMapper.class.getName());
  
  /** Caches information for the document visual line starts sorted in ascending order. */
  private final List<CacheEntry>               myCache                               = new ArrayList<>();
  private final List<CacheEntry>               myAffectedByUpdateCacheEntries        = new ArrayList<>();
  private final List<SoftWrapImpl>             myAffectedByUpdateSoftWraps           = new ArrayList<>();

  private final LogicalToOffsetCalculationStrategy myLogicalToOffsetStrategy;
  private final OffsetToLogicalCalculationStrategy myOffsetToLogicalStrategy;
  private final VisualToLogicalCalculationStrategy myVisualToLogicalStrategy;
  private final EditorEx                           myEditor;
  private final SoftWrapsStorage                   myStorage;
  private final CacheEntry                         mySearchKey;

  public CachingSoftWrapDataMapper(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage)
  {
    myEditor = editor;
    myStorage = storage;
    mySearchKey = new CacheEntry(0, editor);

    myLogicalToOffsetStrategy = new LogicalToOffsetCalculationStrategy(editor, storage, myCache);
    myOffsetToLogicalStrategy = new OffsetToLogicalCalculationStrategy(editor, storage, myCache);
    myVisualToLogicalStrategy = new VisualToLogicalCalculationStrategy(editor, storage, myCache);
  }

  /**
   * Maps given visual position to corresponding logical.
   *
   * @param visual    visual position to map
   * @return          logical position that corresponds to the given visual position
   * @throws IllegalStateException    if it's not possible to perform a mapping
   */
  @NotNull
  public LogicalPosition visualToLogical(@NotNull VisualPosition visual) throws IllegalStateException {
    if (myCache.isEmpty()) {
      return new LogicalPosition(visual.line, visual.column, 0, 0, 0, 0, 0);
    }

    myVisualToLogicalStrategy.init(visual, myCache);
    return calculate(myVisualToLogicalStrategy);
  }

  /**
   * Maps given offset to corresponding logical position.
   *
   * @param offset      offset to map
   * @return            logical position that corresponds to the given offset
   * @throws IllegalStateException    if it's not possible to perform a mapping
   */
  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    myOffsetToLogicalStrategy.init(offset, myCache);
    return calculate(myOffsetToLogicalStrategy);
  }

  /**
   * Maps given logical position to corresponding offset.
   *
   * @param logical                 logical position to map
   * @return                        offset that corresponds to the given logical position
   * @throws IllegalStateException  if it's not possible to perform a mapping
   */
  public int logicalPositionToOffset(@NotNull LogicalPosition logical) throws IllegalStateException {
    myLogicalToOffsetStrategy.init(logical);
    return calculate(myLogicalToOffsetStrategy);
  }

  boolean matchesOldSoftWrap(SoftWrap newSoftWrap, int lengthDiff) {
    return Collections.binarySearch(myAffectedByUpdateSoftWraps, new SoftWrapImpl(new TextChangeImpl(newSoftWrap.getText(),
                                                                                                     newSoftWrap.getStart() - lengthDiff,
                                                                                                     newSoftWrap.getEnd() - lengthDiff),
                                                                                  newSoftWrap.getIndentInColumns(),
                                                                                  newSoftWrap.getIndentInPixels()),
                                    (o1, o2) -> {
                                      int offsetDiff = o1.getStart() - o2.getStart();
                                      if (offsetDiff != 0) {
                                        return offsetDiff;
                                      }
                                      int textDiff = o1.getText().toString().compareTo(o2.getText().toString());
                                      if (textDiff != 0) {
                                        return textDiff;
                                      }
                                      int colIndentDiff = o1.getIndentInColumns() - o2.getIndentInColumns();
                                      if (colIndentDiff != 0) {
                                        return colIndentDiff;
                                      }
                                      return o1.getIndentInPixels() - o2.getIndentInPixels();
                                    }) >= 0;
  }

  public int getVisualLineStartOffset(int visualLine) {
    int index = getCacheEntryIndexForVisualLine(visualLine);
    if (index >= 0) {
      return myCache.get(index).startOffset;
    }
    else {
      int previousIndex = - index - 2;
      if (previousIndex < 0) {
        return getLogicalLineStartOffset(visualLine);
      }
      else {
        CacheEntry prevEntry = myCache.get(previousIndex);
        return getLogicalLineStartOffset(prevEntry.endLogicalLine + visualLine - prevEntry.visualLine);
      }
    }
  }

  private int getLogicalLineStartOffset(int logicalLine) {
    DocumentEx document = myEditor.getDocument();
    return logicalLine >= document.getLineCount() ? document.getTextLength() : document.getLineStartOffset(logicalLine);
  }

  public int getPreviousVisualLineStartOffset(int offset) {
    Document document = myEditor.getDocument();
    int cacheIndex = MappingUtil.getCacheEntryIndexForOffset(offset, document, myCache);
    if (cacheIndex < 0) {
      return document.getLineStartOffset(document.getLineNumber(offset));
    }
    else if (cacheIndex == 0) {
      return myCache.get(cacheIndex).startOffset;
    }
    else {
      CacheEntry thisEntry = myCache.get(cacheIndex);
      CacheEntry prevEntry = myCache.get(cacheIndex - 1);
      return  thisEntry.startOffset == prevEntry.endOffset ? prevEntry.startOffset : thisEntry.startOffset;
    }
  }

  /**
   * Maps given logical position to corresponding visual position.
   *
   * @param logical                 logical position to map
   * @param softWrapUnawareVisual   soft wrap-unaware visual position that corresponds to the given logical position
   * @return                        visual position that corresponds to the given logical position
   * @throws IllegalStateException  if it's not possible to perform a mapping
   */
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition softWrapUnawareVisual)
    throws IllegalStateException
  {
    // We can't use standard strategy-based approach with logical -> visual mapping because folding processing quite often
    // temporarily disables folding. So, there is an inconsistency between cached data (folding aware) and current folding
    // state. So, we use direct soft wraps adjustment instead of normal calculation.


    if (logical.visualPositionAware) {
      // We don't need to recalculate logical position adjustments because given object already has them.
      return logical.toVisualPosition();
    }

    List<? extends SoftWrap> softWraps = myStorage.getSoftWraps();

    // Check if there are registered soft wraps before the target logical position.
    int maxOffset = myEditor.logicalPositionToOffset(logical);
    int endIndex = myStorage.getSoftWrapIndex(maxOffset);
    if (endIndex < 0) {
      endIndex = -endIndex - 2; // We subtract '2' instead of '1' here in order to point to offset of the first soft wrap that
                                // is located before the given logical position.
    }

    // Return eagerly if no soft wraps are registered before the target offset.
    if (endIndex < 0 || endIndex >= softWraps.size()) {
      return softWrapUnawareVisual;
    }

    int lineDiff = 0;
    int column = -1;

    int targetLogicalLineStartOffset = myEditor.logicalPositionToOffset(new LogicalPosition(logical.line, 0));
    for (int i = endIndex; i >= 0; i--) {
      SoftWrap softWrap = softWraps.get(i);
      if (softWrap == null) {
        assert false;
        continue;
      }

      // Count soft wrap column offset only if it's located at the same line as the target offset.
      if (column < 0 && softWrap.getStart() >= targetLogicalLineStartOffset) {
        column = softWrap.getIndentInColumns() + SoftWrapModelImpl.getEditorTextRepresentationHelper(myEditor).toVisualColumnSymbolsNumber(
          softWrap.getStart(), maxOffset, softWrap.getIndentInPixels()
        );

        // Count lines introduced by the current soft wrap. We assume that every soft wrap has a single line feed.
        lineDiff++;
      }
      else {
        // We assume that every soft wrap has a single line feed.
        lineDiff += i + 1;
        break;
      }
    }

    int columnToUse = column >= 0 ? column : softWrapUnawareVisual.column;
    return new VisualPosition(softWrapUnawareVisual.line + lineDiff, columnToUse);
  }

  public void release() {
    myCache.clear();
  }

  private <T> T calculate(@NotNull MappingStrategy<T> strategy) throws IllegalStateException {
    T eagerMatch = strategy.eagerMatch();
    if (eagerMatch != null) {
      return eagerMatch;
    }

    EditorPosition position = strategy.buildInitialPosition();

    // Folding model doesn't return information about fold regions if their 'enabled' state is set to 'false'. Unfortunately,
    // such situation occurs not only on manual folding disabling but on internal processing as well. E.g. 'enabled' is set
    // to 'false' during fold preview representation. So, we set it to 'true' in order to retrieve fold regions and restore
    // to previous state after that.
    FoldingModelEx foldingModel = myEditor.getFoldingModel();
    boolean foldingState = foldingModel.isFoldingEnabled();
    foldingModel.setFoldingEnabled(true);
    CompositeDataProvider provider;
    try {
      provider = new CompositeDataProvider(
        new SoftWrapsDataProvider(myStorage), new FoldingDataProvider(myEditor.getFoldingModel().fetchTopLevel()),
        getTabulationDataProvider(position.visualLine)
      );
    }
    finally {
      foldingModel.setFoldingEnabled(foldingState);
    }
    provider.advance(position.offset);

    while (provider.hasData()) {
      Pair<SoftWrapDataProviderKeys, ?> data = provider.getData();
      T result = null;
      int sortingKey = provider.getSortingKey();

      // There is a possible case that, say, fold region is soft wrapped. We don't want to perform unnecessary then.
      if (position.offset <= sortingKey) {
        result = strategy.advance(position, sortingKey);
        if (result != null) {
          return result;
        }
      }

      switch (data.first) {
        case SOFT_WRAP: result = strategy.processSoftWrap(position, (SoftWrap)data.second); break;
        case COLLAPSED_FOLDING: result = strategy.processFoldRegion(position, (FoldRegion)data.second); break;
        case TABULATION: result = strategy.processTabulation(position, (TabData)data.second); break;
      }
      if (result != null) {
        return result;
      }
      provider.next();
    }
    return strategy.build(position);
  }

  private TabulationDataProvider getTabulationDataProvider(int visualLine) throws IllegalStateException {
    mySearchKey.visualLine = visualLine;
    int i = Collections.binarySearch(myCache, mySearchKey);
    List<TabData> tabs;
    if (i >= 0) {
      tabs = myCache.get(i).getTabData();
    }
    else {
      tabs = Collections.emptyList();
    }
    return new TabulationDataProvider(tabs);
  }

  @Override
  public void onVisualLineStart(@NotNull EditorPosition position) {
    if (isNewRendering()) return;
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, true);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.setLineStartPosition(position);
  }

  @Override
  public void onVisualLineEnd(@NotNull EditorPosition position) {
    if (isNewRendering()) return;
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, false);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.setLineEndPosition(position);
  }

  @Override
  public void onCollapsedFoldRegion(@NotNull FoldRegion foldRegion, int widthInColumns, int visualLine) {
    if (isNewRendering()) return;
    CacheEntry cacheEntry = getCacheEntryForVisualLine(visualLine, false);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.store(new FoldingData(foldRegion, widthInColumns), foldRegion.getStartOffset());
  }

  @Override
  public void beforeSoftWrapLineFeed(@NotNull EditorPosition position) {
    if (isNewRendering()) return;
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, false);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.setLineEndPosition(position);
  }

  @Override
  public void afterSoftWrapLineFeed(@NotNull EditorPosition position) {
    if (isNewRendering()) return; 
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, true);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.setLineStartPosition(position);
  }

  @Override
  public void revertToOffset(final int offset, int visualLine) {
    if (isNewRendering()) return;
    final CacheEntry entry = getCacheEntryForVisualLine(visualLine, false);
    if (entry != null) {
      entry.removeAllDataAtOrAfter(offset);
    }
    // Do nothing more in assumption that we store only information about start and end visual line positions and
    // that start information remains the same and end of line is not reached yet.
  }

  @Override
  public void onTabulation(@NotNull EditorPosition position, int widthInColumns) {
    if (isNewRendering()) return;
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, false);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.storeTabData(new TabData(widthInColumns, position.offset));
  }

  @Override
  public void recalculationEnds() {
  }

  /**
   * Tries to retrieve {@link CacheEntry} object that stores data for the given visual line.
   * <p/>
   * There is a possible case that no such object is registered yet and it may be created if necessary as a result of this
   * method call.
   * 
   * @param visualLine          target visual line
   * @param createIfNecessary   flag that indicates if new {@link CacheEntry} object should be created in case no information
   *                            is stored for the target visual line 
   * @return                    {@link CacheEntry} object that stores cache data for the target visual line if any;
   *                            <code>null</code> otherwise
   */
  @Nullable
  private CacheEntry getCacheEntryForVisualLine(int visualLine, boolean createIfNecessary) {
    // Blind guess, worth to perform in assumption that this method is called on document parsing most of the time.
    if (!myCache.isEmpty()) {
      CacheEntry lastEntry = myCache.get(myCache.size() - 1);
      if (lastEntry.visualLine == visualLine) {
        return lastEntry;
      }
      else if (lastEntry.visualLine < visualLine && createIfNecessary) {
        CacheEntry result = new CacheEntry(visualLine, myEditor);
        myCache.add(result);
        return result;
      }
    }
    
    int cacheEntryIndex = getCacheEntryIndexForVisualLine(visualLine);

    CacheEntry result = null;
    if (cacheEntryIndex < 0) {
      cacheEntryIndex = - cacheEntryIndex - 1;
      if (createIfNecessary) {
        myCache.add(cacheEntryIndex, result = new CacheEntry(visualLine, myEditor));
      }
    }
    else {
      result = myCache.get(cacheEntryIndex);
    }
    return result;
  }

  private int getCacheEntryIndexForVisualLine(int visualLine) {
    int start = 0;
    int end = myCache.size() - 1;
    int cacheEntryIndex = -1;

    // We inline binary search here because profiling indicates that it becomes bottleneck to use Collections.binarySearch().
    while (start <= end) {
      int i = (end + start) >>> 1;
      CacheEntry cacheEntry = myCache.get(i);
      if (cacheEntry.visualLine < visualLine) {
        start = i + 1;
        continue;
      }
      if (cacheEntry.visualLine > visualLine) {
        end = i - 1;
        continue;
      }

      cacheEntryIndex = i;
      break;
    }
    return cacheEntryIndex < 0 ? (- start - 1) : cacheEntryIndex;
  }

  @Override
  public void onCacheUpdateStart(@NotNull IncrementalCacheUpdateEvent event) {
    int startOffset = event.getStartOffset();

    if (!isNewRendering()) {
      int startIndex = MappingUtil.getCacheEntryIndexForOffset(startOffset, myEditor.getDocument(), myCache);
      if (startIndex < 0) {
        startIndex = - startIndex - 1;
      }

      myAffectedByUpdateCacheEntries.clear();
      if (startIndex < myCache.size()) {
        List<CacheEntry> affectedEntries = myCache.subList(startIndex, myCache.size());
        myAffectedByUpdateCacheEntries.addAll(affectedEntries);
        affectedEntries.clear();
      }
    }

    myAffectedByUpdateSoftWraps.clear();
    myAffectedByUpdateSoftWraps.addAll(myStorage.removeStartingFrom(startOffset + 1));
  }

  @Override
  public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event) {
    int softWrappedLinesDiff = advanceSoftWrapOffsets(event);
    applyStateChange(event, softWrappedLinesDiff);
  }

  @Override
  public void reset() {
    myCache.clear();
    myAffectedByUpdateCacheEntries.clear();
    myAffectedByUpdateSoftWraps.clear();
  }

  /**
   * Determines which soft wraps were not affected by recalculation, and shifts them to their new offsets.
   *
   * @return Change in soft wraps count after recalculation
   */
  private int advanceSoftWrapOffsets(@NotNull IncrementalCacheUpdateEvent event) {
    int lengthDiff = event.getLengthDiff();
    int recalcEndOffsetTranslated = event.getActualEndOffset() - lengthDiff;

    int firstIndex = -1;
    int softWrappedLinesDiff = myStorage.getNumberOfSoftWrapsInRange(event.getStartOffset() + 1, myEditor.getDocument().getTextLength());
    boolean softWrapsChanged = softWrappedLinesDiff > 0;
    for (int i = 0; i < myAffectedByUpdateSoftWraps.size(); i++) {
      SoftWrap softWrap = myAffectedByUpdateSoftWraps.get(i);
      if (firstIndex < 0) {
        if (softWrap.getStart() > recalcEndOffsetTranslated) {
          firstIndex = i;
          if (lengthDiff == 0) {
            break;
          }
        } else {
          softWrappedLinesDiff--;
          softWrapsChanged = true;
        }
      } 
      if (firstIndex >= 0 && i >= firstIndex) {
        ((SoftWrapImpl)softWrap).advance(lengthDiff);
      }
    }
    if (firstIndex >= 0) {
      List<SoftWrapImpl> updated = myAffectedByUpdateSoftWraps.subList(firstIndex, myAffectedByUpdateSoftWraps.size());
      SoftWrapImpl lastSoftWrap = getLastSoftWrap();
      if (lastSoftWrap != null && lastSoftWrap.getStart() >= updated.get(0).getStart()) {
        LOG.error("Invalid soft wrap recalculation", new Attachment("state.txt", myEditor.getSoftWrapModel().toString()));
      }
      myStorage.addAll(updated);
    }
    myAffectedByUpdateSoftWraps.clear();
    if (softWrapsChanged) {
      myStorage.notifyListenersAboutChange();
    }
    return softWrappedLinesDiff;
  }

  /**
   * Determines which cache entries were not affected by recalculation, and 'shifts' them according to recalculation results.
   */
  private void applyStateChange(@NotNull IncrementalCacheUpdateEvent event, int softWrappedLinesDiff) {
    if (isNewRendering()) return;
    CacheEntry lastEntry = myCache.isEmpty() ? null : myCache.get(myCache.size() - 1);
    int lengthDiff = event.getLengthDiff();
    int recalcEndOffsetTranslated = event.getActualEndOffset() - lengthDiff;

    int startIndex = MappingUtil.getCacheEntryIndexForOffset(event.getStartOffset(), myEditor.getDocument(), myCache);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }
    int foldedLinesDiff = 0;
    for (int i = startIndex; i < myCache.size(); i++) {
      CacheEntry entry = myCache.get(i);
      foldedLinesDiff += entry.endFoldedLines - entry.startFoldedLines;
    }

    int logicalLinesDiff = event.getLogicalLinesDiff();
    int firstIndex = -1;
    int borderLogicalLine = lastEntry == null ? -1 : lastEntry.endLogicalLine;
    int borderColumnDiff = 0;
    int borderFoldedColumnDiff = 0;
    int borderSoftWrapColumnDiff = 0;
    int borderSoftWrapLinesBeforeDiff = 0;
    int borderSoftWrapLinesCurrentDiff = 0;
    int affectedEntriesCount = myAffectedByUpdateCacheEntries.size();
    for (int i = 0; i < affectedEntriesCount; i++) {
      CacheEntry entry = myAffectedByUpdateCacheEntries.get(i);
      if (firstIndex < 0) {
        if (entry.startOffset < recalcEndOffsetTranslated) {
          foldedLinesDiff -= entry.endFoldedLines - entry.startFoldedLines;
          continue;
        }
        firstIndex = i;
        if (lastEntry != null && entry.startLogicalLine + logicalLinesDiff == borderLogicalLine) {
          borderColumnDiff = lastEntry.endLogicalColumn - entry.startLogicalColumn;
          borderSoftWrapLinesBeforeDiff = lastEntry.endSoftWrapLinesBefore - entry.startSoftWrapLinesBefore;
          borderSoftWrapLinesCurrentDiff = lastEntry.endSoftWrapLinesCurrent - entry.startSoftWrapLinesCurrent + 1;
          borderFoldedColumnDiff = lastEntry.endFoldingColumnDiff - entry.startFoldingColumnDiff;
          borderSoftWrapColumnDiff = -borderColumnDiff - borderFoldedColumnDiff;
        }
        if (lengthDiff == 0 && logicalLinesDiff == 0 && foldedLinesDiff == 0 && softWrappedLinesDiff == 0 
            && borderColumnDiff == 0 && borderSoftWrapColumnDiff == 0 && borderFoldedColumnDiff == 0 
            && borderSoftWrapLinesBeforeDiff == 0 && borderSoftWrapLinesCurrentDiff == 0) {
          break;
        }
      }
      if (firstIndex >= 0 && i >= firstIndex) {
        entry.visualLine += (logicalLinesDiff + softWrappedLinesDiff - foldedLinesDiff);
        entry.startLogicalLine += logicalLinesDiff;
        entry.endLogicalLine += logicalLinesDiff;
        entry.advance(lengthDiff);
        entry.startFoldedLines += foldedLinesDiff;
        entry.endFoldedLines += foldedLinesDiff;
        if (entry.startLogicalLine == borderLogicalLine) {
          entry.startLogicalColumn += borderColumnDiff;
          entry.startSoftWrapLinesBefore += borderSoftWrapLinesBeforeDiff;
          entry.startSoftWrapLinesCurrent += borderSoftWrapLinesCurrentDiff;
          entry.startSoftWrapColumnDiff += borderSoftWrapColumnDiff;
          entry.startFoldingColumnDiff += borderFoldedColumnDiff;
        }
        else {
          entry.startSoftWrapLinesBefore += softWrappedLinesDiff;
        }
        if (entry.endLogicalLine == borderLogicalLine) {
          entry.endLogicalColumn += borderColumnDiff;
          entry.endSoftWrapLinesBefore += borderSoftWrapLinesBeforeDiff;
          entry.endSoftWrapLinesCurrent += borderSoftWrapLinesCurrentDiff;
          entry.endSoftWrapColumnDiff += borderSoftWrapColumnDiff;
          entry.endFoldingColumnDiff += borderFoldedColumnDiff;
        }
        else {
          entry.endSoftWrapLinesBefore += softWrappedLinesDiff;
        }
      }
    }
    if (firstIndex >= 0) {
      if (lastEntry != null) {
        CacheEntry nextEntry = myAffectedByUpdateCacheEntries.get(firstIndex);
        if (lastEntry.visualLine >= nextEntry.visualLine) {
          LOG.error("Invalid soft wrap cache update", new Attachment("state.txt", myEditor.getSoftWrapModel().toString()));
        }
      }
      if (myAffectedByUpdateCacheEntries.get(affectedEntriesCount - 1).endOffset > myEditor.getDocument().getTextLength()) {
        LOG.error("Invalid soft wrap cache entries emerged", new Attachment("state.txt", myEditor.getSoftWrapModel().toString()));
      }
      myCache.addAll(myAffectedByUpdateCacheEntries.subList(firstIndex, myAffectedByUpdateCacheEntries.size()));
    }
    myAffectedByUpdateCacheEntries.clear();
  }
  
  @Nullable
  SoftWrapImpl getLastSoftWrap() {
    List<SoftWrapImpl> softWraps = myStorage.getSoftWraps();
    return softWraps.isEmpty() ? null : softWraps.get(softWraps.size() - 1);
  }
  
  @NotNull
  @Override
  public String dumpState() {
    return "Current cache: " + myCache.toString() + ", entries affected by current update: " + myAffectedByUpdateCacheEntries
      + ", soft wraps affected by current update: " + myAffectedByUpdateSoftWraps;
  }

  @Override
  public String toString() {
    return dumpState();
  }

  /**
   * Allows to register new entry with the given data at the soft wraps cache.
   * <p/>
   * One entry is expected to contain information about single visual lines (what logical lines are mapped to it, what fold
   * regions and tabulations are located there etc).
   * 
   * @param visualLine   target entry's visual line
   * @param startOffset  target entry's start offset
   * @param endOffset    target entry's end offset
   * @param foldRegions  target entry's fold regions
   * @param tabData      target entry's tab data
   */
  @TestOnly
  public void rawAdd(int visualLine,
                     int startOffset,
                     int endOffset,
                     int startLogicalLine,
                     int startLogicalColumn,
                     int endLogicalLine,
                     int endLogicalColumn,
                     int endVisualColumn,
                     @NotNull List<Pair<Integer, FoldRegion>> foldRegions,
                     @NotNull List<Pair<Integer, Integer>> tabData)
  {
    final CacheEntry entry = new CacheEntry(visualLine, myEditor);
    entry.startOffset = startOffset;
    entry.endOffset = endOffset;
    entry.startLogicalLine = startLogicalLine;
    assert startLogicalLine == myEditor.getDocument().getLineNumber(startOffset);
    entry.startLogicalColumn = startLogicalColumn;
    entry.endLogicalLine = endLogicalLine;
    assert endLogicalLine == myEditor.getDocument().getLineNumber(endOffset);
    entry.endLogicalColumn = endLogicalColumn;
    entry.endVisualColumn = endVisualColumn;
    for (Pair<Integer, FoldRegion> region : foldRegions) {
      final FoldingData foldData = new FoldingData(region.second, region.first);
      entry.store(foldData, region.second.getStartOffset());
    }
    for (Pair<Integer, Integer> pair : tabData) {
      entry.storeTabData(new TabData(pair.second, pair.first));
    }
    myCache.add(entry);
  }

  void removeLastCacheEntry() {
    if (isNewRendering()) return;
    LOG.assertTrue(!myCache.isEmpty());
    myCache.remove(myCache.size() - 1);
  }
  
  private boolean isNewRendering() {
    return myEditor instanceof EditorImpl && ((EditorImpl)myEditor).myUseNewRendering;
  }

  @TestOnly
  public List<CacheEntry> getCache() {
    return myCache;
  }
}