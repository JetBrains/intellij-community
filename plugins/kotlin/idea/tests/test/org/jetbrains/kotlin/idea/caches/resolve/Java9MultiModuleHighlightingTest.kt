// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.module.Module
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class Java9MultiModuleHighlightingTest : AbstractMultiModuleHighlightingTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiModuleHighlighting/java9")

    private fun module(name: String): Module = super.module(name, false) { IdeaTestUtil.getMockJdk9() }

    fun testSimpleModuleExportsPackage() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    fun testSimpleLibraryExportsPackage() {
        val sources = listOf(File(testDataPath, getTestName(true) + "/library"))
        // -Xallow-kotlin-package to avoid "require kotlin.stdlib" in module-info.java
        val extraOptions = listOf("-jdk-home", KotlinTestUtils.getCurrentProcessJdkHome().path, "-Xallow-kotlin-package")
        val libraryJar = KotlinCompilerStandalone(
            sources,
            platform = KotlinCompilerStandalone.Platform.Jvm(JvmTarget.JVM_9),
            options = extraOptions
        ).compile()

        module("main").addLibrary(libraryJar, "library")
        checkHighlightingInProject()
    }

    fun testNamedDependsOnUnnamed() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    fun testUnnamedDependsOnNamed() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    fun testDeclarationKinds() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    fun testExportsTo() {
        val d = module("dependency")
        module("first").addDependency(d)
        module("second").addDependency(d)
        module("unnamed").addDependency(d)
        checkHighlightingInProject()
    }

    fun testExportedPackageIsInaccessibleWithoutRequires() {
        module("main").addDependency(module("dependency"))
        checkHighlightingInProject()
    }

    fun testTypealiasToUnexported() {
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
}
