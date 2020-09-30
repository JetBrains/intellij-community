// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class EditorEmbeddedComponentManager {
  private static final Key<ComponentInlays> COMPONENT_INLAYS_KEY = Key.create("editor.embedded.component.inlays");
  private static final int RESIZE_POINT_DELTA = JBUI.scale(5);

  private static final EditorEmbeddedComponentManager ourInstance = new EditorEmbeddedComponentManager();

  private EditorEmbeddedComponentManager() {
  }

  @NotNull
  public static EditorEmbeddedComponentManager getInstance() {
    return ourInstance;
  }

  @Nullable
  public Inlay<?> addComponent(@NotNull EditorEx editor, @NotNull JComponent component, @NotNull Properties properties) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    ComponentInlays inlays = getComponentInlaysFor(editor);
    return inlays.add(component, properties.resizePolicy, properties.rendererFactory,
                      properties.relatesToPrecedingText, properties.showAbove,
                      properties.priority, properties.offset);
  }

  @NotNull
  private static ComponentInlays getComponentInlaysFor(@NotNull EditorEx editor) {
    if (!COMPONENT_INLAYS_KEY.isIn(editor)) COMPONENT_INLAYS_KEY.set(editor, new ComponentInlays(editor));
    return COMPONENT_INLAYS_KEY.get(editor);
  }

  public static final class ResizePolicy {
    private static final int RIGHT = 2;
    private static final int BOTTOM = RIGHT * 2;

    private static final ResizePolicy ourAny = new ResizePolicy(RIGHT | BOTTOM);
    private static final ResizePolicy ourNone = new ResizePolicy(0);

    private final int myFlags;

    private ResizePolicy(int flags) {
      myFlags = flags;
    }

    public boolean isResizable() {
      return isResizableFromRight() || isResizableFromBottom();
    }

    public static ResizePolicy any() {
      return ourAny;
    }

    @NotNull
    public static ResizePolicy none() {
      return ourNone;
    }

    public boolean isResizableFromRight() {
      return (myFlags & RIGHT) != 0;
    }

    public boolean isResizableFromBottom() {
      return (myFlags & BOTTOM) != 0;
    }
  }

  public static class Properties {
    final ResizePolicy resizePolicy;
    final RendererFactory rendererFactory;
    final boolean relatesToPrecedingText;
    final boolean showAbove;
    final int priority;
    final int offset;

    public Properties(@NotNull ResizePolicy resizePolicy, @Nullable RendererFactory rendererFactory,
                      boolean relatesToPrecedingText, boolean showAbove, int priority, int offset) {
      this.resizePolicy = resizePolicy;
      this.rendererFactory = rendererFactory;
      this.relatesToPrecedingText = relatesToPrecedingText;
      this.showAbove = showAbove;
      this.priority = priority;
      this.offset = offset;
    }

    public interface RendererFactory {
      @Nullable GutterIconRenderer createRenderer(@NotNull Inlay<?> inlay);
    }
  }

  private static class MyRenderer extends JPanel implements EditorCustomElementRenderer {
    private static final int UNDEFINED = -1;

    final ResizePolicy resizePolicy;

    private final Properties.RendererFactory myRendererFactory;
    private int myCustomWidth = UNDEFINED;
    private int myCustomHeight = UNDEFINED;

    MyRenderer(@NotNull JComponent component,
               @NotNull ResizePolicy resizePolicy,
               @Nullable Properties.RendererFactory rendererFactory) {
      super(new BorderLayout());
      this.resizePolicy = resizePolicy;
      myRendererFactory = rendererFactory;
      add(component, BorderLayout.CENTER);
      setOpaque(false);
    }

    @Nullable
    @Override
    public GutterIconRenderer calcGutterIconRenderer(@NotNull Inlay inlay) {
      return myRendererFactory == null ? null : myRendererFactory.createRenderer(inlay);
    }

    void setCustomWidth(int customWidth) {
      if (customWidth != getPreferredWidth()) myCustomWidth = customWidth;
    }

    void setCustomHeight(int customHeight) {
      if (customHeight != getPreferredHeight()) myCustomHeight = customHeight;
    }

    int getPreferredWidth() {
      return isWidthSet() ? myCustomWidth : getPreferredSize().width;
    }

    int getPreferredHeight() {
      return myCustomHeight == UNDEFINED ? getPreferredSize().height : myCustomHeight;
    }

    boolean isWidthSet() {
      return myCustomWidth != UNDEFINED;
    }

    @Override
    public int calcHeightInPixels(@NotNull Inlay inlay) {
      return Math.max(getHeight(), 0);
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
      return Math.max(getWidth(), 0);
    }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {
      Rectangle currentBounds = inlay.getBounds();
      if (currentBounds == null || Objects.equals(currentBounds, getBounds())) return;
      setBounds(currentBounds);
      revalidate();
      repaint(50);
    }

    @Override
    public void paint(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;
      Composite old = g2d.getComposite();
      try {
        g2d.setComposite(AlphaComposite.SrcOver);
        super.paint(g);
      }
      finally {
        g2d.setComposite(old);
      }
    }
  }

  private static class ComponentInlays implements Disposable {
    private final EditorEx myEditor;
    private final List<Inlay<? extends MyRenderer>> myInlays;
    private final ResizeListener myResizeListener;

    ComponentInlays(@NotNull EditorEx editor) {
      myEditor = editor;
      myInlays = new ArrayList<>();
      myResizeListener = new ResizeListener();
      setup();
    }


    @Nullable
    Inlay<MyRenderer> add(@NotNull JComponent component, @NotNull ResizePolicy policy, @Nullable Properties.RendererFactory rendererFactory,
                          boolean relatesToPrecedingText, boolean showAbove, int priority, int offset) {
      MyRenderer renderer = new MyRenderer(component, policy, rendererFactory);
      Inlay<MyRenderer> inlay = myEditor.getInlayModel().addBlockElement(offset, relatesToPrecedingText, showAbove, priority, renderer);
      if (inlay == null) return null;

      renderer.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          inlay.update();
          updateAllInlaysBelow(component.getBounds());
        }
      });
      component.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          updateSize(inlay);
          inlay.update();
          updateAllInlaysBelow(component.getBounds());
        }
      });
      renderer.addMouseWheelListener(myEditor.getContentComponent()::dispatchEvent);

      update(inlay);
      inlay.update();

      myEditor.getContentComponent().add(renderer);
      myInlays.add(inlay);
      Disposer.register(inlay, () -> {
        myEditor.getContentComponent().remove(renderer);
        myInlays.remove(inlay);
      });
      return inlay;
    }

    private void updateAllInlaysBelow(@NotNull Rectangle bounds) {
      for (Inlay<? extends MyRenderer> i : getInlaysBelow(bounds.y)) {
        update(i);
      }
    }

    private void setup() {
      EditorUtil.disposeWithEditor(myEditor, this);
      myEditor.getFoldingModel().addListener(new FoldingListener() {
        @Override
        public void onFoldProcessingEnd() {
          for (Inlay<? extends MyRenderer> inlay : myInlays) {
            update(inlay);
          }
        }
      }, this);
      myEditor.getInlayModel().addListener(new InlayModel.SimpleAdapter() {
        @Override
        public void onUpdated(@NotNull Inlay inlay, int changeFlags) {
          if ((changeFlags & InlayModel.ChangeFlags.HEIGHT_CHANGED) != 0) {
            Rectangle bounds = inlay.getBounds();
            if (bounds != null) {
              updateAllInlaysBelow(bounds);
            }
          }
        }

        @Override
        public void onRemoved(@NotNull Inlay inlay) {
          Disposer.dispose(inlay);
        }
      }, this);
      ComponentAdapter viewportListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          int maxY = myEditor.getScrollPane().getViewport().getViewRect().y;
          for (Inlay<? extends MyRenderer> inlay : getInlaysBelow(maxY)) {
            updateSize(inlay);
          }
        }
      };
      JViewport viewport = myEditor.getScrollPane().getViewport();
      viewport.addComponentListener(viewportListener);
      Disposer.register(this, () -> viewport.removeComponentListener(viewportListener));

      myEditor.addEditorMouseListener(myResizeListener);
      myEditor.addEditorMouseMotionListener(myResizeListener);
    }

    @NotNull
    private Collection<Inlay<? extends MyRenderer>> getInlaysBelow(int y) {
      return ContainerUtil.filter(myInlays, inlay -> {
        Rectangle bounds = inlay.getRenderer().getBounds();
        return bounds != null && bounds.y + bounds.height >= y;
      });
    }

    private void update(@NotNull Inlay<? extends MyRenderer> inlay) {
      Rectangle bounds = inlay.getBounds();
      MyRenderer renderer = inlay.getRenderer();
      if (bounds == null) {
        renderer.setVisible(false);
        return;
      }
      JScrollBar vsb = myEditor.getScrollPane().getVerticalScrollBar();
      renderer.setLocation(new Point(isVerticalScrollbarFlipped() ? vsb.getWidth() : 0, bounds.getLocation().y));
      renderer.setVisible(true);
      updateSize(inlay);
    }

    private boolean isVerticalScrollbarFlipped() {
      Object flipProperty = myEditor.getScrollPane().getClientProperty(JBScrollPane.Flip.class);
      return flipProperty == JBScrollPane.Flip.HORIZONTAL || flipProperty == JBScrollPane.Flip.BOTH;
    }

    private void updateSize(@NotNull Inlay<? extends MyRenderer> inlay) {
      MyRenderer renderer = inlay.getRenderer();
      if (!renderer.isVisible() || myResizeListener.isResizeInProgress(renderer)) return;

      int componentWidth = renderer.getPreferredWidth();
      int componentHeight = renderer.getPreferredHeight();
      JScrollPane scrollPane = myEditor.getScrollPane();
      int visibleWidth = scrollPane.getViewport().getWidth() - scrollPane.getVerticalScrollBar().getWidth();
      int minWidth = renderer.isWidthSet() ? componentWidth : visibleWidth;
      int width =  Math.min(componentWidth, minWidth);
      Dimension newSize = new Dimension(Math.max(width, 0), Math.max(componentHeight, 0));
      if (!renderer.getSize().equals(newSize)) renderer.setSize(newSize);
    }

    @Override
    public void dispose() {
      myEditor.setCustomCursor(this, null);
      for (int i = myInlays.size() - 1; i > -1; i--) {
        Disposer.dispose(myInlays.get(i));
      }
    }

    private static class ResizeInfo {
      final Inlay<? extends MyRenderer> inlay;
      final ResizeDirection direction;
      final int startWidth;

      ResizeInfo(@NotNull Inlay<? extends MyRenderer> inlay, @NotNull ResizeDirection direction) {
        this.inlay = inlay;
        this.direction = direction;
        this.startWidth = inlay.getRenderer().getWidth();
      }
    }

    private enum ResizeDirection {
      BOTTOM(0, 1, Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)),
      RIGHT(1, 0, Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)),
      BOTTOM_RIGHT(1, 1, Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));

      final int xMultiplier;
      final int yMultiplier;
      final Cursor cursor;

      ResizeDirection(int xMultiplier, int yMultiplier, @NotNull Cursor cursor) {
        this.xMultiplier = xMultiplier;
        this.yMultiplier = yMultiplier;
        this.cursor = cursor;
      }
    }

    private class ResizeListener implements EditorMouseListener, EditorMouseMotionListener {
      private ResizeInfo info;

      @Override
      public void mouseMoved(EditorMouseEvent e) {
        ResizeInfo info = getInfoForResizeUnder(e.getMouseEvent().getPoint());
        if (info == null) {
          resetCursor();
          return;
        }
        myEditor.setCustomCursor(ComponentInlays.this, info.direction.cursor);
      }

      @Override
      public void mousePressed(EditorMouseEvent event) {
        Point point = event.getMouseEvent().getPoint();
        info = getInfoForResizeUnder(point);
        if (info == null) return;
        event.consume();
      }

      @Override
      public void mouseReleased(@NotNull EditorMouseEvent event) {
        info = null;
        resetCursor();
      }

      @Override
      public void mouseDragged(@NotNull EditorMouseEvent event) {
        if (info == null) return;
        Point currentPoint = event.getMouseEvent().getPoint();
        MyRenderer renderer = info.inlay.getRenderer();
        int xDelta = info.direction.xMultiplier * (currentPoint.x - renderer.getX() - renderer.getWidth());
        int yDelta = info.direction.yMultiplier * (currentPoint.y - renderer.getY() - renderer.getHeight());
        Dimension size = renderer.getSize();

        int newWidth = Math.max(Math.max(info.inlay.getRenderer().getMinimumSize().width, size.width + xDelta), 0);
        int newHeight = Math.max(Math.max(info.inlay.getRenderer().getMinimumSize().height, size.height + yDelta), 0);
        renderer.setCustomWidth(newWidth);
        renderer.setCustomHeight(newHeight);

        renderer.setSize(newWidth, newHeight);
        renderer.revalidate();
        renderer.repaint(50);
        scrollTo(renderer.getBounds());
        event.consume();
      }

      @Override
      public void mouseExited(@NotNull EditorMouseEvent event) {
        resetCursor();
      }

      boolean isResizeInProgress(@NotNull MyRenderer wrapper) {
        return info != null && wrapper == info.inlay.getRenderer();
      }

      private void scrollTo(@NotNull Rectangle inlayBounds) {
        Rectangle contentComponentBounds = myEditor.getScrollPane().getViewport().getViewRect();
        int compMinX = contentComponentBounds.x;
        int compMaxX = compMinX + contentComponentBounds.width;
        int compMinY = contentComponentBounds.y;
        int compMaxY = compMinY + contentComponentBounds.height;
        int inlayMaxX = inlayBounds.x + inlayBounds.width;
        int inlayMaxY = inlayBounds.y + inlayBounds.height;
        JScrollBar hsb = myEditor.getScrollPane().getHorizontalScrollBar();
        JScrollBar vsb = myEditor.getScrollPane().getVerticalScrollBar();
        int hsbNewValue = hsb.getValue() + info.direction.xMultiplier * (inlayMaxX > compMaxX ? inlayMaxX - compMaxX :
                                                                         inlayMaxX < compMinX ? inlayMaxX - compMinX :
                                                                         0);
        int vsbNewValue = vsb.getValue() + info.direction.yMultiplier * (inlayMaxY > compMaxY ? inlayMaxY - compMaxY :
                                                                         inlayMaxY < compMinY ? inlayMaxY - compMinY :
                                                                         0);
        hsb.setValue(Math.max(0, hsbNewValue));
        vsb.setValue(Math.max(0, vsbNewValue));
      }

      private void resetCursor() {
        if (info == null) myEditor.setCustomCursor(ComponentInlays.this, null);
      }

      @Nullable
      private ResizeInfo getInfoForResizeUnder(@NotNull Point point) {
        return ContainerUtil.getFirstItem(ContainerUtil.mapNotNull(myInlays, inlay -> {
          ResizePolicy policy = inlay.getRenderer().resizePolicy;
          if (!policy.isResizable()) return null;
          Rectangle bounds = inlay.getBounds();
          if (bounds == null) return null;
          int pressY = point.y;
          int pressX = point.x;
          int inlayTY = bounds.y;
          int inlayLX = bounds.x;
          int inlayBY = bounds.y + bounds.height;
          int inlayRX = bounds.x + bounds.width;
          boolean dragFromBottom = policy.isResizableFromBottom() && isInside(pressX, inlayLX, inlayRX) && isNearTo(pressY, inlayBY);
          boolean dragFromRight = policy.isResizableFromRight() && isInside(pressY, inlayTY, inlayBY) && isNearTo(pressX, inlayRX);
          ResizeDirection direction = dragFromBottom && dragFromRight ? ResizeDirection.BOTTOM_RIGHT :
                                      dragFromBottom ? ResizeDirection.BOTTOM :
                                      dragFromRight ? ResizeDirection.RIGHT :
                                      null;
          return direction == null ? null : new ResizeInfo(inlay, direction);
        }));
    }

      private boolean isNearTo(int value, int coordinate) {
        return isInside(value, coordinate, coordinate);
      }

      private boolean isInside(int value, int min, int max) {
        return value > min - RESIZE_POINT_DELTA && value < max + RESIZE_POINT_DELTA;
      }
    }
  }
}
