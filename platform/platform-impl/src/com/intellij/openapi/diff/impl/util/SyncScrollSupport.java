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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class SyncScrollSupport implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.util.SyncScrollSupport");
  private boolean myDuringVerticalScroll = false;
  private final ArrayList<ScrollListener> myScrollers = new ArrayList<ScrollListener>();

  public void install(EditingSides[] sideContainers) {
    dispose();
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
      scrollListener.dispose();
    }
    myScrollers.clear();
  }

  private void install2(Editor[] editors, EditingSides[] sideContainers) {
    addSlavesScroller(editors[0], new Pair<FragmentSide, EditingSides>(FragmentSide.SIDE1, sideContainers[0]));
    addSlavesScroller(editors[1], new Pair<FragmentSide, EditingSides>(FragmentSide.SIDE2, sideContainers[0]));
  }

  private void install3(Editor[] editors, EditingSides[] sideContainers) {
    addSlavesScroller(editors[0],
                      new Pair<FragmentSide, EditingSides>(FragmentSide.SIDE1, sideContainers[0]),
                      new Pair<FragmentSide, EditingSides>(FragmentSide.SIDE1, sideContainers[1]));
    addSlavesScroller(editors[1],
                      new Pair<FragmentSide, EditingSides>(FragmentSide.SIDE2, sideContainers[0]),
                      new Pair<FragmentSide, EditingSides>(FragmentSide.SIDE1, sideContainers[1]));
    addSlavesScroller(editors[2],
                      new Pair<FragmentSide, EditingSides>(FragmentSide.SIDE2, sideContainers[1]),
                      new Pair<FragmentSide, EditingSides>(FragmentSide.SIDE2, sideContainers[0]));
  }

  private void addSlavesScroller(Editor editor, Pair<FragmentSide, EditingSides>... contexts) {
    ScrollListener scroller = new ScrollListener(contexts, editor);
    scroller.install();
    myScrollers.add(scroller);
  }

  private class ScrollListener implements VisibleAreaListener, Disposable {
    private final Pair<FragmentSide, EditingSides>[] myScrollContexts;
    private final Editor myEditor;

    public ScrollListener(Pair<FragmentSide, EditingSides>[] scrollContexts, Editor editor) {
      myScrollContexts = scrollContexts;
      myEditor = editor;
      install();
    }

    public void install() {
      myEditor.getScrollingModel().addVisibleAreaListener(this);
    }

    public void dispose() {
      myEditor.getScrollingModel().removeVisibleAreaListener(this);
    }

    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (myDuringVerticalScroll) return;
      Rectangle newRectangle = e.getNewRectangle();
      Rectangle oldRectangle = e.getOldRectangle();
      if (newRectangle == null || oldRectangle == null) return;
      myDuringVerticalScroll = true;
      try {
        for (Pair<FragmentSide, EditingSides> context : myScrollContexts) {
          syncVerticalScroll(context, newRectangle, oldRectangle);
          syncHorizontalScroll(context, newRectangle, oldRectangle);
        }
      }
      finally { myDuringVerticalScroll = false; }
    }
  }

  private static void syncHorizontalScroll(Pair<FragmentSide,EditingSides> context, Rectangle newRectangle, Rectangle oldRectangle) {
    int newScrollOffset = newRectangle.x;
    if (newScrollOffset == oldRectangle.x) return;
    EditingSides sidesContainer = context.getSecond();
    FragmentSide masterSide = context.getFirst();
    Editor slaveEditor = sidesContainer.getEditor(masterSide.otherSide());
    if (slaveEditor == null) return;

    ScrollingModel scrollingModel = slaveEditor.getScrollingModel();
    scrollingModel.disableAnimation();
    scrollingModel.scrollHorizontally(newScrollOffset);
    scrollingModel.enableAnimation();
  }

  private static void syncVerticalScroll(Pair<FragmentSide,EditingSides> context, Rectangle newRectangle, Rectangle oldRectangle) {
    if (newRectangle.y == oldRectangle.y && newRectangle.height == oldRectangle.height) return;
    EditingSides sidesContainer = context.getSecond();
    FragmentSide masterSide = context.getFirst();

    Editor master = sidesContainer.getEditor(masterSide);
    Editor slave = sidesContainer.getEditor(masterSide.otherSide());

    if (master == null || slave == null) return;

    Rectangle viewRect = master.getScrollingModel().getVisibleArea();
    int middleY = viewRect.height / 3;

    int masterVerticalScrollOffset = getScrollOffset(master);
    int slaveVerticalScrollOffset = getScrollOffset(slave);

    LogicalPosition masterPos = master.xyToLogicalPosition(new Point(viewRect.x, masterVerticalScrollOffset + middleY));
    int masterCenterLine = masterPos.line;
    if (masterCenterLine > master.getDocument().getLineCount()) {
      masterCenterLine = master.getDocument().getLineCount();
    }
    int scrollToLine = sidesContainer.getLineBlocks().transform(masterSide, masterCenterLine) + 1;
    int actualLine = scrollToLine - 1;


    slave.getScrollingModel().disableAnimation();
    try {
      if (scrollToLine <= 0) {
        int offset = newRectangle.y - oldRectangle.y;
        slave.getScrollingModel().scrollVertically(slaveVerticalScrollOffset + offset);
      }
      else {
        int correction = (masterVerticalScrollOffset + middleY) % master.getLineHeight();
        Point point = slave.logicalPositionToXY(new LogicalPosition(actualLine, masterPos.column));
        slave.getScrollingModel().scrollVertically(point.y - middleY + correction);
      }
    } finally {
      slave.getScrollingModel().enableAnimation();
    }
  }

  private static int getScrollOffset(final Editor editor) {
    final JComponent header = editor.getHeaderComponent();
    int headerOffset = header == null ? 0 : header.getHeight();
    
    return editor.getScrollingModel().getVerticalScrollOffset() - headerOffset;
  }

  public static void scrollEditor(Editor editor, int logicalLine) {
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(logicalLine, 0));
    ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.disableAnimation();
    scrollingModel.scrollToCaret(ScrollType.CENTER);
    scrollingModel.enableAnimation();
  }
}
