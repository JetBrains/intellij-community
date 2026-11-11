// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.testFramework.common.EditorCaretTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Checks the file type that the structural search dialog detects at the caret.
 */
@TestOnly
object DetectFileTypeTestUtil {
  /**
   * Configures [text] in the file `test.<ext>`, and checks that [UIUtil.detectFileType] gives [fileType] at the caret.
   * When [ext] is `null`, the file has the default extension of [fileType].
   * When the caret is in an injected fragment, the check uses the injected editor and file of [fixture].
   */
  @JvmStatic
  @JvmOverloads
  fun doTest(fixture: CodeInsightTestFixture, fileType: LanguageFileType, text: String, ext: String? = null) {
    val fileName = "test." + (ext ?: fileType.defaultExtension)
    assertTrue(text.contains(EditorCaretTestUtil.CARET_TAG), "No caret specified in $fileName")
    fixture.configureByText(fileName, text)
    val detected = ReadAction.computeBlocking<LanguageFileType, RuntimeException> {
      UIUtil.detectFileType(SearchContext(fixture.project, fixture.file, fixture.editor))
    }
    assertEquals(fileType, detected)
  }
}
