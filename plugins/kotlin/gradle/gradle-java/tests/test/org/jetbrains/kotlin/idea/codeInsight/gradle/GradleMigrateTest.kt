// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.notification.catchNotificationText
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleMigrateTest : GradleImportingTestCase() {
    @Test
    @TargetVersions("6.9")
    fun testMigrateStdlib() {
        val notificationText = doMigrationTest(
            beforeText = """
            buildscript {
                repositories {
                    ${GradleKotlinTestUtils.listRepositories(false, gradleVersion)}                    
                }
                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${GradleKotlinTestUtils.TestedKotlinGradlePluginVersions.V_1_5_31}"
                }
            }

            apply plugin: 'kotlin'

            dependencies {
                compile "org.jetbrains.kotlin:kotlin-stdlib:${GradleKotlinTestUtils.TestedKotlinGradlePluginVersions.V_1_5_31}"
            }
            """,
            afterText =
            """
            buildscript {
                repositories {
                    ${GradleKotlinTestUtils.listRepositories(false, gradleVersion)}                    
                }
                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${GradleKotlinTestUtils.TestedKotlinGradlePluginVersions.V_1_6_10}"
                }
            }

            apply plugin: 'kotlin'

            dependencies {
                compile "org.jetbrains.kotlin:kotlin-stdlib:${GradleKotlinTestUtils.TestedKotlinGradlePluginVersions.V_1_6_10}"
            }
            """
        )

        assertEquals(
            "Migrations for Kotlin code are available<br/><br/>Detected migration:<br/>&nbsp;&nbsp;Language version: 1.5 -> 1.6<br/>&nbsp;&nbsp;API version: 1.5 -> 1.6<br/>",
            notificationText,
        )
    }

    private fun doMigrationTest(beforeText: String, afterText: String): String? = catchNotificationText(myProject) {
        createProjectSubFile("settings.gradle", "include ':app'")
        val gradleFile = createProjectSubFile("app/build.gradle", beforeText.trimIndent())
        importProject()

        val document = runReadAction {
            val gradlePsiFile = PsiManager.getInstance(myProject).findFile(gradleFile) ?: error("Can't find psi file for gradle file")
            PsiDocumentManager.getInstance(myProject).getDocument(gradlePsiFile) ?: error("Can't find document for gradle file")
        }

        runInEdtAndWait { runWriteAction { document.setText(afterText.trimIndent()) } }
        importProject()
    }
}