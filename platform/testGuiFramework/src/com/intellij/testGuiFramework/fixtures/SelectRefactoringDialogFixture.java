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
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JRadioButtonFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SelectRefactoringDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static SelectRefactoringDialogFixture findByTitle(@NotNull Robot robot) {
    final Ref<DialogWrapper> wrapperRef = new Ref<DialogWrapper>();
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
