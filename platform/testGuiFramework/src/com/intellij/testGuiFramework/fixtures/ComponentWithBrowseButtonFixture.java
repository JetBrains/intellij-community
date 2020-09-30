/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.TextUI;
import java.awt.*;
import java.util.Optional;
import java.util.function.Predicate;

public class ComponentWithBrowseButtonFixture extends JComponentFixture<ComponentWithBrowseButtonFixture, ComponentWithBrowseButton> {

  public ComponentWithBrowseButtonFixture(ComponentWithBrowseButton componentWithBrowseButton, @NotNull Robot robot) {
    super(ComponentWithBrowseButtonFixture.class, robot, componentWithBrowseButton);
  }

  public void clickButton() {
    FixedSizeButton button = target().getButton();
    robot().click(button);
  }

  public void clickAnyExtensionButton() {
    clickExtensionButton(extension -> true);
  }

  public void clickExtensionButton(@NotNull final Predicate<? super ExtendableTextComponent.Extension> extensionFilter) {
    robot().click(target(), getExtensionIconLocation(extensionFilter));
  }

  private Point getExtensionIconLocation(@NotNull final Predicate<? super ExtendableTextComponent.Extension> extensionFilter) {
    final JComponent component = target().getChildComponent();
    if (!(component instanceof ExtendableTextField)) {
      throw new ComponentLookupException("Child component is not an instance of ExtendableTextField");
    }
    final ExtendableTextField extendableTextField = (ExtendableTextField) component;

    final Optional<ExtendableTextComponent.Extension> extension =
      extendableTextField.getExtensions().stream().filter(extensionFilter).findFirst();
    if (extension.isEmpty()) {
      throw new ComponentLookupException("Unable to find extension");
    }

    final TextUI textUI = extendableTextField.getUI();
    if (!(textUI instanceof TextFieldWithPopupHandlerUI)) {
      throw new ComponentLookupException("Unable to find extension button");
    }
    return ((TextFieldWithPopupHandlerUI) textUI).getExtensionIconLocation(extension.get().toString());
  }
}
