// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.documentation

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.editorconfig.language.codeinsight.documentation.EditorConfigDocumentationProvider

class EditorConfigDocumentationTest : BasePlatformTestCase() {
  fun testIndentSizeDoc() {
    myFixture.configureByText(
      ".editorconfig",
      """
        [*]
        indent_<caret>size = 2
      """.trimIndent()
    )
    val offset = myFixture.editor.caretModel.offset
    val originalElement = myFixture.file.findElementAt(offset)
    val element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
    val targets = IdeDocumentationTargetProvider.getInstance(project).documentationTargets(myFixture.editor, myFixture.file, offset)
    UsefulTestCase.assertSize(1, targets)
    val target = targets[0]
    val text = target.computePresentation().presentableText
    assertEquals("indent_size", text)
    val doc = EditorConfigDocumentationProvider().generateDoc(element, originalElement)
    val expected = "number of whitespace symbols used for indents"
    assertEquals(expected, doc)
  }
}
