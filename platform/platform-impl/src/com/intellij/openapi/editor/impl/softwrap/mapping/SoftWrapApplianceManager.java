// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.AttachmentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LanguageLineWrapPositionStrategy;
import com.intellij.openapi.editor.LineWrapPositionStrategy;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.ScrollingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapEngine;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapHelper;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapNotifier;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.JScrollBar;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.List;

/**
 * The general idea of soft wraps processing is to build a cache to use for quick document dimensions mapping
 * ({@code 'logical position -> visual position'}, {@code 'offset -> logical position'} etc) and update it incrementally
 * on events like document modification fold region(s) expanding/collapsing etc.
 * <p/>
 * This class encapsulates document parsing logic. It notifies {@link SoftWrapParsingListener registered listeners}
 * about parsing and they are free to store necessary information for further usage.
 * <p/>
 * Not thread-safe.
 */
//@ApiStatus.Internal
public final class SoftWrapApplianceManager implements Dumpable {
  private static final Logger LOG = Logger.getInstance(SoftWrapApplianceManager.class);
  private static final int QUICK_DUMMY_WRAPPING = Integer.MAX_VALUE; // special value to request a tentative wrapping
                                                                     // before editor is shown and actual available width is known
  private static final int QUICK_WRAP_CHAR_COUNT = 1000;

  /** Enumerates possible type of soft wrap indents to use. */
  enum IndentType {
    /** Don't apply special indent to soft-wrapped line at all. */
    NONE,

    /**
     * Indent soft wraps for the {@link EditorSettings#getCustomSoftWrapIndent() user-defined number of columns}
     * to the start of the previous visual line.
     */
    CUSTOM
  }

  private final SoftWrapsStorage myStorage;
  private final EditorImpl myEditor;
  private final DocumentEx myDocument;
  private SoftWrapPainter myPainter;
  private final CachingSoftWrapDataMapper myDataMapper;
  private final @NotNull SoftWrapNotifier mySoftWrapNotifier;

  /**
   * Visual area width change causes soft wraps addition/removal, so, we want to update {@code 'y'} coordinate
   * of the editor viewport then. For example, we observe particular text region at the 'vcs diff' control and change
   * its width. We would like to see the same text range at the viewport then.
   * <p/>
   * This field holds offset of the text range that is shown at the top-left viewport position. It's used as an anchor
   * during viewport's {@code 'y'} coordinate adjustment on visual area width change.
   */
  private int myLastTopLeftCornerOffset;

  private VisibleAreaWidthProvider       myWidthProvider;
  private boolean mySoftWrapsUnderScrollBar;
  private IncrementalCacheUpdateEvent    myEventBeingProcessed;
  private boolean                        myCustomIndentUsedLastTime;
  private int                            myCustomIndentValueUsedLastTime;
  private int                            myVisibleAreaWidth;
  private boolean                        myInProgress;
  private String                         myLastRecalculationReason;
  private boolean                        myIsDirty = true;
  private int                            myDocumentChangeStartOffset = -1;
  private int                            myDocumentChangeEndOffset = -1;
  private int                            myAvailableWidth = QUICK_DUMMY_WRAPPING;
  private @Nullable LineWrapPositionStrategy myLineWrapPositionStrategy;


  @ApiStatus.Internal
  public SoftWrapApplianceManager(@NotNull SoftWrapsStorage storage,
                                  @NotNull EditorImpl editor,
                                  @NotNull SoftWrapPainter painter,
                                  @NotNull CachingSoftWrapDataMapper dataMapper,
                                  @NotNull SoftWrapNotifier softWrapNotifier)
  {
    myStorage = storage;
    myEditor = editor;
    myDocument = editor.getElfDocument();
    myPainter = painter;
    myDataMapper = dataMapper;
    mySoftWrapNotifier = softWrapNotifier;
    myWidthProvider = new DefaultVisibleAreaWidthProvider();
    myEditor.getScrollingModel().addVisibleAreaListener(e -> EditorThreading.run(() -> {
      updateAvailableArea();
      updateLastTopLeftCornerOffset();
    }));
  }

