// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.AutoConfigurationSettings
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinGradleModuleConfigurator
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test


class KotlinGradleAutoConfigTest : KotlinGradleImportingTestCase() {
    private fun findGradleModuleConfigurator(): KotlinGradleModuleConfigurator {
        return KotlinProjectConfigurator.EP_NAME.findExtensionOrFail(KotlinGradleModuleConfigurator::class.java)
    }

    override fun testDataDirName(): String = "kotlinAutoConfig"

    private fun testConfigure(moduleName: String, expectedSuccess: Boolean): AutoConfigurationSettings? {
        return runInEdtAndGet {
            val registryValue = Registry.get("kotlin.configuration.gradle.autoConfig.enabled")
            val oldValue = registryValue.asBoolean()
            registryValue.setValue(true, testRootDisposable)
            try {
                val module = runReadAction {
                    ModuleManager.getInstance(myProject).findModuleByName(moduleName)!!
                }

                val settings = runBlocking { findGradleModuleConfigurator().calculateAutoConfigSettings(module) }
                if (expectedSuccess) {
                    assertEquals(module, settings?.module)
                } else {
                    assertNull(settings)
                }
                settings
            } finally {
                registryValue.setValue(oldValue)
            }
        }
    }

    private fun testConfigure(moduleName: String, expectedResult: IdeKotlinVersion?) {
        val autoConfigStatus = testConfigure(moduleName, expectedResult != null)
        assertEquals(expectedResult, autoConfigStatus?.kotlinVersion)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testSingleModule() {
        importProjectFromTestData()
        val settings = testConfigure("project", true)
        assertNotNull(settings)
        // Should always be the latest version, but we actually only care that some version is chosen
        assertTrue(settings!!.kotlinVersion.compare("1.9.20") > 0)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testKotlinAlreadyConfigured() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testUnsupportedJvmTarget() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testInheritedKotlin() {
        importProjectFromTestData()
        testConfigure("project.submodule", IdeKotlinVersion.get("1.8.20"))
    }

    @Test
    @TargetVersions("7.6.x")
    fun testInheritedKotlinIncompatibleGradle() {
        importProjectFromTestData()
        testConfigure("project.submodule", null)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testSubmoduleKotlin() {
        importProjectFromTestData()
        testConfigure("project", IdeKotlinVersion.get("1.8.20"))
    }

    @Test
    @TargetVersions("7.6.x")
    fun testMultipleSubmodulesKotlin() {
        importProjectFromTestData()
        testConfigure("project", IdeKotlinVersion.get("1.8.20"))
    }

    @Test
    @TargetVersions("7.6.x")
    fun testMultipleSubmodulesKotlinDeepConflict() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testMultipleSubmodulesKotlinDeepConflict2() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testMultipleSubmodulesKotlinConflict() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testLibrary() {
        importProjectFromTestData()
        testConfigure("project", null)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testLibrarySubmodule() {
        importProjectFromTestData()
        val settings = testConfigure("project.submodule", true)
        assertNotNull(settings)
        // Should always be the latest version, but we actually only care that some version is chosen
        assertTrue(settings!!.kotlinVersion.compare("1.9.20") > 0)
    }

    @Test
    @TargetVersions("7.6.x")
    fun testSiblingKotlinModule() {
        importProjectFromTestData()
        testConfigure("project.submoduleB", IdeKotlinVersion.get("1.8.21"))
    }

    @Test
    @TargetVersions("7.6.x")
    fun testSiblingAndParentKotlinModule() {
        importProjectFromTestData()
        testConfigure("project.submoduleB", IdeKotlinVersion.get("1.8.21"))
    }

    @Test
    @TargetVersions("7.6.x")
    fun testPreventBuildSrc() {
        importProjectFromTestData()
        testConfigure("project", null)
    }
}