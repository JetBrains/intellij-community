// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind
import com.intellij.turboComplete.CompletionPerformanceParameters
import com.intellij.turboComplete.analysis.PipelineListener
import com.intellij.util.application
import org.jetbrains.kotlin.idea.base.test.JUnit4Assertions.assertEquals
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.junit.Assert

private class SmartPipelineChecker : PipelineListener {
    private val collectedKinds = mutableSetOf<CompletionKind>()
    private val rankedKinds = mutableSetOf<CompletionKind>()
    private val invokedKinds = mutableSetOf<CompletionKind>()
    private var initializedCompletionParameters: CompletionParameters? = null
    private var collectionPerformed = false

    override fun onInitialize(parameters: CompletionParameters) {
        initializedCompletionParameters = parameters
    }

    override fun onCollectionStarted() {
        collectionPerformed = true
    }

    override fun onGeneratorCollected(suggestionGenerator: SuggestionGenerator) {
        collectedKinds.add(suggestionGenerator.kind)
    }

    override fun onRanked(ranked: List<RankedKind>) {
        ranked.forEach { rankedKinds.add(it.kind) }
    }

    override fun onGenerationFinished(suggestionGenerator: SuggestionGenerator) {
        invokedKinds.add(suggestionGenerator.kind)
    }

    fun validateAndClear() {
        try {
            requireNotNull(initializedCompletionParameters)
            val orderedCollected = collectedKinds.map { it.name.name }.sorted()
            val orderedRanked = rankedKinds.map { it.name.name }.sorted()
            val orderedInvoked = invokedKinds.map { it.name.name }.sorted()
            if (collectionPerformed) {
                require(orderedRanked.isNotEmpty())
            }
            assertEquals(orderedCollected, orderedInvoked)
        } finally {
            collectedKinds.clear()
            rankedKinds.clear()
            invokedKinds.clear()
        }
    }
}

private fun <T> withProperties(vararg parameters: Pair<String, Any>, action: () -> T): T {
    val initialValues: Map<String, String?> = parameters.associate { it.first to System.getProperty(it.first) }
    return try {
        parameters.forEach { System.setProperty(it.first, it.second.toString()) }
        action()
    } finally {
        initialValues.forEach { (parameter, value) ->
            value?.let {
                System.setProperty(parameter, value)
            } ?: run {
                System.clearProperty(parameter)
            }
        }
    }
}

private fun <T> enablingMLPerformance(action: () -> T): T {
    return withProperties(
        CompletionPerformanceParameters.PROPERTY_SHOW_LOOKUP_EARLY to true,
        CompletionPerformanceParameters.PROPERTY_EXECUTE_IMMEDIATELY to false
    ) {
        val pipelineValidityChecker = SmartPipelineChecker()
        val pipelineCheckerLifetime = Disposer.newDisposable()
        application.extensionArea.getExtensionPoint(PipelineListener.EP_NAME)
            .registerExtension(pipelineValidityChecker, pipelineCheckerLifetime)
        return@withProperties try {
            action()
        } finally {
            pipelineValidityChecker.validateAndClear()
            Disposer.dispose(pipelineCheckerLifetime)
        }
    }
}

abstract class AbstractK1MLPerformanceCompletionTest : AbstractK1JvmBasicCompletionTest() {
    override fun doTest(testPath: String) {
        val actualTestFile = handleTestPath(dataFilePath(fileName()))
        configureFixture(actualTestFile.path)

        val fileText = FileUtil.loadFile(actualTestFile, true)

        withCustomCompilerOptions(fileText, project, module) {
            assertTrue("\"<caret>\" is missing in file \"$testPath\"", fileText.contains("<caret>"))
            ConfigLibraryUtil.configureLibrariesByDirective(module, fileText)

            executeTest {
                if (ExpectedCompletionUtils.shouldRunHighlightingBeforeCompletion(fileText)) {
                    myFixture.doHighlighting()
                }
                val platform = getPlatform()
                testWithAutoCompleteSetting(fileText) {
                    val completionType = ExpectedCompletionUtils.getCompletionType(fileText) ?: defaultCompletionType()
                    val invocationCount = ExpectedCompletionUtils.getInvocationCount(fileText) ?: defaultInvocationCount()

                    //complete(completionType, invocationCount)

                    val items = enablingMLPerformance {
                        complete(completionType, invocationCount) ?: emptyArray<LookupElement>()
                    }

                    val expected = ExpectedCompletionUtils.itemsShouldExist(fileText, platform)
                    val unexpected = ExpectedCompletionUtils.itemsShouldAbsent(fileText, platform)
                    val itemsNumber = ExpectedCompletionUtils.getExpectedNumber(fileText, platform)
                    val nothingElse = ExpectedCompletionUtils.isNothingElseExpected(fileText)

                    Assert.assertTrue(
                        "Should be some assertions about completion",
                        expected.size != 0 || unexpected.size != 0 || itemsNumber != null || nothingElse
                    )
                    ExpectedCompletionUtils.assertContainsRenderedItems(expected, items, false, nothingElse)
                    ExpectedCompletionUtils.assertNotContainsRenderedItems(unexpected, items)

                    if (itemsNumber != null) {
                        val expectedItems =
                            ExpectedCompletionUtils.listToString(ExpectedCompletionUtils.getItemsInformation(items))
                        Assert.assertEquals("Invalid number of completion items: ${expectedItems}", itemsNumber, items.size)
                    }

                    //var itemsNoPerformance: Array<LookupElement> = emptyArray()
                    //var itemsWithPerformance: Array<LookupElement> = emptyArray()
                    //itemsNoPerformance = items
                    //itemsWithPerformance =
                    //    //enablingMLPerformance {
                    //    complete(completionType, invocationCount) ?: emptyArray()
                    ////}
                    //
                    //val renderedItemsNoPerformance = ExpectedCompletionUtils.getItemsInformation(itemsNoPerformance).map { it.toString() }
                    //val renderedItemsWithPerformance = ExpectedCompletionUtils.getItemsInformation(itemsWithPerformance).map { it.toString() }
                    //
                    //assertEquals(renderedItemsNoPerformance.sorted(), renderedItemsWithPerformance.sorted())
                }
            }
        }
    }
}