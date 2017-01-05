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

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import org.fest.swing.core.ComponentFinder;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.actionSystem.ActionPlaces.MAIN_TOOLBAR;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class RunConfigurationComboBoxFixture extends JComponentFixture<RunConfigurationComboBoxFixture, JButton> {
  @NotNull
  static RunConfigurationComboBoxFixture find(@NotNull final IdeFrameFixture parent) {
    ComponentFinder finder = parent.robot().finder();
    ActionToolbarImpl toolbar = finder.find(parent.target(), new GenericTypeMatcher<ActionToolbarImpl>(ActionToolbarImpl.class) {
      @Override
      protected boolean isMatching(@NotNull ActionToolbarImpl toolbar) {
        String place = field("myPlace").ofType(String.class).in(toolbar).get();
        return MAIN_TOOLBAR.equals(place);
      }
    });

    JButton button = finder.find(toolbar, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        return button.getClass().getSimpleName().equals("ComboBoxButton");
      }
    });
    return new RunConfigurationComboBoxFixture(parent.robot(), button);
  }

  private RunConfigurationComboBoxFixture(@NotNull Robot robot, @NotNull JButton target) {
    super(RunConfigurationComboBoxFixture.class, robot, target);
  }

  @Nullable
  public String getText() {
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return target().getText();
      }
    });
  }
}
