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

import com.intellij.testGuiFramework.fixtures.JComponentFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JTextComponentDriver;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class AbstractWizardStepFixture<S> extends JComponentFixture<S, JRootPane> {
  protected AbstractWizardStepFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull JRootPane target) {
    super(selfType, robot, target);
  }

  @NotNull
  protected JCheckBoxFixture findCheckBoxWithLabel(@NotNull final String label) {
    JCheckBox checkBox = robot().finder().find(target(), new GenericTypeMatcher<>(JCheckBox.class) {
      @Override
      protected boolean isMatching(@NotNull JCheckBox component) {
        return label.equals(component.getText());
      }
    });
    return new JCheckBoxFixture(robot(), checkBox);
  }

  @NotNull
  protected JComboBoxFixture findComboBoxWithLabel(@NotNull String label) {
    JComboBox comboBox = robot().finder().findByLabel(target(), label, JComboBox.class, true);
    return new JComboBoxFixture(robot(), comboBox);
  }

  @NotNull
  protected JTextField findTextFieldWithLabel(@NotNull String label) {
    return robot().finder().findByLabel(target(), label, JTextField.class, true);
  }

  protected void replaceText(@NotNull JTextField textField, @NotNull String text) {
    JTextComponentDriver driver = new JTextComponentDriver(robot());
    driver.selectAll(textField);
    driver.enterText(textField, text);
  }
}
