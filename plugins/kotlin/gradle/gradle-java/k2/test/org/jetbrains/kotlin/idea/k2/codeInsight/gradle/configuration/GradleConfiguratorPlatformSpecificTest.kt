// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.configuration

import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleConfiguratorPlatformSpecificTest3 : KotlinGradleImportingTestCase() {

    @TargetVersions("5.6+")
    @Test
    fun testEnableFeatureSupportMultiplatform() = doTestEnableFeatureSupportMultiplatform()

    @Test
    @TargetVersions("5.6+")
    fun testEnableFeatureSupportMultiplatformWithXFlag() = doTestEnableFeatureSupportMultiplatform()

    @Test
    @TargetVersions("5.6+")
    fun testEnableFeatureSupportMultiplatform2() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                )
            }

            checkFiles(files)
        }
    }

    @Test
    @TargetVersions("5.6+")
    fun testEnableFeatureSupportMultiplatformToExistentArguments() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                )
            }

            checkFiles(files)
        }
    }

    @Test
    @TargetVersions("6.8.3+")
    fun testEnableFeatureSupportMultiplatformKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                )
            }

            checkFiles(files)
        }
    }

    @Test
    @TargetVersions("5.6+")
    fun testAddLibraryMultiplatform() = doTestAddLibrary()

    @Test
    @TargetVersions("5.6+")
    fun testAddLibraryMultiplatformGSK() = doTestAddLibrary()

    @Test
    @TargetVersions("5.6+")
    fun testAddLibraryMultiplatformGSK2() = doTestAddLibrary()

    @Test
    @TargetVersions("5.6+")
    fun testAddLibraryMultiplatformGSK3() = doTestAddLibrary()

    @Test
    @TargetVersions("5.6+")
    fun testAddLibraryMultiplatformGSK4() = doTestAddLibrary()

    @Test
    @TargetVersions("5.6+")
    fun testAddLibraryMultiplatformGSK5() = doTestAddLibrary()

    private fun doTestAddLibrary() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                val module = myTestFixture.project.modules.find { it.name == "project.jvmMain" }
                requireNotNull(module) { "Module 'project.jvmMain' not found" }
                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    module,
                    DependencyScope.COMPILE,
                    object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-reflect", "1.3.50", "1.3.50") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })
            }

            checkFiles(files)
        }
    }

    private fun doTestEnableFeatureSupportMultiplatform() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                )
            }

            checkFiles(files)
        }
    }

    override fun testDataDirName(): String = "configurator"
}