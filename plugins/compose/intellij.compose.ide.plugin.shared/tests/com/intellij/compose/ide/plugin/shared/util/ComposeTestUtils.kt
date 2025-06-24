/*
 * Copyright (C) 2023 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.shared.util

import com.intellij.compose.ide.plugin.shared.isComposeEnabledInModule
import com.intellij.psi.PsiElement
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertTrue

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

private fun String.offsetForWindow(window: String, startIndex: Int = 0): Int {
  require(window.count { it == '|' } == 1) {
    "Must provide exactly one '|' character in window. Got \"$window\""
  }
  val delta = window.indexOf("|")
  val target = window.substring(0, delta) + window.substring(delta + 1)
  val start = indexOf(target, startIndex - delta)
  assertTrue(start >= 0, "Didn't find the string $target in the source of $this")
  return start + delta
}

/**
 * Returns the offset of the caret in the currently open editor as indicated by the [window] string.
 *
 * The [window] string needs to contain a `|` character surrounded by a prefix and/or suffix to be
 * found in the file. The file is searched for the concatenation of prefix and suffix strings and
 * the caret is placed at the first matching offset, between the prefix and suffix.
 */
fun CodeInsightTestFixture.offsetForWindow(window: String, startIndex: Int = 0): Int =
  editor.document.text.offsetForWindow(window, startIndex)


/**
 * Moves caret in the currently open editor to position indicated by the [window] string.
 *
 * The [window] string needs to contain a `|` character surrounded by a prefix and/or suffix to be
 * found in the file. The file is searched for the concatenation of prefix and suffix strings and
 * the caret is placed at the first matching offset, between the prefix and suffix.
 */
fun CodeInsightTestFixture.moveCaret(window: String): PsiElement {
  val offset = offsetForWindow(window)
  editor.caretModel.moveToOffset(offset)
  // myFixture.elementAtCaret seems to do something else
  return file.findElementAt(offset)!!
}
