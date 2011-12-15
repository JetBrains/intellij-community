/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.apple.eawt.event.*;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.components.Magnificator;
import com.intellij.ui.components.ZoomableViewport;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

/**
* User: anna
* Date: 11/29/11
*/
class MacGestureAdapter extends GestureAdapter {
  double magnification;
  private final IdeFrame myFrame;
  private MouseGestureManager myManager;
  private ZoomableViewport myMagnifyingViewport;

  public MacGestureAdapter(MouseGestureManager manager, IdeFrame frame) {
    myFrame = frame;
    magnification = 0;
    myManager = manager;
    GestureUtilities.addGestureListenerTo(frame.getComponent(), this);
  }

  @Override
  public void gestureBegan(GesturePhaseEvent event) {
    magnification = 0;

    Point mouse = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(mouse, myFrame.getComponent());
    Component deepest = SwingUtilities.getDeepestComponentAt(myFrame.getComponent(), mouse.x, mouse.y);
    ZoomableViewport viewport = (ZoomableViewport) SwingUtilities.getAncestorOfClass(ZoomableViewport.class, deepest);
    if (viewport != null) {
      Magnificator magnificator = viewport.getMagnificator();

      if (magnificator != null) {
        Point at = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(at, (JComponent)viewport);
        viewport.magnificationStarted(at);
        myMagnifyingViewport = viewport;
      }
    }
  }

  @Override
  public void gestureEnded(GesturePhaseEvent event) {
    if (myMagnifyingViewport != null) {
      myMagnifyingViewport.magnificationFinished(magnification);
      myMagnifyingViewport = null;
      magnification = 0;
    }
  }

  @Override
  public void swipedLeft(SwipeEvent event) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction forward = actionManager.getAction("Forward");
    if (forward == null) return;

    actionManager.tryToExecute(forward, createMouseEventWrapper(myFrame), null, null, false);
  }

  @Override
  public void swipedRight(SwipeEvent event) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction back = actionManager.getAction("Back");
    if (back == null) return;

    actionManager.tryToExecute(back, createMouseEventWrapper(myFrame), null, null, false);
  }

  private static MouseEvent createMouseEventWrapper(IdeFrame frame) {
    return new MouseEvent(frame.getComponent(), ActionEvent.ACTION_PERFORMED, System.currentTimeMillis(), 0, 0, 0, 0, false, 0);
  }


  @Override
  public void magnify(MagnificationEvent event) {
    myManager.activateTrackpad();
    magnification += event.getMagnification();
    if (myMagnifyingViewport != null) {
      myMagnifyingViewport.magnify(magnification);
    }
  }

  public void remove(JComponent cmp) {
    GestureUtilities.removeGestureListenerFrom(cmp, this);
  }
}
