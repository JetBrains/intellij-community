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
package com.intellij.testGuiFramework.script

import com.intellij.internal.inspector.UiDropperActionExtension
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.ui.components.JBList
import org.fest.swing.core.BasicRobot
import java.awt.Component
import java.awt.Container
import javax.swing.*

/**
 * ScriptRecorder needs to create GUI Tests quickly. For recording UI actions like click, text entering, checkbox marking we using UI dropper.
 *
 * @author Sergey Karashevich
 */
class ScriptRecorder() : AnAction(), UiDropperActionExtension {

  override fun getAnAction(): com.intellij.openapi.actionSystem.AnAction? {
    return this
  }

  var currentContextComponent: Component? = null
  private var currentContext = Contexts()


  override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent?) {
    //get action type for script: click, enter text, mark checkbox
    val component = e!!.getDataContext().getData("Component") as Component
    checkContext(component)
    clickCmp(component, e)
  }

  //registering custom actions for a UI Dropper by ctrl + alt + click
  fun registerUiDropperAction() {
    com.intellij.internal.inspector.UiDropperAction.setClickAction(this)
  }

  fun recordAction() {

  }

  fun clickCmp(cmp: Component, e: com.intellij.openapi.actionSystem.AnActionEvent) {
    when (cmp) {
      is JButton -> write(Templates.findAndClickButton(cmp.text))
      is com.intellij.ui.components.labels.ActionLink -> write(Templates.clickActionLink(cmp.text))
      is JTextField -> {
        val label = getLabel(currentContextComponent as Container, cmp)
        if (label == null)
          write(Templates.findJTextField())
        else
          write(Templates.findJTextFieldByLabel(label.text))
      }
      is JBList<*> -> {
        val itemName = e.dataContext.getData("ItemName") as String
        if (isPopupList(cmp))
          write(Templates.clickPopupItem(itemName))
        else
          write(Templates.clickListItem(itemName))
      }
    }
  }

  private fun isPopupList(cmp: Component) = cmp.javaClass.name.toLowerCase().contains("listpopup")
  private fun getLabel(container: Container, jTextField: JTextField): JLabel? {
    val robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock()
    return GuiTestUtil.findBoundedLabel(container, jTextField, robot)
  }

  fun checkContext(cmp: Component) {
    cmp as JComponent
    if (isPopupList(cmp)) return //dont' change context for a popup menu
    if (currentContextComponent != null && !cmp.rootPane.equals(currentContextComponent)) {
      write(currentContext.closeContext())
    }
    if (currentContextComponent == null || !cmp.rootPane.equals(currentContextComponent)) {
      currentContextComponent = cmp.rootPane
      when (cmp.rootPane.parent) {
        is JDialog -> {
          if ((cmp.rootPane.parent as JDialog).title.equals(com.intellij.ide.IdeBundle.message("title.new.project")))
            write(currentContext.projectWizardContextStart())
          else
            write(currentContext.dialogContextStart((cmp.rootPane.parent as JDialog).title))
        }
        is com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame -> write(currentContext.welcomeFrameStart())
        is JFrame -> println("is JFrame")
      }

    }
  }

  fun write(str: String) {
    println(str)
  }

}

//TEMPLATES
private object Templates {
  fun findDialog(name: String, title: String?) = "val ${name} = DialogFixture.find(robot(), \"${title}\")"
  fun withDialog(name: String) = "with (${name}){"
  fun findProjectWizard(name: String) = "val ${name} = findNewProjectWizard()"
  fun withProjectWizard(name: String) = "with (${name}){"
  fun findWelcomeFrame(name: String) = "val ${name} = findWelcomeFrame()"
  fun withWelcomeFrame(name: String) = "with (${name}){"

  fun findAndClickButton(text: String) = "GuiTestUtil.findAndClickButton(this, \"${text}\")"
  fun clickActionLink(text: String) = "ActionLinkFixture.findActionLinkByName(\"${text}\", robot(), this.target()).click()"

  fun clickPopupItem(itemName: String) = "GuiTestUtil.clickPopupMenuItem(\"${itemName}\", this.target(), robot())"
  fun clickListItem(name: String) = "clickListItem(\"${name}\", robot(), this.target())"

  fun findJTextField() = "val textField = myRobot.finder().findByType(JTextField::class.java)"
  fun findJTextFieldByLabel(labelText: String) = "val textField = findTextField(\"${labelText}\").click()"
}


private class Contexts() {

  enum class Type {DIALOG, WELCOME_FRAME, PROJECT_WIZARD, IDE_FRAME}

  var dialogCount = 0
  var currentContextType: Type? = null

  fun dialogContextStart(title: String): String {
    currentContextType = Type.DIALOG
    val name = "dialog${dialogCount++}"
    val findDialog = Templates.findDialog(name, title)
    val withDialog = Templates.withDialog(name)
    return findDialog + "\n" + withDialog
  }

  fun projectWizardContextStart(): String {
    currentContextType = Type.PROJECT_WIZARD
    val name = "projectWizard"
    val findProjectWizard = Templates.findProjectWizard(name)
    val withProjectWizard = Templates.withProjectWizard(name)
    return findProjectWizard + "\n" + withProjectWizard
  }

  fun welcomeFrameStart(): String {
    currentContextType = Type.WELCOME_FRAME
    val name = "welcomeFrame"
    val findWelcomeFrame = Templates.findWelcomeFrame(name)
    var withWelcomeFrame = Templates.withWelcomeFrame(name)
    return findWelcomeFrame + "\n" + withWelcomeFrame
  }

  fun closeContext() = "}"

}
