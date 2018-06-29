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
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Timeout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.fest.swing.edt.GuiActionRunner.execute;

public class MessageDialogFixture extends IdeaDialogFixture<DialogWrapper> implements MessagesFixture.Delegate {

  @NotNull
  static MessageDialogFixture findByTitle(@NotNull Robot robot, @NotNull final String title) {
    return findByTitle(robot, title, GuiTestUtil.INSTANCE.getLONG_TIMEOUT());
  }

  @NotNull
  static MessageDialogFixture findByTitle(@NotNull Robot robot, @NotNull final String title, @NotNull Timeout timeout) {
    final Ref<DialogWrapper> wrapperRef = new Ref<DialogWrapper>();
    JDialog dialog = GuiTestUtil.INSTANCE.waitUntilFound(robot, null, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (!title.equals(dialog.getTitle()) || !dialog.isShowing()) {
          return false;
        }
        return isMessageDialog(dialog, wrapperRef);
      }
    }, timeout);
    return new MessageDialogFixture(robot, dialog, wrapperRef.get());
  }

  static MessageDialogFixture findAny(@NotNull Robot robot) {
    return findAny(robot, GuiTestUtil.INSTANCE.getLONG_TIMEOUT());
  }

  static MessageDialogFixture findAny(@NotNull Robot robot, @NotNull Timeout timeout) {
    final Ref<DialogWrapper> wrapperRef = new Ref<DialogWrapper>();
    JDialog dialog = GuiTestUtil.INSTANCE.waitUntilFound(robot, null, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return isMessageDialog(dialog, wrapperRef);
      }
    }, timeout);
    return new MessageDialogFixture(robot, dialog, wrapperRef.get());
  }

  public static boolean isMessageDialog(@NotNull JDialog dialog, Ref<DialogWrapper> wrapperRef) {
    DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
    if (wrapper != null) {
      if(wrapper instanceof MessageDialog){
        wrapperRef.set(wrapper);
        return true;
      }
    }
    return false;
  }

  private MessageDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull DialogWrapper dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  @Override
  @NotNull
  public String getMessage() {
    final JTextPane textPane = robot().finder().findByType(target(), JTextPane.class);
    //noinspection ConstantConditions
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return StringUtil.notNullize(textPane.getText());
      }
    });
  }
}
