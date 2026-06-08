// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.mainkts.codeInsight

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.psi.util.PsiUtilCore
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.SERVER
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.replaceService
import com.intellij.util.ThreeState
import com.intellij.util.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class MainKtsDependsOnCompletionTest : KotlinLightCodeInsightFixtureTestCase() {

    private val fakeCompletionService = object : DependencyCompletionService {
        override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> =
            flowOf(
                DependencyCompletionEvent.Item(DependencyCompletionResult("org.example", "lib-alpha", "1.0", source = SERVER)),
                DependencyCompletionEvent.Item(DependencyCompletionResult("org.example", "lib-beta", "2.0", source = SERVER)),
                DependencyCompletionEvent.Item(DependencyCompletionResult("com.other", "util", "3.0", source = SERVER)),
            )
    }

    override fun setUp() {
        super.setUp()
        application.replaceService(DependencyCompletionService::class.java, fakeCompletionService, testRootDisposable)

        // Register extensions programmatically because the content module intellij.kotlin.base.scripting.main.kts
        // is excluded in test environments (its XML dependency on intellij.repository.search.completion is not loadable)
        val pluginDescriptor = DefaultPluginDescriptor("testMainKtsCompletion")
        val contributorEP = CompletionContributorEP(
            KotlinLanguage.INSTANCE.id,
            MainKtsDependsOnCompletionContributor::class.java.name,
            pluginDescriptor
        )
        application.registerExtension(CompletionContributor.EP, contributorEP, testRootDisposable)
    }

    fun `test findCoordinateStart returns index of first coordinate character`() {
        val text = """@DependsOn("org.example:lib:1.0")"""
        val quoteIndex = text.indexOf('"')
        val coordinateEnd = quoteIndex + 1 + "org.example:lib:1.0".length
        @Suppress("AssertBetweenInconvertibleTypes")
        assertEquals(quoteIndex + 1, findCoordinateStart(text, coordinateEnd))
    }

    fun `test findCoordinateStart with partial coordinate at end of string`() {
        val text = """@DependsOn("org."""
        assertEquals(text.indexOf('o'), findCoordinateStart(text, text.length))
    }

    fun `test findCoordinateStart stops at non-Maven-coordinate character`() {
        val text = """prefix @DependsOn("org.example")"""
        val quoteIndex = text.indexOf('"')
        val coordinateEnd = quoteIndex + 1 + "org.example".length
        @Suppress("AssertBetweenInconvertibleTypes")
        assertEquals(quoteIndex + 1, findCoordinateStart(text, coordinateEnd))
    }


    fun `test confidence returns UNSURE for fewer than 2 characters typed`() {
        myFixture.configureByText("script.main.kts", """@file:"kotlin.script.experimental.dependencies.DependsOn("o<caret>")""")
        val confidence = MainKtsDependsOnCompletionConfidence()
        val position = PsiUtilCore.getElementAtOffset(myFixture.file, myFixture.caretOffset - 1)
        val result = confidence.shouldSkipAutopopup(myFixture.editor, position, myFixture.file, myFixture.caretOffset)
        assertEquals(ThreeState.UNSURE, result)
    }

    fun `test completion activates for short-form DependsOn annotation`() {
        myFixture.configureByText("script.main.kts", """@file:DependsOn("org.ex<caret>")""")
        val items = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Expected Maven coordinate suggestions", items)
        assertContainsElements(items.map { it.lookupString }, "org.example:lib-alpha:1.0")
    }

    fun `test confidence returns NO for 2 or more characters typed`() {
        myFixture.configureByText("script.main.kts", "@file:kotlin.script.experimental.dependencies.DependsOn(\"or<caret>\")")
        val confidence = MainKtsDependsOnCompletionConfidence()
        val position = PsiUtilCore.getElementAtOffset(myFixture.file, myFixture.caretOffset - 1)
        val result = confidence.shouldSkipAutopopup(myFixture.editor, position, myFixture.file, myFixture.caretOffset)
        assertEquals(ThreeState.NO, result)
    }

    fun `test completion shows Maven coordinates inside DependsOn in main kts`() {
        myFixture.configureByText("script.main.kts", """@file:kotlin.script.experimental.dependencies.DependsOn("org.ex<caret>")""")
        val items = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Expected Maven coordinate suggestions", items)
        val lookupStrings = items.map { it.lookupString }
        assertContainsElements(lookupStrings, "org.example:lib-alpha:1.0", "org.example:lib-beta:2.0")
    }

    fun `test completion does not activate in a plain kts file`() {
        myFixture.configureByText("script.kts", """@file:kotlin.script.experimental.dependencies.DependsOn("org.ex<caret>")""")
        val items = myFixture.complete(CompletionType.BASIC)
        val lookupStrings = items?.map { it.lookupString }.orEmpty()
        assertDoesntContain(lookupStrings, "org.example:lib-alpha:1.0")
    }

    fun `test completion does not activate outside DependsOn annotation`() {
        myFixture.configureByText("script.main.kts", """val x = "org.ex<caret>"""")
        val items = myFixture.complete(CompletionType.BASIC)
        val lookupStrings = items?.map { it.lookupString }.orEmpty()
        assertDoesntContain(lookupStrings, "org.example:lib-alpha:1.0")
    }

    fun `test completion does not activate for custom DependsOn annotation from different package`() {
        myFixture.configureByText("script.main.kts", """@file:com.example.custom.DependsOn("org.ex<caret>")""")
        val items = myFixture.complete(CompletionType.BASIC)
        val lookupStrings = items?.map { it.lookupString }.orEmpty()
        assertDoesntContain(lookupStrings, "org.example:lib-alpha:1.0")
    }

    fun `test completion insert handler replaces the full typed coordinate`() {
        myFixture.configureByText("script.main.kts", """@file:kotlin.script.experimental.dependencies.DependsOn("org.ex<caret>")""")
        val items = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Expected completion lookup to be open", items)
        myFixture.lookup.currentItem = myFixture.lookupElements?.firstOrNull {
            it.lookupString == "org.example:lib-alpha:1.0"
        }
        myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        myFixture.checkResult("""@file:kotlin.script.experimental.dependencies.DependsOn("org.example:lib-alpha:1.0")""")
    }

    fun `test prefix matcher filters suggestions by substring of coordinate`() {
        myFixture.configureByText("script.main.kts", """@file:kotlin.script.experimental.dependencies.DependsOn("lib<caret>")""")
        val items = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Expected filtered suggestions", items)
        val lookupStrings = items.map { it.lookupString }
        assertContainsElements(lookupStrings, "org.example:lib-alpha:1.0", "org.example:lib-beta:2.0")
        assertDoesntContain(lookupStrings, "com.other:util:3.0")
    }
}
