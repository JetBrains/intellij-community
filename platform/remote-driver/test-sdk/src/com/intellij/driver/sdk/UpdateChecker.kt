package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility

fun Driver.updateAndShowResult(project: Project?) {
  utility<UpdateChecker>().updateAndShowResult(project)
}

@Remote("com.intellij.openapi.updateSettings.impl.UpdateChecker")
interface UpdateChecker {
  fun updateAndShowResult(project: Project?)
}
