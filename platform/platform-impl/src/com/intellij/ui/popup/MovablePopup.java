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
package com.intellij.ui.popup;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey Malenkov
 */
public class MovablePopup {
  private final Component myOwner;
  private final Component myContent;
  private Rectangle myViewBounds;
  private Container myView;
  private boolean myAlwaysOnTop;
  private boolean myHeavyWeight;
  private boolean myWindowFocusable;
  private boolean myWindowShadow;

  /**
   * @param owner   a component to which this popup belongs
   * @param content a component to show within this popup
   */
  public MovablePopup(@NotNull Component owner, @NotNull Component content) {
    myOwner = owner;
    myContent = content;
    myViewBounds = new Rectangle(content.getPreferredSize());
    myHeavyWeight = true;
  }

  public void dispose() {
    disposeAndUpdate(false);
  }

  /**
   * Sets whether this popup should be always on top.
   * This property is used by heavy weight popups only.
   */
  public void setAlwaysOnTop(boolean value) {
    if (myAlwaysOnTop != value) {
      myAlwaysOnTop = value;
      disposeAndUpdate(true);
    }
  }

  /**
   * Sets whether this popup should be a separate window.
   * A light weight popups are painted on the layered pane.
   */
  public void setHeavyWeight(boolean value) {
    if (myHeavyWeight != value) {
      myHeavyWeight = value;
      disposeAndUpdate(true);
    }
  }

  /**
   * Sets whether this popup should grab a focus.
   * This property is used by heavy weight popups only.
   */
  public void setWindowFocusable(boolean value) {
    if (myWindowFocusable != value) {
      myWindowFocusable = value;
      disposeAndUpdate(true);
    }
  }

  /**
   * Sets whether this popup should have a shadow.
   * This property is used by heavy weight popups only.
   */
  public void setWindowShadow(boolean value) {
    if (myWindowShadow != value) {
      myWindowShadow = value;
      disposeAndUpdate(true);
    }
  }

  public void setBounds(@NotNull Rectangle bounds) {
    setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
  }

  public void setBounds(int x, int y, int width, int height) {
    if (myViewBounds != null) {
      myViewBounds.setBounds(x, y, width, height);
    }
    else {
      setBounds(new Point(x, y), new Dimension(width, height));
    }
  }

  public void setLocation(@NotNull Point location) {
    setLocation(location.x, location.y);
  }

  public void setLocation(int x, int y) {
    if (myViewBounds != null) {
      myViewBounds.setLocation(x, y);
    }
    else {
      setBounds(new Point(x, y), null);
    }
  }

  public void setSize(@NotNull Dimension size) {
    setSize(size.width, size.height);
  }

  public void setSize(int width, int height) {
    if (myViewBounds != null) {
      myViewBounds.setSize(width, height);
    }
    else {
      setBounds(null, new Dimension(width, height));
    }
  }

  public void setVisible(boolean visible) {
    if (myView != null) {
      myView.setVisible(visible);
    }
    else if (visible) {
      Window owner = UIUtil.getWindow(myOwner);
      if (owner != null) {
        if (myHeavyWeight) {
          JWindow view = new JWindow(owner);
          // TODO 1.7+: temporary fix for the i3 window manager because of Java 1.6
          try {
            @SuppressWarnings("unchecked")
            Class<? extends Enum> type = (Class<? extends Enum>)Class.forName("java.awt.Window$Type");
            Object value = Enum.valueOf(type, "POPUP");
            view.getClass().getMethod("setType", type).invoke(view, value);
          }
          catch (Exception ignored) {
          }
          // TODO 1.7+: setType(Window.Type.POPUP); // or UTILITY
          if (myAlwaysOnTop) {
            try {
              view.setAlwaysOnTop(true);
            }
            catch (SecurityException ignored) {
              myAlwaysOnTop = false;
            }
          }
          view.setFocusableWindowState(myWindowFocusable);
          if (!myWindowShadow) {
            view.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
          }
          myView = view;
        }
        else if (owner instanceof RootPaneContainer) {
          JLayeredPane parent = ((RootPaneContainer)owner).getLayeredPane();
          if (parent != null) {
            JPanel view = new JPanel(new BorderLayout());
            view.setVisible(false);
            parent.add(view, JLayeredPane.POPUP_LAYER, 0);
            myView = view;
          }
        }
      }
      if (myView != null) {
        myView.add(myContent);
        Component parent = myView instanceof Window ? null : myView.getParent();
        if (parent != null) {
          Point location = myViewBounds.getLocation();
          SwingUtilities.convertPointFromScreen(location, parent);
          myViewBounds.setLocation(location);
        }
        myView.setBounds(myViewBounds);
        myView.setVisible(true);
        myViewBounds = null;
      }
    }
  }

  /**
   * Determines whether this popup should be visible.
   */
  public boolean isVisible() {
    return myView != null && myView.isVisible();
  }

  private void disposeAndUpdate(boolean update) {
    if (myView != null) {
      boolean visible = myView.isVisible();
      if (myView instanceof Window) {
        myViewBounds = myView.getBounds();
        Window window = (Window)myView;
        window.dispose();
      }
      else {
        Container parent = myView.getParent();
        if (parent == null) {
          myViewBounds = new Rectangle(myContent.getPreferredSize());
        }
        else {
          myViewBounds = new Rectangle(myView.getBounds());
          parent.remove(myView);
          Point point = new Point(myViewBounds.x, myViewBounds.y);
          SwingUtilities.convertPointToScreen(point, parent);
          myViewBounds.x = point.x;
          myViewBounds.y = point.y;
        }
      }
      myView = null;
      if (update && visible) {
        setVisible(true);
      }
    }
  }

  private void setBounds(Point location, Dimension size) {
    if (myView != null) {
      if (size == null) {
        size = myView.getSize();
      }
      if (location == null) {
        location = myView.getLocation();
      }
      else {
        Component parent = myView instanceof Window ? null : myView.getParent();
        if (parent != null) {
          SwingUtilities.convertPointFromScreen(location, parent);
        }
      }
      myView.setBounds(location.x, location.y, size.width, size.height);
      if (myView.isVisible()) {
        myView.invalidate();
        myView.validate();
        myView.repaint();
      }
    }
  }
}
