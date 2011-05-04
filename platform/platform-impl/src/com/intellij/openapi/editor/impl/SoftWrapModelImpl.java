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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.editor.impl.softwrap.mapping.CachingSoftWrapDataMapper;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapAwareVisualSizeManager;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link SoftWrapModelEx} implementation.
 * <p/>
 * Works as a mix of <code>GoF Facade and Bridge</code>, i.e. delegates the processing to the target sub-components and provides
 * utility methods built on top of sub-components API.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 12:47:32 PM
 */
public class SoftWrapModelImpl implements SoftWrapModelEx, PrioritizedDocumentListener, FoldingListener, PropertyChangeListener {

  /**
   * Holds name of JVM property which presence should trigger debug-aware soft wraps processing.
   */
  public static final String DEBUG_PROPERTY_NAME = "idea.editor.wrap.soft.debug";

  private static final Logger LOG = Logger.getInstance("#" + SoftWrapModelImpl.class.getName());

  private final OffsetToLogicalTask   myOffsetToLogicalTask   = new OffsetToLogicalTask();
  private final VisualToLogicalTask   myVisualToLogicalTask   = new VisualToLogicalTask();
  private final LogicalToVisualTask   myLogicalToVisualTask   = new LogicalToVisualTask();
  private final FoldProcessingEndTask myFoldProcessingEndTask = new FoldProcessingEndTask();

  private final List<DocumentListener> myDocumentListeners   = new ArrayList<DocumentListener>();
  private final List<FoldingListener>  myFoldListeners       = new ArrayList<FoldingListener>();
  
  /**
   * There is a possible case that particular activity performs batch fold regions operations (addition, removal etc).
   * We don't want to process them at the same time we get notifications about that because there is a big chance that
   * we see inconsistent state (e.g. there was a problem with {@link FoldingModel#getCollapsedRegionAtOffset(int)} because that
   * method uses caching internally and cached data becomes inconsistent if, for example, the top region is removed).
   * <p/>
   * So, our strategy is to collect information about changed fold regions and process it only when batch folding processing ends.
   */
  private final List<FoldRegion>       myDeferredFoldRegions = new ArrayList<FoldRegion>();

  private final SoftWrapFoldBasedApplianceStrategy myFoldBasedApplianceStrategy;
  private final CachingSoftWrapDataMapper          myDataMapper;
  private final SoftWrapsStorage                   myStorage;
  private final SoftWrapPainter                    myPainter;
  private final SoftWrapApplianceManager           myApplianceManager;
  private final SoftWrapAwareVisualSizeManager     myVisualSizeManager;

  private final EditorEx myEditor;
  
  /**
   * We don't want to use soft wraps-aware processing from non-EDT and profiling shows that 'is EDT' check that is called too
   * often is rather expensive. Hence, we use caching here for performance improvement.
   */
  private SoftReference<Thread> myLastEdt = new SoftReference<Thread>(null);
  /** Holds number of 'active' calls, i.e. number of methods calls of the current object within the current call stack. */
  private int myActive;
  private boolean myUseSoftWraps;

  /**
   * Standard IJ editor starts showing horizontal scroll bar event when text line ends couple of symbols before the right visual
   * area edge (exact value of columns to use for such preliminary scrolling is identified by
   * {@link EditorSettings#getAdditionalColumnsCount() additionalColumnsCount} value).
   * <p/>
   * However, we want to avoid using horizontal scrolling within soft wraps whenever possible. Hence, we set that additional
   * columns property to zero when soft wraps are used and restore it if soft wraps are turned off.
   * <p/>
   * Current field holds initial <code>'additional columns count'</code> property value that is to be restored if
   * soft wraps are turned off.
   */
  private int myAdditionalColumnsCount;

  /**
   * Soft wraps need to be kept up-to-date on all editor modification (changing text, adding/removing/expanding/collapsing fold
   * regions etc). Hence, we need to react to all types of target changes. However, soft wraps processing uses various information
   * provided by editor and there is a possible case that that information is inconsistent during update time (e.g. fold model 
   * advances fold region offsets when end-user types before it, hence, fold regions data is inconsistent between the moment
   * when text changes are applied to the document and fold data is actually updated).
   * <p/>
   * Current field serves as a flag that indicates if all preliminary actions necessary for successful soft wraps processing is done. 
   */
  private boolean myUpdateInProgress;

