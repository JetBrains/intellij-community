// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.testFramework

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.gradle.testFramework.util.getProjectFile
import com.intellij.gradle.testFramework.util.writeTextAndCommit
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.highlighting.dsl.DslStyleUtils
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME
import kotlin.time.Duration.Companion.minutes

/**
 * Writes [expression] into the build script of the project behind this fixture and checks its highlighting
 * against the `<weak_warning>`, `<warning>` and `<error>` markup of [expression].
 *
 * This is the fixture-based counterpart of `K2GradleCodeInsightTestCase.testHighlighting`, meant to be used with
 * [com.intellij.gradle.testFramework.fixtures.immutableProjectFixture] and
 * [com.intellij.gradle.testFramework.fixtures.gradleCodeInsightFixture].
 */
fun JavaCodeInsightTestFixture.checkGradleScriptHighlighting(
  expression: String,
  relativePath: String = KOTLIN_DSL_SCRIPT_NAME,
) {
  project.writeTextAndCommit(relativePath, expression)
  timeoutRunBlocking(timeout = 2.minutes, context = Dispatchers.EDT) {
    openFileInEditor(project.getProjectFile(relativePath))
    val editor = checkNotNull(editor) { "Cannot open $relativePath in the editor" }
    val data = ExpectedHighlightingData(editor.document, true, true, false, false)
    // Kotlin DSL highlighting has a severity of its own, which isn't a part of what these tests check.
    val dslSeverity = DslStyleUtils.typeById(1).getSeverity(null)
    data.registerHighlightingType(dslSeverity.name, ExpectedHighlightingData.ExpectedHighlightingSet(dslSeverity, false, false))
    data.init()
    (this@checkGradleScriptHighlighting as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
  }
}

/**
 * Writes [before] into the build script at [relativePath], applies the single intention that starts with
 * [intentionPrefix] at the `<caret>` of [before], and checks that the script is then equal to [after].
 *
 * The immutable project reverts the changed script after the test.
 */
fun JavaCodeInsightTestFixture.checkGradleScriptIntention(
  before: String,
  after: String,
  intentionPrefix: String,
  relativePath: String = KOTLIN_DSL_SCRIPT_NAME,
) {
  configureGradleScript(before, relativePath) {
    val intention = filterAvailableIntentions(intentionPrefix).single()
    launchAction(intention)
    checkResult(after)
  }
}

/**
 * Writes [before] into the build script at [relativePath] and checks that no intention that starts with
 * [intentionPrefix] is available at the `<caret>` of [before].
 */
fun JavaCodeInsightTestFixture.checkNoGradleScriptIntentions(
  before: String,
  intentionPrefix: String,
  relativePath: String = KOTLIN_DSL_SCRIPT_NAME,
) {
  configureGradleScript(before, relativePath) {
    assertThat(filterAvailableIntentions(intentionPrefix)).isEmpty()
  }
}

/**
 * Writes [text] into the build script at [relativePath], opens it in the editor, and runs [action] on the EDT.
 * [text] must contain a `<caret>`.
 */
fun JavaCodeInsightTestFixture.configureGradleScript(
  text: String,
  relativePath: String = KOTLIN_DSL_SCRIPT_NAME,
  action: JavaCodeInsightTestFixture.() -> Unit = {},
) {
  assertThat(text).withFailMessage("Define the caret position in $relativePath").contains("<caret>")
  project.writeTextAndCommit(relativePath, text)
  timeoutRunBlocking(timeout = 2.minutes, context = Dispatchers.EDT) {
    configureFromExistingVirtualFile(project.getProjectFile(relativePath))
    action()
  }
}

/**
 * Returns true when the Kotlin DSL of [gradleVersion] does not mark the deprecated Gradle API with an annotation.
 * Kotlin then highlights the deprecated API calls of a test script.
 */
fun isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion: GradleVersion): Boolean =
  GradleVersionUtil.isGradleAtLeast(gradleVersion, "9.7")

/**
 * Hides the highlighting that Kotlin itself produces, leaving only the inspections a test enabled.
 */
fun JavaCodeInsightTestFixture.disableBuiltInKotlinHighlighters() {
  maskExtensions<HighlightVisitor>(HighlightVisitor.EP_HIGHLIGHT_VISITOR, emptyList(), projectDisposable, false, project)
}
