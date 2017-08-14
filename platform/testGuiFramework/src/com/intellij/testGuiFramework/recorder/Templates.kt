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
package com.intellij.testGuiFramework.recorder

/**
 * @author Sergey Karashevich
 */
object Templates {
  fun withDialog(title: String) = "dialog(\"${title}\") {"
  fun withProjectWizard() = "projectWizard {"
  fun withWelcomeFrame() = "welcomeFrame {"
  fun withIdeFrame() = "ideFrame {"

  fun findAndClickButton(name: String) = "button(\"${name}\").click()"
  fun findAndClickActionButton(actionId: String) = "actionButton(\"${actionId}\").click()"

  fun clickActionLink(text: String) = "actionLink(\"$text\").click()"
  fun clickPopupItem(itemName: String) = "popupClick(\"$itemName\")"
  fun clickLinkLabel(text: String) = "linkLabel(\"$text\").click()"

  fun clickListItem(name: String) = "jList(\"$name\").clickItem(\"$name\")"
  fun findJTextField() = "textfield()"
  fun findJTextFieldByLabel(labelText: String) = "textfield(\"${labelText}\").click()"
  fun findJTextFieldAndDoubleClick() = "textfield().doubleClick()"

  fun findJTextFieldByLabelAndDoubleClick(labelText: String) = "textfield(\"${labelText}\").doubleClick()"
  fun typeText(text: String) = "typeText(\"$text\")"
  fun clickFrameworksTree(itemName: String) = "selectFramework(\"$itemName\")"
  fun selectSimpleTreeItem(path: String) = "jTree(\"$path\").selectPath(\"$path\")"
  fun clickJBCheckBox(text: String) = "checkbox(\"$text\").click()"

  fun clickJCheckBox(text: String) = "checkbox(\"$text\").click()"
  fun onJComboBox(text: String) = "combobox(\"$text\")"
  fun selectComboBox(itemName: String) = ".selectItem(\"$itemName\")"

  fun clickRadioButton(text: String) = "radioButton(\"$text\").select()"
  fun invokeActionComment(actionId: String) = "//invokeAction(\"$actionId\")"

  fun shortcut(keyStrokeStr: String) = "shortcut(\"$keyStrokeStr\")"
  fun invokeMainMenuAction(menuActionId: String) = "invokeMainMenu(\"$menuActionId\")"

  fun selectTreePath(path: String) = "jTree(\"$path\").clickPath(\"$path\")"
//    fun selectTreePath(treeClass: String, path: String) = "//GuiTestUtil.findJTreeFixtureByClassName(robot(), this.target(), \"$treeClass\").clickPath(\"$path\")"
}