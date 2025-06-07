package com.intellij.compose.ide.plugin.shared.util

import com.intellij.compose.ide.plugin.shared.isComposeEnabledInModule
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Enables Compose in the tests by adding Composable annotation to the test classpath.
 */
internal fun JavaCodeInsightTestFixture.enableComposeInTest() {
  if (!this.isComposeEnabled()) {
    createComposableAnnotationInFile("Composable.kt")
  }
}

/**
 * Checks if Compose is enabled in tests by checking existence of 'androidx.runtime.Composable' annotation in the module.
 */
internal fun JavaCodeInsightTestFixture.isComposeEnabled(): Boolean {
  return isComposeEnabledInModule(this.module)
}

/**
 * Creates a Kotlin file [fileRelativePath] containing the definition of the `@Composable` annotation in the test's project directory.
 *
 * @param fileRelativePath The name of the file, including its relative path, where the `@Composable` annotation definition will
 *                 be added within the test project.
 */
private fun JavaCodeInsightTestFixture.createComposableAnnotationInFile(fileRelativePath: String) {
  val composableAnnotationDefinitionFileText = """
                  package androidx.compose.runtime
  
                  annotation class Composable
              """.trimIndent()
  this.addFileToProject(fileRelativePath, composableAnnotationDefinitionFileText)
}

internal fun resolveTestDataDirectory(directoryPath: String): Path {
  val dirPath = Paths.get(directoryPath)
  return Paths.get(PlatformTestUtil.getCommunityPath())
    .resolve("plugins/compose/intellij.compose.ide.plugin.shared")
    .resolve(dirPath)
}

@ApiStatus.Internal
fun JavaCodeInsightTestFixture.configureByText(text: String): KtFile {
  return configureByText("Test.kt", text) as KtFile
}

@ApiStatus.Internal
const val CARET = EditorTestUtil.CARET_TAG
