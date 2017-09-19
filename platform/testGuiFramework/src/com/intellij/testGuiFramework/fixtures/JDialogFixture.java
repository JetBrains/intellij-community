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

import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class JDialogFixture extends ComponentFixture<JDialogFixture, JDialog> implements ContainerFixture<JDialog> {


  public JDialogFixture(@NotNull Robot robot, JDialog jDialog) {
    super(JDialogFixture.class, robot, jDialog);
  }

  public void waitTillGone() {
    String title = target().getTitle();
    GenericTypeMatcher<JDialog> matcher = getMatcher(title);
    Pause.pause(new Condition("Wait till dialog with title '" + title+ "' gone ") {
      @Override
      public boolean test() {
        return robot().finder().findAll(matcher).isEmpty();
      }
    });
  }

  @NotNull
  public static JDialogFixture find(@NotNull Robot robot, String title) {
    return find(robot, title, GuiTestUtil.SHORT_TIMEOUT);
  }

  @NotNull
  public static JDialogFixture find(@NotNull Robot robot, String title, Timeout timeout) {
    GenericTypeMatcher<JDialog> matcher = getMatcher(title);

    Pause.pause(new Condition("Finding for JDialogFixture with title \"" + title + "\"") {
      @Override
      public boolean test() {
        Collection<JDialog> dialogs = robot.finder().findAll(matcher);
        return !dialogs.isEmpty();
      }
    }, timeout);

    JDialog dialog = robot.finder().find(matcher);
    return new JDialogFixture(robot, dialog);
  }

  @NotNull
  public static JDialogFixture findByPartOfTitle(@NotNull Robot robot, String partTitle, Timeout timeout) {
    GenericTypeMatcher<JDialog> matcher = new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return dialog.getTitle().contains(partTitle) && dialog.isShowing();
      }
    };

    Pause.pause(new Condition("Finding for JDialogFixture with part of title \"" + partTitle + "\"") {
      @Override
      public boolean test() {
        Collection<JDialog> dialogs = robot.finder().findAll(matcher);
        return !dialogs.isEmpty();
      }
    }, timeout);

    JDialog dialog = robot.finder().find(matcher);
    return new JDialogFixture(robot, dialog);
  }

  private static GenericTypeMatcher<JDialog> getMatcher(String title) {
    return new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return title.equals(dialog.getTitle()) && dialog.isShowing();
      }
    };
  }

}
