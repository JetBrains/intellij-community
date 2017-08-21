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

import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.ui.EditorTextField;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.waitUntilFound;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

public class RenameDialogFixture extends IdeaDialogFixture<RenameDialog> {

  public RenameDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull RenameDialog dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  /**
   * Starts 'rename' refactoring for the given data.
   * <p/>
   * <b>Note:</b> proper way would be to write dedicated 'project view fixture' and emulate user actions like 'expand nodes until
   * we find a target one' but that IJ component (project view) is rather complex and it's much easier to start the refactoring
   * programmatically.
   *
   * @param element  target PSI element for which 'rename' refactoring should begin
   * @param handler  rename refactoring handler to use
   * @param robot    robot to use
   * @return         a fixture for the 'rename dialog' which occurs when we start 'rename' refactoring for the given data
   */
  @NotNull
  public static RenameDialogFixture startFor(@NotNull final PsiElement element,
                                             @NotNull final RenameHandler handler,
                                             @NotNull Robot robot)
  {
    // We use SwingUtilities instead of FEST here because RenameDialog is modal and GuiActionRunner doesn't return until
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(
      () -> handler.invoke(element.getProject(), new PsiElement[] { element }, SimpleDataContext.getProjectContext(element.getProject())));
    final Ref<RenameDialog> ref = new Ref<RenameDialog>();
    JDialog dialog = waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (!RefactoringBundle.message("rename.title").equals(dialog.getTitle()) || !dialog.isShowing()) {
          return false;
        }
        RenameDialog renameDialog = getDialogWrapperFrom(dialog, RenameDialog.class);
        if (renameDialog == null) {
          return false;
        }
        ref.set(renameDialog);
        return true;
      }
    });
    return new RenameDialogFixture(robot, dialog, ref.get());
  }

  @NotNull
  public String getNewName() {
    //noinspection ConstantConditions
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        String text = robot().finder().findByType(target(), EditorTextField.class).getText();
        return text == null ? "" : text;
      }
    });
  }

  public void setNewName(@NotNull final String newName) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        robot().finder().findByType(target(), EditorTextField.class).setText(newName);
      }
    });
  }

  /**
   * Allows to check if a warning exists at the target 'rename dialog'
   *
   * @param warningText  {@code null} as a wildcard to match any non-empty warning text;
   *                     non-null text which is evaluated to be a part of the target dialog's warning text
   * @return             {@code true} if the target 'rename dialog' has a warning and given text matches it according to the
   *                     rules described above; {@code false} otherwise
   */
  public boolean warningExists(@Nullable final String warningText) {
    //noinspection ConstantConditions
    return execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        JComponent errorTextPane = field("myErrorText").ofType(JComponent.class).in(getDialogWrapper()).get();
        assertNotNull(errorTextPane);
        if (!errorTextPane.isVisible()) {
          return false;
        }
        JLabel errorLabel = field("myLabel").ofType(JLabel.class).in(errorTextPane).get();
        assertNotNull(errorLabel);
        String text = errorLabel.getText();
        if (!StringUtil.isNotEmpty(text)) {
          return false;
        }
        return warningText == null || text.contains(warningText);
      }
    });
  }
}
