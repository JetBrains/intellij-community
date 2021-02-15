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
package com.intellij.testGuiFramework.driver;

import com.intellij.ui.SearchTextField;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComponentDriver;
import org.fest.swing.driver.TextDisplayDriver;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class SearchTextFieldDriver extends JComponentDriver implements TextDisplayDriver<SearchTextField> {
  public SearchTextFieldDriver(@NotNull Robot robot) {
    super(robot);
  }

  @Override
  @RunsInEDT
  public void requireText(@NotNull SearchTextField component, String expected) {
    assertThat(textOf(component)).isEqualTo(expected);
  }

  @Override
  @RunsInEDT
  public void requireText(@NotNull SearchTextField component, final @NotNull Pattern pattern) {
    assertThat(textOf(component)).matches(pattern.pattern());
  }

  @Override
  @RunsInEDT
  @Nullable
  public String textOf(final @NotNull SearchTextField component) {
    return execute(new GuiQuery<>() {
      @Override
      protected
      @Nullable
      String executeInEDT() {
        return component.getText();
      }
    });
  }

  @RunsInEDT
  public void enterText(@NotNull SearchTextField textBox, @NotNull String text) {
    focusAndWaitForFocusGain(textBox);
    robot.enterText(text);
  }
}
