package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.dialogs.NewProjectDialogUI
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.ui

fun Finder.newSpringBootProjectDialog(action: NewSpringBootProjectDialogUi.() -> Unit) {
  x("//div[@title='New Project' or @title='New Module']", NewSpringBootProjectDialogUi::class.java).action()
}

fun Driver.newSpringBootProjectDialog(action: NewSpringBootProjectDialogUi.() -> Unit) {
  this.ui.newSpringBootProjectDialog(action)
}

class NewSpringBootProjectDialogUi(data: ComponentData) : NewProjectDialogUI(data) {

  fun selectProjectType(projectType: ProjectType) {
    button { byAccessibleName(projectType.text) }.click()
  }

  enum class ProjectType(val text: String) {
    GRADLE_GROOVY("Gradle - Groovy"),
    GRADLE_KOTLIN("Gradle - Kotlin"),
    MAVEN("Maven")
  }

}
