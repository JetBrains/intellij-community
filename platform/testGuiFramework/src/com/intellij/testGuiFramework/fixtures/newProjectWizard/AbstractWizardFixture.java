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
package com.intellij.testGuiFramework.fixtures.newProjectWizard;

import com.intellij.testGuiFramework.fixtures.ComponentFixture;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * Base class for fixtures which control wizards that extend {@link DynamicWizard}
 */
public abstract class AbstractWizardFixture<S> extends ComponentFixture<S, JDialog> implements ContainerFixture<JDialog> {

  public AbstractWizardFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull JDialog target) {
    super(selfType, robot, target);
  }

  @NotNull
  protected JRootPane findStepWithTitle(@NotNull final String title) {
    JRootPane rootPane = target().getRootPane();
    GuiTestUtil.INSTANCE.waitUntilFound(robot(), rootPane, new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel label) {
        if (!label.isShowing()) {
          return false;
        }
        return title.equals(label.getText());
      }
    });
    return rootPane;
  }

  @NotNull
  public S clickNext() {
    GuiTestUtil.INSTANCE.findAndClickButton(this, "Next");
    return myself();
  }

  @NotNull
  public S clickFinish() {
    GuiTestUtil.INSTANCE.findAndClickButton(this, "Finish");
    return myself();
  }

  @NotNull
  public S clickCancel() {
    GuiTestUtil.INSTANCE.findAndClickCancelButton(this);
    return myself();
  }

  @NotNull
  public JTextComponentFixture findTextField(@NotNull final String labelText) {
    return new JTextComponentFixture(robot(), robot().finder().findByLabel(labelText, JTextComponent.class));
  }

  @NotNull
  public JButtonFixture findWizardButton(@NotNull final String text) {
    JButton button = robot().finder().find(target(), new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        String buttonText = button.getText();
        if (buttonText != null) {
          return buttonText.trim().equals(text) && button.isShowing();
        }
        return false;
      }
    });
    return new JButtonFixture(robot(), button);
  }

  @NotNull
  public JLabelFixture findLabel(@NotNull final String text) {
    JLabel label = GuiTestUtil.INSTANCE.waitUntilFound(robot(), target(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel label) {
        return text.equals(label.getText().replaceAll("(?i)<.?html>", ""));
      }
    });

    return new JLabelFixture(robot(), label);
  }
}
