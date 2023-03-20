// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.testFramework.findReferenceByText
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest

class GradleBuildNavigationTest: GradleCodeInsightTestCase() {

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testBuildGradleWithMppPlugin(gradleVersion: GradleVersion) {
        test(gradleVersion, KOTLIN_PLUGIN_FIXTURE) {
            runReadAction {
                val buildGradle = getFile("build.gradle").getPsiFile(project)
                val jvmElement = buildGradle.findReferenceByText("jvm").element
                val documentationProvider = DocumentationManager.getProviderFromElement(jvmElement)
                val doc = documentationProvider.generateDoc(jvmElement, jvmElement)
                assertEquals("""
                    <html>Candidates for method call <b>jvm</b> are:<br><br>&nbsp;&nbsp;<a href="psi_element://org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension#jvm()"><code><span style="color:#0000ff;">KotlinJvmTarget jvm</span><span style="">()</span></code></a><br></html>
                """.trimIndent(), doc)
            }
        }
    }

    companion object {

        private val KOTLIN_PLUGIN_FIXTURE = GradleTestFixtureBuilder.create("kotlin-plugin-project") { gradleVersion ->
            withSettingsFile {
                setProjectName("kotlin-plugin-project")
            }
            withBuildFile(gradleVersion) {
                withPlugin("org.jetbrains.kotlin.multiplatform", "1.7.0")
                    .withMavenCentral()
                    .addPostfix("""
                        |kotlin {
                        |    jvm()
                        |}
                    """.trimMargin())
            }
        }
    }
}