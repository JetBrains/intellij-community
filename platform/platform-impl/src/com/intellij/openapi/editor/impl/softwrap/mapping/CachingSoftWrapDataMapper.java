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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
public class CachingSoftWrapDataMapper implements SoftWrapDataMapper, SoftWrapAwareDocumentParsingListener {

  private static final boolean DEBUG_SOFT_WRAP_PROCESSING = false;
  
  /** Caches information for the document visual line starts sorted in ascending order. */
  private final List<CacheEntry>               myCache                               = new ArrayList<CacheEntry>();
  private final DelayedRemovalMap<FoldingData> myFoldData                            = new DelayedRemovalMap<FoldingData>();
  private final CacheEntry                     mySearchKey                           = new CacheEntry(0, null, null, null);
  private final List<CacheEntry>               myNotAffectedByUpdateTailCacheEntries = new ArrayList<CacheEntry>();
  private final CacheState                     myBeforeChangeState                   = new CacheState();
  private final CacheState                     myAfterChangeState                    = new CacheState();

  private final OffsetToLogicalCalculationStrategy myOffsetToLogicalStrategy;
  private final VisualToLogicalCalculationStrategy myVisualToLogicalStrategy;

  private final EditorEx myEditor;
  private final SoftWrapsStorage myStorage;
  private final EditorTextRepresentationHelper myRepresentationHelper;

  public CachingSoftWrapDataMapper(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage,
                                   @NotNull EditorTextRepresentationHelper representationHelper)
  {
    myEditor = editor;
    myStorage = storage;
    myRepresentationHelper = representationHelper;

    myOffsetToLogicalStrategy = new OffsetToLogicalCalculationStrategy(editor, storage, myCache, myFoldData, representationHelper);
    myVisualToLogicalStrategy = new VisualToLogicalCalculationStrategy(editor, storage, myCache, myFoldData, representationHelper);
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
    //TODO den remove
    // There is a possible case that this method is called during the first refresh (the cache is empty). So, we delegate it to
    // soft wraps-unaware code.
    //if (myCache.isEmpty()) {
    //  return myEditor.offsetToLogicalPosition(offset, false);
    //}
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
    myFoldData.put(foldRegion.getStartOffset(), new FoldingData(foldRegion, x, myRepresentationHelper, myEditor));
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
    // Do nothing in assumption that we store only information about start and end visual line positions and
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
        CacheEntry result = new CacheEntry(visualLine, myEditor, myRepresentationHelper, myCache);
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
        myCache.add(cacheEntryIndex, result = new CacheEntry(visualLine, myEditor, myRepresentationHelper, myCache));
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
    
    myNotAffectedByUpdateTailCacheEntries.clear();
    myFoldData.markForDeletion(event.getOldStartOffset(), event.getOldEndOffset());
    myBeforeChangeState.updateByDocumentOffsets(event.getOldStartOffset(), event.getOldEndOffset(), event.getOldLogicalLinesDiff());
    if (!myBeforeChangeState.cacheShouldBeUpdated) {
      if (DEBUG_SOFT_WRAP_PROCESSING) {
        log(String.format("xxxxxxxxxxxx CachingSoftWrapDataMapper.onRecalculationStart(): performing eager return"));
        dumpCache();
        log("");
      }
      return;
    }
    
    if (myBeforeChangeState.endCacheEntryIndex < myCache.size() - 1) {
      int cacheIndexToUse = myBeforeChangeState.endCacheEntryIndex;
      // There is a possible case that end cache entry index points to the entry that lays withing the changed document range,
      // hence, we need not to store it then.
      if (cacheIndexToUse < myCache.size() && myCache.get(cacheIndexToUse).startOffset < event.getNewEndOffset()) {
        cacheIndexToUse++;
      }
      
      myNotAffectedByUpdateTailCacheEntries.addAll(myCache.subList(cacheIndexToUse, myCache.size()));
      if (DEBUG_SOFT_WRAP_PROCESSING) {
        log("xxxxxxxxxxxxx CachingSoftWrapDataMapper.onRecalculationStart(). Marked the following " + myNotAffectedByUpdateTailCacheEntries.size()
            + " entries for update: ");
        for (CacheEntry cacheEntry : myNotAffectedByUpdateTailCacheEntries) {
          log("\t" + cacheEntry);
        }
      }
    }
    
    if (myBeforeChangeState.startCacheEntryIndex < myCache.size()) {
      myCache.subList(myBeforeChangeState.startCacheEntryIndex, myCache.size()).clear();
      if (DEBUG_SOFT_WRAP_PROCESSING) {
        log("Removed all cache entries starting from index " + myBeforeChangeState.startCacheEntryIndex + ". Remaining: " + myCache.size());
        for (CacheEntry cacheEntry : myCache) {
          log("\t" + cacheEntry);
        }
      }
    }
  }

  @Override
  public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event) {
    if (DEBUG_SOFT_WRAP_PROCESSING) {
      log(String.format("xxxxxxxxxxxx CachingSoftWrapDataMapper.onRecalculationEnd(%s). Current cache size: %d", event, myCache.size()));
      if (myCache.size() < 10) {
        log("\tCurrent cache:");
        for (CacheEntry cacheEntry : myCache) {
          log("\t\t" + cacheEntry);
        }
      }
    }
    myFoldData.deleteMarked();
    myAfterChangeState.updateByDocumentOffsets(event.getNewStartOffset(), event.getNewEndOffset(), event.getNewLogicalLinesDiff());
    myCache.addAll(myNotAffectedByUpdateTailCacheEntries);

    applyStateChange(event.getExactOffsetsDiff(), event.getNewEndOffset());
    myNotAffectedByUpdateTailCacheEntries.clear();

    if (DEBUG_SOFT_WRAP_PROCESSING) {
      log("After Applying state change");
      dumpCache();
    }

    myBeforeChangeState.cacheShouldBeUpdated = false;
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
        log(String.format("line %d. %d-%d: '%s'", i, entry.startOffset, entry.endOffset,
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
   </pre>
   */
  private void applyStateChange(int offsetsDiff, int endOffset) {
    // Update offsets for soft wraps that lay beyond the changed region.
    if (offsetsDiff != 0 && endOffset < myEditor.getDocument().getTextLength()) {
      int softWrapIndex = myStorage.getSoftWrapIndex(endOffset);
      if (softWrapIndex < 0) {
        softWrapIndex = -softWrapIndex - 1;
      }

      List<SoftWrapImpl> softWraps = myStorage.getSoftWraps();
      for (int i = softWrapIndex; i < softWraps.size(); i++) {
        softWraps.get(i).advance(offsetsDiff);
      }
    }
    
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

  @Override
  public String toString() {
    return myCache.toString();
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
      if (startCacheEntryIndex < 0) {
        startCacheEntryIndex = -startCacheEntryIndex - 1;
      }
      if (endCacheEntryIndex < 0) {
        endCacheEntryIndex = -endCacheEntryIndex - 1;
      }
      
      logicalLines = logicalLinesDiff;
      visualLines = logicalLinesDiff;
      softWrapLines = myStorage.getNumberOfSoftWrapsInRange(startOffset, endOffset);
      visualLines += softWrapLines;
      
      if (startCacheEntryIndex >= myCache.size()) {
        cacheShouldBeUpdated = false;
        return;
      }
      
      // We assume here that the cache contains entries for all visual lines that contain collapsed fold regions.
      foldedLines = 0;
      for (int i = startCacheEntryIndex, max = Math.max(0, Math.min(myCache.size() - 1, endCacheEntryIndex)); i <= max; i++) {
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
  }
}