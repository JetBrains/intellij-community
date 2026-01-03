// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleCodeInsightBaseTest
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
abstract class K2GradleCodeInsightTestCase : AbstractKotlinGradleCodeInsightBaseTest() {

    protected fun checkCaret(expression: String) {
        assertTrue("<caret>" in expression) { "Please define caret position in build script." }
    }

    fun testCompletionStrict(expression: String, vararg completionCandidates: String) =
        testCompletion("build.gradle.kts", expression, completionCandidates.toList(), strict = true)

    fun testCompletion(relativePath: String, expression: String, completionCandidates: List<String>, strict: Boolean) =
        testCompletion(relativePath, expression) { lookupElements ->
            assertNotNull(lookupElements) {
                "There was only one completion suggestion, so it was auto completed. Please use `testAutoCompletion()` instead."
            }

            if (strict) CollectionAssertions.assertEqualsUnordered(completionCandidates, lookupElements.map { it.lookupString })
            else CollectionAssertions.assertContainsUnordered(completionCandidates, lookupElements.map { it.lookupString })
        }

    fun testCompletion(relativePath: String, expression: String, checker: (Array<LookupElement>?) -> Unit) {
        checkCaret(expression)
        writeTextAndCommit(relativePath, expression)
        runInEdtAndWait {
            codeInsightFixture.configureFromExistingVirtualFile(getFile(relativePath))
            checker(codeInsightFixture.completeBasic())
        }
    }

    fun testAutoCompletion(before: String, after: String) = testAutoCompletion("build.gradle.kts", before, after)

    fun testAutoCompletion(relativePath: String, before: String, after: String) =
        testCompletion(relativePath, before) { lookupElements ->
            assertNull(lookupElements) { "There was no or more than one completion suggestion, so it was not auto completed." }
            codeInsightFixture.checkResult(after)
        }

    companion object {
        @JvmStatic
        protected val WITH_CUSTOM_CONFIGURATIONS_FIXTURE =
            GradleTestFixtureBuilder.create("with-custom-configurations") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("with-custom-configurations")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    withKotlinJvmPlugin()
                    withPrefix { code("val customConf by configurations.creating {}") }
                    withPrefix { code("val customSourceSet by sourceSets.creating {}") }
                }
            }
    }
}
