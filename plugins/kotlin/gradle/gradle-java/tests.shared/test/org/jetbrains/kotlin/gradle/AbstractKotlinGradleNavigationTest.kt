// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertTrue

private const val EXPECTED_NAVIGATION_DIRECTIVE = "EXPECTED-NAVIGATION-SUBSTRING"

@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/navigation/")
abstract class AbstractKotlinGradleNavigationTest : AbstractGradleCodeInsightTest() {

    private val actionName: String get() = IdeActions.ACTION_GOTO_DECLARATION

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectDependency.test")
    fun testProjectDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectKmpDependency.test")
    fun testProjectKmpDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectAccessorSimpleModule.test")
    fun testProjectAccessorSimpleModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectAccessorSubSubModule.test")
    fun testProjectAccessorSubSubModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectFullAccessorSubSubModule.test")
    fun testProjectFullAccessorSubSubModule(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("projectAccessorSubModuleInTheMiddle.test")
    fun testProjectAccessorSubModuleInTheMiddle(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("librarySourceDependency.test")
    fun testLibrarySourceDependency(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("pluginPrecompiled/inGroovy.test")
    fun testPluginPrecompiledInGroovy(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("pluginPrecompiled/inKotlin.test")
    fun testPluginPrecompiledInKotlin(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("pluginPrecompiled/inKotlinWithPackage.test")
    fun testPluginPrecompiledInKotlinWithPackage(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("pluginPrecompiled/inKotlinLocatedInJavaDir.test")
    fun testPluginPrecompiledInKotlinLocatedInJavaDir(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    private fun verifyNavigationFromCaretToExpected(gradleVersion: GradleVersion) {
        val systemSettings = GradleSystemSettings.getInstance()
        systemSettings.isDownloadSources = true

        test(gradleVersion, GRADLE_KMP_KOTLIN_FIXTURE) {
            val mainFileContent = mainTestDataFile
            val mainFile = mainTestDataPsiFile
            val expectedNavigationText =
                InTextDirectivesUtils.findStringWithPrefixes(mainFileContent.content, "// \"$EXPECTED_NAVIGATION_DIRECTIVE\": ") ?: error("$EXPECTED_NAVIGATION_DIRECTIVE is not specified")

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

    companion object {
        val GRADLE_KMP_KOTLIN_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
            withSettingsFile(useKotlinDsl = true) {
                setProjectName("GradleKotlinFixture")
                include("module1", ":module1:a-module11", ":module1:a-module11:module111")
                enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            }
            withBuildFile(gradleVersion, useKotlinDsl = true) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, relativeModulePath = "buildSrc", useKotlinDsl = true) {
                withKotlinDsl()
            }
            withBuildFile(gradleVersion, relativeModulePath = "module1", useKotlinDsl = true) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, relativeModulePath = "module1/a-module11", useKotlinDsl = true) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withBuildFile(gradleVersion, relativeModulePath = "module1/a-module11/module111", useKotlinDsl = true) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withFile(
                "gradle/libs.versions.toml",
                /* language=TOML */
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
                "gradle.properties",
                """
                kotlin.code.style=official
                """.trimIndent()
            )
            withFile(
                "buildSrc/src/main/kotlin/MyTask.kt",
                """
                    
                """.trimIndent()
            )
            withDirectory("src/main/kotlin")
        }
    }

}