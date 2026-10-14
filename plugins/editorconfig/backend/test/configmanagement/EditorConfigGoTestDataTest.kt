// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.fileEditor.TrailingSpacesOptionsProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Checks the section of `community/.editorconfig` that keeps the Go test data unchanged on save.
 * The test copies that file into its fixture, so a removal of the section from the repository turns it red.
 * A Go file under a `testData` directory stays out of the formatter and keeps its whitespace.
 */
class EditorConfigGoTestDataTest : EditorConfigFileSettingsTestCase() {
  override fun setUp() {
    super.setUp()
    val repositoryConfig = Paths.get(PathManagerEx.getCommunityHomePath(), ".editorconfig")
    Files.copy(repositoryConfig, this.testDataPath.resolve(".editorconfig"))
  }

  fun testGoTestData() {
    this.assertKeptUnchanged(this.findPsiFile("impl/testData/highlighting/source.go"))
    this.assertKeptUnchanged(this.findPsiFile("impl/testData/go.mod"))

    val sourceFile = this.findPsiFile("impl/src/source.go")
    assertFalse("The formatter must accept the file under src",
                CodeStyle.getSettings(sourceFile).excludedFiles.contains(sourceFile))
    val sourceOptions = this.trailingSpacesOptions(sourceFile.virtualFile)
    assertEquals("trim_trailing_whitespace must stay on under src", true, sourceOptions.stripTrailingSpaces)
    assertEquals("insert_final_newline must stay on under src", true, sourceOptions.ensureNewLineAtEOF)
  }

  /**
   * Asserts that the formatter skips [file] and that both whitespace options are off for it.
   */
  private fun assertKeptUnchanged(file: PsiFile) {
    val path = file.virtualFile.path
    assertTrue("The formatter must skip $path", CodeStyle.getSettings(file).excludedFiles.contains(file))
    val options = this.trailingSpacesOptions(file.virtualFile)
    assertEquals("trim_trailing_whitespace must be off for $path", false, options.stripTrailingSpaces)
    assertEquals("insert_final_newline must be off for $path", false, options.ensureNewLineAtEOF)
  }

  /**
   * Trailing spaces options that the EditorConfig plugin reports for [file].
   */
  private fun trailingSpacesOptions(file: VirtualFile): TrailingSpacesOptionsProvider.Options {
    return TrailingSpacesOptionsProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getOptions(this.project, file) }
           ?: error("The EditorConfig plugin reports no trailing spaces options for ${file.path}")
  }

  override fun getRelativePath(): String {
    return "plugins/editorconfig/testData/org/editorconfig/configmanagement"
  }
}