  /**
   * There is a possible case that target document is changed while its editor is inactive (e.g. user opens two editors for classes
   * <code>'Part'</code> and <code>'Whole'</code>; activates editor for the class <code>'Whole'</code> and performs 'rename class'
   * for <code>'Part'</code> from it). Soft wraps cache is not recalculated during that because corresponding editor is not shown
   * and we lack information about visible area width. Hence, we will need to recalculate the whole soft wraps cache as soon
   * as target editor becomes visible.
   * <p/>
   * Current field serves as a flag for that <code>'dirty document, need complete soft wraps cache recalculation'</code> state. 
   */
  private boolean myDirty;

  public SoftWrapModelImpl(@NotNull EditorEx editor) {
    this(editor, new SoftWrapsStorage(), new CompositeSoftWrapPainter(editor));
  }

  public SoftWrapModelImpl(@NotNull final EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter) {
    this(editor, storage, painter, new DefaultEditorTextRepresentationHelper(editor));
  }

  public SoftWrapModelImpl(@NotNull final EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter,
                           @NotNull EditorTextRepresentationHelper representationHelper) {
    this(editor, storage, painter, representationHelper, new CachingSoftWrapDataMapper(editor, storage, representationHelper));
    myApplianceManager.addListener(myDataMapper);
  }

  public SoftWrapModelImpl(@NotNull final EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter,
                           @NotNull EditorTextRepresentationHelper representationHelper, @NotNull CachingSoftWrapDataMapper dataMapper)
  {
    this(editor, storage, painter, new SoftWrapApplianceManager(storage, editor, painter, representationHelper, dataMapper), dataMapper);
  }

  public SoftWrapModelImpl(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter,
                           @NotNull SoftWrapApplianceManager applianceManager, @NotNull CachingSoftWrapDataMapper dataMapper)
  {
    myEditor = editor;
    myStorage = storage;
    myPainter = painter;
    myApplianceManager = applianceManager;
    myDataMapper = dataMapper;
    myFoldBasedApplianceStrategy = new SoftWrapFoldBasedApplianceStrategy(editor);
    myVisualSizeManager = new SoftWrapAwareVisualSizeManager(painter);

    myDocumentListeners.add(myApplianceManager);
    myFoldListeners.add(myApplianceManager);
    applianceManager.addListener(myVisualSizeManager);
    EditorSettings settings = myEditor.getSettings();
    myAdditionalColumnsCount = settings.getAdditionalColumnsCount();
    myUseSoftWraps = settings.isUseSoftWraps();
    
    editor.addPropertyChangeListener(this);
  }

  /**
   * Called on editor settings change. Current model is expected to drop all cached information about the settings if any.
   */
  public void reinitSettings() {
    boolean softWrapsUsedBefore = myUseSoftWraps;
    EditorSettings settings = myEditor.getSettings();
    myUseSoftWraps = settings.isUseSoftWraps();
    if (myUseSoftWraps && (!softWrapsUsedBefore || settings.getAdditionalColumnsCount() > 0)) {
      myApplianceManager.reset();
      myDeferredFoldRegions.clear();
      myAdditionalColumnsCount = settings.getAdditionalColumnsCount();
      settings.setAdditionalColumnsCount(0);
    }
    else if (!myUseSoftWraps && softWrapsUsedBefore) {
      settings.setAdditionalColumnsCount(myAdditionalColumnsCount);
    }
  }

  public boolean isSoftWrappingEnabled() {
    if (myEditor.isOneLineMode()) {
      return false;
    }

    // We check that current thread is EDT because attempt to retrieve information about visible area width may fail otherwise
    Application application = ApplicationManager.getApplication();
    Thread lastEdt = myLastEdt.get();
    Thread currentThread = Thread.currentThread();
    if (lastEdt != currentThread) {
      if (application.isDispatchThread()) {
        myLastEdt = new SoftReference<Thread>(currentThread);
      }
      else {
        myLastEdt = new SoftReference<Thread>(null);
        return false;
      }
    }

    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    if (!application.isUnitTestMode() && (visibleArea.width <= 0 || visibleArea.height <= 0)) {
      return false;
    }

    return myUseSoftWraps;
  }

