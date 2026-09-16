// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@ComposeResourcesAllSourceSets
internal class ComposeResourcesErrorsHighlightingTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test no errors in source files`() = testComposeResourcesProject {
    assertNoHighlightingErrors(sourceSetKotlinFiles())
  }

  /**
   * Collects the Kotlin files of the current source set.
   * The resource resolution needs the canonical file, so this function maps every file through [canonicalProjectFile].
   */
  private fun sourceSetKotlinFiles(): List<VirtualFile> {
    val sourceSetRoot = projectRoot.findFileByRelativePath("composeApp/src/$sourceSetName/kotlin")
                        ?: error("Cannot find the Kotlin source root of $sourceSetName")

    val kotlinFiles = mutableListOf<VirtualFile>()
    VfsUtilCore.iterateChildrenRecursively(sourceSetRoot, null) { file ->
      if (!file.isDirectory && file.extension == "kt" && file.isTestableSourceFile()) {
        kotlinFiles.add(file)
      }
      true
    }

    return kotlinFiles.map { canonicalProjectFile(projectRelativePath(it)) }
  }

  private fun assertNoHighlightingErrors(files: List<VirtualFile>) {
    val errorsByFileName = mutableMapOf<String, List<String>>()

    timeoutRunBlocking(context = Dispatchers.EDT) {
      for (file in files) {
        codeInsightFixture.configureFromExistingVirtualFile(file)

        val errors = codeInsightFixture
          .doHighlighting()
          .filter { it.severity == HighlightSeverity.ERROR }
          .mapNotNull { it.description }

        if (errors.isNotEmpty()) {
          errorsByFileName[file.name] = errors
        }
      }
    }

    assertTrue(
      errorsByFileName.isEmpty(),
      errorsByFileName.entries.joinToString("\n\n") { (fileName, errors) ->
        "$fileName:\n${errors.joinToString("\n") { "  - $it" }}"
      }
    )
  }

  private fun VirtualFile.isTestableSourceFile(): Boolean {
    // Android SDK is not configured in tests, so we can only verify files
    // that don't depend on Android APIs.
    return sourceSetName != ANDROID_MAIN || this.name == "test.$ANDROID_MAIN.kt"
  }
}
