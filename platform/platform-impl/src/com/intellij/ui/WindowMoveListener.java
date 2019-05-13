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

import com.intellij.openapi.progress.util.PotemkinProgress;
import org.jetbrains.annotations.ApiStatus;

import java.awt.*;
import java.awt.event.MouseEvent;

import static java.awt.Cursor.*;
import static java.awt.event.InputEvent.BUTTON1_MASK;

/**
 * @author Sergey Malenkov
 */
public class WindowMoveListener extends WindowMouseListener {
  public WindowMoveListener(Component content) {
    super(content);
  }

  @Override
  int getCursorType(Component view, Point location) {
    return DEFAULT_CURSOR;
  }

  @Override
  void updateBounds(Rectangle bounds, Component view, int dx, int dy) {
    bounds.x += dx;
    bounds.y += dy;
  }

  @Override
  public void mouseMoved(MouseEvent event) {
    // ignore cursor updating
  }

  @Override
  public void mouseClicked(MouseEvent event) {
    if (event.isConsumed()) return;
    if (BUTTON1_MASK == (BUTTON1_MASK & event.getModifiers()) && 1 < event.getClickCount()) {
      Component view = getView(getContent(event));
      if (view instanceof Frame) {
        Frame frame = (Frame)view;
        int state = frame.getExtendedState();
        if (!isStateSet(Frame.ICONIFIED, state) && frame.isResizable()) {
          event.consume();
          frame.setExtendedState(isStateSet(Frame.MAXIMIZED_BOTH, state)
                                 ? (state & ~Frame.MAXIMIZED_BOTH)
                                 : (state | Frame.MAXIMIZED_BOTH));
        }
      }
    }
    super.mouseClicked(event);
  }

  /**
   * @author tav
   */
  @ApiStatus.Experimental
  public static class ToolkitListener extends WindowMoveListener {
    private final ToolkitListenerHelper myHelper;

    public ToolkitListener(Component content) {
      super(content);
      myHelper = new ToolkitListenerHelper(this);
    }

    @Override
    public void mousePressed(MouseEvent event) {
      if (!ourIsResizing && hitTest(event)) {
        super.mousePressed(event);
      }
    }

    @Override
    public void mouseReleased(MouseEvent event) {
      if (!ourIsResizing && hitTest(event)) {
        super.mouseReleased(event);
      }
    }

    @Override
    public void mouseDragged(MouseEvent event) {
      // Should not do hit test when drag has already been detected on the window,
      // otherwise we can miss a drag event when it's slightly out of the window bounds.
      if (!ourIsResizing/* && hitTest(event)*/) {
        super.mouseDragged(event);
      }
    }

    @Override
    public void mouseMoved(MouseEvent event) {
      if (hitTest(event)) {
        super.mouseMoved(event);
      }
    }

    @Override
    public void mouseClicked(MouseEvent event) {
      if (hitTest(event)) {
        PotemkinProgress.invokeLaterNotBlocking(event.getSource(), () -> super.mouseClicked(event));
      }
    }

    private boolean hitTest(MouseEvent e) {
      Rectangle bounds = getScreenBounds(myContent);
      return bounds.contains(e.getLocationOnScreen());
    }

    @Override
    protected void setCursor(Component content, Cursor cursor) {
      myHelper.setCursor(content, cursor, () -> super.setCursor(content, cursor));
    }

    @Override
    protected void setBounds(Component comp, Rectangle bounds) {
      myHelper.setBounds(comp, bounds, () -> super.setBounds(comp, bounds));
    }

    public void addTo(Component comp) {
      myHelper.addTo(comp);
    }

    public void removeFrom(Component comp) {
      myHelper.removeFrom(comp);
    }

    // tree lock free
    private static Rectangle getScreenBounds(Component comp) {
      Rectangle bounds = comp.getBounds();
      Component ancestor = comp.getParent();
      while (ancestor != null) {
        Point loc = ancestor.getLocation();
        bounds.setLocation(bounds.x + loc.x, bounds.y + loc.y);
        ancestor = ancestor.getParent();
      }
      return bounds;
    }
  }
}