  @Nullable
  public SoftWrap getSoftWrap(int offset) {
    if (!isSoftWrappingEnabled()) {
      return null;
    }
    return myStorage.getSoftWrap(offset);
  }

  @Override
  public int getSoftWrapIndex(int offset) {
    return myStorage.getSoftWrapIndex(offset);
  }

  @NotNull
  @Override
  public List<? extends SoftWrap> getSoftWrapsForRange(int start, int end) {
    if (!isSoftWrappingEnabled() || end < start) {
      return Collections.emptyList();
    }

    List<? extends SoftWrap> softWraps = myStorage.getSoftWraps();
    
    int startIndex = myStorage.getSoftWrapIndex(start);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
      if (startIndex >= softWraps.size() || softWraps.get(startIndex).getStart() > end) {
        return Collections.emptyList();
      }
    }

    int endIndex = myStorage.getSoftWrapIndex(end);
    if (endIndex >= 0) {
      return softWraps.subList(startIndex, endIndex + 1);
    }
    else {
      endIndex = -endIndex - 1;
      return softWraps.subList(startIndex, endIndex);
    }
  }

  @Override
  @NotNull
  public List<? extends SoftWrap> getSoftWrapsForLine(int documentLine) {
    if (!isSoftWrappingEnabled() || documentLine < 0) {
      return Collections.emptyList();
    }
    Document document = myEditor.getDocument();
    int start = document.getLineStartOffset(documentLine);
    int end = document.getLineEndOffset(documentLine);
    return getSoftWrapsForRange(start, end + 1/* it's theoretically possible that soft wrap is registered just before the line feed,
                                               * hence, we add '1' here assuming that end line offset points to line feed symbol */
    );
  }

  /**
   * @return    total number of soft wrap-introduced new visual lines
   */
  public int getSoftWrapsIntroducedLinesNumber() {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    int result = 0;
    FoldingModel foldingModel = myEditor.getFoldingModel();
    for (SoftWrap softWrap : myStorage.getSoftWraps()) {
      if (!foldingModel.isOffsetCollapsed(softWrap.getStart())) {
        result++; // Assuming that soft wrap has single line feed all the time
      }
    }
    return result;
  }

  /**
   * Callback method that is expected to be invoked before editor painting.
   * <p/>
   * It's primary purpose is to recalculate soft wraps at least for the painted area if necessary.
   */
  public void registerSoftWrapsIfNecessary() {
    if (!isSoftWrappingEnabled()) {
      return;
    }

    myActive++;
    try {
      myApplianceManager.registerSoftWrapIfNecessary();
    }
    finally {
      myActive--;
    }
  }

  @Override
  public List<? extends SoftWrap> getRegisteredSoftWraps() {
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    return myStorage.getSoftWraps();
  }

  @Override
  public boolean isVisible(SoftWrap softWrap) {
    FoldingModel foldingModel = myEditor.getFoldingModel();
    int start = softWrap.getStart();
    if (foldingModel.isOffsetCollapsed(start)) {
      return false;
    }

    // There is a possible case that soft wrap and collapsed folding region share the same offset, i.e. soft wrap is represented
    // before the folding. We need to return 'true' in such situation. Hence, we check if offset just before the soft wrap
    // is collapsed as well.
    return start <= 0 || !foldingModel.isOffsetCollapsed(start - 1);
  }

  @Override
  public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    return myPainter.paint(g, drawingType, x, y, lineHeight);
  }

  @Override
  public int getMinDrawingWidthInPixels(@NotNull SoftWrapDrawingType drawingType) {
    return myPainter.getMinDrawingWidth(drawingType);
  }

  @NotNull
  @Override
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visual) {
    if (myUpdateInProgress || !prepareToMapping()) {
      return myEditor.visualToLogicalPosition(visual, false);
    }
    myActive++;
    try {
      myVisualToLogicalTask.input = visual;
      executeSafely(myVisualToLogicalTask);
      return myVisualToLogicalTask.output;
    } finally {
      myActive--;
    }
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    if (myUpdateInProgress || !prepareToMapping()) {
      return myEditor.offsetToLogicalPosition(offset, false);
    }
    myActive++;
    try {
      myOffsetToLogicalTask.input = offset;
      executeSafely(myOffsetToLogicalTask);
      return myOffsetToLogicalTask.output;
    } finally {
      myActive--;
    }
  }

  @NotNull
  public LogicalPosition adjustLogicalPosition(LogicalPosition defaultLogical, int offset) {
    if (myUpdateInProgress || !prepareToMapping()) {
      return defaultLogical;
    }

    myActive++;
    try {
      myOffsetToLogicalTask.input = offset;
      executeSafely(myOffsetToLogicalTask);
      return myOffsetToLogicalTask.output;
    } finally {
      myActive--;
    }
  }

  @NotNull
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual) {
    if (myUpdateInProgress || !prepareToMapping()) {
      return defaultVisual;
    }

    myActive++;
    try {
      myLogicalToVisualTask.input = logical;
      myLogicalToVisualTask.defaultOutput = defaultVisual;
      executeSafely(myLogicalToVisualTask);
      return myLogicalToVisualTask.output;
    }
    finally {
      myActive--;
    }
  }

  /**
   * Encapsulates preparations for performing document dimension mapping (e.g. visual to logical position) and answers
   * if soft wraps-aware processing should be used (e.g. there is no need to consider soft wraps if user configured them
   * not to be used).
   *
   * @return      <code>true</code> if soft wraps-aware processing should be used; <code>false</code> otherwise
   */
  private boolean prepareToMapping() {
    boolean useSoftWraps = myActive <= 0 && isSoftWrappingEnabled() && myEditor.getDocument().getTextLength() > 0
                             && myFoldBasedApplianceStrategy.processSoftWraps();

    if (!useSoftWraps) {
      return useSoftWraps;
    }

    if (myDirty) {
      myApplianceManager.reset();
      myDeferredFoldRegions.clear();
      myDirty = false;
    }
    
    myApplianceManager.recalculateIfNecessary();
    return true;
  }

  /**
   * Allows to answer if given visual position points to soft wrap-introduced virtual space.
   *
   * @param visual    target visual position to check
   * @return          <code>true</code> if given visual position points to soft wrap-introduced virtual space;
   *                  <code>false</code> otherwise
   */
  @Override
  public boolean isInsideSoftWrap(@NotNull VisualPosition visual) {
    return isInsideSoftWrap(visual, false);
  }

  /**
   * Allows to answer if given visual position points to soft wrap-introduced virtual space or points just before soft wrap.
   *
   * @param visual    target visual position to check
   * @return          <code>true</code> if given visual position points to soft wrap-introduced virtual space;
   *                  <code>false</code> otherwise
   */
  @Override
  public boolean isInsideOrBeforeSoftWrap(@NotNull VisualPosition visual) {
    return isInsideSoftWrap(visual, true);
  }

  private boolean isInsideSoftWrap(@NotNull VisualPosition visual, boolean countBeforeSoftWrap) {
    if (!isSoftWrappingEnabled()) {
      return false;
    }
    SoftWrapModel model = myEditor.getSoftWrapModel();
    if (!model.isSoftWrappingEnabled()) {
      return false;
    }
    LogicalPosition logical = myEditor.visualToLogicalPosition(visual);
    int offset = myEditor.logicalPositionToOffset(logical);
    if (offset <= 0) {
      // Never expect to be here, just a defensive programming.
      return false;
    }

    SoftWrap softWrap = model.getSoftWrap(offset);
    if (softWrap == null) {
      return false;
    }

    // We consider visual positions that point after the last symbol before soft wrap and the first symbol after soft wrap to not
    // belong to soft wrap-introduced virtual space.
    VisualPosition visualAfterSoftWrap = myEditor.offsetToVisualPosition(offset);
    if (visualAfterSoftWrap.line == visual.line && visualAfterSoftWrap.column <= visual.column) {
      return false;
    }

    VisualPosition visualBeforeSoftWrap = myEditor.offsetToVisualPosition(offset - 1);
    int x = 0;
    LogicalPosition logLineStart = myEditor.visualToLogicalPosition(new VisualPosition(visualBeforeSoftWrap.line, 0));
    if (logLineStart.softWrapLinesOnCurrentLogicalLine > 0) {
      int offsetLineStart = myEditor.logicalPositionToOffset(logLineStart);
      softWrap = model.getSoftWrap(offsetLineStart);
      if (softWrap != null) {
        x = softWrap.getIndentInPixels();
      }
    }
    int width = EditorUtil.textWidthInColumns(myEditor, myEditor.getDocument().getCharsSequence(), offset - 1, offset, x);
    int softWrapStartColumn = visualBeforeSoftWrap.column  + width;
    if (visual.line > visualBeforeSoftWrap.line) {
      return true;
    }
    return countBeforeSoftWrap ? visual.column >= softWrapStartColumn : visual.column > softWrapStartColumn;
  }

  @Override
  public void beforeDocumentChangeAtCaret() {
    CaretModel caretModel = myEditor.getCaretModel();
    VisualPosition visualCaretPosition = caretModel.getVisualPosition();
    if (!isInsideSoftWrap(visualCaretPosition)) {
      return;
    }

    SoftWrap softWrap = myStorage.getSoftWrap(caretModel.getOffset());
    if (softWrap == null) {
      return;
    }
    
    myEditor.getDocument().replaceString(softWrap.getStart(), softWrap.getEnd(), softWrap.getText());
    caretModel.moveToVisualPosition(visualCaretPosition);
  }

  @Override
  public void setPlace(@NotNull SoftWrapAppliancePlaces place) {
    myFoldBasedApplianceStrategy.setCurrentPlace(place);
  }

  @Override
  public boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener) {
    return myStorage.addSoftWrapChangeListener(listener);
  }

  public boolean addVisualSizeChangeListener(@NotNull VisualSizeChangeListener listener) {
    return myVisualSizeManager.addVisualSizeChangeListener(listener);
  }
  
  @Override
  public int getPriority() {
    return EditorDocumentPriorities.SOFT_WRAP_MODEL;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myUpdateInProgress = true;
    if (!isSoftWrappingEnabled()) {
      myDirty = true;
      return;
    }
    for (DocumentListener listener : myDocumentListeners) {
      listener.beforeDocumentChange(event);
    }
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    myUpdateInProgress = false;
    if (!isSoftWrappingEnabled()) {
      return;
    }
    for (DocumentListener listener : myDocumentListeners) {
      listener.documentChanged(event);
    }
  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    myUpdateInProgress = true;
    if (!isSoftWrappingEnabled() || !region.isValid()) {
      myDirty = true;
      return;
    }
    
    myDeferredFoldRegions.add(region);
  }

  @Override
  public void onFoldProcessingEnd() {
    myUpdateInProgress = false;
    if (!isSoftWrappingEnabled()) {
      return;
    }
    executeSafely(myFoldProcessingEndTask);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) {
      myDirty = true;
    }
  }

  @Override
  public void release() {
    myDataMapper.release();
    myApplianceManager.release();
    myStorage.removeAll();
    myDeferredFoldRegions.clear();
  }

  public SoftWrapApplianceManager getApplianceManager() {
    return myApplianceManager;
  }

  /**
   * We know that there are problems with incremental soft wraps cache update at the moment. Hence, we may implement full cache
   * reconstruction when the problem is encountered in order to avoid customer annoyance.
   * <p/>
   * However, the problems still should be fixed, hence, we report them only if dedicated flag is set.
   * <p/>
   * Current method encapsulates the logic mentioned above.
   *
   * @param task    command object that which execution may trigger incremental update of update soft wraps cache
   */
  @SuppressWarnings({"UseOfArchaicSystemPropertyAccessors"})
  private void executeSafely(SoftWrapAwareTask task) {
    try {
      task.run(true);
    } catch (Throwable e) {
      if (Boolean.getBoolean(DEBUG_PROPERTY_NAME)) {
        LOG.error(String.format(
          "Unexpected exception occurred during performing '%s'. Current soft wraps cache: %n"
          + "%s%nDocument:%n%s%nFold regions: %s",
          task, myDataMapper, myEditor.getDocument().getText(), Arrays.toString(myEditor.getFoldingModel().fetchTopLevel())), e);
      }
      myEditor.getFoldingModel().rebuild();
      myDataMapper.release();
      myApplianceManager.reset();
      myStorage.removeAll();
      try {
        task.run(true);
      }
      catch (Throwable e1) {
        LOG.error(String.format(
          "Can't perform %s even with complete soft wraps cache re-parsing. Current soft wraps cache: %n"
          + "%s. Document:%n%s%nFold regions: %s", task, myDataMapper, myEditor.getDocument().getText(),
          Arrays.toString(myEditor.getFoldingModel().fetchTopLevel())), e1
        );
        myEditor.getSettings().setUseSoftWraps(false);
        task.run(false);
      }
    }
  }

  @Override
  public String toString() {
    return myDataMapper.toString();
  }

  /**
   * Defines generic interface for the command that may be proceeded in both <code>'soft wraps aware'</code> and
   * <code>'soft wraps unaware'</code> modes.
   */
  private interface SoftWrapAwareTask {

    /**
     * Asks current task to do the job.
     * <p/>
     * It's assumed that input data (if any) is already stored at the task object. Processing result (if any) is assumed
     * to be stored there as well for further retrieval in implementation-specific manner.
     *
     * @param softWrapAware             flag that indicates if soft wraps-aware processing should be performed
     * @throws IllegalStateException    in case of inability to do the job
     */
    void run(boolean softWrapAware) throws IllegalStateException;
  }

  private class OffsetToLogicalTask implements SoftWrapAwareTask {

    public int             input;
    public LogicalPosition output;

    @Override
    public void run(boolean softWrapAware) throws IllegalStateException {
      if (softWrapAware) {
        output = myDataMapper.offsetToLogicalPosition(input);
      }
      else {
        output = myEditor.offsetToLogicalPosition(input, false);
      }
    }

    @Override
    public String toString() {
      return "mapping from offset (" + input + ") to logical position";
    }
  }

  private class VisualToLogicalTask implements SoftWrapAwareTask {

    public VisualPosition  input;
    public LogicalPosition output;

    @Override
    public void run(boolean softWrapAware) throws IllegalStateException {
      if (softWrapAware) {
        output = myDataMapper.visualToLogical(input);
      }
      else {
        output = myEditor.visualToLogicalPosition(input, false);
      }
    }

    @Override
    public String toString() {
      return "mapping from visual position (" + input + ") to logical position";
    }
  }

  private class LogicalToVisualTask implements SoftWrapAwareTask {

    public LogicalPosition input;
    public VisualPosition  defaultOutput;
    public VisualPosition  output;

    @Override
    public void run(boolean softWrapAware) throws IllegalStateException {
      output = softWrapAware ? myDataMapper.logicalToVisualPosition(input, defaultOutput) : defaultOutput;
    }

    @Override
    public String toString() {
      return "mapping from logical position (" + input + ") to visual position";
    }
  }
  
  private class FoldProcessingEndTask implements SoftWrapAwareTask {
    @Override
    public void run(boolean softWrapAware) {
      if (!softWrapAware) {
        return;
      }

      try {
        for (FoldRegion region : myDeferredFoldRegions) {
          // There is a possible case that given fold region is contained inside another collapsed fold region. We don't want to process
          // such nested region then.
          FoldRegion outerRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(region.getStartOffset());
          if (outerRegion != null && outerRegion != region && outerRegion.getStartOffset() <= region.getStartOffset()
              && outerRegion.getEndOffset() >= region.getEndOffset())
          {
            continue;
          }
        
          for (FoldingListener listener : myFoldListeners) {
            listener.onFoldRegionStateChange(region);
          }
        }
      }
      finally {
        myDeferredFoldRegions.clear();
      }
      
      for (FoldingListener listener : myFoldListeners) {
        listener.onFoldProcessingEnd();
      }
    }

    @Override
    public String toString() {
      return "fold regions state change processing";
    }
  }
}
