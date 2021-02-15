// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.testGuiFramework.framework.Timeouts;
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
    Pause.pause(new Condition("Wait till dialog with title '" + title + "' gone ") {
      @Override
      public boolean test() {
        return robot().finder().findAll(matcher).isEmpty();
      }
    });
  }

  @NotNull
  public static JDialogFixture find(@NotNull Robot robot, String title) {
    return find(robot, title, Timeouts.INSTANCE.getMinutes02());
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
    GenericTypeMatcher<JDialog> matcher = new GenericTypeMatcher<>(JDialog.class) {
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

  public EditorFixture getEditor() {
    EditorComponentImpl editor = GuiTestUtil.INSTANCE
      .waitUntilFound(robot(), this.target(), new GenericTypeMatcher<>(EditorComponentImpl.class, true) {
        @Override
        protected boolean isMatching(@NotNull EditorComponentImpl component) {
          return true;
        }
      });
    return new EditorFixture(robot(), editor.getEditor());
  }

  private static GenericTypeMatcher<JDialog> getMatcher(String title) {
    return new GenericTypeMatcher<>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return title.equals(dialog.getTitle()) && dialog.isShowing();
      }
    };
  }
}
