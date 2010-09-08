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
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
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
  private final List<CacheEntry> myCache = new ArrayList<CacheEntry>();
  private final CacheEntry mySearchKey = new CacheEntry(0);

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

    return calculate(new VisualToLogicalCalculationStrategy(visual));
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    return calculate(new OffsetToLogicalCalculationStrategy(offset));
  }

  @Override
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition softWrapUnawareVisual)
    throws IllegalStateException
  {
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
  public void onFoldRegion(@NotNull FoldRegion foldRegion, int x, int visualLine) {
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
      myCache.add(i, result = new CacheEntry(visualLine));
    }
    else {
      result = myCache.get(i);
    }
    return result;
  }

  @Override
  public void onRangeRecalculationStart(@NotNull TextRange range) {
  }

  @Override
  public void onRangeRecalculationEnd(@NotNull TextRange range) {
  }

  /**
   * Caches information about number of logical columns inside the collapsed single line folding.
   */
  private class FoldingData {

    private final FoldRegion myFoldRegion;
    private int myWidthInColumns = -1;
    private int myStartX;

    FoldingData(FoldRegion foldRegion, int startX) {
      myFoldRegion = foldRegion;
      myStartX = startX;
    }

    public int getCollapsedSymbolsWidthInColumns() {
      if (myWidthInColumns < 0) {
        Document document = myEditor.getDocument();
        myWidthInColumns = myRepresentationHelper.toVisualColumnSymbolsNumber(
          document.getCharsSequence(), myFoldRegion.getStartOffset(), myFoldRegion.getEndOffset(), myStartX
        );
      }

      return myWidthInColumns;
    }
  }

  private static class TabData {

    public final int widthInColumns;
    public final int offset;

    TabData(@NotNull ProcessingContext context) {
      widthInColumns = context.symbolWidthInColumns;
      offset = context.offset;
    }
  }

  private static final TIntObjectHashMap<FoldingData> DUMMY = new TIntObjectHashMap<FoldingData>();

  //TODO den remove
  private static long counter;

  /**
   * Encapsulates information to cache for the single visual line.
   */
  @SuppressWarnings("unchecked")
  private class CacheEntry implements Comparable<CacheEntry> {

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

    /** Holds positions for the tabulation symbols on a target visual line sorted by offset in ascending order. */
    private List<TabData> myTabPositions = Collections.EMPTY_LIST;

    /** Holds information about single line fold regions representation data. */
    private TIntObjectHashMap<FoldingData> myFoldingData = DUMMY;

    CacheEntry(int visualLine) {
      this.visualLine = visualLine;
      //TODO den remove
      counter++;
      if (counter > 200000) {
        int i = 1;
      }
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
      if (myFoldingData == DUMMY) {
        myFoldingData = new TIntObjectHashMap<FoldingData>();
      }
      myFoldingData.put(foldRegion.getStartOffset(), new FoldingData(foldRegion, startX));
    }

    public TIntObjectHashMap<FoldingData> getFoldingData() {
      return myFoldingData;
    }

    public List<TabData> getTabData() {
      return myTabPositions;
    }

    public void storeTabData(ProcessingContext context) {
      if (myTabPositions == Collections.EMPTY_LIST) {
        myTabPositions = new ArrayList<TabData>();
      }
      myTabPositions.add(new TabData(context));
    }

    @Override
    public int compareTo(CacheEntry e) {
      return visualLine - e.visualLine;
    }

    @Override
    public String toString() {
      return "visual line: " + visualLine + ", offsets: " + startOffset + "-" + endOffset;
    }
  }

  private static class TabulationDataProvider extends AbstractListBasedDataProvider<SoftWrapDataProviderKeys, TabData> {

    TabulationDataProvider(@NotNull List<TabData> data) {
      super(SoftWrapDataProviderKeys.TABULATION, data);
    }

    @Override
    protected int getSortingKey(@NotNull TabData data) {
      return data.offset;
    }
  }

  /**
   * Defines contract for the strategy that knows how to map one document dimension to another (e.g. visual position to logical position).
   *
   * @param <T>     target dimension type
   */
  private interface MappingStrategy<T> {

    /**
     * @return    target mapped dimension if it's possible to perform the mapping eagerly; <code>null</code> otherwise
     */
    @Nullable
    T eagerMatch();

    /**
     * Builds initial context to start calculation from.
     * <p/>
     * It's assumed that we store information about 'anchor' document positions like visual line starts and calculate
     * target result starting from the nearest position.
     *
     * @return    initial context to use for target result calculation
     */
    @NotNull
    ProcessingContext buildInitialContext();

    /**
     * Notifies current strategy that there are no special symbols and regions between the document position identified
     * by the current state of the given context and given offset. I.e. it's safe to assume that all symbols between the offset
     * identified by the given context and given offset have occupy one visual and logical column.
     *
     * @param context   context that identifies currently processed position
     * @param offset    nearest offset to the one identified by the given context that conforms to requirement that every symbol
     *                  between them increments offset, visual and logical position by one
     * @return          resulting dimension if it's located between the document position identified by the given context
     *                  and given offset if any; <code>null</code> otherwise
     */
    @Nullable
    T advance(ProcessingContext context, int offset);

    /**
     * Notifies current strategy that soft wrap is encountered during the processing. There are two ways to continue the processing then:
     * <pre>
     * <ul>
     *   <li>Target dimension is located after the given soft wrap;</li>
     *   <li>Target dimension is located within the given soft wrap bounds;</li>
     * </ul>
     * </pre>
     *
     * @param context     current processing context
     * @param softWrap    soft wrap encountered during the processing
     * @return            target document dimension if it's located within the bounds of the given soft wrap; <code>null</code> otherwise
     */
    @Nullable
    T processSoftWrap(ProcessingContext context, SoftWrap softWrap);

    /**
     * Notifies current strategy that collapsed fold region is encountered during the processing. There are two ways to
     * continue the processing then:
     * <pre>
     * <ul>
     *   <li>Target dimension is located after the given fold region;</li>
     *   <li>Target dimension is located within the given fold region bounds;</li>
     * </ul>
     * </pre>
     *
     * @param context       current processing context
     * @param foldRegion    collapsed fold region encountered during the processing
     * @return              target document dimension if it's located within the bounds of the given fold region;
     *                      <code>null</code> otherwise
     */
    @Nullable
    T processFoldRegion(ProcessingContext context, FoldRegion foldRegion);

    /**
     * Notifies current strategy that tabulation symbols is encountered during the processing. Tabulation symbols
     * have special treatment because they may occupy different number of visual and logical columns.
     * See {@link EditorUtil#nextTabStop(int, Editor)} for more details. So, there are two ways to continue the processing then:
     * <pre>
     * <ul>
     *   <li>Target dimension is located after the given tabulation symbol bounds;</li>
     *   <li>Target dimension is located within the given tabulation symbol bounds;</li>
     * </ul>
     * </pre>
     *
     * @param context       current processing context
     * @param tabData       document position for the active tabulation symbol encountered during the processing
     * @return              target document dimension if it's located within the bounds of the given fold region;
     *                      <code>null</code> otherwise
     */
    @Nullable
    T processTabulation(ProcessingContext context, TabData tabData);

    /**
     * This method is assumed to be called when there are no special symbols between the document position identified by the
     * given context and target dimension. E.g. this method may be called when we perform {@code visual -> logical} mapping
     * and there are no soft wraps, collapsed fold regions and tabulation symbols on a current visual line.
     *
     * @param context   current processing context that identifies active document position
     * @return          resulting dimension that is built on the basis of the given context and target anchor dimension
     */
    @NotNull
    T build(ProcessingContext context);
  }

  /**
   * Abstract super class for mapping strategies that encapsulates shared logic of advancing the context to the points
   * of specific types. I.e. it's main idea is to ask sub-class if target dimension lays inside particular region
   * (e.g. soft wrap, fold region etc) and use common algorithm for advancing context to the region's end in the case
   * of negative answer.
   *
   * @param <T>     resulting document dimension type
   */
  private abstract class AbstractMappingStrategy<T> implements MappingStrategy<T> {

    protected final CacheEntry myCacheEntry;
    private final T myEagerMatch;

    protected AbstractMappingStrategy(Computable<Pair<CacheEntry, T>> cacheEntryProvider) throws IllegalStateException {
      Pair<CacheEntry, T> pair = cacheEntryProvider.compute();
      myCacheEntry = pair.first;
      myEagerMatch = pair.second;
    }

    @Nullable
    @Override
    public T eagerMatch() {
      return myEagerMatch;
    }

    @NotNull
    @Override
    public ProcessingContext buildInitialContext() {
      return myCacheEntry.buildStartLineContext();
    }

    protected FoldingData getFoldRegionData(FoldRegion foldRegion) {
      return myCacheEntry.getFoldingData().get(foldRegion.getStartOffset());
    }

    @Override
    public T advance(ProcessingContext context, int offset) {
      T result = buildIfExceeds(context, offset);
      if (result != null) {
        return result;
      }

      // Update context state and continue processing.
      context.logicalLine = myEditor.getDocument().getLineNumber(offset);
      int diff = offset - context.offset;
      context.visualColumn += diff;
      context.logicalColumn += diff;
      context.offset = offset;
      return null;
    }

    @Nullable
    protected abstract T buildIfExceeds(ProcessingContext context, int offset);

    @Override
    public T processFoldRegion(ProcessingContext context, FoldRegion foldRegion) {
      T result = buildIfExceeds(context, foldRegion);
      if (result != null) {
        return result;
      }

      Document document = myEditor.getDocument();
      int endOffsetLogicalLine = document.getLineNumber(foldRegion.getEndOffset());
      int collapsedSymbolsWidthInColumns;
      if (context.logicalLine == endOffsetLogicalLine) {
        // Single-line fold region.
        FoldingData foldingData = getFoldRegionData(foldRegion);
        if (foldingData == null) {
          assert false;
          collapsedSymbolsWidthInColumns = context.visualColumn * myRepresentationHelper.textWidth(" ", 0, 1, Font.PLAIN, 0);
        }
        else {
          collapsedSymbolsWidthInColumns = foldingData.getCollapsedSymbolsWidthInColumns();
        }
      }
      else {
        // Multi-line fold region.
        collapsedSymbolsWidthInColumns = myRepresentationHelper.toVisualColumnSymbolsNumber(
          document.getCharsSequence(), foldRegion.getStartOffset(), foldRegion.getEndOffset(), 0
        );
        context.softWrapColumnDiff = 0;
        context.softWrapLinesBefore += context.softWrapLinesCurrent;
        context.softWrapLinesCurrent = 0;
      }

      context.advance(foldRegion, collapsedSymbolsWidthInColumns);
      return null;
    }

    @Nullable
    protected abstract T buildIfExceeds(ProcessingContext context, FoldRegion foldRegion);

    @Override
    public T processTabulation(ProcessingContext context, TabData tabData) {
      T result = buildIfExceeds(context, tabData);
      if (result != null) {
        return result;
      }

      context.visualColumn += tabData.widthInColumns;
      context.logicalColumn += tabData.widthInColumns;
      context.offset++;
      return null;
    }

    @Nullable
    protected abstract T buildIfExceeds(ProcessingContext context, TabData tabData);
  }

  private class VisualToLogicalCalculationStrategy extends AbstractMappingStrategy<LogicalPosition> {

    private final VisualPosition myTargetVisual;

    VisualToLogicalCalculationStrategy(@NotNull final VisualPosition targetVisual) {
      super(new Computable<Pair<CacheEntry, LogicalPosition>>() {
        @Override
        public Pair<CacheEntry, LogicalPosition> compute() {
          mySearchKey.visualLine = targetVisual.line;
          int i = Collections.binarySearch(myCache, mySearchKey);
          if (i >= 0) {
            CacheEntry cacheEntry = myCache.get(i);
            LogicalPosition eager = null;
            if (targetVisual.column == 0) {
              eager = cacheEntry.buildStartLineContext().buildLogicalPosition();
            }
            return new Pair<CacheEntry, LogicalPosition>(cacheEntry, eager);
          }

          // Handle situation with corrupted cache.
          throw new IllegalStateException(String.format(
            "Can't map visual position (%s) to logical. Reason: no cached information information about target visual line is found. "
            + "Registered entries: %s", targetVisual, myCache
          ));
        }
      });
      myTargetVisual = targetVisual;
    }

    @Nullable
    @Override
    public LogicalPosition eagerMatch() {
      return null;
    }

    @Override
    protected LogicalPosition buildIfExceeds(ProcessingContext context, int offset) {
      // There is a possible case that target visual line starts with soft wrap. We want to process that at 'processSoftWrap()' method.
      if (offset == myCacheEntry.startOffset && myStorage.getSoftWrap(offset) != null) {
        return null;
      }

      // Return eagerly if target visual position remains between current context position and the one defined by the given offset.
      if (offset > myCacheEntry.endOffset || (context.visualColumn + offset - context.offset >= myTargetVisual.column)) {
        int diff = myTargetVisual.column - context.visualColumn;
        context.offset = Math.min(myCacheEntry.endOffset, context.offset + diff);
        context.logicalColumn += diff;
        context.visualColumn = myTargetVisual.column;
        return context.buildLogicalPosition();
      }
      return null;
    }

    @Nullable
    @Override
    public LogicalPosition processSoftWrap(ProcessingContext context, SoftWrap softWrap) {
      // There is a possible case that current visual line starts with soft wrap and target visual position points to its
      // virtual space.
      if (myCacheEntry.startOffset == softWrap.getStart()) {
        if (myTargetVisual.column <= softWrap.getIndentInColumns()) {
          ProcessingContext resultingContext = myCacheEntry.buildStartLineContext();
          resultingContext.visualColumn = myTargetVisual.column;
          resultingContext.softWrapColumnDiff += myTargetVisual.column;
          return resultingContext.buildLogicalPosition();
        }
        else {
          context.visualColumn = softWrap.getIndentInColumns();
          context.softWrapColumnDiff += softWrap.getIndentInColumns();
          return null;
        }
      }

      // We assume that target visual position points to soft wrap-introduced virtual space if this method is called (we expect
      // to iterate only a single visual line and also expect soft wrap to have line feed symbol at the first position).
      ProcessingContext targetContext = myCacheEntry.buildEndLineContext();
      targetContext.softWrapColumnDiff += myTargetVisual.column - targetContext.visualColumn;
      targetContext.visualColumn = myTargetVisual.column;
      return targetContext.buildLogicalPosition();
    }

    @Override
    protected LogicalPosition buildIfExceeds(ProcessingContext context, FoldRegion foldRegion) {
      // We assume that fold region placeholder contains only 'simple' symbols, i.e. symbols that occupy single visual column.
      String placeholder = foldRegion.getPlaceholderText();

      // Check if target visual position points inside collapsed fold region placeholder
      if (myTargetVisual.column < /* note that we don't use <= here */ context.visualColumn + placeholder.length()) {
        // Map all visual positions that point inside collapsed fold region as the logical position of it's start.
        return context.buildLogicalPosition();
      }

      return null;
    }

    @Override
    protected LogicalPosition buildIfExceeds(ProcessingContext context, TabData tabData) {
      if (context.visualColumn + tabData.widthInColumns >= myTargetVisual.column) {
        context.logicalColumn += myTargetVisual.column - context.visualColumn;
        context.visualColumn = myTargetVisual.column;
        return context.buildLogicalPosition();
      }
      return null;
    }

    @NotNull
    @Override
    public LogicalPosition build(ProcessingContext context) {
      int diff = myTargetVisual.column - context.visualColumn;
      context.logicalColumn += diff;
      context.offset += diff;
      context.visualColumn = myTargetVisual.column;
      return context.buildLogicalPosition();
    }
  }

  private class OffsetToLogicalCalculationStrategy extends AbstractMappingStrategy<LogicalPosition> {

    private final int myTargetOffset;

    private OffsetToLogicalCalculationStrategy(final int targetOffset) {
      super(new Computable<Pair<CacheEntry, LogicalPosition>>() {
        @Override
        public Pair<CacheEntry, LogicalPosition> compute() {
          if (targetOffset >= myEditor.getDocument().getTextLength()) {
            if (myCache.isEmpty()) {
              return new Pair<CacheEntry, LogicalPosition>(null, new LogicalPosition(0, 0, 0, 0, 0, 0, 0));
            }
            else {
              CacheEntry lastEntry = myCache.get(myCache.size() - 1);
              LogicalPosition eager = new LogicalPosition(
                lastEntry.endLogicalLine, lastEntry.endLogicalColumn + 1, lastEntry.endSoftWrapLinesBefore,
                lastEntry.endSoftWrapLinesCurrent, lastEntry.endSoftWrapColumnDiff, lastEntry.endFoldedLines,
                lastEntry.endFoldingColumnDiff
              );
              return new Pair<CacheEntry, LogicalPosition>(null, eager);
            }
          }

          int start = 0;
          int end = myCache.size() - 1;

          // We inline binary search here because profiling indicates that it becomes bottleneck to use Collections.binarySearch().
          while (start <= end) {
            int i = (end + start) >>> 1;
            CacheEntry cacheEntry = myCache.get(i);
            if (cacheEntry.endOffset < targetOffset) {
              start = i + 1;
              continue;
            }
            if (cacheEntry.startOffset > targetOffset) {
              end = i - 1;
              continue;
            }

            // There is a possible case that currently found cache entry corresponds to soft-wrapped line and soft wrap occurred
            // at target offset. We need to return cache entry for the next visual line then (because document offset shared with
            // soft wrap offset is assumed to point to 'after soft wrap' position).
            if (targetOffset == cacheEntry.endOffset && i < myCache.size() - 1) {
              CacheEntry nextLineCacheEntry = myCache.get(i + 1);
              if (nextLineCacheEntry.startOffset == targetOffset) {
                return new Pair<CacheEntry, LogicalPosition>(nextLineCacheEntry, null);
              }
            }
            return new Pair<CacheEntry, LogicalPosition>(cacheEntry, null);
          }

          throw new IllegalStateException(String.format(
            "Can't map offset (%d) to logical position. Reason: no cached information information about target visual line is found. "
            + "Registered entries: %s", targetOffset, myCache
          ));
        }
      });
      myTargetOffset = targetOffset;
    }

    @Override
    protected LogicalPosition buildIfExceeds(ProcessingContext context, int offset) {
      if (myTargetOffset > offset) {
        return null;
      }

      // Process use-case when target offset points to 'after soft wrap' position.
      SoftWrap softWrap = myStorage.getSoftWrap(offset);
      if (softWrap != null && offset < myCacheEntry.endOffset) {
        context.visualColumn = softWrap.getIndentInColumns();
        context.softWrapColumnDiff = context.visualColumn - context.logicalColumn;
        return context.buildLogicalPosition();
      }

      int diff = myTargetOffset - context.offset;
      context.logicalColumn += diff;
      context.visualColumn += diff;
      context.offset = myTargetOffset;
      return context.buildLogicalPosition();
    }

    @Override
    protected LogicalPosition buildIfExceeds(ProcessingContext context, FoldRegion foldRegion) {
      if (myTargetOffset >= foldRegion.getEndOffset()) {
        return null;
      }

      Document document = myEditor.getDocument();
      int targetLogicalLine = document.getLineNumber(myTargetOffset);
      if (targetLogicalLine == context.logicalLine) {
        // Target offset is located on the same logical line as folding start.
        FoldingData cachedData = getFoldRegionData(foldRegion);
        context.logicalColumn += myRepresentationHelper.toVisualColumnSymbolsNumber(
          document.getCharsSequence(), foldRegion.getStartOffset(), myTargetOffset, cachedData.myStartX
        );
      }
      else {
        // Target offset is located on a different line with folding start.
        context.logicalColumn = myRepresentationHelper.toVisualColumnSymbolsNumber(
          document.getCharsSequence(), foldRegion.getStartOffset(), myTargetOffset, 0
        );
        context.softWrapColumnDiff = 0;
        int linesDiff = document.getLineNumber(myTargetOffset) - document.getLineNumber(foldRegion.getStartOffset());
        context.logicalLine += linesDiff;
        context.foldedLines += linesDiff;
        context.softWrapLinesBefore += context.softWrapLinesCurrent;
        context.softWrapLinesCurrent = 0;
      }

      context.foldingColumnDiff = context.visualColumn - context.softWrapColumnDiff - context.logicalColumn;
      context.offset = myTargetOffset;
      return context.buildLogicalPosition();
    }

    @Nullable
    @Override
    protected LogicalPosition buildIfExceeds(ProcessingContext context, TabData tabData) {
      if (tabData.offset == myTargetOffset) {
        return context.buildLogicalPosition();
      }
      return null;
    }

    @Nullable
    @Override
    public LogicalPosition processSoftWrap(ProcessingContext context, SoftWrap softWrap) {
      context.visualColumn = softWrap.getIndentInColumns();
      context.softWrapColumnDiff += softWrap.getIndentInColumns();
      if (softWrap.getStart() == myTargetOffset) {
        return context.buildLogicalPosition();
      }
      if (softWrap.getStart() == myCacheEntry.startOffset) {
        return null;
      }
      assert false;
      return context.buildLogicalPosition();
    }

    @NotNull
    @Override
    public LogicalPosition build(ProcessingContext context) {
      int diff = myTargetOffset - context.offset;
      context.logicalColumn += diff;
      context.visualColumn += diff;
      context.offset = myTargetOffset;
      return context.buildLogicalPosition();
    }
  }

  // We can't use standard strategy-based approach with logical -> visual mapping because folding processing quite often
  // temporarily disables folding. So, there is an inconsistency between cached data (folding aware) and current folding
  // state. So, we use direct soft wraps adjustment instead of normal calculation.

  //private class LogicalToVisualMappingStrategy extends AbstractMappingStrategy<VisualPosition> {
  //
  //  private final LogicalPosition myTargetLogical;
  //
  //  LogicalToVisualMappingStrategy(@NotNull final LogicalPosition logical) throws IllegalStateException {
  //    super(new Computable<CacheEntry>() {
  //      @Override
  //      public CacheEntry compute() {
  //        int start = 0;
  //        int end = myCache.size() - 1;
  //
  //        // We inline binary search here because profiling indicates that it becomes bottleneck to use Collections.binarySearch().
  //        while (start <= end) {
  //          int i = (end + start) >>> 1;
  //          CacheEntry cacheEntry = myCache.get(i);
  //
  //          // There is a possible case that single logical line is represented on multiple visual lines due to soft wraps processing.
  //          // Hence, we check for bot logical line and logical columns during searching 'anchor' cache entry.
  //
  //          if (cacheEntry.endLogicalLine < logical.line
  //              || (cacheEntry.endLogicalLine == logical.line && myStorage.getSoftWrap(cacheEntry.endOffset) != null
  //                  && cacheEntry.endLogicalColumn <= logical.column))
  //          {
  //            start = i + 1;
  //            continue;
  //          }
  //          if (cacheEntry.startLogicalLine > logical.line
  //              || (cacheEntry.startLogicalLine == logical.line
  //                  && cacheEntry.startLogicalColumn > logical.column))
  //          {
  //            end = i - 1;
  //            continue;
  //          }
  //
  //          // There is a possible case that currently found cache entry corresponds to soft-wrapped line and soft wrap occurred
  //          // at target logical column. We need to return cache entry for the next visual line then (because single logical column
  //          // is shared for 'before soft wrap' and 'after soft wrap' positions and we want to use the one that points to
  //          // 'after soft wrap' position).
  //          if (cacheEntry.endLogicalLine == logical.line && cacheEntry.endLogicalColumn == logical.column && i < myCache.size() - 1) {
  //            CacheEntry nextLineCacheEntry = myCache.get(i + 1);
  //            if (nextLineCacheEntry.startLogicalLine == logical.line
  //                && nextLineCacheEntry.startLogicalColumn == logical.column)
  //            {
  //              return nextLineCacheEntry;
  //            }
  //          }
  //          return cacheEntry;
  //        }
  //
  //        throw new IllegalStateException(String.format(
  //          "Can't map logical position (%s) to visual position. Reason: no cached information information about target visual "
  //          + "line is found. Registered entries: %s", logical, myCache
  //        ));
  //      }
  //    });
  //    myTargetLogical = logical;
  //  }
  //
  //  @Override
  //  protected VisualPosition buildIfExceeds(ProcessingContext context, int offset) {
  //    if (context.logicalLine < myTargetLogical.line) {
  //       return null;
  //    }
  //
  //    int diff = myTargetLogical.column - context.logicalColumn;
  //    if (offset - context.offset < diff) {
  //      return null;
  //    }
  //
  //    context.visualColumn += diff;
  //    // Don't update other dimensions like logical position and offset because we need only visual position here.
  //    return context.buildVisualPosition();
  //  }
  //
  //  @Override
  //  protected VisualPosition buildIfExceeds(ProcessingContext context, FoldRegion foldRegion) {
  //    int foldEndLine = myEditor.getDocument().getLineNumber(foldRegion.getEndOffset());
  //    if (myTargetLogical.line > foldEndLine) {
  //      return null;
  //    }
  //
  //    if (myTargetLogical.line < foldEndLine) {
  //      // Map all logical position that point inside collapsed fold region to visual position of its start.
  //      return context.buildVisualPosition();
  //    }
  //
  //    int foldEndColumn = getFoldRegionData(foldRegion).getCollapsedSymbolsWidthInColumns();
  //    if (foldEndLine == context.logicalLine) {
  //      // Single-line fold region.
  //      foldEndColumn += context.logicalColumn;
  //    }
  //
  //    if (foldEndColumn <= myTargetLogical.column) {
  //      return null;
  //    }
  //
  //    // Map all logical position that point inside collapsed fold region to visual position of its start.
  //    return context.buildVisualPosition();
  //  }
  //
  //  @Override
  //  protected VisualPosition buildIfExceeds(ProcessingContext context, TabData tabData) {
  //    if (context.logicalLine < myTargetLogical.line) {
  //      return null;
  //    }
  //
  //    int diff = myTargetLogical.column - context.logicalColumn;
  //    if (diff >= tabData.widthInColumns) {
  //      return null;
  //    }
  //
  //    context.logicalColumn += diff;
  //    context.visualColumn += diff;
  //
  //    return context.buildVisualPosition();
  //  }
  //
  //  @Override
  //  public VisualPosition processSoftWrap(ProcessingContext context, SoftWrap softWrap) {
  //    context.visualColumn = softWrap.getIndentInColumns();
  //    context.softWrapColumnDiff += softWrap.getIndentInColumns();
  //
  //    if (context.logicalLine < myTargetLogical.line || context.logicalColumn != myTargetLogical.column) {
  //      return null;
  //    }
  //    return context.buildVisualPosition();
  //  }
  //
  //  @NotNull
  //  @Override
  //  public VisualPosition build(ProcessingContext context) {
  //    int diff = myTargetLogical.column - context.logicalColumn;
  //    context.logicalColumn += diff;
  //    context.visualColumn += diff;
  //    context.offset += diff;
  //    return context.buildVisualPosition();
  //  }
  //}
}