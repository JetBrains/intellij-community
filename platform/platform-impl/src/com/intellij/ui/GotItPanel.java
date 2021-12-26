// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class GotItPanel {
  private static final JBColor BODY_COLOR_1 = new JBColor(new Color(77, 143, 253), new Color(52, 74, 100));
  private static final JBColor BODY_COLOR_2 = new JBColor(new Color(71, 135, 237), new Color(38, 53, 73));
  private static final JBColor BORDER_COLOR = new JBColor(new Color(71, 91, 167), new Color(78, 120, 161));

  JPanel myRoot;
  JLabel myTitle;
  JEditorPane myMessage;
  JPanel myButton;
  JLabel myButtonLabel;

  public GotItPanel() {
    scaleFont(myTitle);
    scaleFont(myButtonLabel);
  }

  private static void scaleFont(@NotNull JComponent component) {
    component.setFont(component.getFont().deriveFont(1.0f * JBUIScale.scaleFontSize((float)component.getFont().getSize())));
  }

  private void createUIComponents() {
    myButton = new JPanel(new BorderLayout()) {
      {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        GraphicsUtil.setupAAPainting(g);
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, BODY_COLOR_1, 0, getHeight(), BODY_COLOR_2));
        g.fillRoundRect(0, 0, getWidth() - JBUIScale.scale(1), getHeight() - JBUIScale.scale(1), JBUIScale.scale(5), JBUIScale.scale(5));
        ((Graphics2D)g).setStroke(new BasicStroke(StartupUiUtil.isUnderDarcula() ? 2f : 1f));
        g.setColor(BORDER_COLOR);
        g.drawRoundRect(0, 0, getWidth() - JBUIScale.scale(1), getHeight() - JBUIScale.scale(1), JBUIScale.scale(5), JBUIScale.scale(5));
      }
    };

    myMessage = IdeTooltipManager.initPane("", new HintHint().setAwtTooltip(true), null);
    myMessage.setFont(StartupUiUtil.getLabelFont().deriveFont(StartupUiUtil.getLabelFont().getSize() + 2f));
  }
}
