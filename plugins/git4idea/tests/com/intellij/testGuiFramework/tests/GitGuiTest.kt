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
package com.intellij.testGuiFramework.tests

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.testGuiFramework.fixtures.JBListPopupFixture
import com.intellij.testGuiFramework.fixtures.ToolWindowFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.ui.EditorComboBox
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiTask
import org.junit.Ignore
import org.junit.Test

/**
 * @author Sergey Karashevich
 */
class GitGuiTest : GitGuiTestCase() {

  @Test @Ignore
  fun testGitImport(){
    val vcsName = "Git"
    val gitApp = "path_to_git_repo"
    val projectPath = guiTestRule.getMasterProjectDirPath(gitApp)

    welcomeFrame {
      checkoutFrom()
      JBListPopupFixture.findListPopup(robot()).invokeAction(vcsName)

      dialog("Clone Repository") {
        val labelText = "Git Repository URL:"
        val editorComboBox = robot().finder().findByLabel(this.target(), labelText, EditorComboBox::class.java)
        GuiActionRunner.execute(object : GuiTask() {
          @Throws(Throwable::class)
          override fun executeInEDT() {
            editorComboBox.text = projectPath.absolutePath
          }
        })
        button("Clone").click()
      }
      message(VcsBundle.message("checkout.title")).clickYes()
      dialog("Import Project") {
        button("Next").click()
        val textField = GuiTestUtil.findTextField(robot(), "Project name:").click()
        button("Next").click()
        button("Next").click()
        button("Next").click() //libraries
        button("Next").click() //module dependencies
        button("Next").click() //select sdk
        button("Finish").click()
      }
    }
    val ideFrame = guiTestRule.findIdeFrame()
    ideFrame.waitForBackgroundTasksToFinish()


    val projectView = ideFrame.projectView
    val testJavaPath = "src/First.java"
    val editor = ideFrame.editor
    editor.open(testJavaPath)

    ToolWindowFixture.showToolwindowStripes(robot())

    //prevent from ProjectLeak (if the project is closed during the indexing
    DumbService.getInstance(ideFrame.project).waitForSmartMode()

  }
}

