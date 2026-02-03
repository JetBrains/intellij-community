// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration
import org.jetbrains.kotlin.idea.highlighter.CHECK_SYMBOL_NAMES
import org.jetbrains.kotlin.idea.highlighter.checkHighlighting
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest
import java.io.File

@TestRoot("idea/tests/testData/")
@TestDataPath($$"$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/highlighting/")
abstract class AbstractKotlinGradleResilientHighlightingTest : AbstractGradleCodeInsightTest() {

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("resilient/simple.test")
    fun testSimple(gradleVersion: GradleVersion) {
        verifyHighlighting(gradleVersion)
    }

    private val outputFileExtensions: List<String> = listOfNotNull(".highlighting.k2".takeIf { useK2Plugin == true }, ".highlighting")

    private fun verifyHighlighting(gradleVersion: GradleVersion) {
        test(gradleVersion, GRADLE_KOTLIN_FIXTURE_WITH_ERROR) {
            val mainFile = mainTestDataPsiFile

            val ktsFileUnderTest = mainFile.virtualFile.toNioPath().toFile()
            val path = ktsFileUnderTest.path
            val ktsFileHighlighting = outputFileExtensions.firstNotNullOfOrNull { ext ->
                val resolveSibling = ktsFileUnderTest.resolveSibling("$path$ext")
                resolveSibling.takeIf(File::exists)
            } ?: error("highlighting file does not exist for ${ktsFileUnderTest.path}")

            val directives = KotlinTestUtils.parseDirectives(mainTestDataFile.content).also {
                it.put(CHECK_SYMBOL_NAMES, true.toString())
            }

          runInEdtAndWait {
            IgnoreTests.runTestIfNotDisabledByFileDirective(
              mainFile.virtualFile.toNioPath(),
              if (useK2Plugin == true) IgnoreTests.DIRECTIVES.IGNORE_K2 else IgnoreTests.DIRECTIVES.IGNORE_K1
            ) {
              checkHighlighting(
                mainFile,
                ktsFileHighlighting,
                directives,
                project,
                highlightWarnings = true,
                severityOption = HighlightingConfiguration.SeverityRenderingOption.ALWAYS
              )
            }
          }
        }
    }

    companion object {
        val GRADLE_KOTLIN_FIXTURE_WITH_ERROR: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("GradleKotlinFixture")
                include(":module1")
                enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                call("some-error-code")
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