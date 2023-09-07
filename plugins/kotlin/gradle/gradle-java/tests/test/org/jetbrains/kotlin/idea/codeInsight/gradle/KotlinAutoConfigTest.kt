// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinGradleModuleConfigurator
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test


class KotlinAutoConfigTest : KotlinGradleImportingTestCase() {
    private fun findGradleModuleConfigurator(): KotlinGradleModuleConfigurator {
        return KotlinProjectConfigurator.EP_NAME.findExtensionOrFail(KotlinGradleModuleConfigurator::class.java)
    }

    override fun testDataDirName(): String = "kotlinAutoConfig"

    private fun testConfigure(moduleName: String, expectedResult: IdeKotlinVersion?) {
        runInEdtAndWait {
            val registryValue = Registry.get("kotlin.configuration.gradle.autoConfig.enabled")
            val oldValue = registryValue.asBoolean()
            registryValue.setValue(true, testRootDisposable)
            try {
                val module = runReadAction {
                    ModuleManager.getInstance(myProject).findModuleByName(moduleName)!!
                }

                val autoConfigStatus = runBlocking { findGradleModuleConfigurator().calculateAutoConfigSettings(module) }
                assertEquals(expectedResult, autoConfigStatus?.kotlinVersion)
                if (expectedResult != null) {
                    assertEquals(module, autoConfigStatus?.module)
                }
            } finally {
                registryValue.setValue(oldValue)
            }
        }
    }

    @Test
    @TargetVersions("7.6")
    fun testSingleModule() {
        importProjectFromTestData()
        testConfigure("project", IdeKotlinVersion.get("1.9.10"))
    }

    @Test
    @TargetVersions("7.6")
    fun testKotlinAlreadyConfigured() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6")
    fun testUnsupportedJvmTarget() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6")
    fun testInheritedKotlin() {
        importProjectFromTestData()
        testConfigure("project.submodule", IdeKotlinVersion.get("1.8.20"))
    }

    @Test
    @TargetVersions("7.6")
    fun testInheritedKotlinIncompatibleGradle() {
        importProjectFromTestData()
        testConfigure("project.submodule", null)
    }

    @Test
    @TargetVersions("7.6")
    fun testSubmoduleKotlin() {
        importProjectFromTestData()
        testConfigure("project", IdeKotlinVersion.get("1.8.20"))
    }

    @Test
    @TargetVersions("7.6")
    fun testMultipleSubmodulesKotlin() {
        importProjectFromTestData()
        testConfigure("project", IdeKotlinVersion.get("1.8.20"))
    }

    @Test
    @TargetVersions("7.6")
    fun testMultipleSubmodulesKotlinDeepConflict() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6")
    fun testMultipleSubmodulesKotlinDeepConflict2() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6")
    fun testMultipleSubmodulesKotlinConflict() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6")
    fun testLibrary() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6")
    fun testLibrarySubmodule() {
        importProjectFromTestData()
        testConfigure("project.submodule", IdeKotlinVersion.get("1.9.10"))
    }

    @Test
    @TargetVersions("7.6")
    fun testSiblingKotlinModule() {
        importProjectFromTestData()
        testConfigure("project.submoduleB", IdeKotlinVersion.get("1.8.21"))
    }

    @Test
    @TargetVersions("7.6")
    fun testSiblingAndParentKotlinModule() {
        importProjectFromTestData()
        testConfigure("project.submoduleB", IdeKotlinVersion.get("1.8.21"))
    }

    @Test
    @TargetVersions("7.6")
    fun testPreventBuildSrc() {
        importProjectFromTestData()
        testConfigure("project", null)
    }
}