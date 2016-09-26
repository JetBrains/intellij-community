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

import com.intellij.testGuiFramework.fixtures.DialogFixtures.CreateDialogFixture
import com.intellij.testGuiFramework.fixtures.EditorFixture
import com.intellij.testGuiFramework.fixtures.JBPopupFixture
import com.intellij.testGuiFramework.framework.clickListItem
import com.intellij.testGuiFramework.framework.createNewProject
import com.intellij.testGuiFramework.impl.GuiTestCase
import org.junit.Ignore
import org.junit.Test
import java.io.File
import javax.swing.text.JTextComponent

/**
 * @author Sergey Karashevich
 */
class JavaProjectTest: GuiTestCase() {

  @Test
  @Ignore
  fun testJavaProject(){
    var locationFile: File? = null
    val projectName = "smoke-test"
    createNewProject()

    val newProjectWizard = findNewProjectWizard()
    with(newProjectWizard){
      setupJdk()
//      selectProjectType("Java")
      clickListItem("Java", robot(), this.target())
      selectFramework("JavaEE Persistence")
      clickNext()
      setProjectName(projectName);
      locationFile = locationInFileSystem
      clickFinish()
    }

    requireNotNull(locationFile) {
      "Unable to determine location for \"$projectName\" project"
    }

    val ideFrame = findIdeFrame(projectName, locationFile!!)
    with(ideFrame){
      val paneFixture = projectView.selectProjectPane()

      paneFixture.selectByPath(projectName, "src", "META-INF", "persistence.xml").doubleClick(robot())

      val node = paneFixture.selectByPath(projectName, "src")

      node.invokeContextMenu(robot())
      val contextMenu = JBPopupFixture.findContextMenu(robot())
      waitForBackgroundTasksToFinish()
      with(contextMenu) {
        assertContainsAction("Cut")
        invokeAction("New", "Java Class")
      }
      val newDialog = CreateDialogFixture.find(robot())
      with(newDialog) {
        val jTextComponent = robot().finder().findByLabel("Name:", JTextComponent::class.java)
        jTextComponent.setText("Test")
        clickOK()
      }
      with(editor) {
        invokeAction(EditorFixture.EditorAction.COMPLETE_CURRENT_STATEMENT)
        enterText("psvm")
        invokeAction(EditorFixture.EditorAction.TAB)
      }
      waitForBackgroundTasksToFinish()
    }
  }
}

