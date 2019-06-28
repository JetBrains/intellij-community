// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

/**
 * @author irengrig
 */
public abstract class AdjustComponentWhenShown {
  private boolean myIsAdjusted;

  protected boolean canExecute() {
    return true;
  }

  protected abstract boolean init();

  public void install(final Component component) {
    final ComponentListener listener = new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
        impl();
      }

      private void impl() {
        if (canExecute()) {
          if (init()) {
            component.removeComponentListener(this);
            myIsAdjusted = true;
          }
        }
      }

      @Override
      public void componentMoved(ComponentEvent e) {
      }
      @Override
      public void componentShown(ComponentEvent e) {
        impl();
      }
      @Override
      public void componentHidden(ComponentEvent e) {
      }
    };
    component.addComponentListener(listener);
  }

  public boolean isAdjusted() {
    return myIsAdjusted;
  }
}