  @ApiStatus.Internal
  public void setSoftWrapsUnderScrollBar(boolean softWrapsUnderScrollBar) {
    mySoftWrapsUnderScrollBar = softWrapsUnderScrollBar;
  }

  @ApiStatus.Internal
  public void reset() {
    myIsDirty = true;
    mySoftWrapNotifier.notifyReset();
  }

  private void recalculate(IncrementalCacheUpdateEvent e, @NotNull String reason) {
    if (myIsDirty) {
      return;
    }
    if (myVisibleAreaWidth <= 0) {
      myIsDirty = true;
      return;
    }

    recalculateSoftWraps(e, reason);

    onRecalculationEnd();
  }

  @ApiStatus.Internal
  public void recalculate(@NotNull @Unmodifiable List<? extends Segment> ranges, @NotNull String reason) {
    if (myIsDirty) {
      return;
    }
    if (myVisibleAreaWidth <= 0) {
      myIsDirty = true;
      return;
    }

    SoftWrapHelper.recalculateSegments(
      ranges, mySoftWrapNotifier,
      (startOffset, endOffset) -> recalculateSoftWraps(createEventForVisualChange(startOffset, endOffset), reason)
    );

    onRecalculationEnd();
  }

  @ApiStatus.Internal
  public void recalculateAll(@NotNull String reason) {
    reset();
    myStorage.removeAll();
    mySoftWrapNotifier.notifySoftWrapsChanged();
    myVisibleAreaWidth = myAvailableWidth;
    myCustomIndentUsedLastTime = myEditor.getSettings().isUseCustomSoftWrapIndent();
    myCustomIndentValueUsedLastTime = myEditor.getSettings().getCustomSoftWrapIndent();
    recalculateSoftWraps(reason);
  }

  /**
   * @return    {@code true} if soft wraps were really re-calculated;
   *            {@code false} if it's not possible to do at the moment (e.g. current editor is not shown and we don't
   *            have information about viewport width)
   */
  private boolean recalculateSoftWraps(@NotNull String reason) {
    if (!myIsDirty) {
      return true;
    }
    if (myVisibleAreaWidth <= 0) {
      return false;
    }
    myIsDirty = false;

    recalculateSoftWraps(IncrementalCacheUpdateEvent.forWholeDocument(myDocument), reason);

    onRecalculationEnd();

    return true;
  }

  private void onRecalculationEnd() {
    updateLastTopLeftCornerOffset();
    mySoftWrapNotifier.notifyAllDirtyRegionsReparsed();
  }

  private void recalculateSoftWraps(@NotNull IncrementalCacheUpdateEvent event, @NotNull String reason) {
    if (myInProgress) {
      LOG.error("Detected race condition at soft wraps recalculation", new Throwable(),
                AttachmentFactory.createContext(myEditor.dumpState() + "\n" + event));
    }
    myInProgress = true;
    try {
      myEventBeingProcessed = event;
      mySoftWrapNotifier.notifyRegionReparseStart(event);
      int endOffsetUpperEstimate = SoftWrapHelper.getEndOffsetUpperEstimate(myEditor, myDocument, event);
      if (myVisibleAreaWidth == QUICK_DUMMY_WRAPPING) {
        doRecalculateSoftWrapsRoughly(event);
      }
      else {
        new SoftWrapEngine(myEditor, myPainter, myStorage, myDataMapper, event, myLineWrapPositionStrategy, myVisibleAreaWidth,
                           myCustomIndentUsedLastTime ? myCustomIndentValueUsedLastTime : -1).generate();
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Soft wrap recalculation done: " + event + ". " + (event.getActualEndOffset() - event.getStartOffset()) + " characters processed");
      }
      if (event.getActualEndOffset() > endOffsetUpperEstimate) {
        LOG.error("Unexpected error at soft wrap recalculation", new Attachment("softWrapModel.txt", myEditor.getSoftWrapModel().toString()));
      }
      mySoftWrapNotifier.notifyRegionReparseEnd(event);
      myEventBeingProcessed = null;
    }
    finally {
      myInProgress = false;
      myLastRecalculationReason = reason;
    }

    Project project = myEditor.getProject();
    VirtualFile file = myEditor.getVirtualFile();
    if (project != null && file != null && myEditor.getUserData(EditorImpl.FORCED_SOFT_WRAPS) != null) {
      if (myStorage.isEmpty()) {
        myEditor.putUserData(EditorImpl.SOFT_WRAPS_EXIST, null);
      }
      else if (myEditor.getUserData(EditorImpl.SOFT_WRAPS_EXIST) == null) {
        myEditor.putUserData(EditorImpl.SOFT_WRAPS_EXIST, Boolean.TRUE);
        EditorNotifications.getInstance(project).updateNotifications(file);
      }
    }
  }

