package com.intellij.driver.sdk.ui.components.vcs

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID

class JChangesListViewUi(data: ComponentData) : JTreeUiComponent(data) {
  val changesFixture = driver.new(JChangesListViewFixtureRef::class, robot, component)
  fun addFileName(fileName: String) {
    changesFixture.addFileName(fileName)
  }
  fun removeFileName(fileName: String) {
    changesFixture.removeFileName(fileName)
  }
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JChangesListViewFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JChangesListViewFixtureRef {
  fun addFileName(fileName: String)
  fun removeFileName(fileName: String)
}