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
package com.intellij.testGuiFramework.tests;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.testGuiFramework.fixtures.*;
import git4idea.i18n.GitBundle;
import org.fest.swing.core.FastRobot;
import org.fest.swing.timing.Pause;
import org.junit.Ignore;
import org.junit.Test;

import java.awt.event.KeyEvent;
import java.io.IOException;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.*;
import static com.intellij.testGuiFramework.matcher.TitleMatcher.withTitleMatcher;

/**
 * @author Sergey Karashevich
 */
@Ignore
public class SimpleGitTest extends GitGuiTestCase {

  @Test
  public void testSimpleGit(){
    try {
      IdeFrameFixture ideFrameFixture = importSimpleProject();
      ideFrameFixture.waitForBackgroundTasksToFinish();

      ProjectViewFixture.PaneFixture projectPane = ideFrameFixture.getProjectView().selectProjectPane();
      final String projectName = ideFrameFixture.getProject().getName();
      projectPane.selectByPath(projectName, "src");

      //invoke "New..." action
      invokeAction(myRobot, "NewElement");
      //select first element (Java class)
      myRobot.pressAndReleaseKey(KeyEvent.VK_ENTER);

      JDialogFixture.find(myRobot, IdeBundle.message("action.create.new.class"));
      myRobot.enterText("MyClass");
      myRobot.pressAndReleaseKey(KeyEvent.VK_ENTER);
      EditorFixture editorFixture = new EditorFixture(myRobot, ideFrameFixture);
      FileFixture currentFileFixture = editorFixture.waitUntilFileIsLoaded();

      ideFrameFixture.invokeMenuPath("VCS", ActionsBundle.message("group.Vcs.Import.text"), "Create Git Repository...");
      FileChooserDialogFixture fileChooserDialogFixture = FileChooserDialogFixture.findDialog(myRobot, withTitleMatcher(GitBundle.message("init.destination.directory.title")));
      fileChooserDialogFixture.waitFilledTextField().clickOk();

      pause("Wait when files will be added to Git Repository and marked as untracked",
            () -> currentFileFixture.getVcsStatus().equals(FileStatus.UNKNOWN),
            THIRTY_SEC_TIMEOUT);

      invokeAction(myRobot, "ChangesView.AddUnversioned");

      pause("Wait when file will be marked as added",
                        () -> currentFileFixture.getVcsStatus().equals(FileStatus.ADDED),
                        THIRTY_SEC_TIMEOUT);

      invokeAction(myRobot, "CheckinProject");

      JDialogFixture commitJDialogFixture = JDialogFixture.find(myRobot, VcsBundle.message("commit.dialog.title"));
      myRobot.enterText("initial commit");
      findAndClickButton(commitJDialogFixture, "Commit");

      MessagesFixture messagesFixture = MessagesFixture.findAny(myRobot, commitJDialogFixture.target());
      messagesFixture.click("Commit");

      if (MessagesFixture.exists(myRobot, commitJDialogFixture.target(), "Check TODO is not possible right now")) {
        MessagesFixture.findByTitle(myRobot, commitJDialogFixture.target(), "Check TODO is not possible right now").click("Commit");
      }
      pause("Wait when file will be marked as not changed (committed)",
            () -> currentFileFixture.getVcsStatus().equals(FileStatus.NOT_CHANGED),
            THIRTY_SEC_TIMEOUT);

      Pause.pause(10000);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void waitForIdle() {
    if (myRobot instanceof FastRobot) ((FastRobot)myRobot).superWaitForIdle();
  }
}
