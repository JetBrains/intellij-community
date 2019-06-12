// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.documentation

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.editorconfig.language.codeinsight.documentation.EditorConfigDocumentationProvider

class EditorConfigDocumentationTest : LightCodeInsightFixtureTestCase() {
  fun testIndentSizeDoc() {
    myFixture.configureByText(
      ".editorconfig",
      """
        [*]
        indent_<caret>size = 2
      """.trimIndent()
    )
    val originalElement = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    val element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
    val doc = EditorConfigDocumentationProvider().generateDoc(element, originalElement)
    val expected = "number of whitespace symbols used for indents"
    assertEquals(expected, doc)
  }
}
