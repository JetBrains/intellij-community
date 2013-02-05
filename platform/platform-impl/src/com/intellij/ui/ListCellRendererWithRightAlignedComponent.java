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

import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
//todo[nik,anyone] feel free to rename this class
public abstract class ListCellRendererWithRightAlignedComponent<T> implements ListCellRenderer {
  private ListCellRenderer myLeftRenderer;
  private ListCellRenderer myRightRenderer;
  private JComponent myPanel;
  private String myLeftText;
  private String myRightText;
  private Icon myIcon;
  private Icon myRightIcon;
  private Color myRightForeground;

  public ListCellRendererWithRightAlignedComponent() {
    myPanel = new JPanel(new BorderLayout());
    myLeftRenderer = new ListCellRendererWrapper<T>() {
      @Override
      public void customize(JList list, T value, int index, boolean selected, boolean hasFocus) {
        setText(myLeftText);
        setIcon(myIcon);
      }
    };
    myRightRenderer = new ListCellRendererWrapper<T>() {
      @Override
      public void customize(JList list, T value, int index, boolean selected, boolean hasFocus) {
        setText(StringUtil.notNullize(myRightText));
        setIcon(myRightIcon);
        setForeground(myRightForeground);
      }
    };
  }

  protected abstract void customize(T value);

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    myPanel.removeAll();
    myLeftText = null;
    myRightText = null;
    myIcon = null;
    myRightForeground = null;
    //noinspection unchecked
    customize((T)value);
    myPanel.add(myLeftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus), BorderLayout.CENTER);
    myPanel.add(myRightRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus), BorderLayout.EAST);
    return myPanel;
  }

  protected Icon getRightIcon() {
    return myRightIcon;
  }

  protected void setRightIcon(Icon rightIcon) {
    myRightIcon = rightIcon;
  }

  protected final void setLeftText(String text) {
    myLeftText = text;
  }

  protected final void setIcon(Icon icon) {
    myIcon = icon;
  }

  protected final void setRightText(String text) {
    myRightText = text;
  }

  protected final void setRightForeground(Color color) {
    myRightForeground = color;
  }
}
