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
 * @author oleg
 * @date 9/30/10
 * Please use this wrapper in case you need simple cell renderer with text and icon.
 * This avoids ugly UI under GTK look and feel, because in this case SynthComboBoxUI#SynthComboBoxRenderer is used instead of DefaultComboBoxRenderer
 */
public abstract class ListCellRendererWrapper<T> implements ListCellRenderer {
  private final ListCellRenderer myOriginalRenderer;

  private Icon myIcon;
  private String myText;
  private String myToolTipText;

  /**
   * Default JComboBox cell renderer should be passed here.
   * @param listCellRenderer
   */
  public ListCellRendererWrapper(final ListCellRenderer listCellRenderer) {
    this.myOriginalRenderer = listCellRenderer;
  }

  public final Component getListCellRendererComponent(final JList list,
                                                      final Object value,
                                                      final int index,
                                                      final boolean isSelected,
                                                      final boolean cellHasFocus) {
    try {
      customize(list, (T)value, index, isSelected, cellHasFocus);
      final Component component = myOriginalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (component instanceof JLabel) {
        final JLabel label = (JLabel)component;
        if (myIcon != null) label.setIcon(myIcon);
        if (myText != null) label.setText(myText);
        if (myToolTipText != null) label.setToolTipText(myToolTipText);
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
   * Use setIcon(icon) and setText(text) methods.
   * @param list
   * @param value Value to customize presentation for
   * @param index
   * @param selected
   * @param cellHasFocus
   */
  public abstract void customize(final JList list, final T value, final int index, final boolean selected, final boolean cellHasFocus);

  public final void setIcon(final Icon icon) {
    myIcon = icon;
  }

  public final void setText(final String text) {
    myText = text;
  }

  public void setToolTipText(final String toolTipText) {
    myToolTipText = toolTipText;
  }
}
