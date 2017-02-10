/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class SyncScrollSupport implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.util.SyncScrollSupport");
  private boolean myDuringVerticalScroll = false;
  @NotNull private final ArrayList<ScrollListener> myScrollers = new ArrayList<>();
  private boolean myEnabled = true;

  public void install(EditingSides[] sideContainers) {
    Disposer.dispose(this);
    Editor[] editors = new Editor[sideContainers.length + 1];
    editors[0] = sideContainers[0].getEditor(FragmentSide.SIDE1);
    for (int i = 0; i < sideContainers.length; i++) {
      EditingSides sideContainer = sideContainers[i];
      LOG.assertTrue(sideContainer.getEditor(FragmentSide.SIDE1) == editors[i]);
      editors[i + 1] = sideContainer.getEditor(FragmentSide.SIDE2);
    }
    if (editors.length == 3) install3(editors, sideContainers);
    else if (editors.length == 2) install2(editors, sideContainers);
    else LOG.error(String.valueOf(editors.length));
  }

  public void dispose() {
    for (ScrollListener scrollListener : myScrollers) {
      Disposer.dispose(scrollListener);
    }
    myScrollers.clear();
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  private void install2(@NotNull Editor[] editors, @NotNull EditingSides[] sideContainers) {
    addSlavesScroller(editors[0], new ScrollingContext(FragmentSide.SIDE1, sideContainers[0], FragmentSide.SIDE1));
    addSlavesScroller(editors[1], new ScrollingContext(FragmentSide.SIDE2, sideContainers[0], FragmentSide.SIDE2));
  }

  private void install3(@NotNull Editor[] editors, @NotNull EditingSides[] sideContainers) {
    addSlavesScroller(editors[0],
                      new ScrollingContext(FragmentSide.SIDE1, sideContainers[0], FragmentSide.SIDE2),
                      new ScrollingContext(FragmentSide.SIDE1, sideContainers[1], FragmentSide.SIDE1));
    addSlavesScroller(editors[1],
                      new ScrollingContext(FragmentSide.SIDE2, sideContainers[0], FragmentSide.SIDE1),
                      new ScrollingContext(FragmentSide.SIDE1, sideContainers[1], FragmentSide.SIDE1));
    addSlavesScroller(editors[2],
                      new ScrollingContext(FragmentSide.SIDE2, sideContainers[1], FragmentSide.SIDE2),
                      new ScrollingContext(FragmentSide.SIDE2, sideContainers[0], FragmentSide.SIDE1));
  }

  private void addSlavesScroller(@NotNull Editor editor, @NotNull ScrollingContext... contexts) {
    ScrollListener scroller = new ScrollListener(contexts, editor);
    scroller.install();
    myScrollers.add(scroller);
  }

  private class ScrollListener implements VisibleAreaListener, Disposable {
    private ScrollingContext[] myScrollContexts;
    @NotNull private final Editor myEditor;

    public ScrollListener(@NotNull ScrollingContext[] scrollContexts, @NotNull Editor editor) {
      myScrollContexts = scrollContexts;
      myEditor = editor;
    }

    public void install() {
      myEditor.getScrollingModel().addVisibleAreaListener(this);
    }

    @Override
    public void dispose() {
      myEditor.getScrollingModel().removeVisibleAreaListener(this);
      myScrollContexts = null;
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (!myEnabled || myDuringVerticalScroll) return;
      Rectangle newRectangle = e.getNewRectangle();
      Rectangle oldRectangle = e.getOldRectangle();
      if (newRectangle == null || oldRectangle == null) return;
      myDuringVerticalScroll = true;
      try {
        for (ScrollingContext context : myScrollContexts) {
          syncVerticalScroll(context, newRectangle, oldRectangle);
          syncHorizontalScroll(context, newRectangle, oldRectangle);
        }
      }
      finally { myDuringVerticalScroll = false; }
    }
  }

  private static void syncHorizontalScroll(@NotNull ScrollingContext context,
                                           @NotNull Rectangle newRectangle,
                                           @NotNull Rectangle oldRectangle) {
    int newScrollOffset = newRectangle.x;
    if (newScrollOffset == oldRectangle.x) return;
    EditingSides sidesContainer = context.getSidesContainer();
    FragmentSide masterSide = context.getMasterSide();
    Editor slaveEditor = sidesContainer.getEditor(masterSide.otherSide());
    if (slaveEditor == null) return;

    doScrollHorizontally(slaveEditor.getScrollingModel(), newScrollOffset);
  }

  private static void syncVerticalScroll(@NotNull ScrollingContext context,
                                         @NotNull Rectangle newRectangle,
                                         @NotNull Rectangle oldRectangle) {
    if (newRectangle.y == oldRectangle.y) return;
    EditingSides sidesContainer = context.getSidesContainer();
    FragmentSide masterSide = context.getMasterSide();
    FragmentSide masterDiffSide = context.getMasterDiffSide();

    Editor master = sidesContainer.getEditor(masterSide);
    Editor slave = sidesContainer.getEditor(masterSide.otherSide());

    if (master == null || slave == null) return;
    if (master.isDisposed() || slave.isDisposed()) return;

    int masterVerticalScrollOffset = master.getScrollingModel().getVerticalScrollOffset();
    int slaveVerticalScrollOffset = slave.getScrollingModel().getVerticalScrollOffset();

    Rectangle viewRect = master.getScrollingModel().getVisibleArea();
    int middleY = viewRect.height / 3;

    if (master.getDocument().getTextLength() == 0) return;

    LogicalPosition masterPos = master.xyToLogicalPosition(new Point(viewRect.x, masterVerticalScrollOffset + middleY));
    int masterCenterLine = masterPos.line;
    int scrollToLine = sidesContainer.getLineBlocks().transform(masterDiffSide, masterCenterLine);

    int offset;
    if (scrollToLine < 0) {
      offset = slaveVerticalScrollOffset + newRectangle.y - oldRectangle.y;
    }
    else {
      int correction = (masterVerticalScrollOffset + middleY) % master.getLineHeight();
      Point point = slave.logicalPositionToXY(new LogicalPosition(scrollToLine, masterPos.column));
      offset = point.y - middleY + correction;
    }

    int deltaHeaderOffset = getHeaderOffset(slave) - getHeaderOffset(master);
    doScrollVertically(slave.getScrollingModel(), offset + deltaHeaderOffset);
  }

  private static int getHeaderOffset(@NotNull final Editor editor) {
    final JComponent header = editor.getHeaderComponent();
    return header == null ? 0 : header.getHeight();
  }

  private static void doScrollVertically(@NotNull ScrollingModel model, int offset) {
    model.disableAnimation();
    try {
      model.scrollVertically(offset);
    }
    finally {
      model.enableAnimation();
    }
  }

  private static void doScrollHorizontally(@NotNull ScrollingModel model, int offset) {
    model.disableAnimation();
    try {
      model.scrollHorizontally(offset);
    }
    finally {
      model.enableAnimation();
    }
  }

  public static void scrollEditor(@NotNull Editor editor, int logicalLine) {
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(logicalLine, 0));
    ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.disableAnimation();
    scrollingModel.scrollToCaret(ScrollType.CENTER);
    scrollingModel.enableAnimation();
  }

  private static class ScrollingContext {
    @NotNull private final EditingSides mySidesContainer;
    @NotNull private final FragmentSide myMasterSide;
    @NotNull private final FragmentSide myMasterDiffSide;

    public ScrollingContext(@NotNull FragmentSide masterSide, @NotNull EditingSides sidesContainer, @NotNull FragmentSide masterDiffSide) {
      mySidesContainer = sidesContainer;
      myMasterSide = masterSide;
      myMasterDiffSide = masterDiffSide;
    }

    @NotNull
    public EditingSides getSidesContainer() {
      return mySidesContainer;
    }

    @NotNull
    public FragmentSide getMasterSide() {
      return myMasterSide;
    }

    @NotNull
    public FragmentSide getMasterDiffSide() {
      return myMasterDiffSide;
    }
  }
}
