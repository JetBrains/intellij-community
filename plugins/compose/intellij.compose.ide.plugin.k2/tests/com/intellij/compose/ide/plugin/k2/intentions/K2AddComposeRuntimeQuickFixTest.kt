// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.intentions

import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleMultiFileQuickFixTest
import org.jetbrains.kotlin.idea.fir.K2DirectiveBasedActionUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import java.io.File

@TestRoot("../compose/intellij.compose.ide.plugin.k2/testData")
@TestMetadata("gradle/fixes")
internal class K2AddComposeRuntimeQuickFixTest : AbstractGradleMultiFileQuickFixTest() {

  override fun checkUnexpectedErrors(mainFile: File, ktFile: KtFile, fileText: String) {
    K2DirectiveBasedActionUtils.checkForUnexpectedErrors(mainFile, ktFile, fileText)
  }

  @Test
  @TestMetadata("addComposeRuntimeLibraryKmpComposable")
  @PluginTargetVersions(pluginVersion = "2.0+", gradleVersion = "8.0+")
  fun testAddComposeRuntimeLibraryKmpComposable() = doComposeRuntimeQuickFixTest()

  @Test
  @TestMetadata("addComposeRuntimeLibraryJvm")
  @PluginTargetVersions(pluginVersion = "2.0+", gradleVersion = "8.0+")
  fun testAddComposeRuntimeLibraryJvm() = doComposeRuntimeQuickFixTest()

  @Test
  @TestMetadata("addComposeRuntimeLibraryKmpRemember")
  @PluginTargetVersions(pluginVersion = "2.0+", gradleVersion = "8.0+")
  fun testAddComposeRuntimeLibraryKmpRemember() = doComposeRuntimeQuickFixTest()

  @Test
  @TestMetadata("noComposeRuntimeLibraryFixWhenAlreadyPresent")
  @PluginTargetVersions(pluginVersion = "2.0+", gradleVersion = "8.0+")
  fun testNoComposeRuntimeLibraryFixWhenAlreadyPresent() = doComposeRuntimeQuickFixTest()

  @Test
  @TestMetadata("noComposeRuntimeFixWhenPluginNotInClasspath")
  @PluginTargetVersions(pluginVersion = "2.0+", gradleVersion = "8.0+")
  fun testNoComposeRuntimeFixWhenPluginNotInClasspath() = doComposeRuntimeQuickFixTest()

  private fun doComposeRuntimeQuickFixTest() {
    doMultiFileQuickFixTest(
      ignoreChangesInBuildScriptFiles = false,
      additionalResultFileFilter = { it.name != "settings.gradle.kts" },
    )
  }
}
