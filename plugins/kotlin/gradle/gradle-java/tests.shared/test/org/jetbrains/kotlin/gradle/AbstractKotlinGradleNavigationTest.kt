// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runReadAction
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EXPECTED_NAVIGATION_DIRECTIVE = "EXPECTED-NAVIGATION-SUBSTRING"

abstract class AbstractKotlinGradleNavigationTest : AbstractGradleCodeInsightTest() {
    private val actionName: String get() = IdeActions.ACTION_GOTO_DECLARATION

    abstract val myFixture: GradleTestFixtureBuilder

    protected fun verifyNavigationFromCaretToExpected(gradleVersion: GradleVersion) {
        val systemSettings = GradleSystemSettings.getInstance()
        systemSettings.isDownloadSources = true

        test(gradleVersion, myFixture) {
            val mainFileContent = mainTestDataFile
            val mainFile = mainTestDataPsiFile
            val expectedNavigationText =
                InTextDirectivesUtils.findStringWithPrefixes(mainFileContent.content, "// \"$EXPECTED_NAVIGATION_DIRECTIVE\": ")
                    ?: error("$EXPECTED_NAVIGATION_DIRECTIVE is not specified")

            codeInsightFixture.configureFromExistingVirtualFile(mainFile.virtualFile)
            assertTrue("<caret> is not present") {
                val caretOffset = runReadAction { codeInsightFixture.caretOffset }
                caretOffset != 0
            }
            codeInsightFixture.performEditorAction(actionName)

            val text = document.text
            IgnoreTests.runTestIfNotDisabledByFileDirective(
                mainFile.virtualFile.toNioPath(),
                if (useK2Plugin == true) IgnoreTests.DIRECTIVES.IGNORE_K2 else IgnoreTests.DIRECTIVES.IGNORE_K1
            ) {
                assertTrue("Actual text:\n\n$text") {
                    !text.contains(EXPECTED_NAVIGATION_DIRECTIVE) && text.contains(expectedNavigationText)
                }
            }
        }
    }

    protected fun verifyFileShouldStayTheSame(gradleVersion: GradleVersion, fixture: GradleTestFixtureBuilder = myFixture) {
        val systemSettings = GradleSystemSettings.getInstance()
        systemSettings.isDownloadSources = true

        test(gradleVersion, fixture) {
            val mainFile = mainTestDataPsiFile

            codeInsightFixture.configureFromExistingVirtualFile(mainFile.virtualFile)

            val textBefore = document.text
            assertTrue("<caret> is not present") {
                val caretOffset = runReadAction { codeInsightFixture.caretOffset }
                caretOffset != 0
            }
            codeInsightFixture.performEditorAction(actionName)

            val textAfter = document.text
            IgnoreTests.runTestIfNotDisabledByFileDirective(
                mainFile.virtualFile.toNioPath(),
                if (useK2Plugin == true) IgnoreTests.DIRECTIVES.IGNORE_K2 else IgnoreTests.DIRECTIVES.IGNORE_K1
            ) {
                assertEquals(textBefore, textAfter, "Navigation should not work")
            }
        }
    }

    companion object {
        val GRADLE_KMP_KOTLIN_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("GradleKotlinFixture")
                include("module1", ":module1:a-module11", ":module1:a-module11:module111")
                enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, "buildSrc", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
            }
            withBuildFile(gradleVersion, "module1", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, "module1/a-module11", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, "module1/a-module11/module111", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withFile(
                "gradle/libs.versions.toml",/* language=TOML */
                """
                [libraries]
                some_test-library = { module = "org.junit.jupiter:junit-jupiter" }
                [plugins]
                kotlin = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin"}
                [versions]
                test_library-version = "1.0"
                kotlin = "1.9.24"
                """.trimIndent()
            )
            withFile(
                "gradle.properties", """
                kotlin.code.style=official
                """.trimIndent()
            )
            withFile(
                "buildSrc/src/main/kotlin/MyTask.kt", """
                    
                """.trimIndent()
            )
            withDirectory("src/main/kotlin")
        }

        val GRADLE_KOTLIN_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("GradleKotlinFixture")
                include(":module1")
                enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, "module1", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
                withMavenCentral()
            }
            withFile(
                "gradle.properties", """
                kotlin.code.style=official
                """.trimIndent()
            )
        }
    }
}