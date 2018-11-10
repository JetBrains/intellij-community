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

import com.intellij.find.impl.FindDialog;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

public class FindDialogFixture extends IdeaDialogFixture<FindDialog> {
  @NotNull
  public static FindDialogFixture find(@NotNull Robot robot) {
    return new FindDialogFixture(robot, find(robot, FindDialog.class));
  }

  private FindDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<FindDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public FindDialogFixture setTextToFind(@NotNull final String text) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        JComponent c = getDialogWrapper().getPreferredFocusedComponent();
        assertThat(c).isInstanceOf(ComboBox.class);
        ComboBox input = (ComboBox)c;
        assertNotNull(input);
        input.setSelectedItem(text);
      }
    });
    return this;
  }

  @NotNull
  public FindDialogFixture clickFind() {
    GuiTestUtil.INSTANCE.findAndClickButton(this, "Find");
    return this;
  }
}
