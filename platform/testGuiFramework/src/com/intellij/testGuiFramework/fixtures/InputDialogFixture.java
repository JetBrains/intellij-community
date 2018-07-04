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

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static org.junit.Assert.assertNotNull;

public class InputDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static InputDialogFixture findByTitle(@NotNull Robot robot, @NotNull final String title) {
    final Ref<DialogWrapper> wrapperRef = new Ref<DialogWrapper>();
    JDialog dialog = GuiTestUtil.INSTANCE.waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (!title.equals(dialog.getTitle()) || !dialog.isShowing()) {
          return false;
        }
        DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
        if (wrapper != null) {
          String typeName = Messages.class.getName() + "$InputDialog";
          if (typeName.equals(wrapper.getClass().getName())) {
            wrapperRef.set(wrapper);
            return true;
          }
        }
        return false;
      }
    });
    return new InputDialogFixture(robot, dialog, wrapperRef.get());
  }

  public void enterTextAndClickOk(@NotNull String text) {
    JTextComponent input = robot().finder().find(target(), JTextComponentMatcher.any());
    assertNotNull(input);
    JTextComponentFixture inputFixture = new JTextComponentFixture(robot(), input);
    inputFixture.enterText(text);
    GuiTestUtil.INSTANCE.findAndClickOkButton(this);
  }

  private InputDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull DialogWrapper dialogWrapper) {
    super(robot, target, dialogWrapper);
  }
}
