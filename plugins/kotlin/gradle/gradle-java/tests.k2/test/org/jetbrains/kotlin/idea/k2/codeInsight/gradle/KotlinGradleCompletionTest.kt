// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KMP_KOTLIN_FIXTURE
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("SENSELESS_COMPARISON")
@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/completion")
class KotlinGradleCompletionTest : AbstractGradleCodeInsightTest() {

    //build.gradle.kts
    @Disabled("KTIJ-30645 K2: Gradle.kts: Completion in gradle.kts files should offer only suitable suggestions")
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/topLevelSuggestionsInBuildGradleKts.test")
    fun testTopLevelSuggestionsInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/topLevelSuggestionsForPartOfStatementInBuildGradleKts.test")
    fun testTopLevelSuggestionsForPartOfStatementInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/topLevelSuggestionsCamelCaseInBuildGradleKts.test")
    fun testTopLevelSuggestionsCamelCaseInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/lastTopLevelSuggestionsInBuildGradleKts.test")
    fun testLastTopLevelSuggestionsInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/lastTopLevelSuggestionsChainCallInBuildGradleKts.test")
    fun testLastTopLevelSuggestionsChainCallInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/lastTopLevelSuggestionsForPartOfStatementInBuildGradleKts.test")
    fun testLastTopLevelSuggestionsForPartOfStatementInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/lastTopLevelSuggestionsCamelCaseInBuildGradleKts.test")
    fun testLastTopLevelSuggestionsCamelCaseInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/suggestionsInsideLambdaInBuildGradleKts.test")
    fun testSuggestionsInsideLambdaInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/suggestionsInsideLambdaInsideLambdaCamelCaseInBuildGradleKts.test")
    fun testSuggestionsInsideLambdaInsideLambdaCamelCaseInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/topLevelDestructuringDeclarationsSuggestionsInBuildGradleKts.test")
    fun testTopLevelDestructuringDeclarationsSuggestionsInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/declarationsFromImplicitReceiversSuggestionsInBuildGradleKts.test")
    fun testDeclarationsFromImplicitReceiversSuggestionsInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/declarationsFromImplicitReceiversAssessorSuggestionsInBuildGradleKts.test")
    fun testDeclarationsFromImplicitReceiversAssessorSuggestionsInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildGradleKts/noSuggestionsInBuildGradleKts.test")
    fun testNoSuggestionsInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    //settings.gradle.kts
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/topLevelSuggestionsCamelCaseInSettingsGradleKts.test")
    fun testTopLevelSuggestionsCamelCaseInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/declarationsFromImplicitReceiversAssessorSuggestionsInSettingsGradleKts.test")
    fun testDeclarationsFromImplicitReceiversAssessorSuggestionsInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/declarationsFromImplicitReceiversSuggestionsInSettingsGradleKts.test")
    fun testDeclarationsFromImplicitReceiversSuggestionsInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/topLevelAssessorSuggestionsInSettingsGradleKts.test")
    fun testTopLevelAssessorSuggestionsInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/lastTopLevelSuggestionsForPartOfStatementInSettingsGradleKts.test")
    fun testLastTopLevelSuggestionsForPartOfStatementInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/lastTopLevelSuggestionsInSettingsGradleKts.test")
    fun testLastTopLevelSuggestionsInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/noSuggestionsInSettingsGradleKts.test")
    fun testNoSuggestionsInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/suggestionsInsideLambdaInSettingsGradleKts.test")
    fun testSuggestionsInsideLambdaInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/suggestionsInsideLambdaInsideLambdaCamelCaseInSettingsGradleKts.test")
    fun testSuggestionsInsideLambdaInsideLambdaCamelCaseInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/topLevelDestructuringDeclarationsSuggestionsInSettingsGradleKts.test")
    fun testTopLevelDestructuringDeclarationsSuggestionsInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("settingsGradleKts/topLevelSuggestionsInSettingsGradleKts.test")
    fun testTopLevelSuggestionsInSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    //buildSrc
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/declarationsFromImplicitReceiversAssessorSuggestionsInBuildGradleKtsInBuildSrc.test")
    fun testDeclarationsFromImplicitReceiversAssessorSuggestionsInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/declarationsFromImplicitReceiversSuggestionsInBuildGradleKtsInBuildSrc.test")
    fun testDeclarationsFromImplicitReceiversSuggestionsInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/lastTopLevelSuggestionsCamelCaseInBuildGradleKtsInBuildSrc.test")
    fun testLastTopLevelSuggestionsCamelCaseInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/lastTopLevelSuggestionsChainCallInBuildGradleKtsInBuildSrc.test")
    fun testLastTopLevelSuggestionsChainCallInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/lastTopLevelSuggestionsForPartOfStatementInBuildGradleKtsInBuildSrc.test")
    fun testLastTopLevelSuggestionsForPartOfStatementInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/lastTopLevelSuggestionsInBuildGradleKtsInBuildSrc.test")
    fun testLastTopLevelSuggestionsInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/noSuggestionsInBuildGradleKtsInBuildSrc.test")
    fun testNoSuggestionsInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/suggestionsInsideLambdaInBuildGradleKtsInBuildSrc.test")
    fun testSuggestionsInsideLambdaInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/suggestionsInsideLambdaInsideLambdaCamelCaseInBuildGradleKtsInBuildSrc.test")
    fun testSuggestionsInsideLambdaInsideLambdaCamelCaseInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/topLevelDestructuringDeclarationsSuggestionsInBuildGradleKtsInBuildSrc.test")
    fun testTopLevelDestructuringDeclarationsSuggestionsInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/topLevelSuggestionsCamelCaseInBuildGradleKtsInBuildSrc.test")
    fun testTopLevelSuggestionsCamelCaseInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/topLevelSuggestionsForPartOfStatementInBuildGradleKtsInBuildSrc.test")
    fun testTopLevelSuggestionsForPartOfStatementInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    @Disabled("KTIJ-30645 K2: Gradle.kts: Completion in gradle.kts files should offer only suitable suggestions")
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("buildSrcDir/topLevelSuggestionsInBuildGradleKtsInBuildSrc.test")
    fun testTopLevelSuggestionsInBuildGradleKtsInBuildSrc(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion)
    }

    private fun verifyCompletion(gradleVersion: GradleVersion) {
        test(gradleVersion, GRADLE_KMP_KOTLIN_FIXTURE) {
            val mainFileContent = mainTestDataFile
            var mainFile = mainTestDataPsiFile
            val noExpectedSuggestions =
                InTextDirectivesUtils.isDirectiveDefined(mainFileContent.content, "// \"NO-EXPECTED-SUGGESTIONS\"")
            val expectedSuggestions =
                InTextDirectivesUtils.findListWithPrefixes(mainFileContent.content, "// \"EXPECTED-SUGGESTION\": ")
            val unexpectedSuggestions =
                InTextDirectivesUtils.findListWithPrefixes(mainFileContent.content, "// \"UNEXPECTED-SUGGESTION\": ")

            codeInsightFixture.configureFromExistingVirtualFile(mainFile.virtualFile)
            assertTrue("<caret> is not present") {
                val caretOffset = runReadAction { codeInsightFixture.caretOffset }
                caretOffset != 0
            }

            runInEdtAndWait {
                codeInsightFixture.performEditorAction(IdeActions.ACTION_CODE_COMPLETION)
                val actualLookupElements = codeInsightFixture.lookupElements
                val suggestions = actualLookupElements?.flatMap { it.allLookupStrings } ?: emptyList()
                if (suggestions.isEmpty()) {
                    assertTrue(noExpectedSuggestions, "Actual suggestion list is empty. Expected: $expectedSuggestions")
                } else {
                    for ((index, expectedSuggestion) in expectedSuggestions.withIndex()) {
                        val suggestion = suggestions.getOrNull(index)
                        assertEquals(
                            expectedSuggestion,
                            suggestion,
                            "Actual suggestion at #$index is $suggestion, expected: $expectedSuggestion\n\n" +
                                "actual suggestions: $suggestions\nexpected suggestions: $expectedSuggestions"
                        )
                    }
                    unexpectedSuggestions.forEach {
                        assertFalse(
                            suggestions.contains(it),
                            "Actual suggestions list contains unexpected suggestion: $it"
                        )
                    }
                }
            }
        }
    }
}