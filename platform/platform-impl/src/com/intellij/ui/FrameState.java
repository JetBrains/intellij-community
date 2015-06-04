/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.peer.ComponentPeer;
import java.awt.peer.FramePeer;

/**
 * @author Sergey Malenkov
 */
public class FrameState {
  private Rectangle myBounds;
  private Integer myExtendedState;
  private boolean myFullScreen;

  public Rectangle getBounds() {
    return myBounds == null ? null : new Rectangle(myBounds);
  }

  public Integer getExtendedState() {
    return myExtendedState;
  }

  public boolean isFullScreen() {
    return myFullScreen;
  }

  public static Integer getExtendedState(Component component) {
    Integer state = null;
    if (component instanceof Frame) {
      state = ((Frame)component).getExtendedState();
      if (SystemInfo.isMacOSLion) {
        // workaround: frame.state is not updated by jdk so get it directly from peer
        @SuppressWarnings("deprecation")
        ComponentPeer peer = component.getPeer();
        if (peer instanceof FramePeer) {
          state = ((FramePeer)peer).getState();
        }
      }
    }
    return state;
  }

  public static boolean isFullScreen(Component component) {
    return component instanceof IdeFrameEx
           && WindowManager.getInstance().isFullScreenSupportedInCurrentOS()
           && ((IdeFrameEx)component).isInFullScreen();
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
    myFullScreen = isFullScreen(component);
    myExtendedState = getExtendedState(component);
    if (myBounds != null) {
      if (myFullScreen || myExtendedState != null && (myExtendedState & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
        if (bounds.contains(myBounds.x + myBounds.width / 2, myBounds.y + myBounds.height / 2)) {
          return; // preserve old bounds for the maximized frame if its state can be restored
        }
      }
    }
    myBounds = bounds;
  }
}
