/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;

public abstract class ChildFocusWatcher implements AWTEventListener, Disposable {
  private final JComponent myParent;

  public ChildFocusWatcher(JComponent parent) {
    myParent = parent;
    Toolkit.getDefaultToolkit().addAWTEventListener(this, FocusEvent.FOCUS_EVENT_MASK);
  }

  public void eventDispatched(final AWTEvent event) {
    if (event instanceof FocusEvent) {
      final FocusEvent fe = (FocusEvent)event;
      final Component component = fe.getComponent();
      if (component == null) return;
      if (!SwingUtilities.isDescendingFrom(component, myParent)) return;

      if (fe.getID() == FocusEvent.FOCUS_GAINED) {
        onFocusGained(fe);
      } else if (fe.getID() == FocusEvent.FOCUS_LAST) {
        onFocusLost(fe);
      }
    }
  }

  public void dispose() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
  }

  protected abstract void onFocusGained(FocusEvent event);
  protected abstract void onFocusLost(FocusEvent event);
}
