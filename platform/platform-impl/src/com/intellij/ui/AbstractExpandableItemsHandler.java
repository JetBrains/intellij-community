// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.diagnostic.LoadingState;
import com.intellij.idea.AppMode;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.MovablePopup;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.MouseEventHandler;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractExpandableItemsHandler<KeyType, ComponentType extends JComponent> implements ExpandableItemsHandler<KeyType> {
  protected final ComponentType myComponent;

  private final SingleEdtTaskScheduler updateAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();

  @TestOnly
  @ApiStatus.Internal
  public @NotNull SingleEdtTaskScheduler getUpdateAlarm() {
    return updateAlarm;
  }

  private final CellRendererPane myRendererPane = new CellRendererPane();
  private final JComponent myTipComponent = new JComponent() {
    @Override
    protected void paintComponent(Graphics g) {
      if (myImage != null) {
        UIUtil.drawImage(g, myImage, 0, 0, null);
      }
      else if (myKey != null) {
        ToolTipDetails details = calcToolTipDetails(myKey);
        if (details != null) {
          Graphics2D g2d = (Graphics2D)g.create();
          try {
            if (details.clip != null) {
              g2d.clip(details.clip);
            }
            details.painter.accept(g2d);
          }
          finally {
            g2d.dispose();
          }
        }
      }
    }
  };

  private boolean myEnabled = LoadingState.COMPONENTS_LOADED.isOccurred() && Registry.is("ide.expansion.hints.enabled");
  private final MovablePopup myPopup;
  private KeyType myKey;
  private Rectangle myKeyItemBounds;
  private BufferedImage myImage;
  private int borderArc = 0;
  // We cannot use buffered rendering in rem-dev case as the backend might not have the required fonts to render text
  private static final boolean RENDER_IN_POPUP = AppMode.isRemoteDevHost();

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
    if (RENDER_IN_POPUP) {
      myTipComponent.add(myRendererPane);
    }
    else {
      myComponent.add(myRendererPane);
      myComponent.validate();
    }
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
        // don't hide the hint if mouse exited to an owner component
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
        if (LoadingState.COMPONENTS_LOADED.isOccurred() && Registry.is("ide.hide.expandable.tooltip.owner.mouse.exit") || myTipComponent.getMousePosition() == null) {
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
  public @NotNull @Unmodifiable Collection<KeyType> getExpandedItems() {
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

  protected void handleSelectionChange(KeyType selected, boolean processIfUnfocused) {
    if (!EventQueue.isDispatchThread()) {
      return;
    }

    updateAlarm.cancel();

    if (selected == null || !isHandleSelectionEnabled(selected, processIfUnfocused)) {
      hideHint();
      return;
    }

    if (!selected.equals(myKey)) {
      hideHint();
    }

    updateAlarm.request(10, () -> doHandleSelectionChange(selected, processIfUnfocused));
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

    ToolTipDetails details = calcToolTipDetails(myKey);

    if (details == null) {
      hideHint();
    }
    else {
      myKeyItemBounds = details.keyItemBounds;
      Rectangle bounds = details.bounds;
      Shape clip = details.clip;
      myTipComponent.setPreferredSize(bounds.getSize());
      if (!RENDER_IN_POPUP) {
        myImage = createPopupContent(bounds, details.painter, clip);
      }
      myPopup.setTransparent(clip != null);
      myPopup.setBounds(bounds);
      myPopup.onAncestorFocusLost(() -> onFocusLost());
      myPopup.setVisible(noIntersections(bounds));
      repaintKeyItem();
    }
  }

  private BufferedImage createPopupContent(Rectangle bounds, Consumer<Graphics2D> painter, Shape clip) {
    // We paint to an 'opaque' image type (RGB, not ARGB) initially to support subpixel-antialiased text
    BufferedImage img = UIUtil.createImage(myComponent, bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    painter.accept(g);
    g.dispose();
    if (clip == null) {
      return img;
    }
    else {
      BufferedImage clippedImg = UIUtil.createImage(myComponent, bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = clippedImg.createGraphics();
      g2.clip(clip);
      UIUtil.drawImage(g2, img, 0, 0, null);
      g2.dispose();
      return clippedImg;
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
    updateAlarm.cancel();
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

  private @Nullable ToolTipDetails calcToolTipDetails(@NotNull KeyType key) {
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

    Rectangle cellBounds = rendererAndBounds.second;

    if (ClientProperty.isTrue(renderer, USE_RENDERER_BOUNDS)) {
      cellBounds.translate(renderer.getX(), renderer.getY());
      cellBounds.setSize(renderer.getSize());
    }

    SelectablePanel selectablePanel = findSelectablePanel(renderer);
    int arc = selectablePanel == null ? borderArc : selectablePanel.getSelectionArc();

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

    // exclude case when myComponent touches screen boundary with its right edge, and popup would be displayed on the adjacent screen
    if (location.x == screen.x) return null;

    int borderSize = isPaintBorder() ? 1 : 0;
    int width = Math.min(screen.width + screen.x - location.x - borderSize, cellMaxX - visMaxX);
    int height = cellBounds.height;

    int cellY = 0;
    if (arc > 0 && selectablePanel != null) {
      renderer.setBounds(cellBounds);
      Rectangle rect = calcRectInParent(selectablePanel, renderer);
      width -= selectablePanel.getSelectionInsets().right;
      height = rect.height;
      cellY = rect.y;
      location.y += cellY;
    }

    if (width <= 0 || height <= 0) return null;

    location.y -= borderSize;
    width += borderSize;
    height += borderSize * 2;

    int imgWidth = width;
    int imgHeight = height;
    int xShift = cellBounds.x - visMaxX;
    int yShift = borderSize - cellY;
    Consumer<Graphics2D> painter = (g) -> {
      doFillBackground(imgHeight, imgWidth, g);
      // On showing, popup will be aligned to device pixel's grid. This adjustment tries to compensate for this shift.
      // Ideally, we'd like to compensate also for the similar alignment of the main window. But we don't have enough information for that -
      // from Java API we only have the integer-valued window coordinates in user space. If the main window is moved to an arbitrary place
      // on screen using mouse, exact device-space coordinates cannot be obtained. We can only expect this alignment not to be needed for
      // maximized windows.
      double fractionalScaleCorrection = location.y - PaintUtil.alignToInt(location.y, g, PaintUtil.RoundingMode.ROUND_FLOOR_BIAS);
      g.translate(xShift, yShift + fractionalScaleCorrection);
      doPaintTooltipImage(renderer, cellBounds, g, key);
      g.translate(-xShift, -yShift - fractionalScaleCorrection);

      if (borderSize > 0) {
        g.setColor(getBorderColor());
        if (arc > 0) {
          Area area = new Area(new Rectangle(0, 0, imgWidth, imgHeight) /* will be restricted by clip */);
          area.subtract(new Area(new RoundRectangle2D.Double(- arc + borderSize, borderSize,
                                                             imgWidth + arc - borderSize * 2, imgHeight - borderSize * 2, arc, arc)));
          g.fill(area);
        }
        else {
          g.fillRect(0, 0, imgWidth, borderSize);
          g.fillRect(0, imgHeight - borderSize, imgWidth, borderSize);
          g.fillRect(imgWidth - borderSize, 0, borderSize, imgHeight);
        }
      }
    };

    Shape clip = arc > 0 ? new RoundRectangle2D.Float(-arc, 0, imgWidth + arc, imgHeight, arc, arc) : null;

    return new ToolTipDetails(new Rectangle(location.x, location.y, imgWidth, imgHeight), cellBounds, painter, clip);
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
    myRendererPane.remove(rComponent);
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

  private record ToolTipDetails(@NotNull Rectangle bounds,
                                @Nullable Rectangle keyItemBounds,
                                @NotNull Consumer<Graphics2D> painter,
                                @Nullable Shape clip) {}
}
