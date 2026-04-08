// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.decompiler.navigation

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.navigation.AbstractNavigationToSourceOrDecompiledTest
import org.jetbrains.kotlin.idea.test.navigation.AbstractNavigationWithMultipleLibrariesTest
import org.jetbrains.kotlin.idea.test.navigation.NavigationChecker
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.projectLibrary
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class NavigationWithMultipleCustomLibrariesTest : AbstractNavigationToSourceOrDecompiledTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("decompiler/navigationMultipleLibs")

    fun testNavigationToDecompiled() {
        doTest(false, "expected.decompiled")
    }

    fun testNavigationToLibrarySources() {
        doTest(true, "expected.sources")
    }

    override fun createProjectLib(libraryName: String, withSources: Boolean): Library {
        val sources = listOf(File(getTestDataDirectory(), "libSrc"))
        val libraryJar = KotlinCompilerStandalone(sources, jarWithSources = true).compile()

        val jarRoot = libraryJar.jarRoot
        return projectLibrary(libraryName, jarRoot, jarRoot.findChild("src").takeIf { withSources })
    }
}

class NavigationWithMultipleRuntimesTest : AbstractNavigationToSourceOrDecompiledTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("decompiler/navigationMultipleRuntimes")

    fun testNavigationToDecompiled() {
        doTest(false, "expected.decompiled")
    }

    fun testNavigationToLibrarySources() {
        doTest(true, "expected.sources")
    }

    override fun createProjectLib(libraryName: String, withSources: Boolean): Library {
        val libraryJar = TestKotlinArtifacts.kotlinStdlib.toFile().copyTo(File(createTempDirectory(), "$libraryName.jar"))
        val jarUrl = libraryJar.jarRoot
        return runWriteAction {
            val library = ProjectLibraryTable.getInstance(project).createLibrary(libraryName)
            val modifiableModel = library.modifiableModel
            modifiableModel.addRoot(jarUrl, OrderRootType.CLASSES)
            if (withSources) {
                val sourcesJar = TestKotlinArtifacts.kotlinStdlibSources.toFile().copyTo(File(createTempDirectory(), "$libraryName-sources.jar"))
                val commonSourcesJar = TestKotlinArtifacts.kotlinStdlibCommonSources.toFile().copyTo(File(createTempDirectory(), "$libraryName-sources.jar"))
                modifiableModel.addRoot(sourcesJar.jarRoot, OrderRootType.SOURCES)
                modifiableModel.addRoot(commonSourcesJar.jarRoot, OrderRootType.SOURCES)
            }
            modifiableModel.commit()
            library
        }
    }
}


class NavigationToSingleJarInMultipleLibrariesTest : AbstractNavigationWithMultipleLibrariesTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiModuleReferenceResolve/sameJarInDifferentLibraries")

    fun testNavigatingToLibrarySharingSameJarOnlyOneHasSourcesAttached() {
        val srcPath = getTestDataDirectory().resolve("src").absolutePath
        val moduleA = module("m1", srcPath)
        val moduleB = module("m2", srcPath)
        val moduleC = module("m3", srcPath)

        val libSrc = File(getTestDataDirectory(), "libSrc")
        val sharedJar = KotlinCompilerStandalone(listOf(libSrc), jarWithSources = true).compile()

        val jarRoot = sharedJar.jarRoot
        moduleA.addDependency(projectLibrary("libA", jarRoot))
        moduleB.addDependency(projectLibrary("libB", jarRoot, jarRoot.findChild("src")))
        moduleC.addDependency(projectLibrary("libC", jarRoot))

        val expectedFile = File(getTestDataDirectory(), "expected.sources")
        NavigationChecker.checkAnnotatedCode(findSourceFile(moduleA), expectedFile)
        NavigationChecker.checkAnnotatedCode(findSourceFile(moduleB), expectedFile)
        NavigationChecker.checkAnnotatedCode(findSourceFile(moduleC), expectedFile)
    }
}
