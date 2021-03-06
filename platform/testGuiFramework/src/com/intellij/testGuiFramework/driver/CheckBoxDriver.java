// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.driver;

import com.intellij.util.ui.JBInsets;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.awt.AWT;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.MouseClickInfo;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.AbstractButtonDriver;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import java.awt.*;


/**
 * @author Sergey Karashevich
 */
public class CheckBoxDriver extends AbstractButtonDriver {
  public CheckBoxDriver(@NotNull Robot robot) {
    super(robot);
  }

  @Override
  @RunsInEDT
  public void click(@NotNull Component c) {
    this.click(c, MouseButton.LEFT_BUTTON, 1);
  }

  @Override
  @RunsInEDT
  public void click(@NotNull Component c, @NotNull MouseButton button) {
    this.click(c, button, 1);
  }

  @Override
  @RunsInEDT
  public void click(@NotNull Component c, @NotNull MouseClickInfo mouseClickInfo) {
    this.click(c, mouseClickInfo.button(), mouseClickInfo.times());
  }

  @Override
  @RunsInEDT
  public void click(@NotNull Component c, @NotNull MouseButton button, int times) {
    checkInEdtEnabledAndShowing(c);
    Point centerCheckBox = getCheckBoxClickPoint((JCheckBox)c);
    this.robot.click(AWT.translate(c, centerCheckBox.x, centerCheckBox.y), button, times);
  }

  @NotNull
  private static Point getCheckBoxClickPoint(JCheckBox checkBox) {

    final Graphics2D g = (Graphics2D)checkBox.getGraphics();
    final Dimension size = checkBox.getSize();
    final Font font = checkBox.getFont();

    g.setFont(font);
    FontMetrics fm = checkBox.getFontMetrics(font);

    Rectangle viewRect = new Rectangle(size);
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    JBInsets.removeFrom(viewRect, checkBox.getInsets());

    SwingUtilities.layoutCompoundLabel(checkBox, fm, checkBox.getText(), ((BasicRadioButtonUI)checkBox.getUI()).getDefaultIcon(),
                                       checkBox.getVerticalAlignment(), checkBox.getHorizontalAlignment(),
                                       checkBox.getVerticalTextPosition(), checkBox.getHorizontalTextPosition(),
                                       viewRect, iconRect, textRect, checkBox.getIconTextGap());

    return (AWT.centerOf(iconRect));
  }
}
