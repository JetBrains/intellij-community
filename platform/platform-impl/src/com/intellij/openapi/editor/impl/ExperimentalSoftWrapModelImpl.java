// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.CustomWrap;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.SoftWrapChangeListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.CompositeSoftWrapPainter;
import com.intellij.openapi.editor.impl.softwrap.CustomWrapOnlyRecalculationManager;
import com.intellij.openapi.editor.impl.softwrap.CustomWrapToSoftWrapAdapter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapNotifier;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapRecalculationManager;
import com.intellij.openapi.editor.impl.softwrap.SoftWrappingEnabledRecalculationManager;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapParsingListener;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.Graphics;
import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.List;

/**
 * Soft wrap model implementation supporting custom soft wrap feature (IJPL-156498).
 */
@ApiStatus.Internal
public final class ExperimentalSoftWrapModelImpl extends SoftWrapModelImpl {

  private static final Logger LOG = Logger.getInstance(ExperimentalSoftWrapModelImpl.class);

  private final SoftWrapNotifier mySoftWrapNotifier;

  /**
   * This model operates in two main modes: soft-wrapping is either on or off.
   * <p>
   * In both modes, custom wraps need to be managed.
   * It is the job of {@link SoftWrapRecalculationManager} to recalculate soft and/or custom wraps
   * in reaction to events in other editor models.
   * <p>
   * {@code myRecalculationManager} is kept in sync with {@link #myUseSoftWraps}.
   */
  private SoftWrapRecalculationManager myRecalculationManager;
  private final SoftWrappingEnabledRecalculationManager mySoftWrappingRecalculationManager;
  private final CustomWrapOnlyRecalculationManager myCustomWrapOnlyRecalculationManager;

  private final SoftWrapsStorage storage;
  private       SoftWrapPainter                    myPainter;

  private final @NotNull EditorImpl editor;
  private final @NotNull DocumentEx document;

  private boolean myUseSoftWraps;
  private int myTabWidth = -1;
  private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();

  private boolean myForceAdditionalColumns;
  private boolean myBulkDocumentUpdateInProgress;

  ExperimentalSoftWrapModelImpl(@NotNull EditorImpl editor) {
    this.editor = editor;
    document = editor.getElfDocument();
    storage = new SoftWrapsStorage();
    mySoftWrapNotifier = new SoftWrapNotifier();
    myPainter = new CompositeSoftWrapPainter(editor);
    mySoftWrappingRecalculationManager = new SoftWrappingEnabledRecalculationManager(editor, storage, myPainter, mySoftWrapNotifier,
                                                                                     () -> myBulkDocumentUpdateInProgress);
    myCustomWrapOnlyRecalculationManager = new CustomWrapOnlyRecalculationManager(editor, storage, mySoftWrapNotifier,
                                                                                  () -> myBulkDocumentUpdateInProgress);

    mySoftWrapNotifier.addSoftWrapParsingListener(new SoftWrapParsingListener() {
      @Override
      public void onAllDirtyRegionsReparsed() {
        mySoftWrapNotifier.notifySoftWrapRecalculationEnds();
      }
    });

    if (!editor.getSettings().isUseSoftWraps() && shouldSoftWrapsBeForced()) {
      forceSoftWraps();
    }

    myUseSoftWraps = areSoftWrapsEnabledInEditor();
    reinitRecalculationManager();

    this.editor.getColorsScheme().getFontPreferences().copyTo(myFontPreferences);

    editor.addPropertyChangeListener(this, this);

    this.editor.getInlayModel().addListener(this, this);
  }

  private void reinitRecalculationManager() {
    if (myRecalculationManager instanceof SoftWrappingEnabledRecalculationManager manager) {
      mySoftWrapNotifier.removeSoftWrapParsingListener(manager.myDataMapper);
    }
    myRecalculationManager = myUseSoftWraps ? mySoftWrappingRecalculationManager : myCustomWrapOnlyRecalculationManager;
    if (myRecalculationManager instanceof SoftWrappingEnabledRecalculationManager manager) {
      // CachingSoftWrapDataMapper must be the first to run
      mySoftWrapNotifier.addFirstSoftWrapParsingListener(manager.myDataMapper);
    }
  }


  private void forceSoftWraps() {
    EditorSettings editorSettings = editor.getSettings();

    if (editorSettings instanceof SettingsImpl) {
      ((SettingsImpl)editorSettings).setUseSoftWrapsQuiet();
    }
    else {
      LOG.error(new IllegalStateException("Unexpected implementation class of editor settings: " +
                                          "class=" + editorSettings.getClass() + "editor=" + editor));
    }

    editor.putUserData(EditorImpl.FORCED_SOFT_WRAPS, Boolean.TRUE);
    myUseSoftWraps = areSoftWrapsEnabledInEditor();
    reinitRecalculationManager();
    Project project = editor.getProject();
    VirtualFile file = editor.getVirtualFile();
    if (project != null && file != null) {
      EditorNotifications.getInstance(project).updateNotifications(file);
    }
    ApplicationManager.getApplication().invokeLater(() -> ActivityTracker.getInstance().inc());
  }

