// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.decompiler.navigation

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.JavaModuleTestCase
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibrarySourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.decompiler.navigation.NavigationChecker.Companion.checkAnnotatedCode
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.util.projectStructure.getModuleDir
import org.jetbrains.kotlin.test.util.addDependency
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.projectLibrary
import org.junit.Assert
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
        val libraryJar = TestKotlinArtifacts.kotlinStdlib.copyTo(File(createTempDirectory(), "$libraryName.jar"))
        val jarUrl = libraryJar.jarRoot
        return runWriteAction {
            val library = ProjectLibraryTable.getInstance(project).createLibrary(libraryName)
            val modifiableModel = library.modifiableModel
            modifiableModel.addRoot(jarUrl, OrderRootType.CLASSES)
            if (withSources) {
                val sourcesJar = TestKotlinArtifacts.kotlinStdlibSources.copyTo(File(createTempDirectory(), "$libraryName-sources.jar"))
                val commonSourcesJar = TestKotlinArtifacts.kotlinStdlibCommonSources.copyTo(File(createTempDirectory(), "$libraryName-sources.jar"))
                modifiableModel.addRoot(sourcesJar.jarRoot, OrderRootType.SOURCES)
                modifiableModel.addRoot(commonSourcesJar.jarRoot, OrderRootType.SOURCES)
            }
            modifiableModel.commit()
            library
        }
    }
}

abstract class AbstractNavigationToSourceOrDecompiledTest : AbstractNavigationWithMultipleLibrariesTest() {
    fun doTest(withSources: Boolean, expectedFileName: String) {
        val srcPath = getTestDataDirectory().resolve("src").absolutePath
        val moduleA = module("moduleA", srcPath)
        val moduleB = module("moduleB", srcPath)

        moduleA.addDependency(createProjectLib("libA", withSources))
        moduleB.addDependency(createProjectLib("libB", withSources))

        // navigation code works by providing first matching declaration from indices
        // that's we need to check references in both modules to guard against possibility of code breaking
        // while tests pass by chance
        checkReferencesInModule(moduleA, "libA", expectedFileName)
        checkReferencesInModule(moduleB, "libB", expectedFileName)
    }

    abstract fun createProjectLib(libraryName: String, withSources: Boolean): Library
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
        checkAnnotatedCode(findSourceFile(moduleA), expectedFile)
        checkAnnotatedCode(findSourceFile(moduleB), expectedFile)
        checkAnnotatedCode(findSourceFile(moduleC), expectedFile)
    }
}

abstract class AbstractNavigationWithMultipleLibrariesTest : JavaModuleTestCase() {
    abstract fun getTestDataDirectory(): File

    protected fun module(name: String, srcPath: String) = createModuleFromTestData(srcPath, name, StdModuleTypes.JAVA, true)

    protected fun checkReferencesInModule(module: Module, libraryName: String, expectedFileName: String) {
        checkAnnotatedCode(findSourceFile(module), File(getTestDataDirectory(), expectedFileName)) {
            checkLibraryName(it, libraryName)
        }
    }
}

private fun checkLibraryName(referenceTarget: PsiElement, expectedName: String) {
    val navigationFile = referenceTarget.navigationElement.containingFile ?: return
    val libraryName = when (val libraryInfo = navigationFile.moduleInfoOrNull) {
        is LibraryInfo -> libraryInfo.library.name
        is LibrarySourceInfo -> libraryInfo.library.name
        else -> error("Couldn't get library name")
    }

    Assert.assertEquals("Referenced code from unrelated library: ${referenceTarget.text}", expectedName, libraryName)
}

private fun findSourceFile(module: Module): PsiFile {
    val ioFile = File(module.getModuleDir()).listFiles().orEmpty().first()
    val vFile = LocalFileSystem.getInstance().findFileByIoFile(ioFile)!!
    return PsiManager.getInstance(module.project).findFile(vFile)!!
}
