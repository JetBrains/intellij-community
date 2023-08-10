// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.gradle

import com.intellij.openapi.project.modules
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File


class HasKotlinPluginEnabledGradleTest : KotlinGradleImportingTestCase() {

    override fun testDataDirectory(): File {
        val baseDir = IDEA_TEST_DATA_DIR.resolve("configuration/hasKotlinPluginEnabled/${testDataDirName()}")
        return File(baseDir, getTestName(true).substringBefore("_").substringBefore(" "))
    }

    override fun setUp() {
        super.setUp()
        configureByFiles()
        importProject()
    }

    @Test
    @TargetVersions("7.0+")
    fun testSingleModuleKotlinGradle() {
        // main, test
        val modulesWithKotlin = myProject.modules.filter { it.hasKotlinPluginEnabled() }
        TestCase.assertEquals(modulesWithKotlin.size, 2)
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.main" })
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.test" })
    }

    @Test
    @TargetVersions("7.0+")
    fun testTransitiveDependencyGradle() {
        TestCase.assertTrue(myProject.modules.none { it.hasKotlinPluginEnabled() })
    }

    @Test
    @TargetVersions("7.0+")
    fun testOnlyChildModuleKotlinGradle() {
        // submodule.main, submodule.test
        val modulesWithKotlin = myProject.modules.filter { it.hasKotlinPluginEnabled() }
        TestCase.assertEquals(modulesWithKotlin.size, 2)
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.submodule.main" })
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.submodule.test" })
    }

    @Test
    @TargetVersions("7.0+")
    fun testOnlyParentModuleKotlinGradle() {
        // main, test
        val modulesWithKotlin = myProject.modules.filter { it.hasKotlinPluginEnabled() }
        TestCase.assertEquals(modulesWithKotlin.size, 2)
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.main" })
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.test" })
    }

    @Test
    @TargetVersions("7.0+")
    fun testBothParentAndChildModuleKotlinGradle() {
        // submodule.main, submodule.test
        val modulesWithKotlin = myProject.modules.filter { it.hasKotlinPluginEnabled() }
        TestCase.assertEquals(modulesWithKotlin.size, 4)
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.main" })
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.test" })
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.submodule.main" })
        TestCase.assertTrue(modulesWithKotlin.any { it.name == "project.submodule.test" })
    }
}