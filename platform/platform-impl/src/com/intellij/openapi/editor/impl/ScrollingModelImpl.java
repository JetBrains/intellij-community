// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.components.Interpolable;
import com.intellij.util.MathUtil;
import com.intellij.util.animation.Animations;
import com.intellij.util.animation.Easing;
import com.intellij.util.animation.JBAnimator;
import com.intellij.util.concurrency.annotations.RequiresEdt;
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

  private final @NotNull ScrollingModel.Supplier mySupplier;
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

  @Override
  @RequiresEdt
  public @NotNull Rectangle getVisibleArea() {
    return mySupplier.getScrollPane().getViewport().getViewRect();
  }

  @Override
  @RequiresEdt
  public @NotNull Rectangle getVisibleAreaOnScrollingFinished() {
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
  @RequiresEdt
  public void scrollToCaret(@NotNull ScrollType scrollType) {
    if (LOG.isTraceEnabled()) {
      LOG.trace(new Throwable());
    }

    Editor editor = mySupplier.getEditor();
    AsyncEditorLoader.performWhenLoaded(editor, () -> {
      VisualPosition visualPosition = editor.getCaretModel().getVisualPosition();
      LogicalPosition logicalPosition = editor.visualToLogicalPosition(visualPosition);
      for (ScrollRequestListener listener : myScrollRequestListeners) {
        listener.scrollRequested(logicalPosition, scrollType);
      }
      scrollTo(mySupplier.getScrollingHelper().calculateScrollingLocation(editor, visualPosition), scrollType);
    });
  }

  private void scrollTo(@NotNull Point targetLocation, @NotNull ScrollType scrollType) {
    AnimatedScrollingRunnable canceledThread = cancelAnimatedScrolling(false);
    Rectangle viewRect = canceledThread == null ? getVisibleArea() : canceledThread.getTargetVisibleArea();

    // the model knows nothing about the sticky panel rendering on top of the editor
    // we should adjust target and view rectangle to avoid hidden caret by the sticky panel
    targetLocation = stickyPanelAdjust(targetLocation, viewRect);

    Point p = calcOffsetsToScroll(targetLocation, scrollType, viewRect);
    scroll(p.x, p.y);
  }

  private @NotNull Point stickyPanelAdjust(@NotNull Point targetLocation, @NotNull Rectangle viewRect) {
    if (mySupplier.getEditor() instanceof EditorImpl editor) {
      var panel = editor.getStickyLinesPanel();
      if (panel != null && editor.getSettings().areStickyLinesShown()) {
        int height = panel.getHeight();
        if (height > 0) {
          viewRect.height -= height;
          return new Point(targetLocation.x, targetLocation.y - height);
        }
      }
    }
    return targetLocation;
  }

  @Override
  @RequiresEdt
  public void scrollTo(@NotNull LogicalPosition logicalPosition, @NotNull ScrollType scrollType) {
    Editor editor = mySupplier.getEditor();
    AsyncEditorLoader.performWhenLoaded(editor, () -> {
      for (ScrollRequestListener listener : myScrollRequestListeners) {
        listener.scrollRequested(logicalPosition, scrollType);
      }
      scrollTo(mySupplier.getScrollingHelper().calculateScrollingLocation(editor, logicalPosition), scrollType);
    });
  }

  @Override
  @RequiresEdt
  public void runActionOnScrollingFinished(@NotNull Runnable action) {
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
    return ApplicationManager.getApplication().getService(ScrollPositionCalculator.class)
      .calcOffsetsToScroll(mySupplier.getEditor(), targetLocation, scrollType, viewRect, mySupplier.getScrollPane());
  }

  @RequiresEdt
  public @Nullable JScrollBar getVerticalScrollBar() {
    JScrollPane scrollPane = mySupplier.getScrollPane();
    return scrollPane.getVerticalScrollBar();
  }

  @RequiresEdt
  public @Nullable JScrollBar getHorizontalScrollBar() {
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

  @Override
  public void scrollVertically(int scrollOffset) {
    scroll(getHorizontalScrollOffset(), scrollOffset);
  }

  @RequiresEdt
  private void _scrollVertically(int scrollOffset) {
    JScrollBar scrollbar = mySupplier.getScrollPane().getVerticalScrollBar();

    scrollbar.setValue(scrollOffset);
  }

  @Override
  public void scrollHorizontally(int scrollOffset) {
    scroll(scrollOffset, getVerticalScrollOffset());
  }

  @RequiresEdt
  private void _scrollHorizontally(int scrollOffset) {
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

  private @Nullable AnimatedScrollingRunnable cancelAnimatedScrolling(boolean scrollToTarget) {
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

    @RequiresEdt
    public void cancel(boolean scrollToTarget) {
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