  // this method generates soft-wraps at some places just to ensure visual lines have limited width, to avoid related performance problems
  // correct procedure is not used to speed up editor opening
  private void doRecalculateSoftWrapsRoughly(IncrementalCacheUpdateEvent event) {
    Document document = myDocument;
    int lineCount = document.getLineCount();
    int offset = event.getStartOffset();
    int line = document.getLineNumber(offset);
    int mandatoryEnd = event.getMandatoryEndOffset();
    while (true) {
      if ((offset += QUICK_WRAP_CHAR_COUNT) >= document.getLineEndOffset(line)) {
        if (++line >= lineCount) {
          offset = document.getTextLength();
          break;
        }
        offset = document.getLineStartOffset(line);
        if (offset > mandatoryEnd && myEditor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1) == null) break;
        else continue;
      }
      FoldRegion foldRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(offset);
      if (foldRegion != null) {
        offset = foldRegion.getEndOffset();
        line = document.getLineNumber(offset);
      }
      if (DocumentUtil.isInsideSurrogatePair(document, offset)) offset++;
      if (offset < document.getLineEndOffset(line)) {
        SoftWrapImpl wrap = new SoftWrapImpl(new TextChangeImpl("\n", offset), 1, 1);
        myStorage.storeOrReplace(wrap);
        if (offset > mandatoryEnd && myDataMapper.matchesOldSoftWrap(wrap, event.getLengthDiff())) break;
      }
    }
    event.setActualEndOffset(offset);
  }

  /**
   * There is a possible case that we need to reparse the whole document (e.g. visible area width is changed or user-defined
   * soft wrap indent is changed etc). This method encapsulates that logic, i.e. it checks if necessary conditions are satisfied
   * and updates internal state as necessary.
   *
   * @return {@code true} if re-calculation logic was performed;
   *         {@code false} otherwise (e.g. we need to perform re-calculation but current editor is now shown, i.e. we don't
   *         have information about viewport width
   */
  @ApiStatus.Internal
  public boolean recalculateIfNecessary(@NotNull String reason) {
    if (myInProgress) {
      return false;
    }

    // Check if we need to recalculate soft wraps due to indent settings change.
    boolean indentChanged = false;
    IndentType currentIndentType = getIndentToUse();
    boolean useCustomIndent = currentIndentType == IndentType.CUSTOM;
    int currentCustomIndent = myEditor.getSettings().getCustomSoftWrapIndent();
    if (useCustomIndent ^ myCustomIndentUsedLastTime || useCustomIndent && myCustomIndentValueUsedLastTime != currentCustomIndent) {
      indentChanged = true;
    }
    myCustomIndentUsedLastTime = useCustomIndent;
    myCustomIndentValueUsedLastTime = currentCustomIndent;

    // Check if we need to recalculate soft wraps due to visible area width change.
    int currentVisibleAreaWidth = myAvailableWidth;
    if (!indentChanged && myVisibleAreaWidth == currentVisibleAreaWidth) {
      return recalculateSoftWraps(reason + " (indent and width unchanged)"); // Recalculate existing dirty regions if any.
    }

    // We experienced the following situation:
    //   1. Editor is configured to show scroll bars only when necessary;
    //   2. Editor with active soft wraps is changed in order for the vertical scroll bar to appear;
    //   3. Vertical scrollbar consumes vertical space, hence, soft wraps are recalculated because of the visual area width change;
    //   4. Newly recalculated soft wraps trigger editor size update;
    //   5. Editor size update starts scroll pane update which, in turn, disables vertical scroll bar at first (the reason for that
    //      lays somewhere at the swing depth);
    //   6. Soft wraps are recalculated because of visible area width change caused by the disabled vertical scroll bar;
    //   7. Go to the step 4;
    // I.e. we have an endless EDT activity that stops only when editor is re-sized in a way to avoid vertical scroll bar.
    // That's why we don't recalculate soft wraps when visual area width is changed to the vertical scroll bar width value assuming
    // that such a situation is triggered by the scroll bar (dis)appearance.
    if (currentVisibleAreaWidth - myVisibleAreaWidth == getVerticalScrollBarWidth()) {
      myVisibleAreaWidth = currentVisibleAreaWidth;
      return recalculateSoftWraps(reason + " (indent unchanged, width changed by scrollbar)");
    }

    // We want to adjust viewport's 'y' coordinate on complete recalculation, so, we remember number of soft-wrapped lines
    // before the target offset on recalculation start and compare it with the number of soft-wrapped lines before the same offset
    // after the recalculation.
    final ScrollingModelEx scrollingModel = myEditor.getScrollingModel();
    int yScrollOffset = scrollingModel.getVerticalScrollOffset();
    int anchorOffset = myLastTopLeftCornerOffset;
    int softWrapsBefore = getNumberOfSoftWrapsBefore(anchorOffset);

    // Drop information about processed lines.
    reset();
    myStorage.removeAll();
    mySoftWrapNotifier.notifySoftWrapsChanged();
    myVisibleAreaWidth = currentVisibleAreaWidth;
    final boolean result = recalculateSoftWraps(reason + " (indent or width changed)");
    if (!result) {
      return false;
    }

    // Adjust viewport's 'y' coordinate if necessary.
    int softWrapsNow = getNumberOfSoftWrapsBefore(anchorOffset);
    if (softWrapsNow != softWrapsBefore) {
      scrollingModel.disableAnimation();
      try {
        scrollingModel.scrollVertically(yScrollOffset + (softWrapsNow - softWrapsBefore) * myEditor.getLineHeight());
      }
      finally {
        scrollingModel.enableAnimation();
      }
    }
    updateLastTopLeftCornerOffset();
    return true;
  }

  private void updateLastTopLeftCornerOffset() {
    int visibleAreaTopY = myEditor.getScrollingModel().getVisibleArea().y;
    if (visibleAreaTopY == 0) {
      myLastTopLeftCornerOffset = 0;
    }
    else {
      int visualLine = 1 + myEditor.yToVisualLine(visibleAreaTopY);
      myLastTopLeftCornerOffset = myEditor.visualLineStartOffset(visualLine);
    }
  }

  private int getNumberOfSoftWrapsBefore(int offset) {
    final int i = myStorage.getSoftWrapIndex(offset);
    return i >= 0 ? i : -i - 1;
  }

  private IndentType getIndentToUse() {
    return myEditor.getSettings().isUseCustomSoftWrapIndent() ? IndentType.CUSTOM : IndentType.NONE;
  }

  @ApiStatus.Internal
  public void beforeFoldRegionDisposed(FoldRegion region) {
    myDocumentChangeEndOffset = Math.max(region.getEndOffset(), myDocumentChangeEndOffset);
  }

  @ApiStatus.Internal
  public void beforeDocumentChange(DocumentEvent event) {
    // We call it before the change, because a folding may become invalidated by the change,
    // and the expanded range would not be covered by soft-wrap recalculation.
    myDocumentChangeStartOffset = getIncrementalUpdateStartOffset(event.getOffset());
    myDocumentChangeEndOffset = event.getOffset() + event.getNewLength();
  }

  @ApiStatus.Internal
  public void documentChanged(DocumentEvent event, boolean processAlsoLineEnd) {
    LOG.assertTrue(myDocumentChangeStartOffset != -1);
    IncrementalCacheUpdateEvent cacheUpdateEvent = new IncrementalCacheUpdateEvent(
      myDocumentChangeStartOffset,
      SoftWrapHelper.coerceToValidOffset(myDocumentChangeEndOffset, event.getDocument()),
      event.getNewLength() - event.getOldLength()
    );
    recalculate(cacheUpdateEvent, "document changed");
    if (processAlsoLineEnd) {
      int lineEndOffset = DocumentUtil.getLineEndOffset(cacheUpdateEvent.getMandatoryEndOffset(), event.getDocument());
      if (lineEndOffset > cacheUpdateEvent.getActualEndOffset()) {
        recalculate(createEventForVisualChange(lineEndOffset, lineEndOffset), "line end after document change");
      }
    }
    myDocumentChangeStartOffset = -1;
    myDocumentChangeEndOffset = -1;
  }

  //@ApiStatus.Internal
  public void setWidthProvider(@NotNull VisibleAreaWidthProvider widthProvider) {
    myWidthProvider = widthProvider;
    reset();
  }

  //@ApiStatus.Internal
  public @NotNull VisibleAreaWidthProvider getWidthProvider() {
    return myWidthProvider;
  }

  /**
   * By default, line wrap strategy depends on the editor's file language
   * and can be provided using {@link LanguageLineWrapPositionStrategy}.
   * This method can be used to specify the strategy for the Editor that is not bound to any particular file.
   * Note that the strategy set using this method takes precedence over one provided using {@link LanguageLineWrapPositionStrategy}.
   */
  public void setLineWrapPositionStrategy(@NotNull LineWrapPositionStrategy strategy) {
    myLineWrapPositionStrategy = strategy;
    reset();
  }

  @Override
  public @NotNull String dumpState() {
    return String.format(
      "recalculation in progress: %b; event being processed: %s, available width: %d, visible width: %d, dirty: %b" +
      " last recalculation reason: %s",
      myInProgress, myEventBeingProcessed, myAvailableWidth, myVisibleAreaWidth, myIsDirty, myLastRecalculationReason
    );
  }

  @Override
  public String toString() {
    return dumpState();
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public void setSoftWrapPainter(SoftWrapPainter painter) {
    myPainter = painter;
    recalculateAll("set soft-wrap painter");
  }

  @ApiStatus.Internal
  public void updateAvailableArea() {
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    if (visibleArea.isEmpty()) return;
    int width = myWidthProvider.getVisibleAreaWidth();
    if (width <= 0) return;
    myAvailableWidth = width;
  }

  private int getVerticalScrollBarWidth() {
    JScrollBar scrollBar = myEditor.getScrollPane().getVerticalScrollBar();
    int width = scrollBar.getWidth();
    if (width <= 0) {
      width = scrollBar.getPreferredSize().width;
    }
    return width;
  }

  /**
   * This interface is introduced mostly for encapsulating GUI-specific values retrieval and make it possible to write
   * tests for soft wraps processing.
   */
  @FunctionalInterface
  public interface VisibleAreaWidthProvider {
    int getVisibleAreaWidth();
  }

  private final class DefaultVisibleAreaWidthProvider implements VisibleAreaWidthProvider {

    @Override
    public int getVisibleAreaWidth() {
      Insets insets = myEditor.getContentComponent().getInsets();
      int horizontalInsets = insets.left + insets.right;
      // Guesswork: it isn't easy to figure out whether insets already include the scrollbar width,
      // as the underlying logic is very complicated. But we can reasonably assume that if they
      // do NOT include it, they will be very small (most likely zero).
      final var vsbWidth = getVerticalScrollBarWidth();
      boolean insetsIncludeScrollbar = horizontalInsets >= vsbWidth;
      if (mySoftWrapsUnderScrollBar) {
        if (insetsIncludeScrollbar) {
          horizontalInsets -= vsbWidth;
        }
      }
      else {
        // We don't want soft-wrapped text to go under the scroll bar even if that feature is enabled,
        // because in this case the scrollbar sometimes prevents the user from placing the caret by
        // clicking inside wrapped text. Even if the scroll bar is invisible, some of its marks can get
        // in the way too (errors/warnings, VCS changes, etc.). It's best to soft-wrap earlier.
        // Example: IDEA-305944 (Code goes under the scrollbar with Soft-Wrap).
        if (!insetsIncludeScrollbar) {
          horizontalInsets += vsbWidth;
        }
      }
      int width = Math.max(0, myEditor.getScrollingModel().getVisibleArea().width - horizontalInsets);
      if (myEditor.isInDistractionFreeMode()) {
        int rightMargin = myEditor.getSettings().getRightMargin(myEditor.getProject());
        if (rightMargin > 0) width = Math.min(width, rightMargin * EditorUtil.getPlainSpaceWidth(myEditor));
      }
      return width;
    }
  }

  /**
   * Creates new {@code IncrementalCacheUpdateEvent} object for the event not changing document length
   * (like expansion of folded region).
   */
  private IncrementalCacheUpdateEvent createEventForVisualChange(int startOffset, int endOffset) {
    return new IncrementalCacheUpdateEvent(
      getIncrementalUpdateStartOffset(startOffset),
      endOffset,
      0
    );
  }

  private int getIncrementalUpdateStartOffset(int eventStartOffset) {
    VisualLineInfo info = getVisualLineInfo(eventStartOffset, false);
    if (info.startsWithSoftWrap()) {
      info = getVisualLineInfo(info.startOffset, true);
    }
    // cannot start recalculation from a custom wrap:
    //   SoftWrapEngine relies on the first soft-wrap to calculate indent for further soft wraps within the same logical line
    while (info.startsWithCustomSoftWrap()) {
      info = getVisualLineInfo(info.startOffset, true);
    }
    return info.startOffset;
  }

  /** Finds information about visual line start without using coordinate mapping. */
  private VisualLineInfo getVisualLineInfo(int offset, boolean beforeSoftWrap) {
    int textLength = myDocument.getTextLength();
    if (offset <= 0 || textLength == 0) return new VisualLineInfo(0, null);
    offset = Math.min(offset, textLength);

    // if the startOffset of the logical line is folded, then we find the startOffset corresponding to the start of that folding, recursively
    int startOffset = EditorUtil.getNotFoldedLineStartOffset(myEditor, offset);

    int wrapIndex = myStorage.getSoftWrapIndex(offset);

    int prevSoftWrapIndex = wrapIndex < 0 ?
                            // if not found: the one closest to offset backwards
                            -wrapIndex - 2 :
                            // if soft-wrap at startOffset: beforeSoftWrap decides if to consider this one or the previous one, tie-braker
                            wrapIndex - (beforeSoftWrap ? 1 : 0);
    SoftWrap prevSoftWrap = prevSoftWrapIndex < 0 ? null : myStorage.getSoftWraps().get(prevSoftWrapIndex);

    // the start of the visual line is then whichever is closer to the offset: some soft-wrap or the logical start of the line
    int visualLineStartOffset = prevSoftWrap == null ? startOffset : Math.max(startOffset, prevSoftWrap.getStart());
    return new VisualLineInfo(visualLineStartOffset,
                              prevSoftWrap != null && prevSoftWrap.getStart() == visualLineStartOffset ? prevSoftWrap : null);
  }

  private static final class VisualLineInfo {
    private final int startOffset;
    private final SoftWrap startSoftWrap;

    private VisualLineInfo(int startOffset, SoftWrap wrap) {
      this.startOffset = startOffset;
      startSoftWrap = wrap;
    }

    private boolean startsWithSoftWrap() {
      return startSoftWrap != null;
    }

    private boolean startsWithCustomSoftWrap() {
      return startSoftWrap != null && startSoftWrap.isCustomSoftWrap();
    }
  }
}
