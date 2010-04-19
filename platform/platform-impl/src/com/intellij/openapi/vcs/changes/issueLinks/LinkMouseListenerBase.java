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
package com.intellij.openapi.vcs.changes.issueLinks;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

public abstract class LinkMouseListenerBase extends MouseAdapter implements MouseMotionListener {
  @Nullable
  protected abstract Object getTagAt(final MouseEvent e);

  public void mouseClicked(final MouseEvent e) {
    if (!e.isPopupTrigger() && e.getButton() == 1) {
      Object tag = getTagAt(e);
      handleTagClick(tag, e);
    }
  }

  protected void handleTagClick(final Object tag, MouseEvent event) {
    if (tag instanceof Runnable) {
      ((Runnable) tag).run();
    }
  }
  
  public void mouseDragged(MouseEvent e) {
  }

  public void mouseMoved(MouseEvent e) {
    Component tree = (Component)e.getSource();
    Object tag = getTagAt(e);
    if (tag != null) {
      tree.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else {
      tree.setCursor(Cursor.getDefaultCursor());
    }
  }

  public void install(final JComponent tree) {
    tree.addMouseListener(this);
    tree.addMouseMotionListener(this);
  }
}
