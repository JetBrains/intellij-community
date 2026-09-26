// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.plaf.ComboBoxUI;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

public class XDebuggerEmbeddedComboBox<T> extends ComboBox<T> {

  private @Nullable JComponent myEmbeddedComponent;

  public XDebuggerEmbeddedComboBox() {
  }

  public XDebuggerEmbeddedComboBox(@NotNull ComboBoxModel<T> model, int width) {
    super(model, width);
  }

  @Override
  public void setUI(ComboBoxUI ui) {
    EmbeddedComboBoxUI newUI = new EmbeddedComboBoxUI();
    if (myEmbeddedComponent != null) {
      newUI.setEmbeddedComponent(myEmbeddedComponent);
    }
    super.setUI(newUI);
  }

  public void setExtension(JComponent component) {
    var comboBoxUI = (EmbeddedComboBoxUI)getUI();
    comboBoxUI.setEmbeddedComponent(myEmbeddedComponent = component);
  }
}

/**
 * ComboBoxUI with extra space for a component.
 */
class EmbeddedComboBoxUI extends DarculaComboBoxUI {

  protected final @NotNull NonOpaquePanel myPanel = new NonOpaquePanel();

  EmbeddedComboBoxUI() {
    setPaintArrowButton(false);
  }

  public void setEmbeddedComponent(@NotNull JComponent panel) {
    myPanel.setContent(panel);
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    // there's no 'ComboBox.padding' option in the tests,
    // because they don't use DarculaLaF
    if (padding == null) {
      padding = JBInsets.emptyInsets();
    }
    comboBox.setBorder(new DarculaComboBoxBorder());
    comboBox.putClientProperty(ComboBox.IS_EMBEDDED_PROPERTY, true);
  }

  @Override
  protected boolean isNewBorderSupported(@NotNull JComboBox<?> comboBox) {
    return true;
  }

  @Override
  protected void installComponents() {
    super.installComponents();

    comboBox.add(myPanel);
  }

  @Override
  protected void uninstallDefaults() {
    comboBox.setBorder(null);
    super.uninstallDefaults();
  }

  @Override
  protected LayoutManager createLayoutManager() {
    return new ComboBoxLayoutManager() {
      final LayoutManager lm = EmbeddedComboBoxUI.super.createLayoutManager();

      @Override
      public void layoutContainer(Container parent) {
        lm.layoutContainer(parent);

        JComboBox cb = (JComboBox)parent;
        Dimension aps = arrowButton.getPreferredSize();

        Dimension pps = myPanel.getPreferredSize();
        int availableWidth = cb.getWidth() - aps.width;
        if (comboBox.getComponentOrientation().isLeftToRight()) {
          myPanel.setBounds(
            Math.max(availableWidth - pps.width, 0),
            (cb.getHeight() - pps.height) / 2,
            Math.min(pps.width, availableWidth),
            pps.height
          );
        } else {
          myPanel.setBounds(
            arrowButton.getWidth(),
            (cb.getHeight() - pps.height) / 2,
            Math.min(pps.width, availableWidth),
            pps.height
          );
        }
      }
    };
  }

  @Override
  protected Rectangle rectangleForCurrentValue() {
    Rectangle rectangle = super.rectangleForCurrentValue();
    rectangle.width -= myPanel.getWidth();
    if (!comboBox.getComponentOrientation().isLeftToRight()) {
      rectangle.x += arrowButton.getWidth() + myPanel.getWidth();
    }
    return rectangle;
  }
}
