package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.ui
import javax.swing.JTextField

fun Finder.newProjectKtorDialog(action: NewProjectKtorDialogUI.() -> Unit) = x(NewProjectKtorDialogUI::class.java) { byTitle("New Project") }.action()

fun Driver.newProjectKtorDialog(action: NewProjectKtorDialogUI.() -> Unit){this.ui.newProjectKtorDialog(action)}

class NewProjectKtorDialogUI(data: ComponentData) : NewProjectDialogUI(data){

  val locationField = textField { and(byAccessibleName("Location:"), byType(JTextField::class.java)) }

  fun setProjectLocation(path: String) {
    locationField.text = path
  }

  fun chooseGenerator(name: String) {
    x("//div[@class='JBList']")
      .waitOneText(name)
      .click()
  }

  fun chooseProjectEngine(projectEngine: String) {
    comboBox("//div[@accessiblename='Engine:' and @class='ComboBox']").selectItemContains(projectEngine)
  }

  fun chooseProjectConfiguration(projectConfiguration: String) {
    comboBox("//div[@accessiblename='Configuration in:' and @class='ComboBox']").selectItem(projectConfiguration)
  }

  fun addKtorPlugins(ktorPlugins: List<String>) {
    ktorPlugins.forEach {
      x("//div[@class='ListKtorPluginComponent' and .//div[@javaclass='javax.swing.JLabel' and @visible_text='$it']]//div[@class='AddButton']").click()
    }
  }
}
