/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 10, 2002
 * Time: 10:14:59 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScrollingModelImpl implements ScrollingModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.ScrollingModelImpl");

  private final EditorImpl myEditor;
  private final CopyOnWriteArrayList<VisibleAreaListener> myVisibleAreaListeners = ContainerUtil.createEmptyCOWList();

  private AnimatedScrollingRunnable myCurrentAnimationRequest = null;
  private boolean myAnimationDisabled = false;
  private final DocumentAdapter myDocumentListener;

  public ScrollingModelImpl(EditorImpl editor) {
    myEditor = editor;

    myEditor.getScrollPane().getViewport().addChangeListener(new ChangeListener() {
      private Rectangle myLastViewRect;

      public void stateChanged(ChangeEvent event) {
        Rectangle viewRect = getVisibleArea();
        VisibleAreaEvent visibleAreaEvent = new VisibleAreaEvent(myEditor, myLastViewRect, viewRect);
        myLastViewRect = viewRect;
        for (VisibleAreaListener listener : myVisibleAreaListeners) {
          listener.visibleAreaChanged(visibleAreaEvent);
        }
      }
    });

    myDocumentListener = new DocumentAdapter() {
      public void beforeDocumentChange(DocumentEvent e) {
        cancelAnimatedScrolling(true);
      }
    };
    myEditor.getDocument().addDocumentListener(myDocumentListener);
  }

  public Rectangle getVisibleArea() {
    assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) {
      return new Rectangle(0, 0, 0, 0);
    }
    return myEditor.getScrollPane().getViewport().getViewRect();
  }

  public Rectangle getVisibleAreaOnScrollingFinished() {
    assertIsDispatchThread();
    if (myCurrentAnimationRequest != null) {
      return myCurrentAnimationRequest.getTargetVisibleArea();
    }
    else {
      return getVisibleArea();
    }
  }

  public void scrollToCaret(ScrollType scrollType) {
    assertIsDispatchThread();
    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
    myEditor.validateSize();
    scrollTo(caretPosition, scrollType);
  }

  public void scrollTo(LogicalPosition pos, ScrollType scrollType) {
    assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return;

    AnimatedScrollingRunnable canceledThread = cancelAnimatedScrolling(false);
    Rectangle viewRect = canceledThread != null ? canceledThread.getTargetVisibleArea() : getVisibleArea();

    Point p = calcOffsetsToScroll(pos, scrollType, viewRect);
    scrollToOffsets(p.x, p.y);
  }

  private void assertIsDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditor.getComponent());
  }

  public void runActionOnScrollingFinished(Runnable action) {
    assertIsDispatchThread();

    if (myCurrentAnimationRequest != null) {
      myCurrentAnimationRequest.addPostRunnable(action);
      return;
    }

    action.run();
  }

  public void disableAnimation() {
    myAnimationDisabled = true;
  }

  public void enableAnimation() {
    myAnimationDisabled = false;
  }

  private Point calcOffsetsToScroll(LogicalPosition pos, ScrollType scrollType, Rectangle viewRect) {
    Point targetLocation = myEditor.logicalPositionToXY(pos);

    if (myEditor.getSettings().isRefrainFromScrolling() && viewRect.contains(targetLocation)) {
      if (scrollType == ScrollType.CENTER ||
          scrollType == ScrollType.CENTER_DOWN ||
          scrollType == ScrollType.CENTER_UP) {
        scrollType = ScrollType.RELATIVE;
      }
    }

    int spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, myEditor);
    int xInsets = myEditor.getSettings().getAdditionalColumnsCount() * spaceWidth;

    int hOffset = scrollType == ScrollType.CENTER ||
                  scrollType == ScrollType.CENTER_DOWN ||
                  scrollType == ScrollType.CENTER_UP ? 0 : viewRect.x;
    if (targetLocation.x < hOffset) {
      hOffset = targetLocation.x - 4 * spaceWidth;
      hOffset = hOffset > 0 ? hOffset : 0;
    }
    else if (targetLocation.x > viewRect.x + viewRect.width) {
      hOffset = targetLocation.x - viewRect.width + xInsets;
    }

    int scrollUpBy = viewRect.y + myEditor.getLineHeight() - targetLocation.y;
    int scrollDownBy = targetLocation.y - (viewRect.y + viewRect.height - 2 * myEditor.getLineHeight());
    int centerPosition = targetLocation.y - viewRect.height / 3;

    int vOffset = viewRect.y;
    if (scrollType == ScrollType.CENTER) {
      vOffset = centerPosition;
    }
    else if (scrollType == ScrollType.CENTER_UP) {
      if (scrollUpBy > 0 || scrollDownBy > 0 || vOffset > centerPosition) {
        vOffset = centerPosition;
      }
    }
    else if (scrollType == ScrollType.CENTER_DOWN) {
      if (scrollUpBy > 0 || scrollDownBy > 0 || vOffset < centerPosition) {
        vOffset = centerPosition;
      }
    }
    else if (scrollType == ScrollType.RELATIVE) {
      if (scrollUpBy > 0) {
        vOffset = viewRect.y - scrollUpBy;
      }
      else if (scrollDownBy > 0) {
        vOffset = viewRect.y + scrollDownBy;
      }
    }
    else if (scrollType == ScrollType.MAKE_VISIBLE) {
      if (scrollUpBy > 0 || scrollDownBy > 0) {
        vOffset = centerPosition;
      }
    }

    JScrollPane scrollPane = myEditor.getScrollPane();
    hOffset = Math.max(0, hOffset);
    vOffset = Math.max(0, vOffset);
    hOffset = Math.min(scrollPane.getHorizontalScrollBar().getMaximum() - getExtent(scrollPane.getHorizontalScrollBar()), hOffset);
    vOffset = Math.min(scrollPane.getVerticalScrollBar().getMaximum() - getExtent(scrollPane.getVerticalScrollBar()), vOffset);

    return new Point(hOffset, vOffset);
  }

  @Nullable
  public JScrollBar getVerticalScrollBar() {
    assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return null;

    return myEditor.getScrollPane().getVerticalScrollBar();
  }

  @Nullable
  public JScrollBar getHorizontalScrollBar() {
    assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return null;

    return myEditor.getScrollPane().getHorizontalScrollBar();
  }

  public int getVerticalScrollOffset() {
    return getOffset(getVerticalScrollBar());
  }

  public int getHorizontalScrollOffset() {
    return getOffset(getHorizontalScrollBar());
  }

  private static int getOffset(JScrollBar scrollBar) {
    return scrollBar == null ? 0 : scrollBar.getValue();
  }

  private static int getExtent(JScrollBar scrollBar) {
    return scrollBar == null ? 0 : scrollBar.getModel().getExtent();
  }

  public void scrollVertically(int scrollOffset) {
    scrollToOffsets(getHorizontalScrollOffset(), scrollOffset);
  }

  private void _scrollVertically(int scrollOffset) {
    assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return;

    myEditor.validateSize();
    JScrollBar scrollbar = myEditor.getScrollPane().getVerticalScrollBar();

    if (scrollbar.getVisibleAmount() < Math.abs(scrollOffset - scrollbar.getValue()) + 50) {
      myEditor.stopOptimizedScrolling();
    }

    scrollbar.setValue(scrollOffset);

    //System.out.println("scrolled vertically to: " + scrollOffset);
  }

  public void scrollHorizontally(int scrollOffset) {
    scrollToOffsets(scrollOffset, getVerticalScrollOffset());
  }

  private void _scrollHorizontally(int scrollOffset) {
    assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return;

    myEditor.validateSize();
    JScrollBar scrollbar = myEditor.getScrollPane().getHorizontalScrollBar();
    scrollbar.setValue(scrollOffset);
  }

  private void scrollToOffsets(int hOffset, int vOffset) {
    cancelAnimatedScrolling(false);

    VisibleEditorsTracker editorsTracker = VisibleEditorsTracker.getInstance();
    boolean useAnimation;
    //System.out.println("myCurrentCommandStart - myLastCommandFinish = " + (myCurrentCommandStart - myLastCommandFinish));
    if (!myEditor.getSettings().isAnimatedScrolling() || myAnimationDisabled || UISettings.isRemoteDesktopConnected()) {
      useAnimation = false;
    }
    else if (CommandProcessor.getInstance().getCurrentCommand() == null) {
      useAnimation = myEditor.getComponent().isShowing();
    }
    else if (editorsTracker.getCurrentCommandStart() - editorsTracker.getLastCommandFinish() <
             AnimatedScrollingRunnable.SCROLL_DURATION) {
      useAnimation = false;
    }
    else {
      useAnimation = editorsTracker.wasEditorVisibleOnCommandStart(myEditor);
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

  public void addVisibleAreaListener(VisibleAreaListener listener) {
    myVisibleAreaListeners.add(listener);
  }

  public void removeVisibleAreaListener(VisibleAreaListener listener) {
    boolean success = myVisibleAreaListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void commandStarted() {
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
    myEditor.getDocument().removeDocumentListener(myDocumentListener);
  }

  public void beforeModalityStateChanged() {
    cancelAnimatedScrolling(true);
  }

  public boolean isScrollingNow() {
    return myCurrentAnimationRequest != null;
  }

  private class AnimatedScrollingRunnable {
    private static final int SCROLL_DURATION = 150;
    private static final int SCROLL_INTERVAL = 10;

    private final Timer myTimer;
    private int myTicksCount = 0;

    private final int myStartHOffset;
    private final int myStartVOffset;
    private final int myEndHOffset;
    private final int myEndVOffset;
    private final int myAnimationDuration;

    private final ArrayList<Runnable> myPostRunnables = new ArrayList<Runnable>();

    private final Runnable myStartCommand;
    private final int myHDist;
    private final int myVDist;
    private final int myMaxDistToScroll;
    private final double myTotalDist;
    private final double myScrollDist;

    private final int myStepCount;
    private final double myPow;

    public AnimatedScrollingRunnable(int startHOffset,
                                     int startVOffset,
                                     int endHOffset,
                                     int endVOffset) throws NoAnimationRequiredException {
      myStartHOffset = startHOffset;
      myStartVOffset = startVOffset;
      myEndHOffset = endHOffset;
      myEndVOffset = endVOffset;

      myHDist = Math.abs(myEndHOffset - myStartHOffset);
      myVDist = Math.abs(myEndVOffset - myStartVOffset);

      myMaxDistToScroll = myEditor.getLineHeight() * 50;
      myTotalDist = Math.sqrt((double)myHDist * myHDist + (double)myVDist * myVDist);
      myScrollDist = Math.min(myTotalDist, myMaxDistToScroll);
      myAnimationDuration = calcAnimationDuration();
      if (myAnimationDuration < SCROLL_INTERVAL * 2) {
        throw new NoAnimationRequiredException();
      }
      myStepCount = myAnimationDuration / SCROLL_INTERVAL - 1;
      double firstStepTime = 1.0 / myStepCount;
      double firstScrollDist = 5.0;
      if (myTotalDist > myScrollDist) {
        firstScrollDist *= myTotalDist / myScrollDist;
        firstScrollDist = Math.min(firstScrollDist, myEditor.getLineHeight() * 5);
      }
      myPow = myScrollDist > 0 ? setupPow(firstStepTime, firstScrollDist / myScrollDist) : 1;

      myStartCommand = CommandProcessor.getInstance().getCurrentCommand();

      myTimer = new Timer(SCROLL_INTERVAL, new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          tick();
        }
      });
      myTimer.setRepeats(true);
      myTimer.start();
    }

    public Rectangle getTargetVisibleArea() {
      Rectangle viewRect = getVisibleArea();
      return new Rectangle(myEndHOffset, myEndVOffset, viewRect.width, viewRect.height);
    }

    public Runnable getStartCommand() {
      return myStartCommand;
    }

    private void tick() {
      double time = (myTicksCount + 1) / (double)myStepCount;
      double fraction = timeToFraction(time);

      final int hOffset = (int)(myStartHOffset + (myEndHOffset - myStartHOffset) * fraction + 0.5);
      final int vOffset = (int)(myStartVOffset + (myEndVOffset - myStartVOffset) * fraction + 0.5);

      _scrollHorizontally(hOffset);
      _scrollVertically(vOffset);

      if (++myTicksCount == myStepCount) {
        finish(true);
      }
    }

    public void cancel(boolean scrollToTarget) {
      assertIsDispatchThread();
      finish(scrollToTarget);
    }

    public void addPostRunnable(Runnable runnable) {
      myPostRunnables.add(runnable);
    }

    private void finish(boolean scrollToTarget) {
      if (scrollToTarget || !myPostRunnables.isEmpty()) {
        _scrollHorizontally(myEndHOffset);
        _scrollVertically(myEndVOffset);
        executePostRunnables();
      }

      myTimer.stop();
      if (myCurrentAnimationRequest == this) {
        myCurrentAnimationRequest = null;
      }
    }

    private void executePostRunnables() {
      for (Runnable runnable : myPostRunnables) {
        runnable.run();
      }
    }

    private double timeToFraction(double time) {
      if (time > 0.5) {
        return 1 - timeToFraction(1 - time);
      }

      double fraction = Math.pow(time * 2, myPow) / 2;

      if (myTotalDist > myMaxDistToScroll) {
        fraction *= (double)myMaxDistToScroll / myTotalDist;
      }

      return fraction;
    }

    private double setupPow(double inTime, double moveBy) {
      double pow = Math.log(2 * moveBy) / Math.log(2 * inTime);
      if (pow < 1) pow = 1;
      return pow;
    }

    private int calcAnimationDuration() {
      int lineHeight = myEditor.getLineHeight();
      double lineDist = myTotalDist / lineHeight;
      double part = (lineDist - 1) / 10;
      if (part > 1) part = 1;
      int duration = (int)(part * SCROLL_DURATION);
      //System.out.println("duration = " + duration);
      return duration;
    }
  }

  private static class NoAnimationRequiredException extends Exception {
  }
}
