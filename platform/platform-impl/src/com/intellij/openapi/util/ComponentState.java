// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.FrameInfoHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.peer.ComponentPeer;
import java.awt.peer.FramePeer;

/**
 * @author Sergey Malenkov
 */
final class ComponentState extends WindowAdapter implements ComponentListener {
  private volatile Rectangle myBounds;
  private volatile int myExtendedState;
  private volatile boolean myFullScreen;

  private ComponentState(@NotNull Component component) {
    update(component);
    install(component);
  }

  private void install(@NotNull Component component) {
    component.addComponentListener(this);
    if (component instanceof Window) {
      Window window = (Window)component;
      window.addWindowListener(this);
      window.addWindowStateListener(this);
    }
  }

  void uninstall(@NotNull Component component) {
    component.removeComponentListener(this);
    if (component instanceof Window) {
      Window window = (Window)component;
      window.removeWindowListener(this);
      window.removeWindowStateListener(this);
    }
  }


  public Rectangle getBounds() {
    return myBounds;
  }

  public int getExtendedState() {
    return myExtendedState;
  }

  public boolean isFullScreen() {
    return myFullScreen;
  }


  @Override
  public void windowClosed(WindowEvent event) {
    Object source = event == null ? null : event.getSource();
    if (source instanceof Component) uninstall((Component)source);
  }

  @Override
  public void windowOpened(WindowEvent event) {
    update(event);
  }

  @Override
  public void windowStateChanged(WindowEvent event) {
    update(event);
  }

  @Override
  public void componentMoved(ComponentEvent event) {
    update(event);
  }

  @Override
  public void componentResized(ComponentEvent event) {
    update(event);
  }

  @Override
  public void componentShown(ComponentEvent event) {
  }

  @Override
  public void componentHidden(ComponentEvent event) {
  }

  private void update(@Nullable ComponentEvent event) {
    Object source = event == null ? null : event.getSource();
    if (source instanceof Component) update((Component)source);
  }

  private void update(@NotNull Component component) {
    if (component.isVisible()) {
      myFullScreen = isFullScreen(component);
      myExtendedState = getExtendedState(component);
      if (!myFullScreen && myExtendedState == Frame.NORMAL) {
        myBounds = component.getBounds();
      }
    }
  }


  @Nullable
  public static ComponentState findState(@NotNull Component component) {
    for (ComponentListener listener : component.getComponentListeners()) {
      if (listener instanceof ComponentState) {
        return (ComponentState)listener;
      }
    }
    return null;
  }

  @NotNull
  public static ComponentState getState(@NotNull Component component) {
    ComponentState state = findState(component);
    return state != null ? state : new ComponentState(component);
  }

  static boolean isFullScreen(@NotNull Component component) {
    return component instanceof IdeFrame && FrameInfoHelper.isFullScreenSupportedInCurrentOs() && ((IdeFrame)component).isInFullScreen();
  }

  static int getExtendedState(@NotNull Component component) {
    if (component instanceof Frame) {
      Frame frame = (Frame)component;
      int state = frame.getExtendedState();
      if (SystemInfo.isMacOSLion) {
        // workaround: frame.state is not updated by jdk so get it directly from peer
        ComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(frame);
        if (peer instanceof FramePeer) return ((FramePeer)peer).getState();
      }
      return state;
    }
    return Frame.NORMAL;
  }
}
