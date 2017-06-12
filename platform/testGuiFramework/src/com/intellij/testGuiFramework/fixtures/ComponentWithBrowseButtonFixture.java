/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.FixedSizeButton;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ComponentWithBrowseButtonFixture extends JComponentFixture<ComponentWithBrowseButtonFixture, ComponentWithBrowseButton> {
  private final ComponentWithBrowseButton myComponentWithBrowseButton;
  @NotNull private final Robot myRobot;

  public ComponentWithBrowseButtonFixture(ComponentWithBrowseButton componentWithBrowseButton, @NotNull Robot robot) {
    super(ComponentWithBrowseButtonFixture.class, robot, componentWithBrowseButton);
    myComponentWithBrowseButton = componentWithBrowseButton;
    myRobot = robot;
  }

  public void clickButton() {
    FixedSizeButton button = myComponentWithBrowseButton.getButton();
    Point locationOnScreen = button.getLocationOnScreen();
    Rectangle bounds = button.getBounds();
    final Point point =
      new Point(locationOnScreen.x + bounds.x + bounds.width / 2, locationOnScreen.y + bounds.y + bounds.height / 2);
    myRobot.click(point, MouseButton.LEFT_BUTTON, 1);
  }
}
