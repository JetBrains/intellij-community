// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.grazie

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContentTest
import com.intellij.grazie.text.TextExtractor

class KotlinGrazieSupportTest28 : GrazieTestBase() {
    override val additionalEnabledRules: Set<String> = setOf("UPPERCASE_SENTENCE_START")

    override fun runHighlightTestForFile(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, false, false)
    }

    override fun getBasePath(): String = "community/plugins/kotlin/idea/tests/testData"

    fun `test spellcheck in constructs`() {
        runHighlightTestForFile("grazie/Constructs.kt")
    }

    fun `test grammar check in docs`() {
        runHighlightTestForFile("grazie/Docs.kt")
    }

    fun `test grammar check in string literals`() {
        runHighlightTestForFile("grazie/StringLiterals.kt")
    }

    fun `test text extraction in string literals`() {
        val file = myFixture.configureByText("a.kt", "val s = \"foo $" + "{injection} bar\" ")
        val content = TextExtractor.findTextAt(file, 10, TextContent.TextDomain.ALL)
        assertEquals("foo | bar", TextContentTest.unknownOffsets(content))
    }
}
