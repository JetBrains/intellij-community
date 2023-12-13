// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.MovablePopup;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.MouseEventHandler;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractExpandableItemsHandler<KeyType, ComponentType extends JComponent> implements ExpandableItemsHandler<KeyType> {
  protected final ComponentType myComponent;

  private final Alarm myUpdateAlarm = new Alarm();
  private final CellRendererPane myRendererPane = new CellRendererPane();
  private final JComponent myTipComponent = new JComponent() {
    @Override
    protected void paintComponent(Graphics g) {
      Insets insets = getInsets();
      Graphics2D g2d = (Graphics2D)g.create();
      double scale = JBUIScale.sysScale((Graphics2D)g);
      double devTop = insets.top * scale;
      // A workaround for IDEA-183253. If insets.top is *.5 in device space, then move up the image by one device pixel.
      if (devTop + 0.5 == Math.floor(devTop + 0.5)) {
        double devPix = 1 / scale;
        g2d.translate(0, -devPix);
      }
      try {
        if (transparentPopup) {
          // Not sure why this is necessary
          g2d.setComposite(AlphaComposite.SrcOver);
        }
        UIUtil.drawImage(g2d, myImage, insets.left, insets.top, null);
      }
      finally {
        g2d.dispose();
      }
    }
  };

  private boolean myEnabled = Registry.is("ide.expansion.hints.enabled");
  private final MovablePopup myPopup;
  private KeyType myKey;
  private Rectangle myKeyItemBounds;
  private BufferedImage myImage;
  private int borderArc = 0;
  private boolean transparentPopup;

  public static void setRelativeBounds(@NotNull Component parent, @NotNull Rectangle bounds,
                                       @NotNull Component child, @NotNull Container validationParent) {
    validationParent.add(parent);
    parent.setBounds(bounds);
    parent.validate();
    child.setLocation(SwingUtilities.convertPoint(child, 0, 0, parent));
    validationParent.remove(parent);
  }

  protected AbstractExpandableItemsHandler(final @NotNull ComponentType component) {
    myComponent = component;
    myComponent.add(myRendererPane);
    myComponent.validate();
    var popup = new MovablePopup(myComponent, myTipComponent);
    // On Wayland, heavyweight popup might get automatically displaced
    // by the server if they appear to cross the screen boundary, which
    // is not what we want in this case.
    popup.setHeavyWeight(!StartupUiUtil.isWaylandToolkit());
    myPopup = popup;


    MouseEventHandler dispatcher = new MouseEventHandler() {
      @Override
      protected void handle(MouseEvent event) {
        MouseEventAdapter.redispatch(event, myComponent);
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
        if (Registry.is("ide.hide.expandable.tooltip.owner.mouse.exit") || myTipComponent.getMousePosition() == null) {
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
          updateCurrentSelectionOnMoveOrResize();
        }

        @Override
        public void componentResized(ComponentEvent e) {
          updateCurrentSelectionOnMoveOrResize();
        }
      }
    );

    myComponent.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
      @Override
      public void ancestorMoved(HierarchyEvent e) {
        updateCurrentSelectionOnMoveOrResize();
      }

      @Override
      public void ancestorResized(HierarchyEvent e) {
        updateCurrentSelectionOnMoveOrResize();
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
    if (!isEnabled()) hideHint();
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  protected void setBorderArc(int borderArc) {
    this.borderArc = borderArc;
  }

  @Override
  public @NotNull Collection<KeyType> getExpandedItems() {
    return myKey == null ? Collections.emptyList() : Collections.singleton(myKey);
  }

  private void updateCurrentSelectionOnMoveOrResize() {
    if (ClientProperty.isTrue(myComponent, IGNORE_ITEM_SELECTION)) {
      hideHint();
    }
    else {
      updateCurrentSelection();
    }
  }

  protected void updateCurrentSelection() {
    handleSelectionChange(myKey, true);
  }

  protected void handleMouseEvent(MouseEvent e, boolean forceUpdate) {
    if (ClientProperty.isTrue(myComponent, IGNORE_MOUSE_HOVER)) return;
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
    handleSelectionChange(ClientProperty.isTrue(myComponent, IGNORE_ITEM_SELECTION) ? myKey : selected, false);
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
    return isEnabled() &&
           !ScreenReader.isActive() &&
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
      myPopup.setTransparent(transparentPopup);
      myPopup.setBounds(bounds);
      myPopup.onAncestorFocusLost(() -> onFocusLost());
      myPopup.setVisible(noIntersections(bounds));
      repaintKeyItem();
    }
  }

  protected boolean isPopup() {
    Window window = SwingUtilities.getWindowAncestor(myComponent);
    return UIUtil.isSimpleWindow(window) && !isHintsAllowed(window);
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

  private static SelectablePanel findSelectablePanel(@NotNull JComponent renderer) {
    Component result = renderer;
    while (result instanceof ExpandedItemRendererComponentWrapper) {
      result = ExpandedItemRendererComponentWrapper.unwrap(result);
    }

    if (result instanceof GroupedElementsRenderer.MyComponent myComponent) {
      result = myComponent.getRenderer().getItemComponent();
    }

    return result instanceof SelectablePanel selectablePanel ? selectablePanel : null;
  }

  private @Nullable Point createToolTipImage(@NotNull KeyType key) {
    ClientProperty.put(myComponent, EXPANDED_RENDERER, true);
    Pair<Component, Rectangle> rendererAndBounds = getCellRendererAndBounds(key);
    ClientProperty.put(myComponent, EXPANDED_RENDERER, null);
    if (rendererAndBounds == null) return null;

    JComponent renderer = ObjectUtils.tryCast(rendererAndBounds.first, JComponent.class);
    if (renderer == null) return null;
    if (ClientProperty.isTrue(renderer, RENDERER_DISABLED)) return null;

    if (renderer instanceof ExpandedItemRendererComponentWrapper) {
      Component delegate = ((ExpandedItemRendererComponentWrapper) renderer).getDelegate();
      if (delegate != null && ClientProperty.isTrue(delegate, RENDERER_DISABLED)) return null;
    }

    myKeyItemBounds = rendererAndBounds.second;

    if (ClientProperty.isTrue(renderer, USE_RENDERER_BOUNDS)) {
      myKeyItemBounds.translate(renderer.getX(), renderer.getY());
      myKeyItemBounds.setSize(renderer.getSize());
    }

    SelectablePanel selectablePanel = findSelectablePanel(renderer);
    int arc = selectablePanel == null ? borderArc : selectablePanel.getSelectionArc();
    transparentPopup = arc > 0;

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

    // exclude case when myComponent touches screen boundary with its right edge, and popup would be displayed on adjacent screen
    if (location.x == screen.x) return null;

    int borderSize = isPaintBorder() ? 1 : 0;
    int width = Math.min(screen.width + screen.x - location.x - borderSize, cellMaxX - visMaxX);
    int height = cellBounds.height;

    if (width <= 0 || height <= 0) return null;

    Graphics2D g;
    if (arc > 0) {
      width += borderSize;
      height += borderSize * 2;
      int clippedY = 0;
      int clippedHeight = height;
      if (selectablePanel != null) {
        width -= selectablePanel.getSelectionInsets().right;
        if (width <= 0) {
          return null;
        }
        renderer.setBounds(cellBounds);
        Rectangle rect = calcRectInParent(selectablePanel, renderer);
        clippedY = rect.y;
        clippedHeight = rect.height + borderSize * 2;
      }
      myImage = UIUtil.createImage(myComponent, width, height, BufferedImage.TYPE_INT_ARGB);
      g = myImage.createGraphics();
      //noinspection GraphicsSetClipInspection
      g.setClip(new RoundRectangle2D.Float(-arc, clippedY, width + arc, clippedHeight, arc, arc));

      if (borderSize > 0) {
        location.y -= borderSize;
        g.setColor(getBorderColor());
        g.fillRect(0, 0, width, height);
        //noinspection GraphicsSetClipInspection
        g.setClip(new RoundRectangle2D.Float(-arc, clippedY + borderSize, width + arc - borderSize, clippedHeight - borderSize * 2, arc, arc));
      }
      doFillBackground(height, width, g);
      g.translate(cellBounds.x - visMaxX, borderSize);
      doPaintTooltipImage(renderer, cellBounds, g, key);
      myTipComponent.setBorder(null);
    }
    else {
      myImage = UIUtil.createImage(myComponent, width, height, BufferedImage.TYPE_INT_RGB);
      g = myImage.createGraphics();
      //noinspection GraphicsSetClipInspection
      g.setClip(null);
      doFillBackground(height, width, g);

      if (borderSize > 0) {
        CustomLineBorder border = new CustomLineBorder(getBorderColor(), borderSize, 0, borderSize, borderSize);
        location.y -= borderSize;
        width += borderSize;
        height += borderSize * 2;
        myTipComponent.setBorder(border);
      } else {
        myTipComponent.setBorder(null);
      }
      g.translate(cellBounds.x - visMaxX, 0);
      doPaintTooltipImage(renderer, cellBounds, g, key);
    }

    g.dispose();
    myRendererPane.remove(renderer);

    myTipComponent.setPreferredSize(new Dimension(width, height));
    return location;
  }

  protected boolean isPaintBorder() {
    return true;
  }

  protected Color getBorderColor() {
    return JBColor.border();
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

  protected abstract @Nullable Pair<Component, Rectangle> getCellRendererAndBounds(KeyType key);

  protected abstract KeyType getCellKeyForPoint(Point point);

  private static @NotNull Rectangle calcRectInParent(@NotNull Container child, @NotNull JComponent parent) {
    List<Container> chain = new ArrayList<>();
    Container container = child;
    while (container != parent) {
      chain.add(container);
      container = container.getParent();
      if (container == null) {
        throw new IllegalArgumentException("Source is not part of " + parent);
      }
    }

    Rectangle result = new Rectangle();
    parent.doLayout();
    for (int i = chain.size() - 1; i >= 0; i--) {
      Container c = chain.get(i);
      c.doLayout();
      result.x += c.getX();
      result.y += c.getY();
    }
    result.setSize(child.getSize());
    return result;
  }
}
