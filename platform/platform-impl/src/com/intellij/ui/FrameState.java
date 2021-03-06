// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.FrameInfoHelper;
import org.jetbrains.annotations.NotNull;
import sun.awt.AWTAccessor;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.peer.ComponentPeer;
import java.awt.peer.FramePeer;

public class FrameState {
  private Rectangle myBounds;
  private boolean myMaximized;
  private boolean myFullScreen;

  public Point getLocation() {
    return myBounds == null ? null : myBounds.getLocation();
  }

  public Dimension getSize() {
    return myBounds == null ? null : myBounds.getSize();
  }

  public Rectangle getBounds() {
    return myBounds == null ? null : new Rectangle(myBounds);
  }

  public boolean isMaximized() {
    return myMaximized;
  }

  public boolean isFullScreen() {
    return myFullScreen;
  }

  public static int getExtendedState(Component component) {
    int state = Frame.NORMAL;
    if (component instanceof Frame) {
      state = ((Frame)component).getExtendedState();
      if (SystemInfoRt.isMac) {
        // workaround: frame.state is not updated by jdk so get it directly from peer
        ComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(component);
        if (peer instanceof FramePeer) {
          state = ((FramePeer)peer).getState();
        }
      }
    }
    return state;
  }

  private static FrameState findFrameState(@NotNull Component component) {
    for (ComponentListener listener : component.getComponentListeners()) {
      if (listener instanceof FrameState) {
        return (FrameState)listener;
      }
    }
    return null;
  }

  public static FrameState getFrameState(@NotNull Component component) {
    FrameState state = findFrameState(component);
    if (state == null) {
      state = new FrameState();
    }
    if (state.myBounds == null) {
      state.update(component);
    }
    return state;
  }

  public static void setFrameStateListener(@NotNull Component component) {
    if (component instanceof Frame) {
      // it makes sense for a frame only
      FrameState state = findFrameState(component);
      if (state == null) {
        component.addComponentListener(new Listener());
      }
    }
  }

  private static final class Listener extends FrameState implements ComponentListener {
    @Override
    public void componentMoved(ComponentEvent event) {
      update(event.getComponent());
    }

    @Override
    public void componentResized(ComponentEvent event) {
      update(event.getComponent());
    }

    @Override
    public void componentShown(ComponentEvent event) {
    }

    @Override
    public void componentHidden(ComponentEvent event) {
    }
  }

  final void update(Component component) {
    Rectangle bounds = component.getBounds();
    myFullScreen = component instanceof IdeFrame
                   && FrameInfoHelper.isFullScreenSupportedInCurrentOs()
                   && ((IdeFrame)component).isInFullScreen();
    myMaximized = FrameInfoHelper.isMaximized(getExtendedState(component));
    if (myBounds != null) {
      if (myFullScreen || myMaximized) {
        if (bounds.contains(myBounds.x + myBounds.width / 2, myBounds.y + myBounds.height / 2)) {
          return; // preserve old bounds for the maximized frame if its state can be restored
        }
      }
    }
    myBounds = bounds;
  }
}
