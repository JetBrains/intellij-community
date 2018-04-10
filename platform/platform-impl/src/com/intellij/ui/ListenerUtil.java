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
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class ListenerUtil {

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