  @Override
  @ApiStatus.Internal
  public boolean shouldSoftWrapsBeForced() {
    return shouldSoftWrapsBeForced(null);
  }

  private boolean shouldSoftWrapsBeForced(@Nullable DocumentEvent event) {
    if (Boolean.FALSE.equals(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS))) {
      return false;
    }
    Project project = editor.getProject();
    if (project != null && project.isDisposed()) {
      // TODO: investigate why it happens leading to IJPL-164636
      String isEditorDisposed = editor.isDisposed() ? " (disposed)" : " (not disposed)";
      editor.throwDisposalError(
        editor + isEditorDisposed +  " tries to update soft wraps while project is already disposed " + project
      );
      return false;
    }
    if (project != null && PostprocessReformattingAspect.getInstance(project).isDocumentLocked(editor.getDocument())) {
      // Disable checking for files in intermediate states - e.g., for files during refactoring.
      return false;
    }
    int lineWidthLimit = AdvancedSettings.getInt("editor.soft.wrap.force.limit");
    int startLine = event == null ? 0 : document.getLineNumber(event.getOffset());
    int endLine = event == null ? document.getLineCount() - 1 : document.getLineNumber(event.getOffset() + event.getNewLength());
    for (int i = startLine; i <= endLine; i++) {
      if (document.getLineEndOffset(i) - document.getLineStartOffset(i) > lineWidthLimit) {
        return true;
      }
    }
    return false;
  }

  private boolean areSoftWrapsEnabledInEditor() {
    return editor.getSettings().isUseSoftWraps() && !editor.isOneLineMode();
  }

  /**
   * Called on editor settings change. Current model is expected to drop all cached information about the settings if any.
   */
  @Override
  @ApiStatus.Internal
  public void reinitSettings() {
    boolean softWrapsUsedBefore = myUseSoftWraps;
    myUseSoftWraps = areSoftWrapsEnabledInEditor();

    int tabWidthBefore = myTabWidth;
    myTabWidth = EditorUtil.getTabSize(editor);
    boolean tabWidthChanged = tabWidthBefore >= 0 && myTabWidth != tabWidthBefore;

    boolean fontsChanged = false;
    if (!myFontPreferences.equals(editor.getColorsScheme().getFontPreferences())) {
      fontsChanged = true;
      editor.getColorsScheme().getFontPreferences().copyTo(myFontPreferences);
      myPainter.reinit();
    }

    boolean needsReinit = false;
    if (myUseSoftWraps != softWrapsUsedBefore) {
      needsReinit = true;
    }
    else if (myRecalculationManager.isResetNeeded(tabWidthChanged, fontsChanged)) {
      needsReinit = true;
    }
    if (needsReinit) {
      myRecalculationManager.reset();
      reinitRecalculationManager();
      storage.removeAll();
      mySoftWrapNotifier.notifySoftWrapsChanged();
      editor.myView.reinitSettings();
      if (editor.myAdView != null) editor.myView.reinitSettings();
      if (AsyncEditorLoader.isEditorLoaded(editor)) {
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
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
    EditorThreading.assertInteractionAllowed();
    return myUseSoftWraps && !editor.isPurePaintingMode();
  }

  @Override
  @ApiStatus.Internal
  public @Nullable SoftWrapEx getSoftWrapEx(int offset) {
    if (!isSoftWrappingEnabled() && !editor.getCustomWrapModel().hasWraps()) {
      return null;
    }
    return storage.getSoftWrap(offset);
  }

  @Override
  public int getSoftWrapIndex(int offset) {
    if (!isSoftWrappingEnabled() && !editor.getCustomWrapModel().hasWraps()) {
      return -1;
    }
    return storage.getSoftWrapIndex(offset);
  }

  @Override
  public @NotNull List<? extends SoftWrap> getSoftWrapsForRange(int start, int end) {
    if (!isSoftWrappingEnabled() && !editor.getCustomWrapModel().hasWraps() || end < start) {
      return Collections.emptyList();
    }

    List<? extends SoftWrap> softWraps = storage.getSoftWraps();

    int startIndex = storage.getSoftWrapIndex(start);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
      if (startIndex >= softWraps.size() || softWraps.get(startIndex).getStart() > end) {
        return Collections.emptyList();
      }
    }

    int endIndex = storage.getSoftWrapIndex(end);
    if (endIndex >= 0) {
      return softWraps.subList(startIndex, endIndex + 1);
    }
    else {
      endIndex = -endIndex - 1;
      return softWraps.subList(startIndex, endIndex);
    }
  }

  @Override
  public @NotNull List<? extends SoftWrap> getSoftWrapsForLine(int documentLine) {
    if (!isSoftWrappingEnabled() && !editor.getCustomWrapModel().hasWraps() || documentLine < 0) {
      return Collections.emptyList();
    }
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
  @Override
  @ApiStatus.Internal
  public int getSoftWrapsIntroducedLinesNumber() {
    prepareToMapping();
    return storage.getSoftWraps().size(); // Assuming that soft wrap has single line feed all the time
  }

  @Override
  @ApiStatus.Internal
  public @NotNull List<? extends SoftWrapEx> getRegisteredSoftWrapsEx() {
    if (!isSoftWrappingEnabled() && !editor.getCustomWrapModel().hasWraps()) {
      return Collections.emptyList();
    }
    List<? extends SoftWrapEx> softWraps = storage.getSoftWraps();
    if (!softWraps.isEmpty() && softWraps.getLast().getStart() >= document.getTextLength()) {
      LOG.error("Unexpected soft wrap location", new Attachment("editorState.txt", editor.dumpState()));
    }
    return softWraps;
  }

  @Override
  public boolean isVisible(SoftWrap softWrap) {
    FoldingModel foldingModel = editor.getFoldingModel();
    int start = softWrap.getStart();
    if (foldingModel.isOffsetCollapsed(start)) {
      return false;
    }

    // There is a possible case that soft wrap and collapsed folding region share the same offset, i.e., soft wrap is represented
    // before the folding. We need to return 'true' in such situation. Hence, we check if offset just before the soft wrap
    // is collapsed as well.
    return start <= 0 || !foldingModel.isOffsetCollapsed(start - 1);
  }

  @Override
  public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    if (!isSoftWrappingEnabled() || !editor.getSettings().isPaintSoftWraps()) {
      return 0;
    }
    if (!editor.getSettings().isAllSoftWrapsShown()) {
      int visualLine = y / lineHeight;
      LogicalPosition position = editor.visualToLogicalPosition(new VisualPosition(visualLine, 0));
      if (position.line != editor.getCaretModel().getLogicalPosition().line) {
        return myPainter.getDrawingHorizontalOffset(g, drawingType, x, y, lineHeight);
      }
    }
    return doPaint(g, drawingType, x, y, lineHeight);
  }

  @Override
  @ApiStatus.Internal
  public int doPaint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    if (!editor.getSettings().isPaintSoftWraps()) {
      return 0;
    }
    return myPainter.paint(g, drawingType, x, y, lineHeight);
  }

  @Override
  public int getMinDrawingWidthInPixels(@NotNull SoftWrapDrawingType drawingType) {
    return myPainter.getMinDrawingWidth(drawingType);
  }

  /**
   * Encapsulates preparations for performing document dimension mapping (e.g., visual to logical position) and answers
   * if soft wraps-aware processing should be used (e.g., there is no need to consider soft wraps if user configured them
   * not to be used).
   */
  @Override
  @ApiStatus.Internal
  public void prepareToMapping() {
    myRecalculationManager.prepareToMapping();
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
    if (!isSoftWrappingEnabled() && !editor.getCustomWrapModel().hasWraps()) {
      return false;
    }
    int offset = editor.visualPositionToOffset(visual);
    if (offset <= 0) {
      // Never expect to be here, just a defensive programming.
      return false;
    }

    SoftWrap softWrap = getSoftWrap(offset);
    if (softWrap == null) {
      return false;
    }

    // We consider visual positions that point after the last symbol before soft wrap and the first symbol after soft wrap to not
    // belong to soft wrap-introduced virtual space.
    VisualPosition visualAfterSoftWrap = editor.offsetToVisualPosition(offset);
    if (visualAfterSoftWrap.line == visual.line && visualAfterSoftWrap.column <= visual.column) {
      return false;
    }

    VisualPosition beforeSoftWrap = editor.offsetToVisualPosition(offset, true, true);
    return visual.line > beforeSoftWrap.line ||
           visual.column > beforeSoftWrap.column || visual.column == beforeSoftWrap.column && countBeforeSoftWrap;
  }

  @Override
  public void beforeDocumentChangeAtCaret() {
    CaretModel caretModel = editor.getCaretModel();
    VisualPosition visualCaretPosition = caretModel.getVisualPosition();
    if (!isInsideSoftWrap(visualCaretPosition)) {
      return;
    }

    SoftWrap softWrap = storage.getSoftWrap(caretModel.getOffset());
    if (softWrap == null) {
      return;
    }
    if (softWrap instanceof CustomWrapToSoftWrapAdapter customWrap) {
      editor.getCustomWrapModel().removeWrap(customWrap.getCustomWrap());
    }

    document.replaceString(softWrap.getStart(), softWrap.getEnd(), softWrap.getText());
    caretModel.moveToVisualPosition(visualCaretPosition);
  }

  @Override
  public boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener) {
    return mySoftWrapNotifier.addSoftWrapChangeListener(listener);
  }

  @Override
  @ApiStatus.Internal
  public boolean addSoftWrapParsingListener(@NotNull SoftWrapParsingListener listener) {
    return mySoftWrapNotifier.addSoftWrapParsingListener(listener);
  }

  @Override
  @ApiStatus.Internal
  public boolean removeSoftWrapParsingListener(@NotNull SoftWrapParsingListener listener) {
    return mySoftWrapNotifier.removeSoftWrapParsingListener(listener);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.SOFT_WRAP_MODEL;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    myRecalculationManager.beforeDocumentChange(event);
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    if (myBulkDocumentUpdateInProgress) {
      return;
    }
    if (!myUseSoftWraps) {
      if (shouldSoftWrapsBeForced(event)) {
        forceSoftWraps();
        if (myUseSoftWraps) {
          assert myRecalculationManager instanceof SoftWrappingEnabledRecalculationManager : "soft-wraps were not forced correctly";
          var recalculationManager = (SoftWrappingEnabledRecalculationManager)myRecalculationManager;
          recalculationManager.recalculateAll();
          return;
        }
      }
    }
    myRecalculationManager.documentChanged(event);
  }

  @Override
  void onBulkDocumentUpdateStarted() {
    myBulkDocumentUpdateInProgress = true;
    myRecalculationManager.onBulkDocumentUpdateStarted();
  }

  @Override
  void onBulkDocumentUpdateFinished() {
    myBulkDocumentUpdateInProgress = false;
    if (!myUseSoftWraps && shouldSoftWrapsBeForced()) {
      forceSoftWraps();
    }
    myRecalculationManager.onBulkDocumentUpdateFinished();
  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    myRecalculationManager.onFoldRegionStateChange(region);
  }

  @Override
  public void onFoldProcessingEnd() {
    myRecalculationManager.onFoldProcessingEnd();
  }

  @Override
  public void onUpdated(@NotNull Inlay<?> inlay, int changeFlags) {
    myRecalculationManager.onUpdated(inlay, changeFlags);
  }

  @Override
  public void onBatchModeFinish(@NotNull Editor editor) {
    myRecalculationManager.onBatchModeFinish(editor);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    myRecalculationManager.propertyChange(evt);
  }

  @Override
  public void dispose() {
    release();
  }

  @Override
  public void release() {
    storage.removeAll();
    mySoftWrapNotifier.notifySoftWrapsChanged();
    myRecalculationManager.release();
  }

  @Override
  void recalculate() {
    myRecalculationManager.recalculate();
  }

  @Override
  public SoftWrapApplianceManager getApplianceManager() {
    return mySoftWrappingRecalculationManager.getApplianceManager();
  }

  @Override
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public void setSoftWrapPainter(SoftWrapPainter painter) {
    myPainter = painter;
    mySoftWrappingRecalculationManager.setSoftWrapPainter(painter);
    reinitSettings();
  }

  @Override
  public @NotNull @NonNls String dumpState() {
    return String.format("""

                           use soft wraps: %b, tab width: %d, additional columns: %b
                           bulk document update in progress: %b
                           soft-wrapping recalculation manager state: %s
                           custom-wrap-only recalculation manager state: %s
                           recalculation manager type: %s
                           soft wraps: %s""",
                         myUseSoftWraps, myTabWidth, myForceAdditionalColumns,
                         myBulkDocumentUpdateInProgress,
                         mySoftWrappingRecalculationManager.dumpState(),
                         myCustomWrapOnlyRecalculationManager.dumpState(),
                         myRecalculationManager.dumpName(),
                         storage.dumpState());
  }

  @Override
  public String toString() {
    return dumpState();
  }

  @Override
  public boolean isDirty() {
    return myRecalculationManager.isDirty();
  }

  @Override
  @TestOnly
  void validateState() {
    if (document.isInBulkUpdate()) return;
    FoldingModel foldingModel = editor.getFoldingModel();
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
      LOG.assertTrue(!DocumentUtil.isInsideCharacterPair(document, softWrapOffset),
                     "Soft wrap inside a surrogate pair or inside a line break");
      lastSoftWrapOffset = softWrapOffset;
    }
  }

  @ApiStatus.Experimental
  @Override
  public void customWrapAdded(@NotNull CustomWrap wrap) {
    myRecalculationManager.customWrapAdded(wrap);
  }

  @ApiStatus.Experimental
  @Override
  public void customWrapRemoved(@NotNull CustomWrap wrap) {
    myRecalculationManager.customWrapRemoved(wrap);
  }

  @Override
  @ApiStatus.Internal
  public void customWrapsMerged() {
    myRecalculationManager.customWrapsMerged();
  }
}
