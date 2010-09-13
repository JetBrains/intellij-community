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
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;

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

  /** Caches information for the document visual line starts sorted in ascending order. */
  private final List<CacheEntry> myCache                               = new ArrayList<CacheEntry>();
  private final CacheEntry       mySearchKey                           = new CacheEntry(0, null, null, null);
  private final List<CacheEntry> myNotAffectedByUpdateTailCacheEntries = new ArrayList<CacheEntry>();
  private final CacheState       myBeforeChangeState                   = new CacheState();
  private final CacheState       myAfterChangeState                    = new CacheState();

  private final EditorEx myEditor;
  private final SoftWrapsStorage myStorage;
  private final EditorTextRepresentationHelper myRepresentationHelper;

  public CachingSoftWrapDataMapper(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage,
                                   @NotNull EditorTextRepresentationHelper representationHelper)
  {
    myEditor = editor;
    myStorage = storage;
    myRepresentationHelper = representationHelper;
  }

  @NotNull
  @Override
  public LogicalPosition visualToLogical(@NotNull VisualPosition visual) throws IllegalStateException {
    if (myCache.isEmpty()) {
      return new LogicalPosition(visual.line, visual.column, 0, 0, 0, 0, 0);
    }

    // There is a possible case that we're asked to map visual position that lay beyond the document content (e.g. there is a
    // 'virtual space' below the document text and it's perfectly possible to map visual position that points into it).
    // We consider that there are no soft wraps, tabulations and fold regions between the last cached visual line and target line then.
    if (!myCache.isEmpty()) {
      CacheEntry cacheEntry = myCache.get(myCache.size() - 1);
      if (visual.line > cacheEntry.visualLine) {
        int lineDiff = visual.line - cacheEntry.visualLine;
        return new LogicalPosition(
          cacheEntry.endLogicalLine + lineDiff, visual.column, cacheEntry.endSoftWrapLinesBefore + cacheEntry.endSoftWrapLinesCurrent,
          0, 0, cacheEntry.endFoldedLines, 0
        );
      }
    }

    return calculate(new VisualToLogicalCalculationStrategy(visual, myCache, myEditor, myStorage, myRepresentationHelper));
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    return calculate(new OffsetToLogicalCalculationStrategy(offset, myEditor, myRepresentationHelper, myCache, myStorage));
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

    ProcessingContext context = strategy.buildInitialContext();

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
        getTabulationDataProvider(context.visualLine)
      );
    }
    finally {
      foldingModel.setFoldingEnabled(foldingState);
    }
    provider.advance(context.offset);

    while (provider.hasData()) {
      Pair<SoftWrapDataProviderKeys, ?> data = provider.getData();
      T result = null;
      int sortingKey = provider.getSortingKey();

      // There is a possible case that, say, fold region is soft wrapped. We don't want to perform unnecessary then.
      if (context.offset < sortingKey) {
        result = strategy.advance(context, sortingKey);
        if (result != null) {
          return result;
        }
      }

      switch (data.first) {
        case SOFT_WRAP: result = strategy.processSoftWrap(context, (SoftWrap)data.second); break;
        case COLLAPSED_FOLDING: result = strategy.processFoldRegion(context, (FoldRegion)data.second); break;
        case TABULATION: result = strategy.processTabulation(context, (TabData)data.second); break;
      }
      if (result != null) {
        return result;
      }
      provider.next();
    }
    return strategy.build(context);
  }

  private TabulationDataProvider getTabulationDataProvider(int visualLine) throws IllegalStateException {
    mySearchKey.visualLine = visualLine;
    int i = Collections.binarySearch(myCache, mySearchKey);
    List<TabData> tabs;
    if (i >= 0) {
      tabs = myCache.get(i).getTabData();
    }
    else {
      assert false;
      tabs = Collections.emptyList();
    }
    return new TabulationDataProvider(tabs);
  }

  @Override
  public void onProcessedSymbol(@NotNull ProcessingContext context) {
    switch (context.symbol) {
      case '\n': onLineFeed(context); break;
      case '\t': onTabulation(context); break;
      default: onNonLineFeedSymbol(context); break;
    }
  }

  @Override
  public void onCollapsedFoldRegion(@NotNull FoldRegion foldRegion, int x, int visualLine) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(visualLine);
    cacheEntry.store(foldRegion, x);
  }

  @Override
  public void beforeSoftWrap(@NotNull ProcessingContext context) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(context.visualLine);
    cacheEntry.setLineEndPosition(context);
  }

  @Override
  public void afterSoftWrapLineFeed(@NotNull ProcessingContext context) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(context.visualLine);
    cacheEntry.setLineStartPosition(context, context.visualLine);
  }

  @Override
  public void revertToOffset(final int offset, int visualLine) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(visualLine);

    // Remove information about cached tabulation symbol data.
    int tabIndex = 0;
    for (; tabIndex < cacheEntry.getTabData().size(); tabIndex++) {
      if (cacheEntry.getTabData().get(tabIndex).offset >= offset) {
        break;
      }
    }
    if (tabIndex < cacheEntry.getTabData().size() - 1) {
      cacheEntry.getTabData().subList(tabIndex, cacheEntry.getTabData().size()).clear();
    }

    // Remove information about cached single line fold regions.
    cacheEntry.getFoldingData().retainEntries(new TIntObjectProcedure<FoldingData>() {
      @Override
      public boolean execute(int foldStartOffset, FoldingData data) {
        return foldStartOffset < offset;
      }
    });
  }

  /**
   * Updates inner cache for the context that points to line feed symbol
   *
   * @param context    processing context that points to line feed symbol
   */
  private void onLineFeed(@NotNull ProcessingContext context) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(context.visualLine);
    cacheEntry.setLineEndPosition(context);

    cacheEntry = getCacheEntryForVisualLine(context.visualLine + 1);

    ProcessingContext newLineContext = context.clone();
    newLineContext.onNewLine();
    newLineContext.offset++;
    cacheEntry.setLineStartPosition(newLineContext, context.visualLine + 1);
    cacheEntry.setLineEndPosition(newLineContext);
  }

  /**
   * Updates inner cache for the context that points to tabulation symbol.
   *
   * @param context    processing context that points to tabulation symbol
   */
  private void onTabulation(@NotNull ProcessingContext context) {
    onNonLineFeedSymbol(context);
    CacheEntry cacheEntry = getCacheEntryForVisualLine(context.visualLine);
    cacheEntry.storeTabData(context);
  }

  /**
   * Process given context in assumption that it points to non-line feed symbol.
   *
   * @param context   context that is assumed to point to non-line feed symbol
   */
  private void onNonLineFeedSymbol(@NotNull ProcessingContext context) {
    CacheEntry cacheEntry = getCacheEntryForVisualLine(context.visualLine);
    Document document = myEditor.getDocument();
    if (context.offset >= document.getTextLength() - 1) {
      cacheEntry.setLineEndPosition(context);
    }

    if (context.offset < 0 || (context.offset > 0 && context.offset < document.getTextLength()
                               && document.getCharsSequence().charAt(context.offset - 1) != '\n'))
    {
      return;
    }

    // Process first symbol on a new line.
    cacheEntry.getTabData().clear();
    cacheEntry.getFoldingData().clear();
    cacheEntry.setLineStartPosition(context.clone(), context.visualLine);
  }

  private CacheEntry getCacheEntryForVisualLine(int visualLine) {
    mySearchKey.visualLine = visualLine;
    int i = Collections.binarySearch(myCache, mySearchKey);
    CacheEntry result;
    if (i < 0) {
      i = -i - 1;
      myCache.add(i, result = new CacheEntry(visualLine, myEditor, myRepresentationHelper, myCache));
    }
    else if (myBeforeChangeState.valid && i > myBeforeChangeState.endCacheEntryIndex && myCache.get(i).locked) {
      myCache.set(i, result = myCache.get(i).clone());
    }
    else {
      result = myCache.get(i);
    }
    return result;
  }

  @Override
  public void onRecalculationStart(int startOffset, int endOffset) {
    myBeforeChangeState.updateByDocumentOffsets(startOffset, endOffset);
    if (!myBeforeChangeState.valid) {
      return;
    }
    myNotAffectedByUpdateTailCacheEntries.clear();
    myNotAffectedByUpdateTailCacheEntries.addAll(myCache.subList(myBeforeChangeState.endCacheEntryIndex + 1, myCache.size()));
    myCache.subList(myBeforeChangeState.startCacheEntryIndex + 1, myCache.size()).clear();
    for (CacheEntry entry : myNotAffectedByUpdateTailCacheEntries) {
      entry.locked = true;
    }
  }

  @Override
  public void onRecalculationEnd(int startOffset, int endOffset) {
    if (!myBeforeChangeState.valid) {
      return;
    }
    int endIndex = myCache.size() - 2; // -1 because of zero-based indexing; one more -1 in assumption that re-parsing always adds
                                       // number of target cache entries plus one (because of line feed at the end).
    myAfterChangeState.updateByCacheIndices(myBeforeChangeState.startCacheEntryIndex, endIndex);
    myCache.subList(myAfterChangeState.endCacheEntryIndex + 1, myCache.size()).clear();
    myCache.addAll(myNotAffectedByUpdateTailCacheEntries);
    for (CacheEntry entry : myNotAffectedByUpdateTailCacheEntries) {
      entry.locked = false;
    }
    applyStateChange();


    //Document document = myEditor.getDocument();
    //CharSequence text = document.getCharsSequence();
    //System.out.println("--------------------------------------------------");
    //System.out.println("|");
    //System.out.println("|");
    //System.out.println(text);
    //System.out.println("-  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -");
    //System.out.println("text length: " + text.length() + ", soft wraps: " + myStorage.getSoftWraps());
    //for (int i = 0; i < myCache.size(); i++) {
    //  CacheEntry entry = myCache.get(i);
    //  // TODO den unwrap
    //  try {
    //    System.out.printf("line %d. %d-%d: '%s'%n", i, entry.startOffset, entry.endOffset,
    //                      text.subSequence(entry.startOffset,Math.min(entry.endOffset, text.length())));
    //  }
    //  catch (Throwable e) {
    //    e.printStackTrace();
    //  }
    //}
    //if (!myCache.isEmpty() && myCache.get(myCache.size() - 1).endOffset < text.length() - 1) {
    //  System.out.printf("Incomplete re-parsing detected! Document length is %d but last processed offset is %s%n", text.length(),
    //                    myCache.get(myCache.size() - 1).endOffset);
    //}


    //for (CacheEntry cacheEntry : myCache) {
    //  if (cacheEntry.startOffset > 0) {
    //    if (text.charAt(cacheEntry.startOffset - 1) != '\n' && myStorage.getSoftWrap(cacheEntry.startOffset) == null) {
    //      assert false;
    //    }
    //  }
    //  if (cacheEntry.endOffset < document.getTextLength()) {
    //    if (text.charAt(cacheEntry.endOffset) != '\n' && myStorage.getSoftWrap(cacheEntry.endOffset) == null) {
    //      assert false;
    //    }
    //  }
    //}
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
  private void applyStateChange() {
    int visualLinesDiff = myAfterChangeState.visualLines - myBeforeChangeState.visualLines;
    int logicalLinesDiff = myAfterChangeState.logicalLines - myBeforeChangeState.logicalLines;
    int softWrappedLinesDiff = myAfterChangeState.softWrapLines - myBeforeChangeState.softWrapLines;
    int foldedLinesDiff = myAfterChangeState.foldedLines - myBeforeChangeState.foldedLines;
    int offsetsDiff = (myAfterChangeState.endOffset - myAfterChangeState.startOffset)
                      - (myBeforeChangeState.endOffset - myBeforeChangeState.startOffset);

    if (myNotAffectedByUpdateTailCacheEntries.isEmpty()) {
      return;
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

    int offset = myNotAffectedByUpdateTailCacheEntries.get(0).startOffset + 1/* in order to exclude soft wrap from previous line if any*/;
    if (offsetsDiff == 0 || offset >= myEditor.getDocument().getTextLength()) {
      return;
    }

    int softWrapIndex = myStorage.getSoftWrapIndex(offset);
    if (softWrapIndex < 0) {
      softWrapIndex = -softWrapIndex - 1;
    }

    List<SoftWrapImpl> softWraps = myStorage.getSoftWraps();
    for (int i = softWrapIndex; i < softWraps.size(); i++) {
      softWraps.get(i).advance(offsetsDiff);
    }
  }

  private class CacheState {

    public boolean valid = true;

    public int visualLines;
    public int logicalLines;
    public int softWrapLines;
    public int foldedLines;
    public int startOffset;
    public int endOffset;
    public int startCacheEntryIndex;
    public int endCacheEntryIndex;

    public void updateByDocumentOffsets(int startOffset, int endOffset) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      startCacheEntryIndex = MappingUtil.getCacheEntryIndexForOffset(startOffset, myEditor.getDocument(), myCache);
      endCacheEntryIndex = MappingUtil.getCacheEntryIndexForOffset(endOffset, myEditor.getDocument(), myCache);

      if (startCacheEntryIndex < 0 || endCacheEntryIndex < 0) {
        valid = false;
        // This is correct situation for, say, initial cache update.
        return;
      }
      updateByCacheIndices(startCacheEntryIndex, endCacheEntryIndex);

    }

    public void updateByCacheIndices(int startIndex, int endIndex) {
      reset();
      startOffset = myCache.get(startIndex).startOffset;
      endOffset = myCache.get(endIndex).endOffset;
      startCacheEntryIndex = startIndex;
      endCacheEntryIndex = endIndex;
      visualLines = endIndex - startIndex + 1;

      CacheEntry startEntry = myCache.get(startIndex);
      CacheEntry endEntry = myCache.get(endIndex);
      logicalLines = endEntry.endLogicalLine - startEntry.startLogicalLine + 1;
      foldedLines = endEntry.endFoldedLines - startEntry.startFoldedLines;
      softWrapLines = endEntry.endSoftWrapLinesBefore + endEntry.endSoftWrapLinesCurrent - startEntry.startSoftWrapLinesBefore
                      - startEntry.startSoftWrapLinesCurrent;
    }

    public void reset() {
      logicalLines = 0;
      softWrapLines = 0;
      foldedLines = 0;
      endCacheEntryIndex = 0;
      valid = true;
    }
  }
}