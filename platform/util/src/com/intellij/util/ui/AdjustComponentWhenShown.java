/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
