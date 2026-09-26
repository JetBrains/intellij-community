package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.addDockerInterpreterDialog(func: AddDockerInterpreterDialogUi.() -> Unit = {}) =
  x(AddDockerInterpreterDialogUi::class.java) { byTitle("New Target: Docker") }.apply(func)

class AddDockerInterpreterDialogUi(data: ComponentData): UiComponent(data) {
  val pullImage = x { byAccessibleName("Pull or use existing") }
  val imageTagField = x { byClass("DockerSearchImageTextField") }
  val nextButton = x { byAccessibleName("Next") }
  val okButton = x { byAccessibleName("OK") }
}