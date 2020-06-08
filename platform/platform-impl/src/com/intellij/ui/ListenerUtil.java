// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public final class ListenerUtil {

  public static void addFocusListener(Component component, FocusListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> c.addFocusListener(l));
  }

  public static void removeFocusListener(Component component, FocusListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> c.removeFocusListener(l));
  }

  public static void addMouseListener(Component component, MouseListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> c.addMouseListener(l));
  }

  public static void removeMouseListener(Component component, MouseListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> c.removeMouseListener(l));
  }

  public static void addClickListener(Component component, ClickListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> l.installOn(c));
  }

  public static void removeClickListener(Component component, ClickListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> l.uninstall(c));
  }

  public static void addMouseMotionListener(Component component, MouseMotionListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> c.addMouseMotionListener(l));
  }

  public static void removeMouseMotionListener(Component component, MouseMotionListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> c.removeMouseMotionListener(l));
  }

  public static void addKeyListener(Component component, KeyListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> c.addKeyListener(l));
  }

  public static void removeKeyListener(Component component, KeyListener l) {
    UIUtil.uiTraverser(component).traverse().consumeEach(c -> c.removeKeyListener(l));
  }
}
