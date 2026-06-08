package com.intellij.compose.ide.plugin.resources

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

internal class ComposeResourcesErrorsHighlightingTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test no errors in source files`() {
    val files = importProjectFromTestData()

    val kotlinFiles = files.filter {
      it.extension == "kt" &&
      it.path.contains(sourceSetName) &&
      it.isTestableSourceFile()
    }

    assertNoHighlightingErrors(kotlinFiles)
  }

  private fun assertNoHighlightingErrors(files: List<VirtualFile>) {
    val errorsByFileName = mutableMapOf<String, List<String>>()

    timeoutRunBlocking(context = Dispatchers.EDT) {
      for (file in files) {
        codeInsightTestFixture.openFileInEditor(file)

        val errors = codeInsightTestFixture
          .doHighlighting()
          .filter { it.severity == HighlightSeverity.ERROR }
          .mapNotNull { it.description }

        if (errors.isNotEmpty()) {
          errorsByFileName[file.name] = errors
        }
      }
    }

    assertTrue(
      errorsByFileName.entries.joinToString("\n\n") { (fileName, errors) ->
        "$fileName:\n${errors.joinToString("\n") { "  - $it" }}"
      },
      errorsByFileName.isEmpty()
    )
  }

  private fun VirtualFile.isTestableSourceFile(): Boolean {
    // Android SDK is not configured in tests, so we can only verify files
    // that don't depend on Android APIs.
    return sourceSetName != ANDROID_MAIN || this.name == "test.$ANDROID_MAIN.kt"
  }


}
