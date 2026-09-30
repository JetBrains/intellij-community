package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility

fun Driver.updateAndShowResult(project: Project?) {
  utility<UpdateChecker>().updateAndShowResult(project)
}

@Remote("com.intellij.openapi.updateSettings.impl.UpdateChecker", plugin = "com.intellij/intellij.platform.ide.updateChecker")
interface UpdateChecker {
  fun updateAndShowResult(project: Project?)
}
