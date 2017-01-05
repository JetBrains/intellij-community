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

import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickOkButton;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertNotNull;

public class FileChooserDialogFixture extends IdeaDialogFixture<FileChooserDialogImpl> {

  TreePath myTargetPath;
  private JTextComponentFixture myJTextFieldFixture;


  @NotNull
  public static FileChooserDialogFixture findOpenProjectDialog(@NotNull Robot robot) {
    return findDialog(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return dialog.isShowing() && "Open File or Project".equals(dialog.getTitle());
      }
    });
  }

  @NotNull
  public static FileChooserDialogFixture findImportProjectDialog(@NotNull Robot robot) {
    return findDialog(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        String title = dialog.getTitle();
        return dialog.isShowing() && title != null && title.startsWith("Select") && title.endsWith("Project to Import");
      }
    });
  }

  @NotNull
  public static FileChooserDialogFixture findDialog(@NotNull Robot robot, @NotNull final GenericTypeMatcher<JDialog> matcher) {
    return new FileChooserDialogFixture(robot, find(robot, FileChooserDialogImpl.class, matcher));
  }

  private FileChooserDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<FileChooserDialogImpl> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public FileChooserDialogFixture select(@NotNull final VirtualFile file) {
    final FileSystemTreeImpl fileSystemTree = field("myFileSystemTree").ofType(FileSystemTreeImpl.class)
      .in(getDialogWrapper())
      .get();
    assertNotNull(fileSystemTree);
    final AtomicBoolean fileSelected = new AtomicBoolean();
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        fileSystemTree.select(file, new Runnable() {
          @Override
          public void run() {
            fileSelected.set(true);
          }
        });
      }
    });

    pause(new Condition("File " + quote(file.getPath()) + " is selected") {
      @Override
      public boolean test() {
        return fileSelected.get();
      }
    }, SHORT_TIMEOUT);

    return this;
  }

  private void sleepWithTimeBomb() {
    //TODO: why this bombed?
    assert System.currentTimeMillis() < 1452600000000L;  // 2016-01-12 12:00
    try {
      Thread.sleep(5000);
    }
    catch (InterruptedException e) {
    }
  }

  public JTextComponentFixture getTextFieldFixture() {
    if (myJTextFieldFixture == null) {
      JTextField textField = robot().finder().find(this.target(), new GenericTypeMatcher<JTextField>(JTextField.class, true) {
        @Override
        protected boolean isMatching(@Nonnull JTextField field) {
          return true;
        }
      });
      myJTextFieldFixture = new JTextComponentFixture(robot(), textField);
    }
    return myJTextFieldFixture;

  }

  public FileChooserDialogFixture waitFilledTextField(){
    pause(new Condition("Wait until JTextField component will be filled by default path") {
      @Override
      public boolean test() {
        return !getTextFieldFixture().text().isEmpty();
      }
    }, GuiTestUtil.THIRTY_SEC_TIMEOUT);
    return this;
  }

  @NotNull
  public FileChooserDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }
}
