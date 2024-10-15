// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.highlighting.HyperlinkAnnotator
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.IssueNavigationLink
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KotlinIssueReferenceContributorTest : KotlinLightCodeInsightFixtureTestCase() {
    @Before
    fun setUpIssues() {
        IssueNavigationConfiguration.getInstance(myFixture.project).links = listOf(
            IssueNavigationLink("\\b[A-Z]+\\-\\d+\\b", "http://youtrack.jetbrains.com/issue/\$0")
        )
    }

    @Test
    fun `web reference in strings, comments and kdoc`() {
        val quotes = "\"\"\""
        val dlr = "\$"
        myFixture.configureByText(
            "Main.kt",
            """
                val s1 = "IDEA-320291"
                val s2 = "sometext IDEA-320295 zz IDEA-320297"
                val s3 = ${quotes}
                  IDEA-320295
                ${quotes}
                val s4 = "${dlr}{s1} IDEA-320298"
                val s5 = "  zIDEA-320299 " // IDEA-320300
                
                /**
                 * IDEA-320301
                 */
                 fun foo(){}
            """.trimIndent()
        )
        assertWebReferences("""
            IDEA-320291->http://youtrack.jetbrains.com/issue/IDEA-320291
            IDEA-320295->http://youtrack.jetbrains.com/issue/IDEA-320295
            IDEA-320297->http://youtrack.jetbrains.com/issue/IDEA-320297
            IDEA-320295->http://youtrack.jetbrains.com/issue/IDEA-320295
            IDEA-320298->http://youtrack.jetbrains.com/issue/IDEA-320298
            IDEA-320300->http://youtrack.jetbrains.com/issue/IDEA-320300
            IDEA-320301->http://youtrack.jetbrains.com/issue/IDEA-320301
        """.trimIndent())
    }

    @Test
    fun `issue references are highlighted`() {
        val quotes = "\"\"\""
        val dlr = "\$"
        myFixture.configureByText(
            "Main.kt",
            """
                val s1 = "<info>IDEA-320295</info>"
                val s2 = "sometext <info>IDEA-320295</info> zz <info>IDEA-320296</info>"
                val s3 = ${quotes}
                  <info>IDEA-320295</info>
                ${quotes}
                val s4 = "${dlr}{s1} <info>IDEA-320295</info>"
                val s5 = "  zIDEA-320295 "  // <info>IDEA-320300</info>
                
                /**
                 * <info>IDEA-320301</info>
                 */
                 fun foo(){}
            """.trimIndent()
        )

        myFixture.checkHighlighting(false, true, false)
    }

    private fun assertWebReferences(expectedReferences: String) {
        val webReferences = getAllProvidedReferences(myFixture.file)
            .filterIsInstance<WebReference>()
            .joinToString("\n") { "${it.canonicalText}->${it.url}" }
        Assert.assertEquals(expectedReferences.replace("\r", ""), webReferences)
    }

    private fun getAllProvidedReferences(file: PsiFile): List<PsiReference> {
        val result = mutableListOf<PsiReference>()
        file.accept(object : PsiRecursiveElementWalkingVisitor(true) {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                result.addAll(HyperlinkAnnotator.calculateReferences(element))
            }
        })
        return result
    }
}