/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Please use this wrapper in case you need simple cell renderer with text and icon.
 * This avoids ugly UI under GTK+ look&feel, because in this case SynthComboBoxUI#SynthComboBoxRenderer
 * is used instead of DefaultComboBoxRenderer.
 *
 * @author oleg
 * Date: 9/30/10
 */
public abstract class ListCellRendererWrapper<T> implements ListCellRenderer {
  private final ListCellRenderer myOriginalRenderer;

  private Icon myIcon;
  private String myText;
  private String myToolTipText;
  private Color myForeground;

  /**
   * A combo box for which this cell renderer is created should be passed here.
   * @param comboBox The combo box for which this cell renderer is created.
   */
  public ListCellRendererWrapper(final JComboBox comboBox) {
    myOriginalRenderer = comboBox.getRenderer();
  }

  /**
   * Default JComboBox cell renderer should be passed here.
   * @param listCellRenderer Default cell renderer ({@link javax.swing.JComboBox#getRenderer()}).
   */
  public ListCellRendererWrapper(final ListCellRenderer listCellRenderer) {
    myOriginalRenderer = listCellRenderer;
  }

  public final Component getListCellRendererComponent(final JList list,
                                                      final Object value,
                                                      final int index,
                                                      final boolean isSelected,
                                                      final boolean cellHasFocus) {
    try {
      //noinspection unchecked
      customize(list, (T)value, index, isSelected, cellHasFocus);
      final Component component = myOriginalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (component instanceof JLabel) {
        final JLabel label = (JLabel)component;
        label.setIcon(myIcon);
        if (myText != null) label.setText(myText);
        if (myForeground != null) label.setForeground(myForeground);
        label.setToolTipText(myToolTipText);
      }
      return component;
    }
    finally {
      myIcon = null;
      myText = null;
      myToolTipText = null;
    }
  }

  /**
   * Implement this method to configure text and icon for given value.
   * Use {@link #setIcon(javax.swing.Icon)} and {@link #setText(String)} methods.
   *
   * @param list The JList we're painting.
   * @param value The value returned by list.getModel().getElementAt(index).
   * @param index The cells index.
   * @param selected True if the specified cell was selected.
   * @param hasFocus True if the specified cell has the focus.
   */
  public abstract void customize(final JList list, final T value, final int index, final boolean selected, final boolean hasFocus);

  public final void setIcon(final @Nullable Icon icon) {
    myIcon = icon;
  }

  public final void setText(final String text) {
    myText = text;
  }

  public final void setToolTipText(final String toolTipText) {
    myToolTipText = toolTipText;
  }

  public void setForeground(final Color foreground) {
    myForeground = foreground;
  }
}
