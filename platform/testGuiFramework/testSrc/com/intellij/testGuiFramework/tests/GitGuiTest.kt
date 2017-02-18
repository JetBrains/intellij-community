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
package com.intellij.testGuiFramework.tests

import com.intellij.dvcs.ui.CloneDvcsDialog
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.testGuiFramework.fixtures.*
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.ui.EditorComboBox
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiTask
import org.fest.swing.fixture.DialogFixture
import org.junit.Ignore
import org.junit.Test

/**
 * @author Sergey Karashevich
 */
class GitGuiTest : GuiTestCase() {

  @Test @Ignore
  fun testGitImport(){
    val vcsName = "Git"
    val gitApp = "path_to_git_repo"
    val projectPath = getMasterProjectDirPath(gitApp)

    val welcomeFrame = WelcomeFrameFixture.find(myRobot)
    welcomeFrame.checkoutFrom()
    JBListPopupFixture.findListPopup(myRobot).invokeAction(vcsName)

    val cloneVcsDialog = DialogFixture(myRobot, IdeaDialogFixture.find(myRobot, CloneDvcsDialog::class.java).dialog) //don't miss robot as the first argument or you'll stuck with a deadlock
    with(cloneVcsDialog) {
      val labelText = DvcsBundle.message("clone.repository.url", vcsName)
      val editorComboBox = myRobot.finder().findByLabel(this.target(), labelText, EditorComboBox::class.java)
      GuiActionRunner.execute(object : GuiTask() {
        @Throws(Throwable::class)
        override fun executeInEDT() {
          editorComboBox.text = projectPath.absolutePath
        }
      })
      GuiTestUtil.findAndClickButton(this, DvcsBundle.getString("clone.button"))
    }
    MessagesFixture.findByTitle(myRobot, welcomeFrame.target(), VcsBundle.message("checkout.title")).clickYes()
    val dialog1 = JDialogFixture.find(myRobot, "Import Project")
    with (dialog1) {
      GuiTestUtil.findAndClickButton(this, "Next")
      val textField = GuiTestUtil.findTextField(myRobot, "Project name:").click()
      GuiTestUtil.findAndClickButton(this, "Next")
      GuiTestUtil.findAndClickButton(this, "Next")
      GuiTestUtil.findAndClickButton(this, "Next") //libraries
      GuiTestUtil.findAndClickButton(this, "Next") //module dependencies
      GuiTestUtil.findAndClickButton(this, "Next") //select sdk
      GuiTestUtil.findAndClickButton(this, "Finish")
    }
    val ideFrame = findIdeFrame()
    ideFrame.waitForBackgroundTasksToFinish()


    val projectView = ideFrame.projectView
    val testJavaPath = "src/First.java"
    val editor = ideFrame.editor
    editor.open(testJavaPath)

    ToolWindowFixture.showToolwindowStripes(myRobot)

    //prevent from ProjectLeak (if the project is closed during the indexing
    DumbService.getInstance(ideFrame.project).waitForSmartMode()

  }
}

