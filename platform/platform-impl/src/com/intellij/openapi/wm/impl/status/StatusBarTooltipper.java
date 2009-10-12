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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author cdr
 */
public class StatusBarTooltipper {
  public static void install(@NotNull final StatusBarPatch patch, @NotNull final StatusBar statusBar) {
    final JComponent component = patch.getComponent();
    install(patch, component, statusBar);
  }
  public static void install(@NotNull final StatusBarPatch patch, @NotNull final JComponent component, @NotNull final StatusBar statusBar) {
    component.addMouseListener(new MouseAdapter() {
      public void mouseEntered(final MouseEvent e) {
        final String text = statusBar instanceof StatusBarImpl ? patch.updateStatusBar(((StatusBarImpl)statusBar).getEditor(), component) : null;
        statusBar.setInfo(text);
        component.setToolTipText(text);
      }

      public void mouseExited(final MouseEvent e) {
        statusBar.setInfo(null);
        component.setToolTipText(null);
      }
    });
  }
}
