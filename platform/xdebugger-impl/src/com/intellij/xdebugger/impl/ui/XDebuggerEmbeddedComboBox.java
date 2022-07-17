// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComboBoxUI;
import java.awt.*;
import java.awt.geom.RectangularShape;

public class XDebuggerEmbeddedComboBox<T> extends ComboBox<T> {

  private @Nullable JComponent myEmbeddedComponent;

  public XDebuggerEmbeddedComboBox() {
  }

  public XDebuggerEmbeddedComboBox(@NotNull ComboBoxModel<T> model, int width) {
    super(model, width);
  }

  @Override
  public void setUI(ComboBoxUI ui) {
    BorderlessCombBoxUI newUI = new BorderlessCombBoxUI();
    if (myEmbeddedComponent != null) {
      newUI.setEmbeddedComponent(myEmbeddedComponent);
    }
    super.setUI(newUI);
  }

  public void setExtension(JComponent component) {
    getUI().setEmbeddedComponent(myEmbeddedComponent = component);
  }

  @Override
  public EmbeddedComboBoxUI getUI() {
    return (EmbeddedComboBoxUI)ui;
  }
}

class BorderlessCombBoxUI extends EmbeddedComboBoxUI {

  BorderlessCombBoxUI() {
    super(0f, JBUI.insets(0), false);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      DarculaUIUtil.paintCellEditorBorder(g2, c, new Rectangle(x, y, width, height), hasFocus(c));
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(2);
  }

  @Override
  protected RectangularShape getOuterShape(Rectangle r, float bw, float arc) {
    return r;
  }
}

/**
 * ComboBoxUI with extra space for a component.
 */
class EmbeddedComboBoxUI extends DarculaComboBoxUI {

  protected @NotNull NonOpaquePanel myPanel = new NonOpaquePanel();

  EmbeddedComboBoxUI(float arc,
                     Insets borderCompensation,
                     boolean paintArrowButton) {
    super(arc, borderCompensation, paintArrowButton);
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
  }

  @Override
  protected void installComponents() {
    super.installComponents();

    comboBox.add(myPanel);
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
