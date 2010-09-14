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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.editor.impl.softwrap.mapping.CachingSoftWrapDataMapper;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
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
public class SoftWrapModelImpl implements SoftWrapModelEx, PrioritizedDocumentListener, FoldingListener {

  /** Upper boundary of time interval to check editor settings. */
  private static final long EDITOR_SETTINGS_CHECK_PERIOD_MILLIS = 10000;

  private final List<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private final List<FoldingListener> myFoldListeners = new ArrayList<FoldingListener>();

  private final CachingSoftWrapDataMapper myDataMapper;
  private final SoftWrapsStorage              myStorage;
  private final SoftWrapPainter               myPainter;
  private final SoftWrapApplianceManager      myApplianceManager;

  private final EditorEx myEditor;
  /** Holds number of 'active' calls, i.e. number of methods calls of the current object within the current call stack. */
  private int myActive;
  /** Holds timestamp of the last editor settings check. */
  private long myLastSettingsCheckTimeMillis;
  private boolean myLastUseSoftWraps;

  public SoftWrapModelImpl(@NotNull EditorEx editor) {
    this(editor, new SoftWrapsStorage(), new CompositeSoftWrapPainter(editor));
  }

  public SoftWrapModelImpl(@NotNull final EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter) {
    this(editor, storage, painter, new DefaultEditorTextRepresentationHelper(editor));
  }

  public SoftWrapModelImpl(@NotNull final EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter,
                           EditorTextRepresentationHelper representationHelper) {
    this(
      editor, storage, painter, new SoftWrapApplianceManager(storage, editor, painter, representationHelper),
      new CachingSoftWrapDataMapper(editor, storage, representationHelper)
    );
    myApplianceManager.addListener(myDataMapper);
  }

  public SoftWrapModelImpl(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter,
                           @NotNull SoftWrapApplianceManager applianceManager, @NotNull CachingSoftWrapDataMapper dataMapper)
  {
    myEditor = editor;
    myStorage = storage;
    myPainter = painter;
    myApplianceManager = applianceManager;
    myDataMapper = dataMapper;

    myDocumentListeners.add(myApplianceManager);
    myFoldListeners.add(myApplianceManager);
  }

  public boolean isSoftWrappingEnabled() {
    if (myEditor.isOneLineMode()) {
      return false;
    }

    // Profiling shows that editor settings lookup have impact at overall performance if called often.
    // Hence, we cache value used last time.
    if (System.currentTimeMillis() - myLastSettingsCheckTimeMillis <= EDITOR_SETTINGS_CHECK_PERIOD_MILLIS) {
      return myLastUseSoftWraps;
    }
    myLastSettingsCheckTimeMillis = System.currentTimeMillis();
    return myLastUseSoftWraps = myEditor.getSettings().isUseSoftWraps();
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
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    int startIndex = myStorage.getSoftWrapIndex(start);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }

    List<? extends SoftWrap> softWraps = myStorage.getSoftWraps();
    if (startIndex >= softWraps.size()) {
      return Collections.emptyList();
    }

    int endIndex = myStorage.getSoftWrapIndex(end);
    if (endIndex < 0) {
      endIndex = -endIndex - 1;
    }
    endIndex = Math.min(softWraps.size(), endIndex);

    if (endIndex > startIndex) {
      return softWraps.subList(startIndex, endIndex);
    }
    else {
      return Collections.emptyList();
    }
  }

  @Override
  @NotNull
  public List<? extends SoftWrap> getSoftWrapsForLine(int documentLine) {
    if (!isSoftWrappingEnabled()) {
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

  //TODO den add doc
  public void registerSoftWrapsIfNecessary(@NotNull Rectangle clip, int startOffset) {
    if (!isSoftWrappingEnabled()) {
      return;
    }

    myActive++;
    try {
      myApplianceManager.registerSoftWrapIfNecessary(clip, startOffset);
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
    if (!foldingModel.isOffsetCollapsed(start)) {
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
    if (!prepareToMapping()) {
      return myEditor.visualToLogicalPosition(visual, false);
    }
    myActive++;
    try {
      return myDataMapper.visualToLogical(visual);
    } finally {
      myActive--;
    }
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset) {
    if (!prepareToMapping()) {
      return myEditor.offsetToLogicalPosition(offset, false);
    }
    myActive++;
    try {
      return myDataMapper.offsetToLogicalPosition(offset);
    } finally {
      myActive--;
    }
  }

  @NotNull
  public LogicalPosition adjustLogicalPosition(LogicalPosition defaultLogical, int offset) {
    if (!prepareToMapping()) {
      return defaultLogical;
    }

    myActive++;
    try {
      return myDataMapper.offsetToLogicalPosition(offset);
    } finally {
      myActive--;
    }
  }

  @NotNull
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual) {
    if (!prepareToMapping()) {
      return defaultVisual;
    }

    myActive++;
    try {
      return myDataMapper.logicalToVisualPosition(logical, defaultVisual);
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
    boolean useSoftWraps = myActive <= 0 && isSoftWrappingEnabled() && !myStorage.isEmpty() && myEditor.getDocument().getTextLength() > 0;
    if (!useSoftWraps) {
      return useSoftWraps;
    }

    myApplianceManager.dropDataIfNecessary();
    return true;
    //
    //Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    //if (visibleArea.width <= 0) {
    //  // We don't know visible area width, hence, can't calculate soft wraps positions.
    //  return false;
    //}
    //
    //myActive++;
    //try {
    //  LogicalPosition logicalPosition = myEditor.xyToLogicalPosition(visibleArea.getLocation());
    //  int offset = myEditor.logicalPositionToOffset(logicalPosition);
    //  myApplianceManager.registerSoftWrapIfNecessary(visibleArea, offset);
    //  return true;
    //}
    //finally {
    //  myActive--;
    //}
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
    //TODO den implement
    //if (myDocumentChangeManager.makeHardWrap(caretModel.getOffset())) {
    //  // Restore caret position.
    //  caretModel.moveToVisualPosition(visualCaretPosition);
    //}
  }

  @Override
  public boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener) {
    return myStorage.addSoftWrapChangeListener(listener);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.SOFT_WRAP_MODEL;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    if (!isSoftWrappingEnabled()) {
      return;
    }
    for (DocumentListener listener : myDocumentListeners) {
      listener.beforeDocumentChange(event);
    }
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    if (!isSoftWrappingEnabled()) {
      return;
    }
    for (DocumentListener listener : myDocumentListeners) {
      listener.documentChanged(event);
    }
  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    if (!isSoftWrappingEnabled()) {
      return;
    }
    for (FoldingListener listener : myFoldListeners) {
      listener.onFoldRegionStateChange(region);
    }
  }

  @Override
  public void onFoldProcessingEnd() {
    if (!isSoftWrappingEnabled()) {
      return;
    }
    for (FoldingListener listener : myFoldListeners) {
      listener.onFoldProcessingEnd();
    }
  }

  @Override
  public void release() {
    myDataMapper.release();
    myApplianceManager.release();
    myStorage.removeAll();
  }
}
