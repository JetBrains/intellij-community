package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.common.waitForIdeFrameReady

fun Driver.setExperimentalFeatureEnabled(featureId: String, enabled: Boolean) {
  waitForIdeFrameReady()
  service(Experiments::class).setFeatureEnabled(featureId, enabled)
}

@Remote("com.intellij.openapi.application.Experiments")
interface Experiments {
  fun setFeatureEnabled(featureId: String, enabled: Boolean)
}
