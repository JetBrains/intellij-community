// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.fileEditor.TrailingSpacesOptionsProvider
import com.intellij.openapi.vfs.VirtualFile

/**
 * Checks the `.editorconfig` section that keeps the Go test data unchanged on save.
 * A Go file under a `testData` directory stays out of the formatter and keeps its whitespace.
 */
class EditorConfigGoTestDataTest : EditorConfigFileSettingsTestCase() {
  fun testGoTestData() {
    val testDataFile = findPsiFile("impl/testData/highlighting/source.go")
    val sourceFile = findPsiFile("impl/src/source.go")

    assertTrue("The formatter must skip the file under testData",
               CodeStyle.getSettings(testDataFile).excludedFiles.contains(testDataFile))
    assertFalse("The formatter must accept the file under src",
                CodeStyle.getSettings(sourceFile).excludedFiles.contains(sourceFile))

    val testDataOptions = trailingSpacesOptions(testDataFile.virtualFile)
    assertEquals("trim_trailing_whitespace must be off under testData", false, testDataOptions.stripTrailingSpaces)
    assertEquals("insert_final_newline must be off under testData", false, testDataOptions.ensureNewLineAtEOF)

    val sourceOptions = trailingSpacesOptions(sourceFile.virtualFile)
    assertEquals("trim_trailing_whitespace must stay on under src", true, sourceOptions.stripTrailingSpaces)
    assertEquals("insert_final_newline must stay on under src", true, sourceOptions.ensureNewLineAtEOF)
  }

  /** Returns the trailing spaces options that the EditorConfig plugin reports for the file. */
  private fun trailingSpacesOptions(file: VirtualFile): TrailingSpacesOptionsProvider.Options {
    return TrailingSpacesOptionsProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getOptions(project, file) }
           ?: error("The EditorConfig plugin reports no trailing spaces options for ${file.path}")
  }

  override fun getRelativePath(): String {
    return "plugins/editorconfig/testData/org/editorconfig/configmanagement"
  }
}
