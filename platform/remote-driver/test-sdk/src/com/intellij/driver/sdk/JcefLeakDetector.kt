package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_JCEF_MODULE_ID

@Remote("com.intellij.performanceTesting.remoteDriver.jcef.JcefLeakDetector", plugin = REMOTE_ROBOT_JCEF_MODULE_ID)
@Suppress("unused")
interface JcefLeakDetector {
  fun analyzeSnapshot(path: String): List<String>
}

fun Driver.analyzeSnapshot(path: String): List<String> = utility(JcefLeakDetector::class).analyzeSnapshot(path)