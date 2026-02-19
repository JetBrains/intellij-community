package com.intellij.driver.sdk.ui.components.rust

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.ui

class RustNewProjectDialogUI(data: ComponentData) : NewProjectDialogUI(data) {
  fun selectLibrary() {
    projectTypeList.clickItem("Library", fullMatch = false)
  }

  private val projectTypeList = x("//div[@class='JBList' and contains(@visible_text, 'Library')]", JListUiComponent::class.java)
}

fun Finder.rustNewProjectDialog(action: RustNewProjectDialogUI.() -> Unit) {
  x("//div[@title='New Project']", RustNewProjectDialogUI::class.java).action()
}

fun Driver.rustNewProjectDialog(action: RustNewProjectDialogUI.() -> Unit) {
  ui.rustNewProjectDialog(action)
}

