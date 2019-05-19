/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.driver;

import com.intellij.util.ui.JBInsets;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.awt.AWT;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.MouseClickInfo;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.AbstractButtonDriver;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import java.awt.*;


/**
 * @author Sergey Karashevich
 */
public class CheckBoxDriver extends AbstractButtonDriver {


  public CheckBoxDriver(@Nonnull Robot robot) {
    super(robot);
  }

  @Override
  @RunsInEDT
  public void click(@Nonnull Component c) {
    this.click(c, MouseButton.LEFT_BUTTON, 1);
  }

  @Override
  @RunsInEDT
  public void click(@Nonnull Component c, @Nonnull MouseButton button) {
    this.click(c, button, 1);
  }

  @Override
  @RunsInEDT
  public void click(@Nonnull Component c, @Nonnull MouseClickInfo mouseClickInfo) {
    this.click(c, mouseClickInfo.button(), mouseClickInfo.times());
  }

  @Override
  @RunsInEDT
  public void click(@Nonnull Component c, @Nonnull MouseButton button, int times) {
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
