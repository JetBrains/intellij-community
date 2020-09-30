// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public final class InputDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static InputDialogFixture findByTitle(@NotNull Robot robot, @NotNull final String title) {
    final Ref<DialogWrapper> wrapperRef = new Ref<>();
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
