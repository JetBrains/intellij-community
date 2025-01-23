package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.updateAndShowResult() = utility(UpdateChecker::class).updateAndShowResult()


@Remote("com.intellij.openapi.updateSettings.impl.UpdateChecker")
interface UpdateChecker {
  fun updateAndShowResult()
}
