// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleCodeInsightBaseTest
import org.jetbrains.kotlin.idea.base.highlighting.dsl.DslStyleUtils
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions.assertTrue

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
abstract class K2GradleCodeInsightTestCase : AbstractKotlinGradleCodeInsightBaseTest() {

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
        val virtualFile = writeTextAndCommit(relativePath, expression)
        runInEdtAndWait {
            codeInsightFixture.openFileInEditor(virtualFile)
            checkNotNull(codeInsightFixture.editor) { "Fixture is not configured. Call something like configureByFile() or configureByText()" }
            val data = ExpectedHighlightingData(
                codeInsightFixture.editor.getDocument(), true, true, false, false
            )
            // manually register DSL_TYPE_SEVERITY to ignore it
            val severity = DslStyleUtils.typeById(1).getSeverity(null)
            data.registerHighlightingType(severity.name, ExpectedHighlightingData.ExpectedHighlightingSet(severity, false, false))
            data.init()
            (codeInsightFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
        }
    }
}
