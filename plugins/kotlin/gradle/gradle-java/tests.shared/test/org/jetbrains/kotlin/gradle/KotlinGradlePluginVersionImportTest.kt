// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.writeText
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.gradleJava.kotlinGradlePluginVersion
import org.jetbrains.kotlin.idea.gradleTooling.toKotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class KotlinGradlePluginVersionImportTest : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    @TargetVersions("7.6+")
    fun testJvm() {
        ApplicationManager.getApplication().runWriteAction {
            createProjectSubFile("settings.gradle.kts").writeText(
                """
                pluginManagement {
                    repositories {
                        ${repositories(true)}
                    }
                }
                """.trimIndent()
            )


            createProjectSubFile("build.gradle.kts").writeText(
                """
                plugins {
                    kotlin("jvm") version "$kotlinPluginVersion"
                }
                
                repositories {
                    ${repositories(true)}
                }
                """.trimIndent()
            )
        }

        importProject(true)
        assertEquals(kotlinPluginVersion, getModule("project.main").kotlinGradlePluginVersion?.toKotlinToolingVersion())
    }

    @Test
    @TargetVersions("7.6+")
    fun testKmp() {
        ApplicationManager.getApplication().runWriteAction {
            createProjectSubFile("settings.gradle.kts").writeText(
                """
                pluginManagement {
                    repositories {
                        ${repositories(true)}
                    }
                }
                """.trimIndent()
            )


            createProjectSubFile("build.gradle.kts").writeText(
                """
                plugins {
                    kotlin("multiplatform") version "$kotlinPluginVersion"
                }
                
                repositories {
                    ${repositories(true)}
                }
                
                kotlin {
                    jvm()
                    linuxX64()
                }
                """.trimIndent()
            )
        }
        importProject(true)

        listOf("project", "project.jvmMain", "project.jvmTest", "project.linuxX64Main", "project.linuxX64Test").forEach { moduleName ->
            assertEquals(
                "Expected $kotlinPluginVersion for module $moduleName, got ${getModule(moduleName).kotlinGradlePluginVersion}",
                kotlinPluginVersion, getModule(moduleName).kotlinGradlePluginVersion?.toKotlinToolingVersion())
        }
    }
}