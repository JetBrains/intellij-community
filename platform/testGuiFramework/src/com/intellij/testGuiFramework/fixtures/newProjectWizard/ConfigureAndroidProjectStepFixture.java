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

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class ConfigureAndroidProjectStepFixture extends AbstractWizardStepFixture<ConfigureAndroidProjectStepFixture> {
  protected ConfigureAndroidProjectStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureAndroidProjectStepFixture.class, robot, target);
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture enterApplicationName(@NotNull String text) {
    JTextField textField = findTextFieldWithLabel("Application name:");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture enterCompanyDomain(@NotNull String text) {
    JTextField textField = findTextFieldWithLabel("Company Domain:");
    replaceText(textField, text);
    return this;
  }


  @NotNull
  public File getLocationInFileSystem() {
    final TextFieldWithBrowseButton locationField = robot().finder().findByType(target(), TextFieldWithBrowseButton.class);
    //noinspection ConstantConditions
    return execute(new GuiQuery<>() {
      @Override
      protected File executeInEDT() throws Throwable {
        String location = locationField.getText();
        assertThat(location).isNotNull().isNotEmpty();
        return new File(location);
      }
    });
  }
}
