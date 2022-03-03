// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.completion.test.assertInstanceOf
import org.jetbrains.kotlin.idea.gradleJava.scripting.LoadConfigurationAction
import org.jetbrains.kotlin.idea.gradleJava.scripting.importing.KotlinDslScriptModel
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRoot
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.Imported
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.New
import org.jetbrains.kotlin.idea.gradleJava.scripting.runPartialGradleImport
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.text.Typography.dollar

abstract class PartialGradleImportTest : MultiplePluginVersionGradleImportingTestCase() {

    class Regular : PartialGradleImportTest() {
        /**
         * Regular Gradle sync / import should not run in 'classpath' mode to prevent swallowing issues described in:
         * Only when running the special [LoadConfigurationAction] this special mode shall be used.
         * https://youtrack.jetbrains.com/issue/KT-48823
         * https://youtrack.jetbrains.com/issue/KTIJ-19823
         */
        @Test
        /*
        Actually, running on just one combination would be fine as well. This behaviour should not depend on Gradle or Plugin version
        Right now, there is no API to run such independent tests.
        This test can be considered fast enough, to delay efforts for such an API
        */
        @PluginTargetVersions(gradleVersion = "6.0+", pluginVersion = "1.5.30+")
        fun `test 'regular' sync is not running in 'lenient' or 'classpath' mode`() {
            createSimpleSettingsGradleKtsProjectSubFile()
            createProjectSubFile(
                "build.gradle.kts",
                """
                    import org.gradle.kotlin.dsl.provider.inLenientMode
                    plugins { kotlin("multiplatform") }
                    error("Exception in buildscript. inLenientMode()=$dollar{inLenientMode()}")
                """.trimIndent(),
            )

            val errorMessage = assertFails { importProject(true) }.message.orEmpty()
            assertTrue(
                errorMessage.contains("Exception in buildscript. inLenientMode()=false"),
                "Expected 'Exception in buildscript. inLenientMode()=false' error message\n" +
                        "Found: $errorMessage"
            )
        }
    }

    class RunPartialGradleImport7 : PartialGradleImportTest() {
        @Test
        @PluginTargetVersions(gradleVersion = "6.0+", pluginVersion = "1.6.0-SNAPSHOT+")
        fun `test 'runPartialGradleImport' is running in 'lenient' or 'classpath' mode`() {
            /*
            Setup simple Gradle project inline:
            Will apply Kotlin Multiplatform
            Will throw error during buildscript evaluation reporting 'inLenientMode'
             */
            createSimpleSettingsGradleKtsProjectSubFile()
            createProjectSubFile(
                "build.gradle.kts",
                """
                    import org.gradle.kotlin.dsl.provider.inLenientMode
                    plugins { kotlin("multiplatform") }
                    error("Exception in buildscript. inLenientMode()=$dollar{inLenientMode()}")
                """.trimIndent(),
            )

            /*
            Link & only partially import Gradle project
             */
            linkProject()
            runPartialGradleImport(
                myProject, assertSingleGradleBuildRoot().also { root ->
                    when (root) {
                        is Imported -> assertTrue(root.data.models.isEmpty(), "Expected no models imported yet")
                        is New -> Unit
                        else -> fail("Unexpected root type: ${root.javaClass.simpleName}")
                    }
                }
            )

            /*
            Expect successfully 'imported' the project and proper error message can be found in
            imported models
             */
            val imported = assertSingleGradleBuildRoot().assertInstanceOf<Imported>()
            assertTrue(
                imported.data.models.flatMap { it.messages }.any { message ->
                    message.severity == KotlinDslScriptModel.Severity.ERROR &&
                            "Exception in buildscript. inLenientMode()=true" in message.details
                }, "Expected 'Exception in buildscript. inLenientMode()=true' in any imported messages. " +
                        "Found: ${imported.data.models.flatMap { it.messages }.map { it.details }}"
            )

            /*
            Expect that at least any classPath entry mentions 'kotlin', since
            the Kotlin Gradle plugin was applied
             */
            assertTrue(
                imported.data.models.flatMap { it.classPath }.any { classPath -> "kotlin" in classPath.lowercase() },
                "Expected 'kotlin' mentioned in imported classPath. Found ${imported.data.models.flatMap { it.classPath }.toSet()}"
            )
        }
    }

    protected fun assertSingleGradleBuildRoot(): GradleBuildRoot {
        val gradleBuildRoots = GradleBuildRootsManager.getInstance(myProject)?.getAllRoots() ?: error("Failed to get GradleBuildRoots")
        assertEquals(1, gradleBuildRoots.size, "Expected exactly one GradleBuildRoot. Found $gradleBuildRoots")
        return gradleBuildRoots.single()
    }

    protected fun createSimpleSettingsGradleKtsProjectSubFile() {
        createProjectSubFile(
            "settings.gradle.kts", """
                pluginManagement {
                    repositories {
                        ${repositories(true)}
                    }
                    plugins {
                        kotlin("multiplatform") version "$kotlinPluginVersionString"
                    }
                }
            """.trimIndent()
        )
    }
}
