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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDataMapper;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link SoftWrapDataMapper} implementation that is implemented using the following principles:
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
public class CachingSoftWrapDataMapper implements SoftWrapDataMapper, SoftWrapAwareDocumentParsingListener, Dumpable {
  
  private static final Logger LOG = Logger.getInstance("#" + CachingSoftWrapDataMapper.class.getName());
  private static final boolean DEBUG_SOFT_WRAP_PROCESSING = false;
  
  /** Caches information for the document visual line starts sorted in ascending order. */
  private final List<CacheEntry>               myCache                               = new ArrayList<CacheEntry>();
  private final List<CacheEntry>               myAffectedByUpdateCacheEntries        = new ArrayList<CacheEntry>();
  private final List<CacheEntry>               myNotAffectedByUpdateTailCacheEntries = new ArrayList<CacheEntry>();
  private final CacheState                     myBeforeChangeState                   = new CacheState();
  private final CacheState                     myAfterChangeState                    = new CacheState();

  private final OffsetToLogicalCalculationStrategy myOffsetToLogicalStrategy;
  private final VisualToLogicalCalculationStrategy myVisualToLogicalStrategy;
  private final EditorEx                           myEditor;
  private final SoftWrapsStorage                   myStorage;
  private final EditorTextRepresentationHelper     myRepresentationHelper;
  private final CacheEntry                         mySearchKey;

  public CachingSoftWrapDataMapper(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage,
                                   @NotNull EditorTextRepresentationHelper representationHelper)
  {
    myEditor = editor;
    myStorage = storage;
    myRepresentationHelper = representationHelper;
    mySearchKey = new CacheEntry(0, editor, representationHelper);

    myOffsetToLogicalStrategy = new OffsetToLogicalCalculationStrategy(editor, storage, myCache, representationHelper);
    myVisualToLogicalStrategy = new VisualToLogicalCalculationStrategy(editor, storage, myCache, representationHelper);
  }

  @NotNull
  @Override
  public LogicalPosition visualToLogical(@NotNull VisualPosition visual) throws IllegalStateException {
    if (myCache.isEmpty()) {
      return new LogicalPosition(visual.line, visual.column, 0, 0, 0, 0, 0);
    }

    myVisualToLogicalStrategy.init(visual, myCache);
    return calculate(myVisualToLogicalStrategy);
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    myOffsetToLogicalStrategy.init(offset, myCache);
    return calculate(myOffsetToLogicalStrategy);
  }

