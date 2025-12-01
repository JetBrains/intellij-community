// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.TestLookupElementPresentation
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KMP_KOTLIN_FIXTURE
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.TestFiles
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NO_EXPECTED_SUGGESTIONS_DIRECTIVE = "NO-EXPECTED-SUGGESTIONS"
private const val EXPECTED_SUGGESTIONS_DIRECTIVE = "EXPECTED-SUGGESTION"
private const val EXPECTED_LOOKUP_STRINGS_DIRECTIVE = "EXPECTED-LOOKUP-STRING"
private const val OTHER_SUGGESTIONS_ARE_NOT_EXPECTED = "OTHER-SUGGESTIONS-ARE-NOT-EXPECTED"
private const val UNEXPECTED_SUGGESTIONS_DIRECTIVE = "UNEXPECTED-SUGGESTION"
private const val SUGGESTION_INDEX_TO_APPLY_DIRECTIVE = "SUGGESTION-INDEX-TO-APPLY"
private const val COMPLETION_RESULT_PATH_DIRECTIVE = "COMPLETION-RESULT-PATH"

/**
 * Directives for completion tests don't match to a common [org.jetbrains.kotlin.idea.test.KotlinTestUtils.DIRECTIVE_PATTERN].
 * They have hyphens instead of underscores, and the keys are quoted.
 */
private val COMPLETION_DIRECTIVE_PATTERN = Pattern.compile("^//\\s*!?\"([A-Z0-9\\-]+)\"(:[ \\t]*(.*))?$", Pattern.MULTILINE)

abstract class AbstractKotlinGradleCompletionTest : AbstractGradleCodeInsightTest() {

    /**
     * Verifies the completion, relying on these directives (all are optional):
     * - [NO_EXPECTED_SUGGESTIONS_DIRECTIVE] - if specified, no suggestions are expected to be shown.
     * This is possible either if there were no suggestions at all
     * or if there was only one suggestion that was applied automatically.
     * - [EXPECTED_SUGGESTIONS_DIRECTIVE] - the list of expected suggestions visible to user, in the order they appear.
     * Could be listed in one line, separated with comma or on separate lines starting with the same directive.
     * - [EXPECTED_LOOKUP_STRINGS_DIRECTIVE] - the list of strings matching to the user's input.
     * - [OTHER_SUGGESTIONS_ARE_NOT_EXPECTED] - if specified, there should be not extra suggestions among the expected ones.
     * - [UNEXPECTED_SUGGESTIONS_DIRECTIVE] - the list of unexpected suggestions.
     * - [SUGGESTION_INDEX_TO_APPLY_DIRECTIVE] - if specified, the test executes completion for the suggestion at the specified index.
     * - [COMPLETION_RESULT_PATH_DIRECTIVE] - if specified, the test verifies the completion result at the specified path.
     */
    protected fun verifyCompletion(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder = GRADLE_KMP_KOTLIN_FIXTURE) {
        test(gradleVersion, fixtureBuilder) {
            codeInsightFixture.configureFromExistingVirtualFile(mainTestDataPsiFile.virtualFile)
            assertTrue("<caret> should be specified in the test file") {
                mainTestDataFile.content.contains("<caret>")
            }
            runInEdtAndWait {
                codeInsightFixture.performEditorAction(IdeActions.ACTION_CODE_COMPLETION)
                verifyLookupElements()
                maybeExecuteCompletion()
                maybeCheckResult()
            }
        }
    }

    override val testFileFactory = object : TestFiles.TestFileFactoryNoModules<TestFile>() {
        override fun create(fileName: String, text: String, directives: Directives): TestFile {
            val directives = parseDirectives(text, directives)
            val linesWithoutDirectives = text.lines()
                .filter { !it.startsWith("// FILE") }
                .filter { COMPLETION_DIRECTIVE_PATTERN.matcher(it).matches().not() }
            return TestFile(fileName, linesWithoutDirectives.joinToString(separator = "\n"), directives)
        }
    }

    /**
     * Copied from [org.jetbrains.kotlin.idea.test.KotlinTestUtils.parseDirectives]
     */
    private fun parseDirectives(text: String, directives: Directives): Directives {
        val directiveMatcher = COMPLETION_DIRECTIVE_PATTERN.matcher(text)
        while (directiveMatcher.find()) {
            val name = directiveMatcher.group(1)
            val value = directiveMatcher.group(3)
            directives.put(name, value)
        }
        return directives
    }

