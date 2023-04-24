// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.RemoteDesktopService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.ScrollingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.components.Interpolable;
import com.intellij.util.MathUtil;
import com.intellij.util.animation.Animations;
import com.intellij.util.animation.Easing;
import com.intellij.util.animation.JBAnimator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class ScrollingModelImpl implements ScrollingModelEx {
  private static final Logger LOG = Logger.getInstance(ScrollingModelImpl.class);

  @NotNull private final ScrollingModel.Supplier mySupplier;
  private final List<VisibleAreaListener> myVisibleAreaListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<ScrollRequestListener> myScrollRequestListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private AnimatedScrollingRunnable myCurrentAnimationRequest;
  private boolean myAnimationDisabled;

  private int myAccumulatedXOffset = -1;
  private int myAccumulatedYOffset = -1;
  private boolean myAccumulateViewportChanges;
  private boolean myViewportPositioned;

  private final DocumentListener myDocumentListener = new DocumentListener() {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
      if (!mySupplier.getEditor().getDocument().isInBulkUpdate()) {
        cancelAnimatedScrolling(true);
      }
    }
  };

  private final ChangeListener myViewportChangeListener = new MyChangeListener();

  public ScrollingModelImpl(EditorImpl editor) {
    this(new DefaultEditorSupplier(editor));
  }

  public ScrollingModelImpl(@NotNull ScrollingModel.Supplier supplier) {
    mySupplier = supplier;
  }

  void initListeners() {
    mySupplier.getScrollPane().getViewport().addChangeListener(myViewportChangeListener);
    mySupplier.getEditor().getDocument().addDocumentListener(myDocumentListener);
  }

  /**
   * Corrects viewport position if necessary on initial editor showing.
   *
   * @return {@code true} if the vertical viewport position has been adjusted; {@code false} otherwise
   */
  private boolean adjustVerticalOffsetIfNecessary() {
    Editor editor = mySupplier.getEditor();
    // There is a possible case that the editor is configured to show virtual space at file bottom
    // and the requested position is located somewhere around.
    // We don't want to position the viewport in a way that most of its area is used to represent that virtual empty space.
    // So, we tweak vertical offset if necessary.
    int maxY = Math.max(editor.getLineHeight(), editor.getDocument().getLineCount() * editor.getLineHeight());
    int minPreferredY = maxY - getVisibleArea().height * 2 / 3;
    final int currentOffset = getVerticalScrollOffset();
    int offsetToUse = Math.min(minPreferredY, currentOffset);
    if (offsetToUse != currentOffset) {
      scroll(getHorizontalScrollOffset(), offsetToUse);
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public Rectangle getVisibleArea() {
    assertIsDispatchThread();
    return mySupplier.getScrollPane().getViewport().getViewRect();
  }

  @NotNull
  @Override
  public Rectangle getVisibleAreaOnScrollingFinished() {
    assertIsDispatchThread();
    if (EditorCoreUtil.isTrueSmoothScrollingEnabled()) {
      Rectangle viewRect = mySupplier.getScrollPane().getViewport().getViewRect();
      return new Rectangle(getOffset(getHorizontalScrollBar()), getOffset(getVerticalScrollBar()), viewRect.width, viewRect.height);
    }
    if (myCurrentAnimationRequest != null) {
      return myCurrentAnimationRequest.getTargetVisibleArea();
    }
    return getVisibleArea();
  }

  @Override
  public void scrollToCaret(@NotNull ScrollType scrollType) {
    if (LOG.isTraceEnabled()) {
      LOG.trace(new Throwable());
    }
    assertIsDispatchThread();

    Editor editor = mySupplier.getEditor();
    AsyncEditorLoader.performWhenLoaded(editor, () -> scrollTo(editor.getCaretModel().getVisualPosition(), scrollType));
  }

  private void scrollTo(@NotNull VisualPosition pos, @NotNull ScrollType scrollType) {
    Editor editor = mySupplier.getEditor();
    for (ScrollRequestListener listener : myScrollRequestListeners) {
      listener.scrollRequested(editor.visualToLogicalPosition(pos), scrollType);
    }
    Point targetLocation = mySupplier.getScrollingHelper().calculateScrollingLocation(editor, pos);
    scrollTo(targetLocation, scrollType);
  }

  private void scrollTo(@NotNull Point targetLocation, @NotNull ScrollType scrollType) {
    AnimatedScrollingRunnable canceledThread = cancelAnimatedScrolling(false);
    Rectangle viewRect = canceledThread != null ? canceledThread.getTargetVisibleArea() : getVisibleArea();
    Point p = calcOffsetsToScroll(targetLocation, scrollType, viewRect);
    scroll(p.x, p.y);
  }

  @Override
  public void scrollTo(@NotNull LogicalPosition pos, @NotNull ScrollType scrollType) {
    assertIsDispatchThread();
    Editor editor = mySupplier.getEditor();
    AsyncEditorLoader.performWhenLoaded(editor, () -> {
      for (ScrollRequestListener listener : myScrollRequestListeners) {
        listener.scrollRequested(pos, scrollType);
      }
      scrollTo(mySupplier.getScrollingHelper().calculateScrollingLocation(editor, pos), scrollType);
    });
  }

  private static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  @Override
  public void runActionOnScrollingFinished(@NotNull Runnable action) {
    assertIsDispatchThread();

    if (myCurrentAnimationRequest != null) {
      myCurrentAnimationRequest.addPostRunnable(action);
      return;
    }

    action.run();
  }

  public boolean isAnimationEnabled() {
    return !myAnimationDisabled;
  }

  @Override
  public void disableAnimation() {
    myAnimationDisabled = true;
  }

  @Override
  public void enableAnimation() {
    myAnimationDisabled = false;
  }

  private @NotNull Point calcOffsetsToScroll(@NotNull Point targetLocation, @NotNull ScrollType scrollType, @NotNull Rectangle viewRect) {
    Editor editor = mySupplier.getEditor();
    List<ScrollPositionCalculator> calculators = ScrollPositionCalculator.EXTENSION_POINT_NAME.getExtensionList();
    if (calculators.isEmpty()) {
      if (editor.getSettings().isRefrainFromScrolling() && viewRect.contains(targetLocation)) {
        if (scrollType == ScrollType.CENTER ||
            scrollType == ScrollType.CENTER_DOWN ||
            scrollType == ScrollType.CENTER_UP) {
          scrollType = ScrollType.RELATIVE;
        }
      }

      int hOffset = getHorizontalOffset(editor, targetLocation, scrollType, viewRect);
      int vOffset = getVerticalOffset(editor, targetLocation, scrollType, viewRect);
      return new Point(hOffset, vOffset);
    } else {
      // ScrollPositionCalculator was added for IdeaVim plugin
      if (calculators.size() > 1) {
        LOG.warn("Multiple instances of ScrollPositionCalculator have been found.");
      }
      return calculators.get(0).calcOffsetsToScroll(editor, targetLocation, scrollType, viewRect);
    }
  }

  private int getHorizontalOffset(@NotNull Editor editor, @NotNull Point targetLocation, @NotNull ScrollType scrollType, @NotNull Rectangle viewRect) {
    int horizontalOffset = viewRect.x;
    int spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, editor);

    int editorWidth = viewRect.width;

    JScrollPane scrollPane = mySupplier.getScrollPane();
    int scrollWidth = scrollPane.getHorizontalScrollBar().getMaximum() - getExtent(scrollPane.getHorizontalScrollBar());
    int textWidth = scrollWidth + editorWidth;

    int scrollOffset = editor.getSettings().getHorizontalScrollOffset();
    int scrollJump = editor.getSettings().getHorizontalScrollJump();

    // when we calculate bounds, we assume that characters have the same width (spaceWidth),
    // it's not the most accurate way to handle side scroll offset, but definitely the fastest
    //
    // text between these two following bounds should be visible in view rectangle after scrolling
    // (that's the meaning of the scroll offset setting)
    // if it is not possible e.g. view rectangle is too small to contain the whole range,
    // then scrolling will center the targetLocation
    int leftBound = targetLocation.x - scrollOffset * spaceWidth;
    int rightBound = targetLocation.x + scrollOffset * spaceWidth;

    if (rightBound - leftBound > editorWidth) { // if editor width is not enough to satisfy offsets from both sides, we center target location
      horizontalOffset = targetLocation.x - editorWidth / 2;
    } else if (leftBound < viewRect.x) {
      if (scrollType == ScrollType.MAKE_VISIBLE && rightBound < editorWidth) {
        // here we try to scroll to 0, if it is possible (that's the point of MAKE_VISIBLE)
        horizontalOffset = 0;
      } else {
        // this is done to ensure safety in cases where scrolling excessively may obscure the target location
        // (due to a large scroll jump or a narrow editor width).
        int leftmostPossibleLocation = getLeftmostLocation(targetLocation, editorWidth, scrollOffset, spaceWidth);
        int leftAfterScrollJump = Math.max(leftmostPossibleLocation, viewRect.x - scrollJump * spaceWidth);
        horizontalOffset = Math.min(leftBound, leftAfterScrollJump);
      }
    } else if (rightBound > viewRect.x + editorWidth) {
      int rightmostPossibleLocation = getRightmostLocation(targetLocation, textWidth, editorWidth, scrollOffset, spaceWidth);
      // this is done to ensure safety in cases where scrolling excessively may obscure the target location
      // (due to a large scroll jump or a narrow editor width).
      int rightAfterScrollJump = Math.min(rightmostPossibleLocation, viewRect.x + editorWidth + scrollJump * spaceWidth);
      horizontalOffset = Math.max(rightBound, rightAfterScrollJump) - editorWidth;
    }

    horizontalOffset = Math.max(0, horizontalOffset);
    horizontalOffset = Math.min(scrollWidth, horizontalOffset);
    return horizontalOffset;
  }

  /**
   * Gets the upmost possible y-coordinate that can be container by viewRect to satisfy two following conditions:
   * 1. The targetLocation must be still visible in the viewRect
   * 2. There must be enough space to the bottom of targetLocation to contain offsetBottomBound
   */
  private static int getTopmostLocation(@NotNull Point targetLocation, int editorHeight, int offsetBottomBound) {
    int topmostLocation = targetLocation.y - editorHeight;
    if (topmostLocation < 0) {
      return 0;
    }
    return topmostLocation + (offsetBottomBound - targetLocation.y);
  }

  /**
   * Gets the bottommost possible y-coordinate that can be container by viewRect to satisfy two following conditions:
   * 1. The targetLocation must be still visible in the viewRect
   * 2. There must be enough space to the top of targetLocation to contain offsetTopBound
   */
  private static int getBottommostLocation(@NotNull Point targetLocation, int textHeight, int editorHeight, int offsetTopBound) {
    int bottommostLocation = targetLocation.y + editorHeight;
    if (bottommostLocation > textHeight) {
      return textHeight;
    }
    return bottommostLocation - (targetLocation.y - offsetTopBound);
  }

  /**
   * Gets the leftmost possible x-coordinate that can be container by viewRect to satisfy two following conditions:
   * 1. The targetLocation must be still visible in the viewRect
   * 2. There must be enough space to the right of targetLocation to satisfy the scroll offset
   */
  private static int getLeftmostLocation(@NotNull Point targetLocation, int editorWidth, int scrollOffset, int spaceWidth) {
    int leftmostLocation = targetLocation.x - editorWidth;
    if (leftmostLocation < 0) {
      return 0;
    }
    return leftmostLocation + scrollOffset * spaceWidth;
  }

  /**
   * Gets the rightmost possible x-coordinate that can be container by viewRect to satisfy two following conditions:
   * 1. The targetLocation must be still visible in the viewRect
   * 2. There must be enough space to the left of targetLocation to satisfy the scroll offset
   */
  private static int getRightmostLocation(@NotNull Point targetLocation, int textWidth, int editorWidth, int scrollOffset, int spaceWidth) {
    int rightmostLocation = targetLocation.x + editorWidth;
    if (rightmostLocation > textWidth) {
      return textWidth;
    }
    return rightmostLocation - scrollOffset * spaceWidth;
  }

  private int getVerticalOffset(@NotNull Editor editor, @NotNull Point targetLocation, @NotNull ScrollType scrollType, @NotNull Rectangle viewRect) {
    int editorHeight = viewRect.height;
    int lineHeight = editor.getLineHeight();

    JScrollPane scrollPane = mySupplier.getScrollPane();
    int scrollHeight = scrollPane.getVerticalScrollBar().getMaximum() - getExtent(scrollPane.getVerticalScrollBar());
    int textHeight = scrollHeight + editorHeight;

    int scrollOffset = editor.getSettings().getVerticalScrollOffset();
    int scrollJump = editor.getSettings().getVerticalScrollJump();
    // the two following lines should be both visible in view rectangle after scrolling (that's the meaning of the scroll offset setting)
    //  If it is not possible, e.g. view rectangle is too small to contain both lines, then scrolling will go to the `centerPosition`
    int offsetTopBound = addVerticalOffsetToPosition(editor, -scrollOffset, targetLocation);
    int offsetBottomBound = addVerticalOffsetToPosition(editor, scrollOffset, targetLocation) + lineHeight;
    int minEditorHeightToSatisfyOffsets = offsetBottomBound - offsetTopBound;

    // the position that we consider to be the "central" one
    // for some historical reasons, before scroll offset support, center was actually at the 1/3 of the view rectangle
    int centerPosition;
    if (editorHeight > minEditorHeightToSatisfyOffsets) { // if editor has enough height, let the center be in its historical (expected for users) position
      centerPosition = targetLocation.y - editorHeight / 3;
    } else { // the real centered position for ones who use big offsets or don't have enough height
      centerPosition = targetLocation.y - Math.max(0, viewRect.height - lineHeight) / 2;
    }

    int verticalOffset = viewRect.y;
    if (scrollType == ScrollType.CENTER) {
      verticalOffset = centerPosition;
    } else if (scrollType == ScrollType.CENTER_UP) {
      if (viewRect.y > offsetTopBound || viewRect.y + viewRect.height < offsetBottomBound || viewRect.y > centerPosition) {
        verticalOffset = centerPosition;
      }
    } else if (scrollType == ScrollType.CENTER_DOWN) {
      if (viewRect.y > offsetTopBound || viewRect.y + viewRect.height < offsetBottomBound || viewRect.y < centerPosition) {
        verticalOffset = centerPosition;
      }
    } else if (scrollType == ScrollType.RELATIVE) {
      if (offsetBottomBound - offsetTopBound > editorHeight) {
        verticalOffset = centerPosition;
      } else if (viewRect.y + viewRect.height < offsetBottomBound) {
        // this is done to ensure safety in cases where scrolling excessively may obscure the target location
        // (due to a large scroll jump or a small editor height).
        int bottomAfterScrollJump = Math.min(
          getBottommostLocation(targetLocation, textHeight, editorHeight, offsetTopBound),
          addVerticalOffsetToPosition(editor, scrollJump, new Point(viewRect.x, viewRect.y + viewRect.height))
        );
        verticalOffset = Math.max(offsetBottomBound - viewRect.height, bottomAfterScrollJump - viewRect.height);
      } else if (viewRect.y > offsetTopBound) {
        // this is done to ensure safety in cases where scrolling excessively may obscure the target location
        // (due to a large scroll jump or a small editor height).
        int topAfterScrollJump = Math.max(
          getTopmostLocation(targetLocation, editorHeight, offsetBottomBound),
          addVerticalOffsetToPosition(editor, -scrollJump, new Point(viewRect.x, viewRect.y))
        );
        verticalOffset = Math.min(offsetTopBound, topAfterScrollJump);
      }
    } else if (scrollType == ScrollType.MAKE_VISIBLE) {
      if (viewRect.y > offsetTopBound || viewRect.y + viewRect.height < offsetBottomBound) {
        verticalOffset = centerPosition;
      }
    }

    verticalOffset = Math.max(0, verticalOffset);
    verticalOffset = Math.min(scrollHeight, verticalOffset);

    return verticalOffset;
  }


  /**
   * @param editor target editor
   * @param scrollOffset scroll offset value. Should be positive for bottom scroll bound and negative for top scroll bound
   * @param point target point that we are scrolling to
   * @return y-coordinate of the line obtained by adding scroll offset to the given point
   */
  private static int addVerticalOffsetToPosition(@NotNull Editor editor, int scrollOffset, @NotNull Point point) {
    boolean isUseSoftWraps = editor.getSettings().isUseSoftWraps();
    if (scrollOffset > 0) {
      // if we are calculating the bottom scroll bound, add scroll offset to the end of logical line containing the given point,
      // because we want to see some following lines and not just wrapped visual lines of the same logical line
      VisualPosition pointLogicalLineEnd = getLogicalLineEnd(editor, point, isUseSoftWraps);
      VisualPosition bottomLine = new VisualPosition(pointLogicalLineEnd.line + scrollOffset, 0);
      return editor.visualPositionToXY(bottomLine).y;
    }
    else if (scrollOffset < 0) {
      // we count offset from logical line start to see previous lines (not the same line content if soft wrap is enabled)
      VisualPosition pointLineStart = getLogicalLineStart(editor, point, isUseSoftWraps);
      VisualPosition topLine = new VisualPosition(Math.max(0, pointLineStart.line + scrollOffset), 0);
      // If soft wraps are enabled, last visual lines of one logical line may be not so helpful. That's why we scroll to logical line start
      topLine = getLogicalLineStart(editor, topLine, isUseSoftWraps);
      return editor.visualPositionToXY(topLine).y;
    } else {
      return point.y;
    }
  }

  private static @NotNull VisualPosition getLogicalLineEnd(@NotNull Editor editor, @NotNull Point point, boolean isUseSoftWraps) {
    if (isUseSoftWraps) {
      LogicalPosition logicalPosition = editor.xyToLogicalPosition(point);
      if (logicalPosition.line < 0) {
        return new VisualPosition(0, 0);
      }

      int lineCount = editor.getDocument().getLineCount();
      if (logicalPosition.line < lineCount) {
        int endOffset = editor.getDocument().getLineEndOffset(logicalPosition.line);
        return editor.offsetToVisualPosition(endOffset);
      }
    }
    return editor.xyToVisualPosition(point);
  }

  private static @NotNull VisualPosition getLogicalLineStart(@NotNull Editor editor, @NotNull Point point, boolean isUseSoftWraps) {
    if (isUseSoftWraps) {
      LogicalPosition logicalPosition = editor.xyToLogicalPosition(point);
      return editor.logicalToVisualPosition(new LogicalPosition(logicalPosition.line, 0));
    } else {
      VisualPosition visualPosition = editor.xyToVisualPosition(point);
      return new VisualPosition(visualPosition.line, 0);
    }
  }

  private static @NotNull VisualPosition getLogicalLineStart(@NotNull Editor editor, @NotNull VisualPosition visualPosition, boolean isUseSoftWraps) {
    if (isUseSoftWraps) {
      LogicalPosition logicalPosition = editor.visualToLogicalPosition(visualPosition);
      return editor.logicalToVisualPosition(new LogicalPosition(logicalPosition.line, 0));
    } else {
      return visualPosition;
    }
  }

  @Nullable
  public JScrollBar getVerticalScrollBar() {
    assertIsDispatchThread();
    JScrollPane scrollPane = mySupplier.getScrollPane();
    return scrollPane.getVerticalScrollBar();
  }

  @Nullable
  public JScrollBar getHorizontalScrollBar() {
    assertIsDispatchThread();
    return mySupplier.getScrollPane().getHorizontalScrollBar();
  }

  @Override
  public int getVerticalScrollOffset() {
    return getOffset(getVerticalScrollBar());
  }

  @Override
  public int getHorizontalScrollOffset() {
    return getOffset(getHorizontalScrollBar());
  }

  private static int getOffset(JScrollBar scrollBar) {
    return scrollBar == null ? 0 :
           scrollBar instanceof Interpolable ? ((Interpolable)scrollBar).getTargetValue() : scrollBar.getValue();
  }

  private static int getExtent(JScrollBar scrollBar) {
    return scrollBar == null ? 0 : scrollBar.getModel().getExtent();
  }

  @Override
  public void scrollVertically(int scrollOffset) {
    scroll(getHorizontalScrollOffset(), scrollOffset);
  }

  private void _scrollVertically(int scrollOffset) {
    assertIsDispatchThread();

    JScrollBar scrollbar = mySupplier.getScrollPane().getVerticalScrollBar();

    scrollbar.setValue(scrollOffset);
  }

  @Override
  public void scrollHorizontally(int scrollOffset) {
    scroll(scrollOffset, getVerticalScrollOffset());
  }

  private void _scrollHorizontally(int scrollOffset) {
    assertIsDispatchThread();

    JScrollBar scrollbar = mySupplier.getScrollPane().getHorizontalScrollBar();
    scrollbar.setValue(scrollOffset);
  }

  @Override
  public void scroll(int hOffset, int vOffset) {
    if (myAccumulateViewportChanges) {
      myAccumulatedXOffset = hOffset;
      myAccumulatedYOffset = vOffset;
      return;
    }

    cancelAnimatedScrolling(false);

    Editor editor = mySupplier.getEditor();
    boolean useAnimation;
    //System.out.println("myCurrentCommandStart - myLastCommandFinish = " + (myCurrentCommandStart - myLastCommandFinish));
    if (!editor.getSettings().isAnimatedScrolling() || myAnimationDisabled || RemoteDesktopService.isRemoteSession()) {
      useAnimation = false;
    }
    else if (CommandProcessor.getInstance().getCurrentCommand() == null) {
      useAnimation = editor.getComponent().isShowing();
    }
    else {
      VisibleEditorsTracker editorTracker = VisibleEditorsTracker.getInstance();
      useAnimation = editorTracker.wasEditorVisibleOnCommandStart(editor);
    }

    cancelAnimatedScrolling(false);

    if (useAnimation) {
      //System.out.println("scrollToAnimated: " + endVOffset);

      int startHOffset = getHorizontalScrollOffset();
      int startVOffset = getVerticalScrollOffset();

      if (startHOffset == hOffset && startVOffset == vOffset) {
        return;
      }

      //System.out.println("startVOffset = " + startVOffset);

      try {
        myCurrentAnimationRequest = new AnimatedScrollingRunnable(startHOffset, startVOffset, hOffset, vOffset);
      }
      catch (NoAnimationRequiredException e) {
        _scrollHorizontally(hOffset);
        _scrollVertically(vOffset);
      }
    }
    else {
      _scrollHorizontally(hOffset);
      _scrollVertically(vOffset);
    }
  }

  @Override
  public void addVisibleAreaListener(@NotNull VisibleAreaListener listener) {
    myVisibleAreaListeners.add(listener);
  }

  @Override
  public void removeVisibleAreaListener(@NotNull VisibleAreaListener listener) {
    boolean success = myVisibleAreaListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void finishAnimation() {
    cancelAnimatedScrolling(true);
  }

  @Nullable
  private AnimatedScrollingRunnable cancelAnimatedScrolling(boolean scrollToTarget) {
    AnimatedScrollingRunnable request = myCurrentAnimationRequest;
    myCurrentAnimationRequest = null;
    if (request != null) {
      request.cancel(scrollToTarget);
    }
    return request;
  }

  public void dispose() {
    mySupplier.getEditor().getDocument().removeDocumentListener(myDocumentListener);
    mySupplier.getScrollPane().getViewport().removeChangeListener(myViewportChangeListener);
  }

  public void beforeModalityStateChanged() {
    cancelAnimatedScrolling(true);
  }

  public boolean isScrollingNow() {
    return myCurrentAnimationRequest != null;
  }

  @Override
  public void accumulateViewportChanges() {
    myAccumulateViewportChanges = true;
  }

  @Override
  public void flushViewportChanges() {
    myAccumulateViewportChanges = false;
    if (myAccumulatedXOffset >= 0 && myAccumulatedYOffset >= 0) {
      scroll(myAccumulatedXOffset, myAccumulatedYOffset);
      myAccumulatedXOffset = myAccumulatedYOffset = -1;
      cancelAnimatedScrolling(true);
    }
  }

  void onBulkDocumentUpdateStarted() {
    cancelAnimatedScrolling(true);
  }

  public void addScrollRequestListener(ScrollRequestListener scrollRequestListener, Disposable parentDisposable) {
    myScrollRequestListeners.add(scrollRequestListener);
    Disposer.register(parentDisposable, () -> myScrollRequestListeners.remove(scrollRequestListener));
  }

  private final class AnimatedScrollingRunnable {

    private final int myStartHOffset;
    private final int myStartVOffset;
    private final int myEndHOffset;
    private final int myEndVOffset;

    private final ArrayList<Runnable> myPostRunnables = new ArrayList<>();

    private final JBAnimator myAnimator;

    AnimatedScrollingRunnable(int startHOffset,
                                     int startVOffset,
                                     int endHOffset,
                                     int endVOffset) throws NoAnimationRequiredException {
      myStartHOffset = startHOffset;
      myStartVOffset = startVOffset;
      myEndHOffset = endHOffset;
      myEndVOffset = endVOffset;

      myAnimator = new JBAnimator()
        .setPeriod(4)
        .setName("Scrolling Model Animator");
      myAnimator.animate(
        Animations
          .animation((fraction) -> {
            final int hOffset = (int)(myStartHOffset + (myEndHOffset - myStartHOffset) * fraction + 0.5);
            final int vOffset = (int)(myStartVOffset + (myEndVOffset - myStartVOffset) * fraction + 0.5);

            _scrollHorizontally(hOffset);
            _scrollVertically(vOffset);
          })
          .setDuration(getScrollDuration())
          .setEasing(Easing.EASE_OUT)
          .runWhenExpired(() -> finish(true))
      );
    }

    int getScrollDuration() {
      var defaultDuration = Registry.intValue("idea.editor.smooth.scrolling.navigation.duration", 100);
      if (defaultDuration < 0) {
        return 0;
      }
      // old calculation for animation duration decreasing
      int HDist = Math.abs(myEndHOffset - myStartHOffset);
      int VDist = Math.abs(myEndVOffset - myStartVOffset);
      double totalDist = Math.hypot(HDist, VDist);

      int lineHeight = mySupplier.getEditor().getLineHeight();
      double lineDist = totalDist / lineHeight;
      double part = MathUtil.clamp((lineDist - 1) / 10, 0, 1);
      return (int)Math.round(part * defaultDuration);
    }

    @NotNull
    Rectangle getTargetVisibleArea() {
      Rectangle viewRect = getVisibleArea();
      return new Rectangle(myEndHOffset, myEndVOffset, viewRect.width, viewRect.height);
    }

    public void cancel(boolean scrollToTarget) {
      assertIsDispatchThread();
      finish(scrollToTarget);
    }

    void addPostRunnable(Runnable runnable) {
      myPostRunnables.add(runnable);
    }

    private void finish(boolean scrollToTarget) {
      if (scrollToTarget || !myPostRunnables.isEmpty()) {
        _scrollHorizontally(myEndHOffset);
        _scrollVertically(myEndVOffset);
        executePostRunnables();
      }

      Disposer.dispose(myAnimator);
      if (myCurrentAnimationRequest == this) {
        myCurrentAnimationRequest = null;
      }
    }

    private void executePostRunnables() {
      for (Runnable runnable : myPostRunnables) {
        runnable.run();
      }
    }
  }

  private static final class NoAnimationRequiredException extends Exception {
  }

  @DirtyUI
  private final class MyChangeListener implements ChangeListener {
    private Rectangle myLastViewRect;

    @DirtyUI
    @Override
    public void stateChanged(ChangeEvent event) {
      ReadAction.run(() -> {
        Rectangle viewRect = getVisibleArea();
        VisibleAreaEvent visibleAreaEvent = new VisibleAreaEvent(mySupplier.getEditor(), myLastViewRect, viewRect);
        if (!myViewportPositioned && viewRect.height > 0) {
          myViewportPositioned = true;
          if (adjustVerticalOffsetIfNecessary()) {
            return;
          }
        }
        myLastViewRect = viewRect;
        for (VisibleAreaListener listener : myVisibleAreaListeners) {
          listener.visibleAreaChanged(visibleAreaEvent);
        }
      });
    }
  }

  private static final class DefaultEditorSupplier implements ScrollingModel.Supplier {

    private final EditorEx myEditor;

    private final ScrollingHelper myScrollingHelper = new ScrollingHelper() {
      @Override
      public @NotNull Point calculateScrollingLocation(@NotNull Editor editor, @NotNull VisualPosition pos) {
        return editor.visualPositionToXY(pos);
      }

      @Override
      public @NotNull Point calculateScrollingLocation(@NotNull Editor editor, @NotNull LogicalPosition pos) {
        return editor.logicalPositionToXY(pos);
      }
    };

    private DefaultEditorSupplier(@NotNull EditorEx editor) { myEditor = editor; }

    @Override
    public @NotNull Editor getEditor() {
      return myEditor;
    }

    @Override
    public @NotNull JScrollPane getScrollPane() {
      return myEditor.getScrollPane();
    }

    @Override
    public @NotNull ScrollingHelper getScrollingHelper() {
      return myScrollingHelper;
    }
  }
}
