package org.jetbrains.kotlin.idea.grazie

import com.intellij.grazie.GrazieTestBase

class KotlinGrazieSupportTest : GrazieTestBase() {
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
}
