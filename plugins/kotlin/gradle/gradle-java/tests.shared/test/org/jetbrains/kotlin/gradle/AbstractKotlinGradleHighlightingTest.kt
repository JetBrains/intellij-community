// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KOTLIN_FIXTURE
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration.SeverityRenderingOption
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.highlighter.CHECK_SYMBOL_NAMES
import org.jetbrains.kotlin.idea.highlighter.checkHighlighting
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.parseDirectives
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest
import java.io.File

@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/highlighting/")
abstract class AbstractKotlinGradleHighlightingTest : AbstractGradleCodeInsightTest() {

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("simple.test")
    fun testSimple(gradleVersion: GradleVersion) {
        verifyHighlighting(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("withSdkAndScriptClasses.test")
    fun testWithSdkAndScriptClasses(gradleVersion: GradleVersion) {
        verifyHighlighting(gradleVersion)
    }

    private val outputFileExtensions: List<String> = listOfNotNull(".highlighting.k2".takeIf { useK2Plugin == true }, ".highlighting")

    private fun verifyHighlighting(gradleVersion: GradleVersion) {
        test(gradleVersion, GRADLE_KOTLIN_FIXTURE) {
            val mainFile = mainTestDataPsiFile

            runInEdtAndWait {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(mainFile)
            }

            val ktsFileUnderTest = mainFile.virtualFile.toNioPath().toFile()
            val path = ktsFileUnderTest.path
            val ktsFileHighlighting = outputFileExtensions.mapNotNull<String, File> { ext ->
                val resolveSibling = ktsFileUnderTest.resolveSibling("$path$ext")
                resolveSibling.takeIf(File::exists)
            }.firstOrNull() ?: error("highlighting file does not exist for ${ktsFileUnderTest.path}")

            val directives = parseDirectives(mainTestDataFile.content).also {
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
                        severityOption = SeverityRenderingOption.ALWAYS
                    )
                }
            }
        }
    }

}