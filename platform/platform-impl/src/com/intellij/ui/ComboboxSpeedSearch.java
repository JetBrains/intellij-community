// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * @author Anna.Kozlova
 */
public class ComboboxSpeedSearch extends SpeedSearchBase<JComboBox> {

  public static <T> void installSpeedSearch(JComboBox<T> comboBox, Function<? super T, String> textGetter) {
    ComboboxSpeedSearch search = new ComboboxSpeedSearch(comboBox, null) {
      @Override
      protected String getElementText(Object element) {
        return textGetter.apply((T)element);
      }
    };
    search.setupListeners();
  }

  public static @NotNull ComboboxSpeedSearch installOn(final @NotNull JComboBox<?> comboBox) {
    ComboboxSpeedSearch search = new ComboboxSpeedSearch(comboBox, null);
    search.setupListeners();
    return search;
  }

  /**
   * @deprecated Use the static method {@link ComboboxSpeedSearch#installOn(JComboBox)} to install a speed search.
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link ComboboxSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public ComboboxSpeedSearch(final @NotNull JComboBox comboBox) {
    super(comboBox);
    removeKeyStroke(comboBox.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT), KeyStroke.getKeyStroke(' ', 0));
  }

  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  public ComboboxSpeedSearch(final @NotNull JComboBox comboBox, Void sig) {
    super(comboBox, sig);
  }

  @Override
  public void setupListeners() {
    super.setupListeners();
    removeKeyStroke(myComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT), KeyStroke.getKeyStroke(' ', 0));
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
  protected int getElementCount() {
    return myComponent.getModel().getSize();
  }

  @Override
  protected Object getElementAt(int viewIndex) {
    return myComponent.getModel().getElementAt(viewIndex);
  }

  @Override
  protected String getElementText(Object element) {
    return element == null ? null : element.toString();
  }
}