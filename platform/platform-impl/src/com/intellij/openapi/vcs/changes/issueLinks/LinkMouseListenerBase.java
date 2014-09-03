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

import com.intellij.ui.ClickListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

public abstract class LinkMouseListenerBase<T> extends ClickListener implements MouseMotionListener {
  public static void installSingleTagOn(@NotNull SimpleColoredComponent component) {
    new LinkMouseListenerBase<Object>() {
      @Nullable
      @Override
      protected Object getTagAt(@NotNull MouseEvent e) {
        //noinspection unchecked
        return ((SimpleColoredComponent)e.getSource()).getFragmentTagAt(e.getX());
      }

      @Override
      protected void handleTagClick(@Nullable Object tag, @NotNull MouseEvent event) {
        if (tag != null) {
          if (tag instanceof Consumer) {
            //noinspection unchecked
            ((Consumer<MouseEvent>)tag).consume(event);
          }
          else {
            ((Runnable)tag).run();
          }
        }
      }
    }.installOn(component);
  }

  @Nullable
  protected abstract T getTagAt(@NotNull MouseEvent e);

  @Override
  public boolean onClick(@NotNull MouseEvent e, int clickCount) {
    if (e.getButton() == MouseEvent.BUTTON1) {
      handleTagClick(getTagAt(e), e);
    }
    return false;
  }

  protected void handleTagClick(@Nullable T tag, @NotNull MouseEvent event) {
    if (tag instanceof Runnable) {
      ((Runnable)tag).run();
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    Component component = (Component)e.getSource();
    Object tag = getTagAt(e);
    if (tag != null) {
      component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else {
      component.setCursor(Cursor.getDefaultCursor());
    }
  }

  @Override
  public void installOn(@NotNull Component component) {
    super.installOn(component);

    component.addMouseMotionListener(this);
  }
}
