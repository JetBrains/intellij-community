// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.AttachmentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.ScrollingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapEngine;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The general idea of soft wraps processing is to build a cache to use for quick document dimensions mapping
 * ({@code 'logical position -> visual position'}, {@code 'offset -> logical position'} etc) and update it incrementally
 * on events like document modification fold region(s) expanding/collapsing etc.
 * <p/>
 * This class encapsulates document parsing logic. It notifies {@link SoftWrapAwareDocumentParsingListener registered listeners}
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

  private final List<SoftWrapAwareDocumentParsingListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final SoftWrapsStorage myStorage;
  private final EditorImpl myEditor;
  private SoftWrapPainter myPainter;
  private final CachingSoftWrapDataMapper myDataMapper;

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
  private IncrementalCacheUpdateEvent    myEventBeingProcessed;
  private boolean                        myCustomIndentUsedLastTime;
  private int                            myCustomIndentValueUsedLastTime;
  private int                            myVisibleAreaWidth;
  private boolean                        myInProgress;
  private boolean                        myIsDirty = true;
  private IncrementalCacheUpdateEvent    myDocumentChangedEvent;
  private int                            myAvailableWidth = QUICK_DUMMY_WRAPPING;


  @ApiStatus.Internal
  public SoftWrapApplianceManager(@NotNull SoftWrapsStorage storage,
                                  @NotNull EditorImpl editor,
                                  @NotNull SoftWrapPainter painter,
                                  CachingSoftWrapDataMapper dataMapper)
  {
    myStorage = storage;
    myEditor = editor;
    myPainter = painter;
    myDataMapper = dataMapper;
    myWidthProvider = new DefaultVisibleAreaWidthProvider();
    myEditor.getScrollingModel().addVisibleAreaListener(e -> ReadAction.run(() -> {
      updateAvailableArea();
      updateLastTopLeftCornerOffset();
    }));
  }

  @ApiStatus.Internal
  public void registerSoftWrapIfNecessary() {
    recalculateIfNecessary();
  }

  @ApiStatus.Internal
  public void reset() {
    myIsDirty = true;
    for (SoftWrapAwareDocumentParsingListener listener : myListeners) {
      listener.reset();
    }
  }

  private void recalculate(IncrementalCacheUpdateEvent e) {
    if (myIsDirty) {
      return;
    }
    if (myVisibleAreaWidth <= 0) {
      myIsDirty = true;
      return;
    }

    recalculateSoftWraps(e);

    onRecalculationEnd();
  }

  @ApiStatus.Internal
  public void recalculate(@NotNull List<? extends Segment> ranges) {
    if (myIsDirty) {
      return;
    }
    if (myVisibleAreaWidth <= 0) {
      myIsDirty = true;
      return;
    }

    ranges.sort((o1, o2) -> {
      int startDiff = o1.getStartOffset() - o2.getStartOffset();
      return startDiff == 0 ? o2.getEndOffset() - o1.getEndOffset() : startDiff;
    });
    final int[] lastRecalculatedOffset = {0};
    SoftWrapAwareDocumentParsingListenerAdapter listener = new SoftWrapAwareDocumentParsingListenerAdapter() {
      @Override
      public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event) {
        lastRecalculatedOffset[0] = event.getActualEndOffset();
      }
    };
    myListeners.add(listener);
    try {
      for (Segment range : ranges) {
        int lastOffset = lastRecalculatedOffset[0];
        if (range.getEndOffset() > lastOffset) {
          recalculateSoftWraps(new IncrementalCacheUpdateEvent(Math.max(range.getStartOffset(), lastOffset), range.getEndOffset(),
                                                               myEditor));
        }
      }
    }
    finally {
      myListeners.remove(listener);
    }

    onRecalculationEnd();
  }

  @ApiStatus.Internal
  public void recalculateAll() {
    reset();
    myStorage.removeAll();
    myVisibleAreaWidth = myAvailableWidth;
    myCustomIndentUsedLastTime = myEditor.getSettings().isUseCustomSoftWrapIndent();
    myCustomIndentValueUsedLastTime = myEditor.getSettings().getCustomSoftWrapIndent();
    recalculateSoftWraps();
  }

  /**
   * @return    {@code true} if soft wraps were really re-calculated;
   *            {@code false} if it's not possible to do at the moment (e.g. current editor is not shown and we don't
   *            have information about viewport width)
   */
  private boolean recalculateSoftWraps() {
    if (!myIsDirty) {
      return true;
    }
    if (myVisibleAreaWidth <= 0) {
      return false;
    }
    myIsDirty = false;

    recalculateSoftWraps(new IncrementalCacheUpdateEvent(myEditor.getDocument()));

    onRecalculationEnd();

    return true;
  }

  private void onRecalculationEnd() {
    updateLastTopLeftCornerOffset();
    for (SoftWrapAwareDocumentParsingListener listener : myListeners) {
      listener.recalculationEnds();
    }
  }

  private void recalculateSoftWraps(@NotNull IncrementalCacheUpdateEvent event) {
    if (myInProgress) {
      LOG.error("Detected race condition at soft wraps recalculation", new Throwable(),
                AttachmentFactory.createContext(myEditor.dumpState() + "\n" + event));
    }
    myInProgress = true;
    try {
      myEventBeingProcessed = event;
      notifyListenersOnCacheUpdateStart(event);
      int endOffsetUpperEstimate = getEndOffsetUpperEstimate(event);
      if (myVisibleAreaWidth == QUICK_DUMMY_WRAPPING) {
        doRecalculateSoftWrapsRoughly(event);
      }
      else {
        new SoftWrapEngine(myEditor, myPainter, myStorage, myDataMapper, event, myVisibleAreaWidth,
                           myCustomIndentUsedLastTime ? myCustomIndentValueUsedLastTime : -1).generate();
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Soft wrap recalculation done: " + event + ". " + (event.getActualEndOffset() - event.getStartOffset()) + " characters processed");
      }
      if (event.getActualEndOffset() > endOffsetUpperEstimate) {
        LOG.error("Unexpected error at soft wrap recalculation", new Attachment("softWrapModel.txt", myEditor.getSoftWrapModel().toString()));
      }
      notifyListenersOnCacheUpdateEnd(event);
      myEventBeingProcessed = null;
    }
    finally {
      myInProgress = false;
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
    Document document = myEditor.getDocument();
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

  private int getEndOffsetUpperEstimate(IncrementalCacheUpdateEvent event) {
    int endOffsetUpperEstimate = EditorUtil.getNotFoldedLineEndOffset(myEditor, event.getMandatoryEndOffset());
    int line = myEditor.getDocument().getLineNumber(endOffsetUpperEstimate);
    if (line < myEditor.getDocument().getLineCount() - 1) {
      endOffsetUpperEstimate = myEditor.getDocument().getLineStartOffset(line + 1);
    }
    return endOffsetUpperEstimate;
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
  public boolean recalculateIfNecessary() {
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
      return recalculateSoftWraps(); // Recalculate existing dirty regions if any.
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
      return recalculateSoftWraps();
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
    myVisibleAreaWidth = currentVisibleAreaWidth;
    final boolean result = recalculateSoftWraps();
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

  /**
   * Registers given listener within the current manager.
   *
   * @param listener    listener to register
   * @return            {@code true} if this collection changed as a result of the call; {@code false} otherwise
   */
  @ApiStatus.Internal
  public boolean addListener(@NotNull SoftWrapAwareDocumentParsingListener listener) {
    return myListeners.add(listener);
  }

  @ApiStatus.Internal
  public boolean removeListener(@NotNull SoftWrapAwareDocumentParsingListener listener) {
    return myListeners.remove(listener);
  }


  private void notifyListenersOnCacheUpdateStart(IncrementalCacheUpdateEvent event) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onCacheUpdateStart(event);
    }
  }

  private void notifyListenersOnCacheUpdateEnd(IncrementalCacheUpdateEvent event) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onRecalculationEnd(event);
    }
  }

  @ApiStatus.Internal
  public void beforeDocumentChange(DocumentEvent event) {
    myDocumentChangedEvent = new IncrementalCacheUpdateEvent(event, myEditor);
  }

  @ApiStatus.Internal
  public void documentChanged(DocumentEvent event, boolean processAlsoLineEnd) {
    LOG.assertTrue(myDocumentChangedEvent != null);
    recalculate(myDocumentChangedEvent);
    if (processAlsoLineEnd) {
      int lineEndOffset = DocumentUtil.getLineEndOffset(myDocumentChangedEvent.getMandatoryEndOffset(), event.getDocument());
      if (lineEndOffset > myDocumentChangedEvent.getActualEndOffset()) {
        recalculate(new IncrementalCacheUpdateEvent(lineEndOffset, lineEndOffset, myEditor));
      }
    }
    myDocumentChangedEvent = null;
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

  @Override
  public @NotNull String dumpState() {
    return String.format(
      "recalculation in progress: %b; event being processed: %s, available width: %d, visible width: %d, dirty: %b",
      myInProgress, myEventBeingProcessed, myAvailableWidth, myVisibleAreaWidth, myIsDirty
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
    recalculateAll();
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
      // We don't want soft-wrapped text to go under the scroll bar even if that feature is enabled,
      // because in this case the scrollbar sometimes prevents the user from placing the caret by
      // clicking inside wrapped text. Even if the scroll bar is invisible, some of its marks can get
      // in the way too (errors/warnings, VCS changes, etc.). It's best to soft-wrap earlier.
      // Example: IDEA-305944 (Code goes under the scrollbar with Soft-Wrap).
      final var vsbWidth = getVerticalScrollBarWidth();
      if (horizontalInsets < vsbWidth) {
        // Guesswork: it isn't easy to figure out whether insets already include the scrollbar width,
        // as the underlying logic is very complicated. But we can reasonably assume that if they
        // do NOT include it, they will be very small (most likely zero).
        horizontalInsets += vsbWidth;
      }
      int width = Math.max(0, myEditor.getScrollingModel().getVisibleArea().width - horizontalInsets);
      if (myEditor.isInDistractionFreeMode()) {
        int rightMargin = myEditor.getSettings().getRightMargin(myEditor.getProject());
        if (rightMargin > 0) width = Math.min(width, rightMargin * EditorUtil.getPlainSpaceWidth(myEditor));
      }
      return width;
    }
  }
}
