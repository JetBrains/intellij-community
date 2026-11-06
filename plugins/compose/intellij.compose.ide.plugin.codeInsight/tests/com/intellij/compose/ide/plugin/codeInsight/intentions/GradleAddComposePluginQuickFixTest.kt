// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.codeInsight.intentions

import com.intellij.compose.ide.plugin.codeInsight.composeCodeInsightTestDataRoot
import com.intellij.compose.ide.plugin.codeInsight.isComposeCompilerPluginApplied
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleMultiFileQuickFixTest
import org.jetbrains.kotlin.idea.fir.K2DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import java.io.File

@TestRoot("../compose/intellij.compose.ide.plugin.codeInsight/testData")
@TestMetadata("gradle/fixes")
internal class GradleAddComposePluginQuickFixTest : AbstractGradleMultiFileQuickFixTest() {

  @Suppress("IO_FILE_USAGE")
  override fun testDataDirectory(): File {
    val testDataFileName = requireNotNull(KotlinTestUtils.getTestDataFileName(javaClass, getName())) {
      "Missing @TestMetadata for ${getName()}"
    }
    return composeCodeInsightTestDataRoot().resolve("gradle/fixes/$testDataFileName/before").toFile()
  }

  override fun checkUnexpectedErrors(mainFile: File, ktFile: KtFile, fileText: String) {
    K2DirectiveBasedActionUtils.checkForUnexpectedErrors(mainFile, ktFile, fileText)
  }

  @Test
  @TestMetadata("addComposeCompilerPluginWithoutVersion")
  @PluginTargetVersions(pluginVersion = "2.0+", gradleVersion = "8.0+")
  fun testAddComposeCompilerPluginWithoutVersion() = doComposePluginQuickFixTest(checkSync = true)

  @Test
  @TestMetadata("addComposeCompilerPluginWithVersion")
  @PluginTargetVersions(pluginVersion = "2.0+", gradleVersion = "8.0+")
  fun testAddComposeCompilerPluginWithVersion() = doComposePluginQuickFixTest(checkSync = true)

  @Test
  @TestMetadata("noFixAllForComposeCompilerPlugin")
  @PluginTargetVersions(pluginVersion = "2.0+", gradleVersion = "8.0+")
  fun testNoFixAllForComposeCompilerPlugin() = doComposePluginQuickFixTest(checkSync = false)

  private fun doComposePluginQuickFixTest(checkSync: Boolean) {
    doMultiFileQuickFixTest(
      ignoreChangesInBuildScriptFiles = false,
      additionalResultFileFilter = { it.name != "settings.gradle.kts" },
    )
    if (!checkSync) return

    val modules = ModuleManager.getInstance(myProject).modules
    assertTrue(
      "The sync after the fix did not apply the Compose compiler plugin to a module",
      modules.any { it.isComposeCompilerPluginApplied },
    )
  }
}
