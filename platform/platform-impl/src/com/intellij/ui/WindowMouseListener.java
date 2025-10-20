// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.JBR;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.intellij.ui.WindowMouseListenerSupportKt.createWindowMouseListenerSupport;

@ApiStatus.Internal
public abstract class WindowMouseListener extends MouseAdapter implements MouseInputListener {
  protected final Component myContent;
  protected final WindowMouseListenerSupport support = createWindowMouseListenerSupport(new SourceAdapter());
  
  private class SourceAdapter implements WindowMouseListenerSource {
    @Override
    public @NotNull Component getContent(@NotNull MouseEvent event) {
      return WindowMouseListener.this.getContent(event);
    }

    @Override
    public @Nullable Component getView(@NotNull Component event) {
      return WindowMouseListener.this.getView(event);
    }

    @Override
    public boolean isDisabled(@NotNull Component view) {
      return WindowMouseListener.this.isDisabled(view);
    }

    @Override
    public int getCursorType(@Nullable Component view, @Nullable Point location) {
      return WindowMouseListener.this.getCursorType(view, location);
    }

    @Override
    public void setCursor(@NotNull Component content, @Nullable Cursor cursor) {
      WindowMouseListener.this.setCursor(content, cursor);
    }

    @Override
    public void updateBounds(@NotNull Rectangle bounds, @NotNull Component view, int dx, int dy) {
      WindowMouseListener.this.updateBounds(bounds, view, dx, dy);
    }

    @Override
    public void notifyMoved() {
      WindowMouseListener.this.notifyMoved();
    }

    @Override
    public void notifyResized() {
      WindowMouseListener.this.notifyResized();
    }
  }

  /**
   * @param content the window content to find a window, or {@code null} to use a component from a mouse event
   */
  WindowMouseListener(Component content) {
    myContent = content;
  }

  /**
   * @param view     the component to move/resize
   * @param location the current mouse position on a screen
   * @return cursor type for the specified location on the specified view or CUSTOM_CURSOR if cursor type is not supported
   */
  @JdkConstants.CursorType
  abstract int getCursorType(Component view, Point location);

  @JdkConstants.CursorType
  int getCursorType() {
    return support.getCursorType();
  }

  /**
   * @param bounds the component bounds, which should be updated
   * @param view   the component to move/resize
   * @param dx     horizontal offset
   * @param dy     vertical offset
   */
  abstract void updateBounds(Rectangle bounds, Component view, int dx, int dy);

  public void setLeftMouseButtonOnly(boolean leftMouseButtonOnly) {
    support.setLeftMouseButtonOnly(leftMouseButtonOnly);
  }

  @Override
  public void mouseMoved(MouseEvent event) {
    support.update(event, false);
  }

  @Override
  public void mousePressed(MouseEvent event) {
    support.update(event, true);
  }

  @Override
  public void mouseDragged(MouseEvent event) {
    support.process(event, true);
  }

  @Override
  public void mouseReleased(MouseEvent event) {
    support.process(event, false);
  }

  @Override
  public void mouseClicked(MouseEvent event) {
    support.process(event, false);
  }

  /**
   * @param view the component to move/resize
   * @return {@code true} if the specified component cannot be moved/resized, or {@code false} otherwise
   */
  protected boolean isDisabled(Component view) {
    if (view instanceof Frame) {
      int state = ((Frame)view).getExtendedState();
      if (isStateSet(Frame.ICONIFIED, state)) return true;
      if (isStateSet(Frame.MAXIMIZED_BOTH, state) && !jbrMoveSupported(view)) return true;
    }
    return false;
  }

  /**
   * Returns a window content which is used to find corresponding window and to set a cursor.
   * By default, it returns a component from the specified mouse event if the content is not specified.
   */
  protected Component getContent(MouseEvent event) {
    return myContent != null ? myContent : event.getComponent();
  }

  /**
   * Finds a movable/resizable view for the specified content.
   * By default, it returns the first window ancestor.
   * It can be overridden to return something else,
   * for example, a layered component.
   */
  protected Component getView(Component component) {
    return ComponentUtil.getWindow(component);
  }

  /**
   * Sets the specified cursor for the specified content.
   * It can be overridden if another approach is used.
   * <p>
   * Note: default implementation takes Component.getTreeLock()
   */
  protected void setCursor(@NotNull Component content, Cursor cursor) {
    UIUtil.setCursor(content, cursor);
  }

  /**
   * Returns {@code true} if a window is now moving/resizing.
   */
  public boolean isBusy() {
    return support.isBusy();
  }

  protected void notifyMoved() { }

  protected void notifyResized() { }

  static boolean isStateSet(int mask, int state) {
    return mask == (mask & state);
  }

  private static boolean jbrMoveSupported(Component component) {
    if (StartupUiUtil.isWaylandToolkit()) {
      return (component instanceof Window window) && window.getType() != Window.Type.POPUP;
    }
    else {
      // The JBR team states that isWindowMoveSupported works only for Frame/Dialog
      return (component instanceof Frame || component instanceof Dialog) && JBR.isWindowMoveSupported();
    }
  }
}
