// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.ArrayList;
import java.util.List;

final class MacGestureAdapter extends GestureAdapter {
  double magnification;
  private final IdeFrame myFrame;
  private final MouseGestureManager myManager;
  private ZoomableViewport myMagnifyingViewport;

  MacGestureAdapter(MouseGestureManager manager, IdeFrame frame) {
    myFrame = frame;
    magnification = 0;
    myManager = manager;
    GestureUtilities.addGestureListenerTo(frame.getComponent(), this);
  }

  @Override
  public void gestureBegan(GesturePhaseEvent event) {
    magnification = 0;

    PointerInfo pointerInfo = MouseInfo.getPointerInfo();

    if (pointerInfo == null) return;

    Point mouse = new Point(pointerInfo.getLocation());
    SwingUtilities.convertPointFromScreen(mouse, myFrame.getComponent());
    List<Component> componentsUnderMouse = getAllComponentsAt(myFrame.getComponent(), mouse.x, mouse.y);
    ZoomableViewport viewport = findMagnifyingViewport(componentsUnderMouse);
    if (viewport != null) {
      Magnificator magnificator = viewport.getMagnificator();

      if (magnificator != null) {
        Point at = new Point(pointerInfo.getLocation());
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

  public List<Component> getAllComponentsAt(Component parent, int x, int y) {
    List<Component> components = new ArrayList<>();
    if (!parent.contains(x, y)) return components;

    if (parent instanceof Container) {
      Component[] comps = ((Container)parent).getComponents();
      for (int i = comps.length - 1; i >= 0; i--) {
        Component comp = comps[i];
        if (comp == null || !comp.isVisible()) continue;
        Point loc = comp.getLocation();
        if (!comp.contains(x - loc.x, y - loc.y)) continue;
        components.add(comp);
        if (comp instanceof Container) {
          components.addAll(getAllComponentsAt(comp, x - loc.x, y - loc.y));
        }
      }
    }
    components.add(parent);
    return components;
  }

  private static ZoomableViewport findMagnifyingViewport(List<Component> components) {
    for (Component comp : components) {
      ZoomableViewport viewport = (ZoomableViewport)SwingUtilities.getAncestorOfClass(ZoomableViewport.class, comp);
      if (viewport != null && viewport.getMagnificator() != null) {
        return viewport;
      }
    }
    return null;
  }
}
