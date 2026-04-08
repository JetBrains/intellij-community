// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.openapi.module.Module
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.junit.Ignore
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class Java9MultiModuleHighlightingTest : AbstractMultiModuleTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiModuleHighlighting/java9")

    private fun module(name: String): Module = super.module(name, false) { IdeaTestUtil.getMockJdk9() }

    @Ignore("KT-85271")
    fun _testSimpleModuleExportsPackage() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    @Ignore("KT-85271")
    fun _testSimpleLibraryExportsPackage() {
        val sources = listOf(File(getTestDataPath(), getTestName(true) + "/library"))
        // -Xallow-kotlin-package to avoid "require kotlin.stdlib" in module-info.java
        val extraOptions = listOf(
            "-jdk-home", KotlinTestUtils.getCurrentProcessJdkHome().path,
            "-Xallow-kotlin-package",
            "-Xabi-stability=stable"
        )
        val libraryJar = KotlinCompilerStandalone(
            sources,
            platform = KotlinCompilerStandalone.Platform.Jvm(JvmTarget.JVM_9),
            options = extraOptions
        ).compile()

        module("main").addLibrary(libraryJar, "library")
        checkHighlightingInProject()
    }

    @Ignore("KT-85271")
    fun _testNamedDependsOnUnnamed() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    fun testUnnamedDependsOnNamed() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    @Ignore("KT-85271")
    fun _testDeclarationKinds() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    @Ignore("KT-85271")
    fun _testExportsTo() {
        val d = module("dependency")
        module("first").addDependency(d)
        module("second").addDependency(d)
        module("unnamed").addDependency(d)
        checkHighlightingInProject()
    }

    @Ignore("KT-85271")
    fun _testExportedPackageIsInaccessibleWithoutRequires() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    @Ignore("KT-85271")
    fun _testTypealiasToUnexported() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    fun testCyclicDependency() {
        val a = module("moduleA")
        val b = module("moduleB")
        val c = module("moduleC")
        module("main").addDependency(a).addDependency(b).addDependency(c)
        checkHighlightingInProject()
    }

    fun testAutomaticModuleFromManifest() {
        val d = module("dependency")
        module("automaticByManifest").addDependency(d)
        checkHighlightingInProject()
    }

    fun testJavaBaseIsIncludedByDefault() {
        module("main")
        checkHighlightingInProject()
    }

    private fun checkHighlightingInProject() {
        checkFiles({ project.allKotlinFiles() }) {
            checkHighlighting(myEditor, true, false)
        }
    }
}