  @Override
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
        column = softWrap.getIndentInColumns() + myRepresentationHelper.toVisualColumnSymbolsNumber(
          myEditor.getDocument().getCharsSequence(), softWrap.getStart(), maxOffset, softWrap.getIndentInPixels()
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
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, true);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.setLineStartPosition(position);
  }

  @Override
  public void onVisualLineEnd(@NotNull EditorPosition position) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, false);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.setLineEndPosition(position);
  }

  @Override
  public void onCollapsedFoldRegion(@NotNull FoldRegion foldRegion, int x, int visualLine) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(visualLine, false);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.store(new FoldingData(foldRegion, x, myRepresentationHelper, myEditor), foldRegion.getStartOffset());
  }

  @Override
  public void beforeSoftWrapLineFeed(@NotNull EditorPosition position) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, false);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.setLineEndPosition(position);
  }

  @Override
  public void afterSoftWrapLineFeed(@NotNull EditorPosition position) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(position.visualLine, true);
    if (cacheEntry == null) {
      return;
    }
    cacheEntry.setLineStartPosition(position);
    if (DEBUG_SOFT_WRAP_PROCESSING) {
      log(String.format("Update cache entry on 'after soft wrap event'. Document position: %s, cache entry: %s", position, cacheEntry));
    }
  }

  @Override
  public void revertToOffset(final int offset, int visualLine) {
    final CacheEntry entry = getCacheEntryForVisualLine(visualLine, false);
    if (entry != null) {
      entry.removeAllFoldDataAtOrAfter(offset);
    }
    // Do nothing more in assumption that we store only information about start and end visual line positions and
    // that start information remains the same and end of line is not reached yet.
  }

  @Override
  public void onTabulation(@NotNull EditorPosition position, int widthInColumns) {
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
        CacheEntry result = new CacheEntry(visualLine, myEditor, myRepresentationHelper);
        myCache.add(result);
        return result;
      }
    }

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

    CacheEntry result = null;
    if (cacheEntryIndex < 0) {
      cacheEntryIndex = start;
      if (createIfNecessary) {
        myCache.add(cacheEntryIndex, result = new CacheEntry(visualLine, myEditor, myRepresentationHelper));
      }
    }
    else {
      result = myCache.get(cacheEntryIndex);
    }
    return result;
  }

  @Override
  public void onCacheUpdateStart(@NotNull IncrementalCacheUpdateEvent event) {
    if (DEBUG_SOFT_WRAP_PROCESSING) {
      log(String.format("xxxxxxxxxxxx CachingSoftWrapDataMapper.onRecalculationStart(%s). Current cache size: %d", event, myCache.size()));
    }
    
    myAffectedByUpdateCacheEntries.clear();
    myNotAffectedByUpdateTailCacheEntries.clear();
    myBeforeChangeState.updateByDocumentOffsets(event.getOldStartOffset(), event.getOldEndOffset(), event.getOldLogicalLinesDiff());
    myStorage.removeInRange(event.getOldStartOffset(), event.getOldEndOffset());

    // Advance offsets of all soft wraps that lay beyond the changed document region.
    advanceSoftWrapOffsets(event.getExactOffsetsDiff(), event.getOldEndOffset());
    
    if (!myBeforeChangeState.cacheShouldBeUpdated) {
      if (DEBUG_SOFT_WRAP_PROCESSING) {
        log(String.format("xxxxxxxxxxxx CachingSoftWrapDataMapper.onRecalculationStart(): performing eager return"));
        dumpCache();
        log("");
      }
      return;
    }
    
    // Remember all trailing entries that lay beyond the changed region.
    int startTrailingIndex = myBeforeChangeState.endCacheEntryIndex;
    if (startTrailingIndex >= 0) {
      startTrailingIndex++;
    }
    else {
      startTrailingIndex = -startTrailingIndex - 1;
    }
    if (startTrailingIndex < myCache.size()) {
      List<CacheEntry> entries = myCache.subList(startTrailingIndex, myCache.size());
      myNotAffectedByUpdateTailCacheEntries.addAll(entries);
      entries.clear();
      if (DEBUG_SOFT_WRAP_PROCESSING) {
        log("xxxxxxxxxxxxx CachingSoftWrapDataMapper.onRecalculationStart(). Marked the following " 
            + myNotAffectedByUpdateTailCacheEntries.size() + " entries for update: ");
        for (CacheEntry cacheEntry : myNotAffectedByUpdateTailCacheEntries) {
          log("\t" + cacheEntry);
        }
      }
    }
    
    int startAffectedIndex = myBeforeChangeState.startCacheEntryIndex;
    if (startAffectedIndex < 0) {
      startAffectedIndex = -startAffectedIndex - 1;
    }
    
    if (startAffectedIndex < myCache.size()) {
      List<CacheEntry> entries = myCache.subList(startAffectedIndex, myCache.size());
      myAffectedByUpdateCacheEntries.addAll(entries);
      entries.clear();
      if (DEBUG_SOFT_WRAP_PROCESSING) {
        log("xxxxxxxxxxxxxx   Removed all affected cache entries starting from index " + startAffectedIndex + ". Remaining: " 
            + myAffectedByUpdateCacheEntries.size() + " entries affected by the change: " + myAffectedByUpdateCacheEntries);
        for (CacheEntry cacheEntry : myCache) {
          log("\t" + cacheEntry);
        }
      }
    }
  }

  @Override
  public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event, boolean normal) {
    if (DEBUG_SOFT_WRAP_PROCESSING) {
      log(String.format(
        "xxxxxxxxxxxx CachingSoftWrapDataMapper.onRecalculationEnd(%s, %b). Current cache size: %d", event, normal, myCache.size()
      ));
      if (myCache.size() < 10) {
        log("\tCurrent cache:");
        for (CacheEntry cacheEntry : myCache) {
          log("\t\t" + cacheEntry);
        }
      }
    }

    int exactOffsetsDiff = event.getExactOffsetsDiff();
    if (normal) {
      myAfterChangeState.updateByDocumentOffsets(event.getNewStartOffset(), event.getNewEndOffset(), event.getNewLogicalLinesDiff());
      myCache.addAll(myNotAffectedByUpdateTailCacheEntries);
    }
    else {
      myAfterChangeState.logicalLines = event.getNewLogicalLinesDiff();
      myAfterChangeState.visualLines = event.getNewLogicalLinesDiff();
      myAfterChangeState.softWrapLines = 0;
      myAfterChangeState.foldedLines = 0;
      myCache.addAll(myNotAffectedByUpdateTailCacheEntries);
    }
    applyStateChange(exactOffsetsDiff);

    // TODO den remove before v.12 release
    if (myCache.size() > 1) {
      CacheEntry beforeLast = myCache.get(myCache.size() - 2);
      CacheEntry last = myCache.get(myCache.size() - 1);
      if (beforeLast.visualLine == last.visualLine
          || (beforeLast.visualLine + 1 == last.visualLine && last.startOffset - beforeLast.endOffset > 1)
          || last.startOffset > myEditor.getDocument().getTextLength())
      {
        CharSequence editorState = "";
        if (myEditor instanceof EditorImpl) {
          editorState = ((EditorImpl)myEditor).dumpState();
        }
        LOG.error(
          "Detected invalid soft wraps cache update",
          String.format(
            "Event: %s, normal: %b.%n%nTail cache entries: %s%n%nAffected by change cache entries: %s%n%nBefore change state: %s%n%n"
            + "After change state: %s%n%nEditor state: %s",
            event, normal, myNotAffectedByUpdateTailCacheEntries, myAffectedByUpdateCacheEntries,
            myBeforeChangeState, myAfterChangeState, editorState
          )
        );
      }
    }
    
    myAffectedByUpdateCacheEntries.clear();
    myNotAffectedByUpdateTailCacheEntries.clear();

    if (DEBUG_SOFT_WRAP_PROCESSING) {
      log("After Applying state change");
      dumpCache();
    }

    myBeforeChangeState.cacheShouldBeUpdated = false;
  }

  @Override
  public void reset() {
    myCache.clear();
    myAffectedByUpdateCacheEntries.clear();
    myNotAffectedByUpdateTailCacheEntries.clear();
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "UnusedDeclaration", "CallToPrintStackTrace"})
  private void dumpCache() {
    Document document = myEditor.getDocument();
    CharSequence text = document.getCharsSequence();
    log("--------------------------------------------------");
    log("|");
    log("| xxxxxxxxx START DUMP. Document:");
    log(text);
    log("-  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -");
    log("xxxxxxxxxx text length: " + text.length() + ", soft wraps: " + myStorage.getSoftWraps().size());
    for (int i = 0; i < myCache.size(); i++) {
      CacheEntry entry = myCache.get(i);
      if (text.length() <= 0 && i <= 0) {
        continue;
      }
      if (entry.endOffset < entry.startOffset) {
        assert false;
      }
      if (i > 0 && myCache.get(i - 1).endOffset > entry.startOffset) {
        assert false;
      }
      try {
        log(String.format("line %d. %d-%d: '%s'", entry.visualLine, entry.startOffset, entry.endOffset,
                          text.subSequence(Math.min(text.length() - 1, entry.startOffset) ,Math.min(entry.endOffset, text.length() -  1))));
      }
      catch (Throwable e) {
        e.printStackTrace();
      }
    }

    for (CacheEntry cacheEntry : myCache) {
      if (cacheEntry.startOffset >= document.getTextLength()) {
        continue;
      }
      if (cacheEntry.startOffset > 0) {
        if (text.charAt(cacheEntry.startOffset - 1) != '\n' && myStorage.getSoftWrap(cacheEntry.startOffset) == null) {
          assert false;
        }
      }
    }

    log("\nxxxxxxxxxxxxxxxx Soft wraps: " + myStorage.getSoftWraps());
    
    log("xxxxxxxxxx dump complete. Cache size: " + myCache.size() + "\n");
  }

  /**
   * Applies given offsets diff to all soft wraps that lay after the given offset
   * 
   * @param offsetsDiff   offset diff to apply to the target soft wraps
   * @param offset        offset to use for filtering soft wraps to advance. All soft wraps which offsets are strictly greater
   *                      than the given one should be advanced
   */
  private void advanceSoftWrapOffsets(int offsetsDiff, int offset) {
    if (offsetsDiff == 0) {
      return;
    }

    int softWrapIndex = myStorage.getSoftWrapIndex(offset);
    if (softWrapIndex >= 0) {
      softWrapIndex++; // We want to process only soft wraps which offsets are strictly more than the given one.
    }
    else {
      softWrapIndex = -softWrapIndex - 1;
    }

    List<SoftWrapImpl> softWraps = myStorage.getSoftWraps();
    for (int i = softWrapIndex; i < softWraps.size(); i++) {
      softWraps.get(i).advance(offsetsDiff);
    }
  }

  /**
   * Is assumed to be called for updating {@link #myCache document dimensions cache} entries that lay after document position identified
   * by {@link #myAfterChangeState} in order to apply to them diff between {@link #myBeforeChangeState} and {@link #myAfterChangeState}.
   * <p/>
   * I.e. the general idea of incremental cache update is the following:
   * <p/>
   * <pre>
   * <ol>
   *   <li>We are notified on document region update;</li>
   *   <li>We remember significant information about target region;</li>
   *   <li>Region data recalculation is performed;</li>
   *   <li>Diff between current region state and remembered one is applied to the cache;</li>
   * </ol>
   * </pre>
   * 
   * @param offsetsDiff  offset shift to apply to the tail entries
   */
  private void applyStateChange(int offsetsDiff) {
    if (myNotAffectedByUpdateTailCacheEntries.isEmpty()) {
      return;
    }

    int visualLinesDiff = myAfterChangeState.visualLines - myBeforeChangeState.visualLines;
    int logicalLinesDiff = myAfterChangeState.logicalLines - myBeforeChangeState.logicalLines;
    int softWrappedLinesDiff = myAfterChangeState.softWrapLines - myBeforeChangeState.softWrapLines;
    int foldedLinesDiff = myAfterChangeState.foldedLines - myBeforeChangeState.foldedLines;
    if (DEBUG_SOFT_WRAP_PROCESSING) {
      log(String.format("Modifying trailing cache entries:" +
                        "%n\tvisual lines: before=%d, current=%d, diff=%d" +
                        "%n\tlogical lines: before=%d, current=%d, diff=%d" +
                        "%n\tsoft wrap lines: before=%d, current=%d, diff=%d" +
                        "%n\tfold lines: before=%d, current=%d, diff=%d" +
                        "%n\toffsets: diff=%d",
                        myBeforeChangeState.visualLines, myAfterChangeState.visualLines, visualLinesDiff,
                        myBeforeChangeState.logicalLines, myAfterChangeState.logicalLines, logicalLinesDiff,
                        myBeforeChangeState.softWrapLines, myAfterChangeState.softWrapLines, softWrappedLinesDiff,
                        myBeforeChangeState.foldedLines, myAfterChangeState.foldedLines, foldedLinesDiff,
                        offsetsDiff));
    }

    // 'For-each' loop is not used here because this code is called quite often and profile shows the Iterator usage here
    // produces performance drawback.
    for (CacheEntry cacheEntry : myNotAffectedByUpdateTailCacheEntries) {
      cacheEntry.visualLine += visualLinesDiff;
      cacheEntry.startLogicalLine += logicalLinesDiff;
      cacheEntry.endLogicalLine += logicalLinesDiff;
      cacheEntry.advance(offsetsDiff);
      cacheEntry.startSoftWrapLinesBefore += softWrappedLinesDiff;
      cacheEntry.endSoftWrapLinesBefore += softWrappedLinesDiff;
      cacheEntry.startFoldedLines += foldedLinesDiff;
      cacheEntry.endFoldedLines += foldedLinesDiff;
    } 
  }

  @NotNull
  @Override
  public String dumpState() {
    return myCache.toString();
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
  public void rawAdd(int visualLine,
                     int startOffset,
                     int endOffset,
                     int startLogicalLine,
                     int startLogicalColumn,
                     int endLogicalLine,
                     int endLogicalColumn,
                     int endVisualColumn,
                     @NotNull List<Trinity<Integer, Integer, FoldRegion>> foldRegions,
                     @NotNull List<Pair<Integer, Integer>> tabData)
  {
    final CacheEntry entry = new CacheEntry(visualLine, myEditor, myRepresentationHelper);
    entry.startOffset = startOffset;
    entry.endOffset = endOffset;
    entry.startLogicalLine = startLogicalLine;
    assert startLogicalLine == myEditor.getDocument().getLineNumber(startOffset);
    entry.startLogicalColumn = startLogicalColumn;
    entry.endLogicalLine = endLogicalLine;
    assert endLogicalLine == myEditor.getDocument().getLineNumber(endOffset);
    entry.endLogicalColumn = endLogicalColumn;
    entry.endVisualColumn = endVisualColumn;
    for (Trinity<Integer, Integer, FoldRegion> region : foldRegions) {
      final FoldingData foldData = new FoldingData(region.third, region.second, myRepresentationHelper, myEditor);
      foldData.widthInColumns = region.first;
      entry.store(foldData, region.third.getStartOffset());
    }
    for (Pair<Integer, Integer> pair : tabData) {
      entry.storeTabData(new TabData(pair.second, pair.first));
    }
    myCache.add(entry);
  }
  
  @SuppressWarnings({"UnusedDeclaration"})
  public static void log(Object o) {
    //try {
    //  doLog(o);
    //}
    //catch (Exception e) {
    //  e.printStackTrace();
    //}
  }

  //private static BufferedWriter myWriter;
  //private static void doLog(Object o) throws Exception {
  //  if (myWriter == null) {
  //    myWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/home/denis/log/softwrap.log")));
  //  }
  //  myWriter.write(o.toString());
  //  myWriter.newLine();
  //  myWriter.flush();
  //}
  
  private class CacheState {

    public boolean cacheShouldBeUpdated = true;

    public int visualLines;
    public int logicalLines;
    public int softWrapLines;
    public int foldedLines;
    public int startCacheEntryIndex;
    public int endCacheEntryIndex;

    public void updateByDocumentOffsets(int startOffset, int endOffset, int logicalLinesDiff) {
      reset();
      Document document = myEditor.getDocument();
      
      startCacheEntryIndex = MappingUtil.getCacheEntryIndexForOffset(startOffset, document, myCache);
      endCacheEntryIndex = MappingUtil.getCacheEntryIndexForOffset(endOffset, document, myCache);
      
      logicalLines = logicalLinesDiff;
      visualLines = logicalLinesDiff;
      softWrapLines = myStorage.getNumberOfSoftWrapsInRange(startOffset, endOffset);
      visualLines += softWrapLines;
      
      if (startCacheEntryIndex < 0 && -startCacheEntryIndex - 1 >= myCache.size()) {
        cacheShouldBeUpdated = false;
        return;
      }
      
      // We assume here that the cache contains entries for all visual lines that contain collapsed fold regions.
      foldedLines = 0;
      int startIndex = startCacheEntryIndex;
      if (startIndex < 0) {
        startIndex = -startIndex - 1;
        
        // There is a possible case that the nearest cache entry which start offset is not less than the given start offset layes
        // after the changed region. We just stop processing then.
        if (startIndex >= myCache.size() || myCache.get(startIndex).startOffset > endOffset) {
          return;
        }
      }
      int endIndex = endCacheEntryIndex;
      if (endIndex < 0) {
        endIndex = -endIndex - 2; // Minus 2 because we use non-strict comparison below
        endIndex = Math.max(0, Math.min(endIndex, myCache.size() - 1));
      }
      for (int i = startIndex; i <= endIndex; i++) {
        CacheEntry cacheEntry = myCache.get(i);
        foldedLines += cacheEntry.endFoldedLines - cacheEntry.startFoldedLines;
      }
      visualLines -= foldedLines;

      if (DEBUG_SOFT_WRAP_PROCESSING) {
        log(String.format("CachingSoftWrapDataMapper$CacheState.updateByDocumentOffsets(). Collected %d fold lines for cache entry indices "
                          + "%d-%d (cache size is %d)", foldedLines, startCacheEntryIndex, endCacheEntryIndex, myCache.size()));
      }
    }

    public void reset() {
      logicalLines = 0;
      visualLines = 0;
      softWrapLines = 0;
      foldedLines = 0;
      endCacheEntryIndex = 0;
      cacheShouldBeUpdated = true;
    }

    @Override
    public String toString() {
      return String.format(
        "visual lines: %d, logical lines: %d, soft wrap lines: %d, fold lines: %d, cache entry indices: [%d;%d]",
        visualLines, logicalLines, softWrapLines, foldedLines, startCacheEntryIndex, endCacheEntryIndex
      );
    }
  }
}