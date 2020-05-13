// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import javax.swing.event.MouseInputListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public abstract class MouseEventHandler extends MouseAdapter implements MouseInputListener {
  /**
   * The event shouldn't be just delegated to JComponent.processMouseEvent() or processMouseMotionEvent(), check its ID first
   */
  protected abstract void handle(MouseEvent event);

  @Override
  public void mousePressed(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseClicked(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseReleased(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseEntered(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseExited(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseMoved(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseDragged(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent event) {
    handle(event);
  }

  public static final MouseEventHandler CONSUMER = new MouseEventHandler() {
    @Override
    protected void handle(MouseEvent event) {
      event.consume();
    }
  };
}
