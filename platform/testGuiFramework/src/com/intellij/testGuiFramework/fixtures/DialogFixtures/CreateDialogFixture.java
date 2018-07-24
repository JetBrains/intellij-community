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
package com.intellij.testGuiFramework.fixtures.DialogFixtures;

import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.testGuiFramework.fixtures.IdeaDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CreateDialogFixture extends IdeaDialogFixture<CreateFileFromTemplateDialog> {

  @NotNull
  public static CreateDialogFixture find(@NotNull Robot robot) {
    return new CreateDialogFixture(robot, find(robot, CreateFileFromTemplateDialog.class));
  }


  protected CreateDialogFixture(@NotNull Robot robot,
                                @NotNull JDialog target,
                                @NotNull CreateFileFromTemplateDialog dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  protected CreateDialogFixture(@NotNull Robot robot,
                                @NotNull DialogAndWrapper<CreateFileFromTemplateDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public void clickOK() {
    GuiTestUtil.INSTANCE.findAndClickOkButton(this);
  }
}
