// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

abstract class GradleConfiguratorPlatformSpecificTest : KotlinGradleImportingTestCase() {
    class EnableFeatureSupportMultiplatform : GradleConfiguratorPlatformSpecificTest() {
        @TargetVersions("4.7+")
        @Test
        fun testEnableFeatureSupportMultiplatform() = doTestEnableFeatureSupportMultiplatform()
    }

    class EnableFeatureSupportMultiplatformWithXFlag : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
        fun testEnableFeatureSupportMultiplatformWithXFlag() = doTestEnableFeatureSupportMultiplatform()
    }

    class EnableFeatureSupportMultiplatform2 : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
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
    }

    class EnableFeatureSupportMultiplatformToExistentArguments : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
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
    }

    class EnableFeatureSupportMultiplatformKts : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
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
    }

    class AddLibraryMultiplatform : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
        fun testAddLibraryMultiplatform() = doTestAddLibrary()
    }

    class AddLibraryMultiplatformGSK : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
        fun testAddLibraryMultiplatformGSK() = doTestAddLibrary()
    }

    class AddLibraryMultiplatformGSK2 : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
        fun testAddLibraryMultiplatformGSK2() = doTestAddLibrary()
    }

    class AddLibraryMultiplatformGSK3 : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
        fun testAddLibraryMultiplatformGSK3() = doTestAddLibrary()
    }

    class AddLibraryMultiplatformGSK4 : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
        fun testAddLibraryMultiplatformGSK4() = doTestAddLibrary()
    }

    class AddLibraryMultiplatformGSK5 : GradleConfiguratorPlatformSpecificTest() {
        @Test
        @TargetVersions("4.7+")
        fun testAddLibraryMultiplatformGSK5() = doTestAddLibrary()
    }

    protected fun doTestAddLibrary() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    object : Module by myTestFixture.module {
                        override fun getName(): String = "jvmMain"
                    },
                    DependencyScope.COMPILE,
                    object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-reflect", "1.3.50", "1.3.50") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })
            }

            checkFiles(files)
        }
    }

    protected fun doTestEnableFeatureSupportMultiplatform() {
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