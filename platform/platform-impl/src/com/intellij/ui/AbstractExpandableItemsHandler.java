/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.MovablePopup;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;

public abstract class AbstractExpandableItemsHandler<KeyType, ComponentType extends JComponent> implements ExpandableItemsHandler<KeyType> {
  protected final ComponentType myComponent;

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final CellRendererPane myRendererPane = new CellRendererPane();
  private final JComponent myTipComponent = new JComponent() {
    @Override
    protected void paintComponent(Graphics g) {
      Insets insets = getInsets();
      Graphics2D g2d = (Graphics2D)g;
      double scale = (double)JBUI.sysScale((Graphics2D)g);
      double devTop = insets.top * scale;
      // A workaround for IDEA-183253. If insets.top is *.5 in device space, then move up the image by one device pixel.
      if (devTop + 0.5 == Math.floor(devTop + 0.5)) {
        g2d = (Graphics2D)g2d.create();
        double devPix = 1 / scale;
        g2d.translate(0, -devPix);
      }
      try {
        UIUtil.drawImage(g2d, myImage, insets.left, insets.top, null);
      } finally {
        if (g2d != g) g2d.dispose();
      }
    }
  };

  private boolean myEnabled = Registry.is("ide.expansion.hints.enabled");
  private final MovablePopup myPopup;
  private KeyType myKey;
  private Rectangle myKeyItemBounds;
  private BufferedImage myImage;

  public static void setRelativeBounds(@NotNull Component parent, @NotNull Rectangle bounds,
                                       @NotNull Component child, @NotNull Container validationParent) {
    validationParent.add(parent);
    parent.setBounds(bounds);
    parent.validate();
    child.setLocation(SwingUtilities.convertPoint(child, 0, 0, parent));
    validationParent.remove(parent);
  }

