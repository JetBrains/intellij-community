// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public final class RunConfigurationComboBoxFixture extends JComponentFixture<RunConfigurationComboBoxFixture, JButton> {
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
