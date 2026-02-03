package com.intellij.driver.sdk.ui.components.go

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery

fun Finder.goNewProjectDialog(action: GoNewProjectDialogUI.() -> Unit) {
  x(xQuery { byTitle("New Project") }, GoNewProjectDialogUI::class.java).action()
}

fun Driver.goNewProjectDialog(action: GoNewProjectDialogUI.() -> Unit) {
  this.ui.goNewProjectDialog(action)
}

class GoNewProjectDialogUI(data: ComponentData) : NewProjectDialogUI(data) {
  fun selectGopathProject() {
    projectTypeList.clickItem(itemText = "Go (GOPATH)", fullMatch = false)
  }

  fun selectAppEngineProject() {
    projectTypeList.clickItem(itemText = "App Engine", fullMatch = false)
  }

  private val projectTypeList = x(xQuery { byClass("JBList") }, JListUiComponent::class.java)
}
