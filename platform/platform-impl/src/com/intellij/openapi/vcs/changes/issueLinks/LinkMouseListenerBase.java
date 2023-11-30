// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.ui.ClickListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

public abstract class LinkMouseListenerBase<T> extends ClickListener implements MouseMotionListener {
  public static void installSingleTagOn(@NotNull SimpleColoredComponent component) {
    new LinkMouseListenerBase<Object>() {
      @Override
      protected @Nullable Object getTagAt(@NotNull MouseEvent e) {
        return ((SimpleColoredComponent)e.getSource()).getFragmentTagAt(e.getX());
      }

      @Override
      protected void handleTagClick(@Nullable Object tag, @NotNull MouseEvent event) {
        if (tag != null) {
          if (tag instanceof Consumer consumer) {
            //noinspection unchecked
            consumer.consume(event);
          }
          else if (tag instanceof java.util.function.Consumer consumer) {
            //noinspection unchecked
            consumer.accept(event);
          }
          else {
            ((Runnable)tag).run();
          }
        }
      }
    }.installOn(component);
  }

  protected abstract @Nullable T getTagAt(@NotNull MouseEvent e);

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
    UIUtil.setCursor(component, tag != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
  }

  @Override
  public void installOn(@NotNull Component component) {
    super.installOn(component);

    component.addMouseMotionListener(this);
  }
}
