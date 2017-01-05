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
package com.intellij.testGuiFramework.fixtures;

import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComponentDriver;
import org.fest.swing.fixture.AbstractJComponentFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class JComponentFixture<S, C extends JComponent> extends AbstractJComponentFixture<S, C, JComponentDriver> {
  public JComponentFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull Class<? extends C> type) {
    super(selfType, robot, type);
  }

  public JComponentFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @Nullable String name, @NotNull Class<? extends C> type) {
    super(selfType, robot, name, type);
  }

  public JComponentFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull C target) {
    super(selfType, robot, target);
  }

  @Override
  @NotNull
  protected JComponentDriver createDriver(@NotNull Robot robot) {
    return new JComponentDriver(robot);
  }
}