  protected AbstractExpandableItemsHandler(@NotNull final ComponentType component) {
    myComponent = component;
    myComponent.add(myRendererPane);
    myComponent.validate();
    myPopup = new MovablePopup(myComponent, myTipComponent);

    MouseEventHandler dispatcher = new MouseEventHandler() {
      @Override
      protected void handle(MouseEvent event) {
        myComponent.dispatchEvent(MouseEventAdapter.convert(event, myComponent));
      }

      @Override
      public void mouseEntered(MouseEvent event) {
      }

      @Override
      public void mouseExited(MouseEvent event) {
        // don't hide the hint if mouse exited to owner component
        if (myComponent.getMousePosition() == null) {
          hideHint();
        }
      }
    };
    myTipComponent.addMouseListener(dispatcher);
    myTipComponent.addMouseMotionListener(dispatcher);
    myTipComponent.addMouseWheelListener(dispatcher);

    MouseEventHandler handler = new MouseEventHandler() {
      @Override
      protected void handle(MouseEvent event) {
        handleMouseEvent(event, MouseEvent.MOUSE_MOVED != event.getID());
      }

      @Override
      public void mouseClicked(MouseEvent event) {
      }

      @Override
      public void mouseExited(MouseEvent event) {
        // don't hide the hint if mouse exited to it
        if (myTipComponent.getMousePosition() == null) {
          hideHint();
        }
      }
    };
    myComponent.addMouseListener(handler);
    myComponent.addMouseMotionListener(handler);

    myComponent.addFocusListener(
      new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
          onFocusLost();
        }

        @Override
        public void focusGained(FocusEvent e) {
          updateCurrentSelection();
        }
      }
    );

    myComponent.addComponentListener(
      new ComponentAdapter() {
        @Override
        public void componentHidden(ComponentEvent e) {
          hideHint();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
          updateCurrentSelection();
        }

        @Override
        public void componentResized(ComponentEvent e) {
          updateCurrentSelection();
        }
      }
    );

    myComponent.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
      @Override
      public void ancestorMoved(HierarchyEvent e) {
        updateCurrentSelection();
      }

      @Override
      public void ancestorResized(HierarchyEvent e) {
        updateCurrentSelection();
      }
    });

    myComponent.addHierarchyListener(
      new HierarchyListener() {
        @Override
        public void hierarchyChanged(HierarchyEvent e) {
          hideHint();
        }
      }
    );
  }

  protected void onFocusLost() {
    hideHint();
  }

  @Override
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
    if (!myEnabled) hideHint();
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @NotNull
  @Override
  public Collection<KeyType> getExpandedItems() {
    return myKey == null ? Collections.emptyList() : Collections.singleton(myKey);
  }

  protected void updateCurrentSelection() {
    handleSelectionChange(myKey, true);
  }

  protected void handleMouseEvent(MouseEvent e, boolean forceUpdate) {
    KeyType selected = getCellKeyForPoint(e.getPoint());
    if (forceUpdate || !Comparing.equal(myKey, selected)) {
      handleSelectionChange(selected, true);
    }

    // Temporary workaround
    if (e.getClickCount() == 2) {
      hideHint();
    }
  }

  protected void handleSelectionChange(KeyType selected) {
    handleSelectionChange(selected, false);
  }

  protected void handleSelectionChange(final KeyType selected, final boolean processIfUnfocused) {
    if (!EventQueue.isDispatchThread()) {
      return;
    }
    myUpdateAlarm.cancelAllRequests();
    if (selected == null || !isHandleSelectionEnabled(selected, processIfUnfocused)) {
      hideHint();
      return;
    }
    if (!selected.equals(myKey)) {
      hideHint();
    }
    myUpdateAlarm.addRequest(() -> doHandleSelectionChange(selected, processIfUnfocused), 10);
  }

  private boolean isHandleSelectionEnabled(@NotNull KeyType selected, boolean processIfUnfocused) {
    return myEnabled &&
           myComponent.isEnabled() &&
           myComponent.isShowing() &&
           myComponent.getVisibleRect().intersects(getVisibleRect(selected)) &&
           (processIfUnfocused || myComponent.isFocusOwner());
  }

  private void doHandleSelectionChange(@NotNull KeyType selected, boolean processIfUnfocused) {
    if (!isHandleSelectionEnabled(selected, processIfUnfocused)) {
      hideHint();
      return;
    }

    myKey = selected;

    Point location = createToolTipImage(myKey);

    if (location == null) {
      hideHint();
    }
    else {
      Rectangle bounds = new Rectangle(location, myTipComponent.getPreferredSize());
      myPopup.setBounds(bounds);
      myPopup.onAncestorFocusLost(() -> onFocusLost());
      myPopup.setVisible(noIntersections(bounds));
      repaintKeyItem();
    }
  }

  protected boolean isPopup() {
    Window window = SwingUtilities.getWindowAncestor(myComponent);
    return window != null
           && !(window instanceof Dialog || window instanceof Frame)
           && !isHintsAllowed(window);
  }

  private static boolean isHintsAllowed(Window window) {
    if (window instanceof RootPaneContainer) {
      final JRootPane pane = ((RootPaneContainer)window).getRootPane();
      if (pane != null) {
        return Boolean.TRUE.equals(pane.getClientProperty(AbstractPopup.SHOW_HINTS));
      }
    }
    return false;
  }

  private static boolean isFocused(Window window) {
    return window != null && (window.isFocused() || isFocused(window.getOwner()));
  }

  private boolean noIntersections(Rectangle bounds) {
    Window owner = SwingUtilities.getWindowAncestor(myComponent);
    Window popup = SwingUtilities.getWindowAncestor(myTipComponent);
    Window focus = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    if (focus == owner.getOwner()) {
      focus = null; // do not check intersection with parent
    }
    boolean focused = SystemInfo.isWindows || isFocused(owner);
    for (Window other : owner.getOwnedWindows()) {
      if (!focused) {
        focused = other.isFocused();
      }
      if (popup != other && other.isVisible() && bounds.x + 10 >= other.getX() && bounds.intersects(other.getBounds())) {
        return false;
      }
      if (focus == other) {
        focus = null; // already checked
      }
    }
    return focused && (focus == owner || focus == null || !owner.getBounds().intersects(focus.getBounds()));
  }

  private void hideHint() {
    myUpdateAlarm.cancelAllRequests();
    if (myPopup.isVisible()) {
      myPopup.setVisible(false);
      repaintKeyItem();
    }
    myKey = null;
  }

  public boolean isShowing() {
    return myPopup.isVisible();
  }

  private void repaintKeyItem() {
    if (myKeyItemBounds != null) {
      myComponent.repaint(myKeyItemBounds);
    }
  }

  @Nullable
  private Point createToolTipImage(@NotNull KeyType key) {
    UIUtil.putClientProperty(myComponent, EXPANDED_RENDERER, true);
    Pair<Component, Rectangle> rendererAndBounds = getCellRendererAndBounds(key);
    UIUtil.putClientProperty(myComponent, EXPANDED_RENDERER, null);
    if (rendererAndBounds == null) return null;

    JComponent renderer = ObjectUtils.tryCast(rendererAndBounds.first, JComponent.class);
    if (renderer == null) return null;
    if (UIUtil.isClientPropertyTrue(renderer, RENDERER_DISABLED)) return null;

    if (UIUtil.isClientPropertyTrue(rendererAndBounds.getFirst(), USE_RENDERER_BOUNDS)) {
      rendererAndBounds.getSecond().translate(renderer.getX(), renderer.getY());
      rendererAndBounds.getSecond().setSize(renderer.getSize());
    }

    myKeyItemBounds = rendererAndBounds.second;

    Rectangle cellBounds = myKeyItemBounds;
    Rectangle visibleRect = getVisibleRect(key);

    if (cellBounds.y < visibleRect.y) return null;

    int cellMaxY = cellBounds.y + cellBounds.height;
    int visMaxY = visibleRect.y + visibleRect.height;
    if (cellMaxY > visMaxY) return null;

    int cellMaxX = cellBounds.x + cellBounds.width;
    int visMaxX = visibleRect.x + visibleRect.width;

    Point location = new Point(visMaxX, cellBounds.y);
    SwingUtilities.convertPointToScreen(location, myComponent);

    Rectangle screen = ScreenUtil.getScreenRectangle(location);

    int borderWidth = isPaintBorder() ? 1 : 0;
    int width = Math.min(screen.width + screen.x - location.x - borderWidth, cellMaxX - visMaxX);
    int height = cellBounds.height;

    if (width <= 0 || height <= 0) return null;

    Dimension size = getImageSize(width, height);
    myImage = UIUtil.createImage(myComponent, size.width, size.height, BufferedImage.TYPE_INT_RGB);

    Graphics2D g = myImage.createGraphics();
    g.setClip(null);
    doFillBackground(height, width, g);
    g.translate(cellBounds.x - visMaxX, 0);
    doPaintTooltipImage(renderer, cellBounds, g, key);

    CustomLineBorder border = null;
    if (borderWidth > 0) {
      border = new CustomLineBorder(getBorderColor(), borderWidth, 0, borderWidth, borderWidth);
      Insets insets = border.getBorderInsets(myTipComponent);
      location.y -= insets.top;
      JBInsets.addTo(size, insets);
    }

    g.dispose();
    myRendererPane.remove(renderer);

    myTipComponent.setBorder(border);
    myTipComponent.setPreferredSize(size);
    return location;
  }

  protected boolean isPaintBorder() {
    return true;
  }

  protected Color getBorderColor() {
    return JBColor.border();
  }

  protected Dimension getImageSize(final int width, final int height) {
    return new Dimension(width, height);
  }

  protected void doFillBackground(int height, int width, Graphics2D g) {
    g.setColor(myComponent.getBackground());
    g.fillRect(0, 0, width, height);
  }

  protected void doPaintTooltipImage(Component rComponent, Rectangle cellBounds, Graphics2D g, KeyType key) {
    myRendererPane.paintComponent(g, rComponent, myComponent, 0, 0, cellBounds.width, cellBounds.height, true);
  }

  protected Rectangle getVisibleRect(KeyType key) {
    return myComponent.getVisibleRect();
  }

  @Nullable
  protected abstract Pair<Component, Rectangle> getCellRendererAndBounds(KeyType key);

  protected abstract KeyType getCellKeyForPoint(Point point);
}