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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.xml.util.XmlStringUtil;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.JTextComponent;
import java.awt.event.KeyEvent;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RenameRefactoringDialogFixture extends IdeaDialogFixture<RenameDialog> {
  @NotNull
  public static RenameRefactoringDialogFixture find(@NotNull Robot robot) {
    return new RenameRefactoringDialogFixture(robot, find(robot, RenameDialog.class));
  }

  private RenameRefactoringDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<RenameDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public RenameRefactoringDialogFixture setNewName(@NotNull final String newName) {
    final EditorTextField field = robot().finder().findByType(target(), EditorTextField.class);
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(field, true);
        });
      }
    });
    robot().pressAndReleaseKey(KeyEvent.VK_BACK_SPACE); // to make sure we don't append to existing item on Linux
    robot().enterText(newName);
    return this;
  }

  @NotNull
  public RenameRefactoringDialogFixture clickRefactor() {
    GuiTestUtil.INSTANCE.findAndClickButton(this, "Refactor");
    return this;
  }

  public static class ConflictsDialogFixture extends IdeaDialogFixture<ConflictsDialog> {
    protected ConflictsDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<ConflictsDialog> dialogAndWrapper) {
      super(robot, dialogAndWrapper);
    }

    @NotNull
    public static ConflictsDialogFixture find(@NotNull Robot robot) {
      return new ConflictsDialogFixture(robot, find(robot, ConflictsDialog.class));
    }

    @NotNull
    public ConflictsDialogFixture clickContinue() {
      GuiTestUtil.INSTANCE.findAndClickButton(this, "Continue");
      return this;
    }

    public String getHtml() {
      final JTextComponent component = robot().finder().find(target(), JTextComponentMatcher.any());
      return GuiActionRunner.execute(new GuiQuery<String>() {
        @Override
        protected String executeInEDT() throws Throwable {
          return component.getText();
        }
      });
    }

    public String getText() {
      String html = getHtml();
      //TODO: verify this block
      // return TextFormat.HTML.convertTo(html, TextFormat.TEXT).trim();
      return StringUtil.stripHtml(XmlStringUtil.stripHtml(html), true);
    }

    public void requireMessageText(@NotNull String text) {
      assertEquals(text, getText());
    }

    public void requireMessageTextContains(@NotNull String text) {
      assertTrue(getText() + " does not contain expected message fragment " + text, getText().contains(text));
    }

    public void requireMessageTextMatches(@NotNull String regexp) {
      assertTrue(getText() + " does not match " + regexp, Pattern.matches(regexp, getText()));
    }
  }
}
