// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiFile
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptPsiFile
import org.jetbrains.kotlin.tooling.core.toKotlinVersion
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GetKotlinVersionTest : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    @TargetVersions("7.6.3+")
    fun testVersionInBuildGradleScript() {
        runInEdtAndWait {
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
        }

        importProject(true)

        assertKotlinPluginVersion(kotlinPluginVersion.version.toKotlinVersion(), getTopLevelBuildScriptPsiFile())
    }

    @Test
    @TargetVersions("7.6.3+")
    fun testSpecificKotlinVersionInBuildGradleScript() {
        runInEdtAndWait {
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
                    kotlin("jvm") version "2.3.0"
                }
                
                repositories {
                    ${repositories(true)} 
                }
                """.trimIndent()
                )
            }
        }

        importProject(true)

        assertKotlinPluginVersion(KotlinVersion(2, 3, 0), getTopLevelBuildScriptPsiFile())
    }

    @Test
    @TargetVersions("7.6.3+")
    fun testVersionInSettingsGradle() {
        runInEdtAndWait {
            ApplicationManager.getApplication().runWriteAction {
                createProjectSubFile("settings.gradle.kts").writeText(
                    """
                pluginManagement {
                    plugins {
                        kotlin("jvm") version "$kotlinPluginVersion"
                    }
                    repositories {
                        ${repositories(true)}
                    }
                }
                """.trimIndent()
                )


                createProjectSubFile("build.gradle.kts").writeText(
                    """
                plugins {
                    id("java")
                    kotlin("jvm")
                }
                
                repositories {
                    ${repositories(true)} 
                }
                """.trimIndent()
                )
            }
        }

        importProject(true)

        assertKotlinPluginVersion(kotlinPluginVersion.version.toKotlinVersion(), getTopLevelBuildScriptPsiFile())
    }

    private fun getTopLevelBuildScriptPsiFile(): PsiFile {
        val topLevelBuildScriptPsiFile = myProject.getTopLevelBuildScriptPsiFile()
        assertNotNull(topLevelBuildScriptPsiFile)
        return topLevelBuildScriptPsiFile!!
    }

    private fun assertKotlinPluginVersion(kotlinPluginVersion: KotlinVersion, buildScriptFile: PsiFile) {
        ApplicationManager.getApplication().runReadAction {
            val buildScriptManipulator = GradleBuildScriptSupport.getManipulator(buildScriptFile)
            val kotlinVersionFromManipulator = buildScriptManipulator.getKotlinVersion()

            assertEquals(kotlinPluginVersion, kotlinVersionFromManipulator?.kotlinVersion)
        }
    }

    @Test
    @TargetVersions("7.6.3+")
    fun testVersionInSubmodules() {
        runInEdtAndWait {
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
                    kotlin("multiplatform") version "2.1.0"
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
        }
        importProject(true)
        runInEdtAndWait {
            listOf("project", "project.jvmMain", "project.jvmTest", "project.linuxX64Main", "project.linuxX64Test").forEach { moduleName ->
                val module = getModule(moduleName)
                val buildScriptFile = module.getBuildScriptPsiFile()
                assertNotNull(buildScriptFile)
                assertKotlinPluginVersion(KotlinVersion(2, 1, 0), buildScriptFile!!)
            }
        }
    }

}