    private fun verifyLookupElements() {
        val noExpectedSuggestions = mainTestDataFile.directives.contains(NO_EXPECTED_SUGGESTIONS_DIRECTIVE)
        val noActualSuggestions = codeInsightFixture.lookupElements.isNullOrEmpty()
        if (noActualSuggestions) {
            assertTrue(noExpectedSuggestions,
                       "Actual suggestion list is empty. If it's expected, the test should have the directive: NO-EXPECTED-SUGGESTIONS")
        }
        if (noExpectedSuggestions) {
            assertTrue(noActualSuggestions,
                       "The suggestion list was expected to be empty, but it has elements: ${codeInsightFixture.lookupElementStrings}")
        }

        verifySuggestions()
        verifyLookupStrings()
    }

    /**
     * Verifies the list of the elements visible to the user in the lookup popup.
     * It has the same size as the number of all LookupElement's.
     */
    private fun verifySuggestions() {
        val expectedSuggestions = mainTestDataFile.directives.listValues(EXPECTED_SUGGESTIONS_DIRECTIVE) ?: emptyList()
        val suggestions = codeInsightFixture.lookupElements?.map {
            TestLookupElementPresentation.renderReal(it).itemText ?: it.lookupString
        } ?: emptyList()
        checkIfHasExpected(suggestions, expectedSuggestions)
        checkIfCanHaveExtraSuggestions(suggestions, expectedSuggestions)
        checkIfHasUnexpected(suggestions)
    }

    /**
     * Verifies the list of strings matching to the user's input. It could have a bigger size than the number of all LookupElement's.
     * For example, LookupElement `fooVariable` could have these lookup strings: `fooVariable`,  `getFooVariable`, `setFooVariable`.
     */
    private fun verifyLookupStrings() {
        val expectedLookupStrings = mainTestDataFile.directives.listValues(EXPECTED_LOOKUP_STRINGS_DIRECTIVE) ?: emptyList()
        val lookupStrings = codeInsightFixture.lookupElements
            ?.flatMap { it.allLookupStrings }
            ?: emptyList()
        checkIfHasExpected(lookupStrings, expectedLookupStrings)
        checkIfHasUnexpected(lookupStrings)
    }

    private fun checkIfHasExpected(actualSuggestions: List<String>, expectedSuggestions: List<String>) {
        expectedSuggestions.forEachIndexed { index, expected ->
            val actual = actualSuggestions.getOrNull(index)
            assertEquals(
                expected,
                actual,
                "Actual suggestion at #$index is $actual, expected: $expected\n\n" +
                        "actual suggestions: $actualSuggestions\nexpected suggestions: $expectedSuggestions"
            )
        }
    }

    /**
     * Useful when the list of suggestions has a fixed size, and of it is checked with [checkIfHasExpected].
     */
    private fun checkIfCanHaveExtraSuggestions(
        suggestions: List<String>,
        expectedSuggestions: List<String>
    ) {
        val anythingElseIsNotExpected = mainTestDataFile.directives.contains(OTHER_SUGGESTIONS_ARE_NOT_EXPECTED)
        if (anythingElseIsNotExpected) {
            val notExpectedSuggestions = suggestions.filter { it !in expectedSuggestions }
            assertTrue(
                notExpectedSuggestions.isEmpty(),
                "Actual suggestions list contains unexpected suggestions: $notExpectedSuggestions"
            )
        }
    }

    /**
     * Useful when the list of suggestions is a large, and it's not possible to verify it all with [checkIfHasExpected].
     */
    private fun checkIfHasUnexpected(suggestions: List<String>) {
        val unexpectedSuggestions = mainTestDataFile.directives.listValues(UNEXPECTED_SUGGESTIONS_DIRECTIVE) ?: return
        unexpectedSuggestions.forEach {
            assertFalse(
                suggestions.contains(it),
                "Actual suggestions list contains unexpected suggestion: $it"
            )
        }
    }

    private fun maybeExecuteCompletion() {
        val indexAsString = mainTestDataFile.directives[SUGGESTION_INDEX_TO_APPLY_DIRECTIVE] ?: return
        val suggestionIndex = indexAsString.toIntOrNull()
            ?: error("Invalid suggestion index: $indexAsString")
        val elementToApply = codeInsightFixture.lookupElements?.getOrNull(suggestionIndex)
            ?: error("There is no suggestion with index $suggestionIndex")
        codeInsightFixture.lookup.currentItem = elementToApply
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    private fun maybeCheckResult() {
        val expectedResultPath = mainTestDataFile.directives[COMPLETION_RESULT_PATH_DIRECTIVE] ?: return
        val expectedResultFile = testDataFiles.find { it.path == expectedResultPath }
            ?: error("Unable to find expected result file: $expectedResultPath")
        codeInsightFixture.checkResult(expectedResultFile.content, false)
    }
}