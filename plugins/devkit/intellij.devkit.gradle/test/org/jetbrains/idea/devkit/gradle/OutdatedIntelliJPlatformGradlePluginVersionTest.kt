// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import junit.framework.TestCase

internal class OutdatedIntelliJPlatformGradlePluginVersionTest : TestCase() {

  fun testReportsFirstOutdatedModel() {
    val outdated = findOutdatedIntelliJPlatformGradlePluginVersion(listOf(model("2.2.1", "2.2.1"), model("2.1.0", "2.2.1")))

    assertEquals("2.1.0" to "2.2.1", outdated)
  }

  fun testKeepsPreReleaseVersionText() {
    val outdated = findOutdatedIntelliJPlatformGradlePluginVersion(listOf(model("2.0.0-beta9", "2.2.1")))

    assertEquals("2.0.0-beta9", outdated?.first)
  }

  fun testIgnoresUpToDateModels() {
    assertNull(findOutdatedIntelliJPlatformGradlePluginVersion(listOf(model("2.2.1", "2.2.1"), model("2.3.0", "2.2.1"))))
  }

  fun testIgnoresInvalidVersions() {
    assertNull(findOutdatedIntelliJPlatformGradlePluginVersion(listOf(model("unspecified", "2.3.0"), model("2.1.0", "unspecified"))))
  }

  private fun model(currentVersion: String, latestVersion: String) = object : IntelliJPlatformGradleModel {
    override fun getDependencyHelperProductCodes() = emptyMap<String, String>()
    override fun getProductReleasesFile(): String? = null
    override fun getCurrentPluginVersion() = currentVersion
    override fun getLatestPluginVersion() = latestVersion
  }
}
