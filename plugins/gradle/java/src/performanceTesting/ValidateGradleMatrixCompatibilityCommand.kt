// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.HttpRequests
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilityDataParser
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import kotlin.time.Duration.Companion.seconds

class ValidateGradleMatrixCompatibilityCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "validateGradleMatrixCompatibility"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val url = Registry.stringValue("gradle.compatibility.config.url")
    val expectedUpdateTime = System.currentTimeMillis() - Registry.intValue("gradle.compatibility.update.interval").seconds.inWholeMilliseconds
    val json = HttpRequests.request(url)
      .productNameAsUserAgent()
      .readString()
    val expected = GradleCompatibilityDataParser.parseVersionedJson(json, ApplicationInfo.getInstance().fullVersion)!!
    val actual = GradleJvmSupportMatrix.getInstance().state!!

    if (!actual.supportedGradleVersions.containsAll(expected.supportedGradleVersions)) {
      throw IllegalStateException("Supported gradle versions Expected: ${expected.supportedGradleVersions} but ${actual.supportedGradleVersions} ")
    }
    if (!actual.supportedJavaVersions.containsAll(expected.supportedJavaVersions)) {
      throw IllegalStateException("Supported java versions Expected: ${expected.supportedJavaVersions} but ${actual.supportedJavaVersions} ")
    }
    if (!actual.compatibility.containsAll(expected.compatibility)) {
      throw IllegalStateException("Compatibility Expected: ${expected.compatibility} but ${actual.compatibility} ")
    }
    if (actual.lastUpdateTime < expectedUpdateTime) {
      throw IllegalStateException("Last update time less then expected")
    }
  }

  override fun getName(): String {
    return NAME
  }
}