// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * @author Anna.Kozlova
 */
public class ComboboxSpeedSearch extends SpeedSearchBase<JComboBox> {

  public static <T> void  installSpeedSearch(JComboBox<T> comboBox, Function<T, String> textGetter) {
    new ComboboxSpeedSearch(comboBox) {
      @Override
      protected String getElementText(Object element) {
        return textGetter.apply((T)element);
      }
    };
  }

  public ComboboxSpeedSearch(@NotNull final JComboBox comboBox) {
    super(comboBox);
    removeKeyStroke(comboBox.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT), KeyStroke.getKeyStroke(' ', 0));
  }

  private static void removeKeyStroke(@Nullable InputMap map, KeyStroke ks) {
    while (map != null) {
      map.remove(ks);
      map = map.getParent();
    }
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    myComponent.setSelectedItem(element);
    myComponent.repaint();
  }

  @Override
  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  @Override
  protected Object @NotNull [] getAllElements() {
    ListModel model = myComponent.getModel();
    Object[] elements = new Object[model.getSize()];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = model.getElementAt(i);
    }
    return elements;
  }

  @Override
  protected String getElementText(Object element) {
    return element == null ? null : element.toString();
  }
}