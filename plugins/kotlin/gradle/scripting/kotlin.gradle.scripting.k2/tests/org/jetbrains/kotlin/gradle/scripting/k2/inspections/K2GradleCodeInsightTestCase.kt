// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightBaseTestCase
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinIsSupported
import org.jetbrains.plugins.groovy.util.ExpressionTest
import org.junit.jupiter.api.Assertions.assertTrue

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
abstract class K2GradleCodeInsightTestCase : GradleCodeInsightBaseTestCase(), ExpressionTest {

    override fun setUp() {
        assumeThatKotlinIsSupported(gradleVersion)
        super.setUp()
    }

    override fun tearDown() {
        runAll(
            { KotlinSdkType.removeKotlinSdkInTests() },
            { super.tearDown() }
        )
    }

    protected fun checkCaret(expression: String) {
        assertTrue("<caret>" in expression, "Please define caret position in build script.")
    }

    fun testIntention(before: String, after: String, intentionPrefix: String) {
        testIntention("build.gradle.kts", before, after, intentionPrefix)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun testIntention(fileName: String, before: String, after: String, intentionPrefix: String) {
        checkCaret(before)
        writeTextAndCommit(fileName, before)
        runInEdtAndWait {
            codeInsightFixture.configureFromExistingVirtualFile(getFile(fileName))
            val intention = codeInsightFixture.filterAvailableIntentions(intentionPrefix).single()
            codeInsightFixture.launchAction(intention)
            codeInsightFixture.checkResult(after)
            gradleFixture.fileFixture.rollback(fileName)
        }
    }

    fun testHighlighting(expression: String) = testHighlighting("build.gradle.kts", expression)
    fun testHighlighting(relativePath: String, expression: String) {
        writeTextAndCommit(relativePath, expression)
        runInEdtAndWait {
            codeInsightFixture.configureFromExistingVirtualFile(getFile(relativePath))
            codeInsightFixture.checkHighlighting(true, false, true, true)
        }
    }
}
