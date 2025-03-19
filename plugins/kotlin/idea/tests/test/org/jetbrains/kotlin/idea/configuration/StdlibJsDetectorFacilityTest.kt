// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptStdlibDetectorFacility
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class StdlibJsDetectorFacilityTest {
    companion object {
        // the project is reused
        val project: TestFixture<Project> = projectFixture()
    }

    // the module is recreated after each test to clean up module libraries
    val module: TestFixture<Module> = project.moduleFixture()

    @Test
    fun testJsStdlib_1_0_7() {
        doTest("org.jetbrains.kotlin:kotlin-js-library:1.0.7", isJsStdlib = true)
    }

    @Test
    fun testJsStdlib_1_1_0() {
        doTest("org.jetbrains.kotlin:kotlin-stdlib-js:1.1.0", isJsStdlib = true)
    }

    @Test
    fun testJsStdlib_1_3_0() {
        doTest("org.jetbrains.kotlin:kotlin-stdlib-js:1.3.0", isJsStdlib = true)
    }

    @Test
    fun testJsStdlib_1_6_20() {
        doTest("org.jetbrains.kotlin:kotlin-stdlib-js:1.6.20", isJsStdlib = true)
    }

    @Test
    fun testJsStdlib_1_9_24() {
        doTest("org.jetbrains.kotlin:kotlin-stdlib-js:1.9.24", isJsStdlib = true)
    }

    @Test
    fun testJsStdlib_2_0_0() {
        doTest("org.jetbrains.kotlin:kotlin-stdlib-js:2.0.0", isJsStdlib = true, packaging = "klib")
    }

    @Test
    fun testNonStdlib() {
        doTest("org.jetbrains:annotations:24.0.1", isJsStdlib = false)
    }

    @Test
    fun testNonJsStdlib() {
        doTest("org.jetbrains.kotlin:kotlin-stdlib:1.9.24", isJsStdlib = false)
    }

    private fun doTest(coordinates: String, isJsStdlib: Boolean, packaging: String = "jar") {
        ModuleRootModificationUtil.updateModel(module.get()) { modifiableModel ->
            MavenDependencyUtil.addFromMaven(
                modifiableModel, coordinates, /* includeTransitiveDependencies = */ false,
                DependencyScope.COMPILE, /* additionalRepositories = */ emptyList(), packaging,
            )
        }
        val libraries = mutableListOf<Library>()
        OrderEnumerator.orderEntries(module.get()).forEachLibrary { library ->
            libraries.add(library)
            true
        }

        val theLibrary = libraries.singleOrNull()
        Assertions.assertNotNull(theLibrary, "Expected a single library, got: $libraries")
        KotlinJavaScriptStdlibDetectorFacility.isStdlib(module.get().project, library = theLibrary!!)
        Assertions.assertEquals(
            isJsStdlib, KotlinJavaScriptStdlibDetectorFacility.isStdlib(module.get().project, library = theLibrary),
            "Expected $theLibrary to be${" not".takeIf { !isJsStdlib }.orEmpty()} detected as stdlib",
        )

        val stdlibJar = KotlinJavaScriptStdlibDetectorFacility.getStdlibJar(theLibrary.getFiles(OrderRootType.CLASSES).toList())
        if (isJsStdlib) Assertions.assertNotNull(stdlibJar) else Assertions.assertNull(stdlibJar)
    }
}
