// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.gradle.testFramework.GradleTestFixtureBuilder
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.util.withBuildFile
import com.intellij.gradle.testFramework.util.withSettingsFile
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil.isGradleOlderThan
import com.intellij.lang.documentation.psi.createPsiDocumentationTarget
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.testFramework.findReferenceByText
import com.intellij.testFramework.utils.vfs.getPsiFile
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest

class KotlinGradleBuildNavigationTest : KotlinGradleCodeInsightTestCase() {

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testBuildGradleWithMppPlugin(gradleVersion: GradleVersion) {
        runBlocking {
            test(gradleVersion, KOTLIN_PLUGIN_FIXTURE) {
                runReadActionBlocking {
                    val buildGradle = getFile("build.gradle").getPsiFile(project)
                    val jvmElement = buildGradle.findReferenceByText("jvm").element
                    val documentationTarget = createPsiDocumentationTarget(jvmElement, jvmElement)
                    val docResult = documentationTarget.computeDocumentation() as DocumentationResult.Documentation
                    val doc = (docResult as DocumentationData).html
                    assertEquals(
                        """
                    <html>Candidates for method call <b>jvm</b> are:<br><br>&nbsp;&nbsp;<a href="psi_element://org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension#jvm()"><code><span style="color:#0000ff;">KotlinJvmTarget jvm</span><span style="">()</span></code></a><br></html>
                """.trimIndent(), doc
                    )
                }
            }
        }
    }

    companion object {

        private val KOTLIN_PLUGIN_FIXTURE = GradleTestFixtureBuilder.create("GradleBuildNavigationTest") { gradleVersion ->
            withSettingsFile(gradleVersion) {
                setProjectName("GradleBuildNavigationTest")
            }
            withBuildFile(gradleVersion) {
                if (isGradleOlderThan(gradleVersion, "9.0")) {
                    withPlugin("org.jetbrains.kotlin.multiplatform", "1.7.0")
                } else {
                    withPlugin("org.jetbrains.kotlin.multiplatform", "2.4.20")
                }
                withMavenCentral()
                withPostfix {
                    call("kotlin") {
                        call("jvm")
                    }
                }
            }
        }
    }
}