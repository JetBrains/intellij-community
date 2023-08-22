// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.paths.WebReference
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil.unwrapMultiReference
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.reflect.KClass

@RunWith(JUnit4::class)
class KotlinWebReferenceContributorTest : KotlinLightCodeInsightFixtureTestCase() {
    @Test
    fun `web reference in string`() {
        myFixture.configureByText(
            "Main.kt",
            """
                val normalString = "https//value"
                val single = "https://example.com"
                val multiple = "   https://example-one.com  https://example-two.com"
            """.trimIndent()
        )

        assertInjectedReference(WebReference::class, "https://example.com")
        assertInjectedReference(WebReference::class, "https://example-one.com", "https://example-two.com")
    }

    @Test
    fun `web reference in multiline string`() {
        val quotes = "\"\"\""

        myFixture.configureByText(
            "Main.kt",
            """
                val normalString = ${quotes}https//value${quotes}
                val singleLine = ${quotes}https://example.com${quotes}
                val multipleLine = ${quotes}
                   https://example-one.com Some text here 
                   https://example-two.com
               ${quotes}
            """.trimIndent()
        )

        assertInjectedReference(WebReference::class, "https://example.com")
        assertInjectedReference(WebReference::class, "https://example-one.com", "https://example-two.com")
    }

    @Test
    fun `web reference in string with templates`() {
        val dlr = "\$"
        myFixture.configureByText(
            "Main.kt",
            """
                val path = 123
                val single = "https://example.com/${dlr}{path}/users"
                val multiple = "${dlr}{path}   https://example-one.com  https://example-two.com${dlr}{path}"
            """.trimIndent()
        )

        assertInjectedReference(WebReference::class, "https://example.com")
        assertInjectedReference(WebReference::class, "https://example-one.com", "https://example-two.com")
    }

    @Test
    fun `web references are highlighted`() {
        myFixture.configureByText(
            "Main.kt",
            """
                val normalString = "https//doesNotLookLikeUrl"
                val single = "<info>https://example.com</info> Not URL text"
                val multiple = "   <info>https://example-one.com</info>  <info>https://example-two.com</info>"
            """.trimIndent()
        )

        myFixture.checkHighlighting(false, true, false)
    }

    private fun assertInjectedReference(referenceClass: KClass<*>, vararg fragmentTexts: String) {
        val doc = myFixture.editor.document
        val provider = myFixture.file.viewProvider

        for (text in fragmentTexts) {
            val pos = doc.text.indexOf(text) + text.length / 2

            val foundReference = provider.findReferenceAt(pos)
            assertNotNull("There should be reference", foundReference)

            val reference = unwrapMultiReference(foundReference!!).find { referenceClass.isInstance(it) }
            assertNotNull("There should be reference of class " + referenceClass.java, reference)
        }
    }
}