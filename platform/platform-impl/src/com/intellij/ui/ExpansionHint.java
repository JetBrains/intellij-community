/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.EventObject;
import java.util.List;

public class ExpansionHint implements Hint {
  private final JComponent myComponent;
  private final List<HintListener> myListeners;
  private JBPopup myPopup;

  public ExpansionHint(@NotNull JComponent component) {
    myComponent = component;
    myListeners = ContainerUtil.newSmartList();
  }

  @Override
  public void show(@NotNull JComponent parentComponent, int x, int y, @Nullable JComponent focusBackComponent, @Nullable HintHint hh) {
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myComponent, null)
      .setRequestFocus(false)
      .setFocusable(false)
      .setResizable(false)
      .setMovable(false)
      .setModalContext(false)
      .setShowShadow(false)
      .setShowBorder(false)
      .setCancelKeyEnabled(false)
      .setCancelOnClickOutside(false)
      .setCancelOnOtherWindowOpen(false)
      .createPopup();

    myPopup.show(new RelativePoint(parentComponent, new Point(x, y)));
  }

  @Override
  public boolean isVisible() {
    return myComponent.isShowing();
  }

  @Override
  public void hide() {
    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
    fireHintHidden();
  }

  private void fireHintHidden() {
    EventObject event = new EventObject(this);
    for (HintListener listener : myListeners) {
      listener.hintHidden(event);
    }
  }

  @Override
  public void pack() { }

  @Override
  public void updateBounds(int x, int y) { }

  @Override
  public void setLocation(@NotNull RelativePoint point) {
    if (myPopup != null) {
      myPopup.setLocation(point.getScreenPoint());
    }
  }

  @Override
  public void addHintListener(@NotNull HintListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeHintListener(@NotNull HintListener listener) {
    myListeners.remove(listener);
  }
}