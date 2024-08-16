// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.SimpleProperties
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinBuildSystemFacade
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions
import org.junit.Test
import org.junit.runners.Parameterized

abstract class KotlinGradleBuildSystemFacadeTest : KotlinGradleImportingTestCase() {

    companion object {
        /*
        Running tests against a fixed version of Gradle, expecting latest Kotlin Gradle plugin
         */
        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
        fun data(): Collection<Array<Any>> = listOf(arrayOf("8.8"))
    }

    /*
    Testing projects from 'multiplatform/smoke'
     */
    class MultiplatformSmoke : KotlinGradleBuildSystemFacadeTest() {
        override fun testDataDirName(): String = "multiplatform/smoke"

        @Test
        fun testJvmAndNative() {
            configureByFiles(
                SimpleProperties(currentGradleVersion, KotlinGradlePluginVersions.latest) +
                        ("kgp_version" to KotlinGradlePluginVersions.latest.toString())
            )

            importProject()

            /* Check 'jvmMain' */
            run {
                val jvmMain = ModuleManager.getInstance(myProject).findModuleByName("project.jvmMain")
                    ?: kotlin.test.fail("Missing module jvmMain")

                val jvmMainSourceSet = KotlinBuildSystemFacade.getInstance().findSourceSet(jvmMain)
                    ?: kotlin.test.fail("Could not find Source Set: jvmMain")

                kotlin.test.assertEquals("jvmMain", jvmMainSourceSet.name)

                val jvmMainSourceDirectories = jvmMainSourceSet.sourceDirectories.filter { sourceDirectory ->
                    sourceDirectory == myProjectRoot.toNioPath().resolve("src/jvmMain/kotlin")
                }

                if (jvmMainSourceDirectories.isEmpty()) {
                    kotlin.test.fail(
                        "Expected 'src/jvmMain/kotlin' to be present in jvmMainSourceDirectories. " +
                                "Found: $jvmMainSourceDirectories"
                    )
                }

                if (jvmMainSourceDirectories.size > 1) {
                    kotlin.test.fail(
                        "Expected 'src/jvmMain/kotlin' to be present only once in jvmMainSourceDirectories. " +
                                "Found: $jvmMainSourceDirectories"
                    )
                }
            }

            /* Check 'nativeMain' */
            run {
                val nativeMain = ModuleManager.getInstance(myProject).findModuleByName("project.nativeMain")
                    ?: kotlin.test.fail("Missing module nativeMain")

                val nativeMainSourceSet = KotlinBuildSystemFacade.getInstance().findSourceSet(nativeMain)
                    ?: kotlin.test.fail("Could not find Source Set: nativeMain")

                kotlin.test.assertEquals("nativeMain", nativeMainSourceSet.name)

                val nativeMainSourceDirectories = nativeMainSourceSet.sourceDirectories.filter { sourceDirectory ->
                    sourceDirectory == myProjectRoot.toNioPath().resolve("src/nativeMain/kotlin")
                }

                if (nativeMainSourceDirectories.isEmpty()) {
                    kotlin.test.fail(
                        "Expected 'src/nativeMain/kotlin' to be present in nativeMainSourceDirectories. " +
                                "Found: $nativeMainSourceDirectories"
                    )
                }

                if (nativeMainSourceDirectories.size > 1) {
                    kotlin.test.fail(
                        "Expected 'src/nativeMain/kotlin' to be present only once in nativeMainSourceDirectories. " +
                                "Found: $nativeMainSourceDirectories"
                    )
                }
            }
        }
    }
}

