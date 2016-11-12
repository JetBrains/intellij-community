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
package com.intellij.testGuiFramework.tests;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.testGuiFramework.fixtures.*;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.testGuiFramework.script.GuiTestCase;
import git4idea.i18n.GitBundle;
import org.fest.swing.core.FastRobot;
import org.fest.swing.timing.Pause;
import org.junit.Test;

import java.awt.event.KeyEvent;
import java.io.IOException;

import static com.intellij.testGuiFramework.matcher.TitleMatcher.withTitleMatcher;

/**
 * @author Sergey Karashevich
 */
public class SimpleGitTest extends GuiTestCase {

  @Test
  public void testSimpleGit(){
    try {
      IdeFrameFixture ideFrameFixture = importSimpleApplication();
      ProjectViewFixture.PaneFixture projectPane = ideFrameFixture.getProjectView().selectProjectPane();
      ideFrameFixture.waitForBackgroundTasksToFinish();

      final String projectName = ideFrameFixture.getProject().getName();
      ProjectViewFixture.NodeFixture src = projectPane.selectByPath(projectName, "src");

      //invoke "New..." action
      GuiTestUtil.invokeAction(myRobot, "NewElement");
      //select first element (Java class)
      myRobot.pressAndReleaseKey(KeyEvent.VK_ENTER);

      DialogFixture createNewClassFixture = DialogFixture.find(myRobot, IdeBundle.message("action.create.new.class"));
      myRobot.enterText("MyClass");
      myRobot.pressAndReleaseKey(KeyEvent.VK_ENTER);

      ideFrameFixture.invokeMenuPath("VCS", ActionsBundle.message("group.Vcs.Import.text"), "Create Git Repository...");
      FileChooserDialogFixture fileChooserDialogFixture =
        FileChooserDialogFixture.findDialog(myRobot, withTitleMatcher(GitBundle.message("init.destination.directory.title")));
      fileChooserDialogFixture.select(ideFrameFixture.getProject().getBaseDir()).clickOk();
      Pause.pause(GuiTestUtil.THIRTY_SEC_TIMEOUT.duration()); //wait when file will be added to commit

      GuiTestUtil.invokeAction(myRobot, "ChangesView.AddUnversioned");
      waitForIdle();
      GuiTestUtil.invokeAction(myRobot, "CheckinProject");

      DialogFixture commitDialogFixture = DialogFixture.find(myRobot, VcsBundle.message("commit.dialog.title"));
      myRobot.enterText("initial commit");
      GuiTestUtil.findAndClickButton(commitDialogFixture, "Commit");

      MessagesFixture.findByTitle(myRobot, commitDialogFixture.target(), VcsBundle.message("code.smells.error.messages.tab.name"))
        .click("Commit");

      Pause.pause(GuiTestUtil.THIRTY_SEC_TIMEOUT.duration());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void waitForIdle() {
    if (myRobot instanceof FastRobot) ((FastRobot)myRobot).superWaitForIdle();
  }
}
