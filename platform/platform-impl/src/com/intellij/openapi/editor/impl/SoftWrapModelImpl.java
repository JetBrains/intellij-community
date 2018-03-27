/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.editor.impl.softwrap.mapping.CachingSoftWrapDataMapper;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapAwareDocumentParsingListenerAdapter;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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
 * Works as a mix of {@code GoF Facade and Bridge}, i.e. delegates the processing to the target sub-components and provides
 * utility methods built on top of sub-components API.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 12:47:32 PM
 */
public class SoftWrapModelImpl extends InlayModel.SimpleAdapter
  implements SoftWrapModelEx, PrioritizedInternalDocumentListener, FoldingListener,
             PropertyChangeListener, Dumpable, Disposable
{

  private static final Logger LOG = Logger.getInstance(SoftWrapModelImpl.class);

  private final List<SoftWrapChangeListener>  mySoftWrapListeners = new ArrayList<>();
  
  /**
   * There is a possible case that particular activity performs batch fold regions operations (addition, removal etc).
   * We don't want to process them at the same time we get notifications about that because there is a big chance that
   * we see inconsistent state (e.g. there was a problem with {@link FoldingModel#getCollapsedRegionAtOffset(int)} because that
   * method uses caching internally and cached data becomes inconsistent if, for example, the top region is removed).
   * <p/>
   * So, our strategy is to collect information about changed fold regions and process it only when batch folding processing ends.
   */
  private final List<Segment> myDeferredFoldRegions = new ArrayList<>();

  private final CachingSoftWrapDataMapper          myDataMapper;
  private final SoftWrapsStorage                   myStorage;
  private       SoftWrapPainter                    myPainter;
  private final SoftWrapApplianceManager           myApplianceManager;
  private       EditorTextRepresentationHelper     myEditorTextRepresentationHelper;

  @NotNull
  private final EditorImpl myEditor;

  private boolean myUseSoftWraps;
  private int myTabWidth = -1;
  private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();

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
  
  private boolean myBulkUpdateInProgress;

  /**
   * There is a possible case that target document is changed while its editor is inactive (e.g. user opens two editors for classes
   * {@code 'Part'} and {@code 'Whole'}; activates editor for the class {@code 'Whole'} and performs 'rename class'
   * for {@code 'Part'} from it). Soft wraps cache is not recalculated during that because corresponding editor is not shown
   * and we lack information about visible area width. Hence, we will need to recalculate the whole soft wraps cache as soon
   * as target editor becomes visible.
   * <p/>
   * Current field serves as a flag for that {@code 'dirty document, need complete soft wraps cache recalculation'} state.
   */
  private boolean myDirty;
  
  private boolean myForceAdditionalColumns;

  SoftWrapModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myStorage = new SoftWrapsStorage();
    myPainter = new CompositeSoftWrapPainter(editor);
    myEditorTextRepresentationHelper = new DefaultEditorTextRepresentationHelper(editor);
    myDataMapper = new CachingSoftWrapDataMapper(editor, myStorage);
    myApplianceManager = new SoftWrapApplianceManager(myStorage, editor, myPainter, myDataMapper);

    myApplianceManager.addListener(new SoftWrapAwareDocumentParsingListenerAdapter() {
      @Override
      public void recalculationEnds() {
        for (SoftWrapChangeListener listener : mySoftWrapListeners) {
          listener.recalculationEnds();
        }
      }
    });
    myUseSoftWraps = areSoftWrapsEnabledInEditor();
    myEditor.getColorsScheme().getFontPreferences().copyTo(myFontPreferences);
    
    editor.addPropertyChangeListener(this, this);

    myApplianceManager.addListener(myDataMapper);
    myEditor.getInlayModel().addListener(this, this);
  }

  private boolean areSoftWrapsEnabledInEditor() {
    return myEditor.getSettings().isUseSoftWraps() && !myEditor.isOneLineMode() &&
           (!(myEditor.getDocument() instanceof DocumentImpl) || !((DocumentImpl)myEditor.getDocument()).acceptsSlashR());
  }

  /**
   * Called on editor settings change. Current model is expected to drop all cached information about the settings if any.
   */
  public void reinitSettings() {
    boolean softWrapsUsedBefore = myUseSoftWraps;
    myUseSoftWraps = areSoftWrapsEnabledInEditor();

    int tabWidthBefore = myTabWidth;
    myTabWidth = EditorUtil.getTabSize(myEditor);

    boolean fontsChanged = false;
    if (!myFontPreferences.equals(myEditor.getColorsScheme().getFontPreferences())
        && myEditorTextRepresentationHelper instanceof DefaultEditorTextRepresentationHelper) {
      fontsChanged = true;
      myEditor.getColorsScheme().getFontPreferences().copyTo(myFontPreferences);
      ((DefaultEditorTextRepresentationHelper)myEditorTextRepresentationHelper).clearSymbolWidthCache();
      myPainter.reinit();
    }
    
    if (myUseSoftWraps != softWrapsUsedBefore || tabWidthBefore >= 0 && myTabWidth != tabWidthBefore || fontsChanged) {
      myApplianceManager.reset();
      myDeferredFoldRegions.clear();
      myStorage.removeAll();
      myEditor.myView.reinitSettings();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  @Override
  public boolean isRespectAdditionalColumns() {
    return myForceAdditionalColumns;
  }

  @Override
  public void forceAdditionalColumnsUsage() {
    myForceAdditionalColumns = true; 
  }

  @Override
  public boolean isSoftWrappingEnabled() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myUseSoftWraps && !myEditor.isPurePaintingMode();
  }

  @Override
  @Nullable
  public SoftWrap getSoftWrap(int offset) {
    if (!isSoftWrappingEnabled()) {
      return null;
    }
    return myStorage.getSoftWrap(offset);
  }

  @Override
  public int getSoftWrapIndex(int offset) {
    if (!isSoftWrappingEnabled()) {
      return -1;
    }
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
    if (documentLine >= document.getLineCount()) {
      return Collections.emptyList();
    } 
    int start = document.getLineStartOffset(documentLine);
    int end = document.getLineEndOffset(documentLine);
    return getSoftWrapsForRange(start, end + 1/* it's theoretically possible that soft wrap is registered just before the line feed,
                                               * hence, we add '1' here assuming that end line offset points to line feed symbol */
    );
  }

  /**
   * @return    total number of soft wrap-introduced new visual lines
   */
  int getSoftWrapsIntroducedLinesNumber() {
    return myStorage.getSoftWraps().size(); // Assuming that soft wrap has single line feed all the time
  }

  @Override
  public List<? extends SoftWrap> getRegisteredSoftWraps() {
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    List<SoftWrapImpl> softWraps = myStorage.getSoftWraps();
    if (!softWraps.isEmpty() && softWraps.get(softWraps.size() - 1).getStart() >= myEditor.getDocument().getTextLength()) {
      LOG.error("Unexpected soft wrap location", new Attachment("editorState.txt", myEditor.dumpState()));
    }
    return softWraps;
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
    if (!myEditor.getSettings().isAllSoftWrapsShown()) {
      int visualLine = y / lineHeight;
      LogicalPosition position = myEditor.visualToLogicalPosition(new VisualPosition(visualLine, 0));
      if (position.line != myEditor.getCaretModel().getLogicalPosition().line) {
        return myPainter.getDrawingHorizontalOffset(g, drawingType, x, y, lineHeight);
      }
    }
    return doPaint(g, drawingType, x, y, lineHeight);
  }
  
  public int doPaint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    return myPainter.paint(g, drawingType, x, y, lineHeight);
  }

  @Override
  public int getMinDrawingWidthInPixels(@NotNull SoftWrapDrawingType drawingType) {
    return myPainter.getMinDrawingWidth(drawingType);
  }

  /**
   * Encapsulates preparations for performing document dimension mapping (e.g. visual to logical position) and answers
   * if soft wraps-aware processing should be used (e.g. there is no need to consider soft wraps if user configured them
   * not to be used).
   */
  public void prepareToMapping() {
    if (myUpdateInProgress || myBulkUpdateInProgress || !isSoftWrappingEnabled()) {
      return;
    }

    if (myDirty) {
      myStorage.removeAll();
      myApplianceManager.reset();
      myDeferredFoldRegions.clear();
      myDirty = false;
    }

    myApplianceManager.recalculateIfNecessary();
  }

  /**
   * Allows to answer if given visual position points to soft wrap-introduced virtual space.
   *
   * @param visual    target visual position to check
   * @return          {@code true} if given visual position points to soft wrap-introduced virtual space;
   *                  {@code false} otherwise
   */
  @Override
  public boolean isInsideSoftWrap(@NotNull VisualPosition visual) {
    return isInsideSoftWrap(visual, false);
  }

  /**
   * Allows to answer if given visual position points to soft wrap-introduced virtual space or points just before soft wrap.
   *
   * @param visual    target visual position to check
   * @return          {@code true} if given visual position points to soft wrap-introduced virtual space;
   *                  {@code false} otherwise
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

    VisualPosition beforeSoftWrap = myEditor.offsetToVisualPosition(offset, true, true);
    return visual.line > beforeSoftWrap.line ||
           visual.column > beforeSoftWrap.column || visual.column == beforeSoftWrap.column && countBeforeSoftWrap;
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
  public boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener) {
    mySoftWrapListeners.add(listener);
    return myStorage.addSoftWrapChangeListener(listener);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.SOFT_WRAP_MODEL;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    if (myBulkUpdateInProgress) {
      return;
    }
    myUpdateInProgress = true;
    if (!isSoftWrappingEnabled()) {
      myDirty = true;
      return;
    }
    myApplianceManager.beforeDocumentChange(event);
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    if (myBulkUpdateInProgress) {
      return;
    }
    myUpdateInProgress = false;
    if (!isSoftWrappingEnabled()) {
      return;
    }
    myApplianceManager.documentChanged(event);
  }

  @Override
  public void moveTextHappened(int start, int end, int base) {
    if (myBulkUpdateInProgress) {
      return;
    }
    if (!isSoftWrappingEnabled()) {
      myDirty = true;
      return;
    }
    myApplianceManager.recalculate(Arrays.asList(new TextRange(start, end), new TextRange(base, base + end - start)));
  }

  void onBulkDocumentUpdateStarted() {
    myBulkUpdateInProgress = true;
  }

  void onBulkDocumentUpdateFinished() {
    myBulkUpdateInProgress = false;
    recalculate();
  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    myUpdateInProgress = true;
    if (!isSoftWrappingEnabled() || !region.isValid()) {
      myDirty = true;
      return;
    }

    // We delay processing of changed fold regions till the invocation of onFoldProcessingEnd(), as
    // FoldingModel can return inconsistent data before that moment.
    myDeferredFoldRegions.add(TextRange.create(region)); // copy because region can become invalid later
  }

  @Override
  public void onFoldProcessingEnd() {
    myUpdateInProgress = false;
    if (!isSoftWrappingEnabled()) {
      return;
    }
    try {
      if (!myDirty) { // no need to recalculate specific areas if the whole document will be reprocessed
        myApplianceManager.recalculate(myDeferredFoldRegions);
      }
    }
    finally {
      myDeferredFoldRegions.clear();
    }
  }

  @Override
  public void onUpdated(@NotNull Inlay inlay) {
    if (myEditor.getDocument().isInEventsHandling() || myEditor.getDocument().isInBulkUpdate()) return;
    if (!isSoftWrappingEnabled()) {
      myDirty = true;
      return;
    }
    if (!myDirty) {
      int offset = inlay.getOffset();
      myApplianceManager.recalculate(Collections.singletonList(new TextRange(offset, offset)));
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) {
      myDirty = true;
    }
  }

  @Override
  public void dispose() {
    release();
  }

  @Override
  public void release() {
    myApplianceManager.release();
    myStorage.removeAll();
    myDeferredFoldRegions.clear();
  }

  void recalculate() {
    if (!isSoftWrappingEnabled()) {
      myDirty = true;
      return;
    }
    myApplianceManager.reset();
    myStorage.removeAll();
    myDeferredFoldRegions.clear();
    myApplianceManager.recalculateIfNecessary();
  }

  public SoftWrapApplianceManager getApplianceManager() {
    return myApplianceManager;
  }

  @TestOnly
  public void setSoftWrapPainter(SoftWrapPainter painter) {
    myPainter = painter;
    myApplianceManager.setSoftWrapPainter(painter);
  }

  public static EditorTextRepresentationHelper getEditorTextRepresentationHelper(@NotNull Editor editor) {
    return ((SoftWrapModelEx)editor.getSoftWrapModel()).getEditorTextRepresentationHelper();
  }

  @Override
  public EditorTextRepresentationHelper getEditorTextRepresentationHelper() {
    return myEditorTextRepresentationHelper;
  }

  @TestOnly
  public void setEditorTextRepresentationHelper(EditorTextRepresentationHelper editorTextRepresentationHelper) {
    myEditorTextRepresentationHelper = editorTextRepresentationHelper;
    myApplianceManager.reset();
  }

  @NotNull
  @Override
  public String dumpState() {
    return String.format("\nuse soft wraps: %b, tab width: %d, additional columns: %b, " +
                         "update in progress: %b, bulk update in progress: %b, dirty: %b, deferred regions: %s" +
                         "\nappliance manager state: %s\nsoft wraps mapping info: %s\nsoft wraps: %s",
                         myUseSoftWraps, myTabWidth, myForceAdditionalColumns, myUpdateInProgress, myBulkUpdateInProgress,
                         myDirty, myDeferredFoldRegions.toString(),
                         myApplianceManager.dumpState(), myDataMapper.dumpState(), myStorage.dumpState());
  }

  @Override
  public String toString() {
    return dumpState();
  }

  public boolean isDirty() {
    return myUseSoftWraps && myDirty;
  }

  @TestOnly
  void validateState() {
    Document document = myEditor.getDocument();
    if (myEditor.getDocument().isInBulkUpdate()) return;
    FoldingModel foldingModel = myEditor.getFoldingModel();
    List<? extends SoftWrap> softWraps = getRegisteredSoftWraps();
    int lastSoftWrapOffset = -1;
    for (SoftWrap wrap : softWraps) {
      int softWrapOffset = wrap.getStart();
      LOG.assertTrue(softWrapOffset > lastSoftWrapOffset, "Soft wraps are not ordered");
      LOG.assertTrue(softWrapOffset < document.getTextLength(), "Soft wrap is after document's end");
      FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(softWrapOffset);
      LOG.assertTrue(foldRegion == null || foldRegion.getStartOffset() == softWrapOffset, "Soft wrap is inside fold region");
      LOG.assertTrue(softWrapOffset != DocumentUtil.getLineEndOffset(softWrapOffset, document)
                     || foldRegion != null, "Soft wrap before line break");
      LOG.assertTrue(softWrapOffset != DocumentUtil.getLineStartOffset(softWrapOffset, document) ||
                     foldingModel.isOffsetCollapsed(softWrapOffset - 1), "Soft wrap after line break");
      LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(document, softWrapOffset), "Soft wrap inside a surrogate pair");
      lastSoftWrapOffset = softWrapOffset;
    }
  }
}
