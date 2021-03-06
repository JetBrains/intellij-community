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

import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.fest.swing.edt.GuiActionRunner.execute;

public class ChooseOptionsForNewFileStepFixture extends AbstractWizardStepFixture<ChooseOptionsForNewFileStepFixture> {
  protected ChooseOptionsForNewFileStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ChooseOptionsForNewFileStepFixture.class, robot, target);
  }

  @NotNull
  public ChooseOptionsForNewFileStepFixture enterActivityName(@NotNull String name) {
    JTextField textField = robot().finder().findByLabel(target(), "Activity Name:", JTextField.class, true);
    replaceText(textField, name);
    return this;
  }

  @NotNull
  public String getLayoutName() {
    final JTextField textField = robot().finder().findByLabel("Layout Name:", JTextField.class, true);
    //noinspection ConstantConditions
    return execute(new GuiQuery<>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return textField.getText();
      }
    });
  }
}
