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
package com.intellij.testGuiFramework.finder;

import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.finder.ComponentFinderTemplate;
import org.fest.swing.fixture.JToggleButtonFixture;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

public class JToggleButtonFinder extends ComponentFinderTemplate<JToggleButton> {

  public JToggleButtonFinder(@Nullable String componentName) {
    super(componentName, JToggleButton.class);
  }

  protected JToggleButtonFinder(@Nonnull GenericTypeMatcher<? extends JToggleButton> matcher) {
    super(matcher);
  }


  protected JToggleButtonFinder(@Nonnull Class<? extends JToggleButton> componentType) {
    super(componentType);
  }


  @Nonnull
  public JToggleButtonFinder withTimeout(@Nonnegative long timeout) {
    super.withTimeout(timeout);
    return this;
  }

  @Nonnull
  public JToggleButtonFinder withTimeout(@Nonnegative long timeout, @Nonnull TimeUnit unit) {
    super.withTimeout(timeout, unit);
    return this;
  }

  @Override
  public JToggleButtonFixture using(@Nonnull Robot robot) {
    return new JToggleButtonFixture(robot, (JToggleButton)this.findComponentWith(robot));
  }

  @Override
  protected JToggleButton cast(@Nullable Component component) {
    return (JToggleButton) component;
  }
}
