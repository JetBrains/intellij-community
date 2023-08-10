// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.notifications.disableNewKotlinCompilerAvailableNotification
import org.jetbrains.kotlin.idea.notification.catchNotificationText
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.AssumptionViolatedException
import org.junit.Test

class GradleMigrateTest : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    @TargetVersions("6.9+")
    fun testMigrateStdlib() {
        if (kotlinPluginVersion != KotlinGradlePluginVersions.lastStable) {
            if (IS_UNDER_TEAMCITY) return else throw AssumptionViolatedException("Ignored KGP version $kotlinPluginVersion")
        }

        val notificationText = doMigrationTest(
            beforeText = """
            buildscript {
                repositories {
                    ${GradleKotlinTestUtils.listRepositories(false, gradleVersion)}                    
                }
                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${KotlinGradlePluginVersions.V_1_7_21}"
                }
            }

            apply plugin: 'kotlin'

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:${KotlinGradlePluginVersions.V_1_7_21}"
            }
            """,
            afterText =
            """
            buildscript {
                repositories {
                    ${GradleKotlinTestUtils.listRepositories(false, gradleVersion)}                    
                }
                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${KotlinGradlePluginVersions.V_1_8_0}"
                }
            }

            apply plugin: 'kotlin'

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:${KotlinGradlePluginVersions.V_1_8_0}"
            }
            """
        )

        assertEquals(
            "Update your code to replace the use of deprecated language and library features with supported constructs<br/><br/>" +
                    "Detected migration:<br/>&nbsp;&nbsp;Language version: 1.7 to 1.8<br/>&nbsp;&nbsp;API version: 1.7 to 1.8<br/>",
            notificationText,
        )
    }

    private fun doMigrationTest(beforeText: String, afterText: String): String? = catchNotificationText(myProject) {
        createProjectSubFile("settings.gradle", "include ':app'")
        val gradleFile = createProjectSubFile("app/build.gradle", beforeText.trimIndent())

        runInEdtAndWait {
            runWriteAction {
                disableNewKotlinCompilerAvailableNotification(KotlinPluginLayout.standaloneCompilerVersion.kotlinVersion)
            }
        }

        importProject()

        val document = runReadAction {
            val gradlePsiFile = PsiManager.getInstance(myProject).findFile(gradleFile) ?: error("Can't find psi file for gradle file")
            PsiDocumentManager.getInstance(myProject).getDocument(gradlePsiFile) ?: error("Can't find document for gradle file")
        }

        runInEdtAndWait {
            runWriteAction {
                document.setText(afterText.trimIndent())
            }
        }

        importProject()
    }
}
