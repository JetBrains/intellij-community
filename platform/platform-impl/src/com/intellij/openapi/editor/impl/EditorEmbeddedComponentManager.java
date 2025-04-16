// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public final class EditorEmbeddedComponentManager {
  private static final Key<ComponentInlays> COMPONENT_INLAYS_KEY = Key.create("editor.embedded.component.inlays");
  private static final int RESIZE_POINT_DELTA = JBUI.scale(5);

  private static final EditorEmbeddedComponentManager ourInstance = new EditorEmbeddedComponentManager();

  private EditorEmbeddedComponentManager() {
  }

  public static @NotNull EditorEmbeddedComponentManager getInstance() {
    return ourInstance;
  }

  public @Nullable Inlay<?> addComponent(@NotNull EditorEx editor,
                                         @NotNull JComponent component,
                                         @NotNull Properties properties) {
    ThreadingAssertions.assertEventDispatchThread();

    ComponentInlays inlays = getComponentInlaysFor(editor);
    return inlays.add(component, properties.resizePolicy, properties.rendererFactory,
                      properties.relatesToPrecedingText, properties.showAbove, properties.showWhenFolded, properties.fullWidth,
                      properties.priority, properties.offset);
  }

  private static @NotNull ComponentInlays getComponentInlaysFor(@NotNull EditorEx editor) {
    if (!COMPONENT_INLAYS_KEY.isIn(editor)) {
      COMPONENT_INLAYS_KEY.set(editor, new ComponentInlays(editor));
    }
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

    public static @NotNull ResizePolicy none() {
      return ourNone;
    }

    public boolean isResizableFromRight() {
      return (myFlags & RIGHT) != 0;
    }

    public boolean isResizableFromBottom() {
      return (myFlags & BOTTOM) != 0;
    }
  }

  public static final class Properties {
    final ResizePolicy resizePolicy;
    final RendererFactory rendererFactory;
    final boolean relatesToPrecedingText;
    final boolean showAbove;
    final boolean showWhenFolded;
    final boolean fullWidth;
    final int priority;
    final int offset;

    public Properties(@NotNull ResizePolicy resizePolicy,
                      @Nullable RendererFactory rendererFactory,
                      boolean relatesToPrecedingText,
                      boolean showAbove,
                      int priority,
                      int offset) {
      this(resizePolicy, rendererFactory, relatesToPrecedingText, showAbove, false, false, priority, offset);
    }

    public Properties(@NotNull ResizePolicy resizePolicy,
                      @Nullable RendererFactory rendererFactory,
                      boolean relatesToPrecedingText,
                      boolean showAbove,
                      boolean showWhenFolded,
                      int priority,
                      int offset) {
      this(resizePolicy, rendererFactory, relatesToPrecedingText, showAbove, false, false, priority, offset);
    }

    public Properties(@NotNull ResizePolicy resizePolicy,
                      @Nullable RendererFactory rendererFactory,
                      boolean relatesToPrecedingText,
                      boolean showAbove,
                      boolean showWhenFolded,
                      boolean fullWidth,
                      int priority,
                      int offset) {
      this.resizePolicy = resizePolicy;
      this.rendererFactory = rendererFactory;
      this.relatesToPrecedingText = relatesToPrecedingText;
      this.showAbove = showAbove;
      this.showWhenFolded = showWhenFolded;
      this.fullWidth = fullWidth;
      this.priority = priority;
      this.offset = offset;
    }

    public interface RendererFactory {
      @Nullable GutterIconRenderer createRenderer(@NotNull Inlay<?> inlay);
    }
  }

  @ApiStatus.Internal
  public static class MyRenderer extends JPanel implements EditorCustomElementRenderer {
    private static final int UNDEFINED = -1;

    final ResizePolicy resizePolicy;

    private final Properties.RendererFactory myRendererFactory;
    private int myCustomWidth = UNDEFINED;
    private int myCustomHeight = UNDEFINED;
    protected final @NotNull JScrollPane myEditorScrollPane;
    private final @NotNull ComponentInlays.ResizeListener myResizeListener;
    private @Nullable Inlay<MyRenderer> myInlay;

    MyRenderer(@NotNull JComponent component,
               @NotNull ResizePolicy resizePolicy,
               @Nullable Properties.RendererFactory rendererFactory,
               @NotNull JScrollPane editorScrollPane,
               @NotNull ComponentInlays.ResizeListener resizeListener) {
      super(new BorderLayout());
      this.resizePolicy = resizePolicy;
      myRendererFactory = rendererFactory;
      myEditorScrollPane = editorScrollPane;
      myResizeListener = resizeListener;
      add(component, BorderLayout.CENTER);
      setOpaque(false);
    }

    @Override
    public @Nullable GutterIconRenderer calcGutterIconRenderer(@NotNull Inlay inlay) {
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

    @Override
    public void doLayout() {
      ReadAction.run(() -> {
        synchronizeBoundsWithInlay();
        super.doLayout();
      });
    }

    @Override
    public void validate() {
      synchronizeBoundsWithInlay();
      super.validate();
    }

    private void synchronizeBoundsWithInlay() {
      if (myInlay != null && !myResizeListener.isResizeInProgress(this) && !myInlay.getEditor().getDocument().isInBulkUpdate()) {
        Rectangle inlayBounds = myInlay.getBounds();
        boolean shouldUpdateInlay;
        if (inlayBounds != null) {
          inlayBounds.setLocation(inlayBounds.x + verticalScrollbarLeftShift(), inlayBounds.y);

          int visibleWidth = myEditorScrollPane.getViewport().getWidth() - myEditorScrollPane.getVerticalScrollBar().getWidth();
          Rectangle newBounds = new Rectangle(
            inlayBounds.x,
            inlayBounds.y,
            isWidthSet() ? myCustomWidth : Math.min(getPreferredWidth(), visibleWidth),
            getPreferredHeight()
          );
          shouldUpdateInlay = !isVisible() || !newBounds.equals(inlayBounds);
          if (shouldUpdateInlay || !newBounds.equals(getBounds())) {
            setVisible(true);
            setBounds(newBounds);
          }
        }
        else {
          shouldUpdateInlay = isVisible();
          setVisible(false);
        }
        if (shouldUpdateInlay) {
          myInlay.update();
        }
      }
    }

    public void setInlay(@Nullable Inlay<MyRenderer> inlay) {
      myInlay = inlay;
    }

    private int verticalScrollbarLeftShift() {
      Object flipProperty = myEditorScrollPane.getClientProperty(JBScrollPane.Flip.class);
      if (flipProperty == JBScrollPane.Flip.HORIZONTAL || flipProperty == JBScrollPane.Flip.BOTH) {
        return myEditorScrollPane.getVerticalScrollBar().getWidth();
      }
      return 0;
    }
  }

  @ApiStatus.Internal
  public static final class FullEditorWidthRenderer extends MyRenderer {

    FullEditorWidthRenderer(@NotNull JComponent component,
                            @NotNull ResizePolicy resizePolicy,
                            Properties.@Nullable RendererFactory rendererFactory,
                            @NotNull JScrollPane editorScrollPane,
                            ComponentInlays.@NotNull ResizeListener resizeListener) {
      super(component, resizePolicy, rendererFactory, editorScrollPane, resizeListener);
    }

    @Override
    int getPreferredWidth() {
      return myEditorScrollPane.getViewport().getWidth() - myEditorScrollPane.getVerticalScrollBar().getWidth();
    }
  }

  private static final class ComponentInlays implements Disposable {
    private final EditorEx myEditor;
    private final ResizeListener myResizeListener;

    ComponentInlays(@NotNull EditorEx editor) {
      myEditor = editor;
      myResizeListener = new ResizeListener();
      setup();
    }


    @Nullable
    Inlay<MyRenderer> add(@NotNull JComponent component,
                          @NotNull ResizePolicy policy,
                          @Nullable Properties.RendererFactory rendererFactory,
                          boolean relatesToPrecedingText,
                          boolean showAbove,
                          boolean showWhenFolded,
                          boolean fullWidth,
                          int priority,
                          int offset) {
      if (myEditor.isDisposed()) return null;


      MyRenderer renderer = fullWidth ? new FullEditorWidthRenderer(component, policy, rendererFactory, myEditor.getScrollPane(), myResizeListener) : new MyRenderer(component, policy, rendererFactory, myEditor.getScrollPane(), myResizeListener);
      Inlay<MyRenderer> inlay = myEditor.getInlayModel().addBlockElement(offset,
                                                                         new InlayProperties()
                                                                           .relatesToPrecedingText(relatesToPrecedingText)
                                                                           .showAbove(showAbove)
                                                                           .priority(priority)
                                                                           .showWhenFolded(showWhenFolded),
                                                                         renderer);
      if (inlay == null) return null;
      Disposer.register(this, inlay);

      renderer.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          if (e.getSource() instanceof MyRenderer renderer) {
            revalidateComponents(renderer.getBounds().y);
          }
        }
      });

     // renderer.addMouseWheelListener(myEditor.getContentComponent()::dispatchEvent);

      renderer.setInlay(inlay);
      myEditor.getContentComponent().add(renderer);
      Disposer.register(inlay, () -> {
        Runnable runnable = () -> {
          renderer.setInlay(null);
          myEditor.getContentComponent().remove(renderer);
        };
        Application application = ApplicationManager.getApplication();
        if (application.isDispatchThread()) runnable.run();
        else application.invokeLater(runnable);
      });

      // If validation is postponed, visual artifacts can appear while typing text.
      if (!myEditor.getInlayModel().isInBatchMode()) {
        renderer.validate();
      }

      return inlay;
    }

    private void revalidateComponents() {
      revalidateComponents(Integer.MIN_VALUE);
    }

    private void revalidateComponents(int yTop) {
      JComponent parent = myEditor.getContentComponent();
      for (int i = 0; i < parent.getComponentCount(); ++i) {
        Component component = parent.getComponent(i);
        if (component instanceof MyRenderer && component.getY() >= yTop) {
          component.revalidate();
        }
      }
    }

    private void setup() {
      Disposer.register(((EditorImpl)myEditor).getDisposable(), this);
      myEditor.getFoldingModel().addListener(new FoldingListener() {
        @Override
        public void onFoldProcessingEnd() {
          revalidateComponents();
        }
      }, this);
      myEditor.getDocument().addDocumentListener(new DocumentListener() {
        private int linesBefore;

        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent event) {
          linesBefore = event.getDocument().getLineCount();
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          if (linesBefore != event.getDocument().getLineCount() && !event.getDocument().isInBulkUpdate()) {
            int y = myEditor.logicalPositionToXY(new LogicalPosition(event.getDocument().getLineNumber(event.getOffset()), 0)).y;
            revalidateComponents(y);
          }
        }

        @Override
        public void bulkUpdateFinished(@NotNull Document document) {
          revalidateComponents();
        }
      }, this);
      myEditor.getInlayModel().addListener(new InlayModel.SimpleAdapter() {
        @Override
        public void onUpdated(@NotNull Inlay<?> inlay, int changeFlags) {
          if ((changeFlags & InlayModel.ChangeFlags.HEIGHT_CHANGED) != 0 && inlay.getRenderer() instanceof MyRenderer component) {
            // This method can be called while validating the same component. Prevent resetting parent validation flags.
            if (component.isValid()) {
              component.revalidate();
            }
          }
        }

        @Override
        public void onRemoved(@NotNull Inlay<?> inlay) {
          Disposer.dispose(inlay);
        }
      }, this);
      ComponentAdapter viewportListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          revalidateComponents();
        }
      };
      JViewport viewport = myEditor.getScrollPane().getViewport();
      viewport.addComponentListener(viewportListener);
      Disposer.register(this, () -> viewport.removeComponentListener(viewportListener));

      myEditor.addEditorMouseListener(myResizeListener);
      myEditor.addEditorMouseMotionListener(myResizeListener);
    }

    @Override
    public void dispose() {
      // All inlays are already registered in the disposable.
    }

    private static final class ResizeInfo {
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

    private final class ResizeListener implements EditorMouseListener, EditorMouseMotionListener {
      private ResizeInfo info;

      @Override
      public void mouseMoved(@NotNull EditorMouseEvent e) {
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
        if (info != null) {
          info.inlay.getRenderer().revalidate();
          info = null;
        }
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

      private @Nullable ResizeInfo getInfoForResizeUnder(@NotNull Point point) {
        return ContainerUtil.getFirstItem(ContainerUtil.mapNotNull(myEditor.getContentComponent().getComponents(), component -> {
          if (!(component instanceof MyRenderer)) return null;
          ResizePolicy policy = ((MyRenderer)component).resizePolicy;
          if (!policy.isResizable()) return null;
          Inlay<MyRenderer> inlay = ((MyRenderer)component).myInlay;
          if (inlay == null) return null;
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

      private static boolean isNearTo(int value, int coordinate) {
        return isInside(value, coordinate, coordinate);
      }

      private static boolean isInside(int value, int min, int max) {
        return value > min - RESIZE_POINT_DELTA && value < max + RESIZE_POINT_DELTA;
      }
    }
  }
}
