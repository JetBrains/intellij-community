// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.test.assertNotNull as kAssertNotNull



internal const val TARGET_GRADLE_VERSION = "8.13"
internal const val COMMON_MAIN = "commonMain"
internal const val ANDROID_MAIN = "androidMain"
internal const val IOS_MAIN = "iosMain"
internal val SOURCE_SETS = setOf(COMMON_MAIN, ANDROID_MAIN, IOS_MAIN)

@TestRoot("../../../community/plugins/compose/intellij.compose.ide.plugin.resources/testData")
@TestMetadata("")
abstract class ComposeResourcesTestCase : KotlinGradleImportingTestCase() {
  @Parameterized.Parameter(1)
  lateinit var sourceSetName: String

  companion object {
    @JvmStatic
    @Suppress("ACCIDENTAL_OVERRIDE")
    @Parameters(name = "{index}: source set {1} with Gradle-{0}")
    fun data(): Collection<Any> = SOURCE_SETS.map { arrayOf(TARGET_GRADLE_VERSION, it) }
  }

  protected fun <R> runWriteAction(update: () -> R): R =
    WriteCommandAction.runWriteCommandAction(myProject, Computable { update() })

  protected var _codeInsightTestFixture: CodeInsightTestFixture? = null

  protected val codeInsightTestFixture: CodeInsightTestFixture
    get() = kAssertNotNull(_codeInsightTestFixture, "_codeInsightTestFixture was not initialized")

  override fun setUpFixtures() {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
    _codeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
    codeInsightTestFixture.setUp()
    codeInsightTestFixture.testDataPath = PathManagerEx.getCommunityHomePath() + "/plugins/compose/intellij.compose.ide.plugin.resources/testData/"
  }

  override fun tearDownFixtures() {
    runAll(
      { _codeInsightTestFixture?.tearDown() },
      { _codeInsightTestFixture = null },
      { resetTestFixture() },
    )
  }
}