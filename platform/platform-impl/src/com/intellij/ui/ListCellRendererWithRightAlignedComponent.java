/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;

//todo[nik,anyone] feel free to rename this class
public abstract class ListCellRendererWithRightAlignedComponent<T> implements ListCellRenderer<T> {
  private final ListCellRenderer<T> myLeftRenderer;
  private final ListCellRenderer<T> myRightRenderer;
  private final JComponent myPanel;
  private @NlsContexts.Label String myLeftText;
  private @NlsContexts.Label String myRightText;
  private Icon myIcon;
  private Icon myRightIcon;
  private Color myLeftForeground;
  private Color myRightForeground;

  public ListCellRendererWithRightAlignedComponent() {
    myPanel = new CellRendererPanel();
    myPanel.setLayout(new BorderLayout());
    myLeftRenderer = SimpleListCellRenderer.create((label, value, index) -> {
      label.setText(myLeftText);
      label.setIcon(myIcon);
    });
    myRightRenderer = SimpleListCellRenderer.create((label, value, index) -> {
      label.setText(StringUtil.notNullize(myRightText));
      label.setIcon(myRightIcon);
    });
  }

  protected abstract void customize(T value);

  @Override
  public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected, boolean cellHasFocus) {
    myPanel.removeAll();
    myLeftText = null;
    myRightText = null;
    myIcon = null;
    myRightForeground = null;
    customize(value);
    Component left = myLeftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    Component right = myRightRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (!isSelected) {
      left.setForeground(myLeftForeground);
      right.setForeground(myRightForeground);
    }
    myPanel.add(left, BorderLayout.CENTER);
    myPanel.add(right, BorderLayout.EAST);
    return myPanel;
  }

  protected Icon getRightIcon() {
    return myRightIcon;
  }

  protected void setRightIcon(Icon rightIcon) {
    myRightIcon = rightIcon;
  }

  protected final void setLeftText(@NlsContexts.Label String text) {
    myLeftText = text;
  }

  protected final void setIcon(Icon icon) {
    myIcon = icon;
  }

  protected final void setRightText(@NlsContexts.Label String text) {
    myRightText = text;
  }

  protected final void setLeftForeground(Color color) {
    myLeftForeground = color;
  }

  protected final void setRightForeground(Color color) {
    myRightForeground = color;
  }
}
