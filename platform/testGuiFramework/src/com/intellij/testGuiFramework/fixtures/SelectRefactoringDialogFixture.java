// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JRadioButtonFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class SelectRefactoringDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static SelectRefactoringDialogFixture findByTitle(@NotNull Robot robot) {
    final Ref<DialogWrapper> wrapperRef = new Ref<>();
    JDialog dialog = GuiTestUtil.INSTANCE.waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (!"Select Refactoring".equals(dialog.getTitle()) || !dialog.isShowing()) {
          return false;
        }
        DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
        if (wrapper != null) {
          wrapperRef.set(wrapper);
          return true;
        }
        return false;
      }
    });
    return new SelectRefactoringDialogFixture(robot, dialog, wrapperRef.get());
  }

  public void selectRenameModule() {
    JRadioButton renameModuleCheckbox = robot().finder().find(target(), new GenericTypeMatcher<JRadioButton>(JRadioButton.class) {
      @Override
      protected boolean isMatching(@NotNull JRadioButton checkBox) {
        return "Rename module".equals(checkBox.getText());
      }
    });

    JRadioButtonFixture renameModuleRadioButton = new JRadioButtonFixture(robot(), renameModuleCheckbox);
    renameModuleRadioButton.select();
  }

  public void clickOk() {
    GuiTestUtil.INSTANCE.findAndClickOkButton(this);
  }

  private SelectRefactoringDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull DialogWrapper dialogWrapper) {
    super(robot, target, dialogWrapper);
  }
}
