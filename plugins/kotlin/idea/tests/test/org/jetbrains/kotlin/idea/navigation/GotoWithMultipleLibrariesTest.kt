// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.navigation

import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.projectLibrary
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class GotoWithMultipleLibrariesTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiModuleReferenceResolve/sameJarInDifferentLibraries")

    fun testOneHasSourceAndOneDoesNot() {
        doTestSameJarSharedByLibrariesWithAndWithoutSourceAttached(withSource = 1, noSource = 1)
    }

    fun testOneHasSourceAndManyDoNot() {
        doTestSameJarSharedByLibrariesWithAndWithoutSourceAttached(withSource = 1, noSource = 3)
    }

    fun testSeveralWithSource() {
        doTestSameJarSharedByLibrariesWithAndWithoutSourceAttached(withSource = 2, noSource = 2)
    }

    private fun doTestSameJarSharedByLibrariesWithAndWithoutSourceAttached(withSource: Int, noSource: Int) {
        val srcDir = File(testDataPath, "src")
        val libSrcDir = File(testDataPath, "libSrc")

        val sharedJar = KotlinCompilerStandalone(
            listOf(libSrcDir),
            target = createTempDirectory().resolve("shared.jar").apply { createNewFile() }
        ).compile()

        val jarRoot = sharedJar.jarRoot
        val libSrcRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libSrcDir)!!

        var i = 0
        val libraryA = projectLibrary("libA", jarRoot)
        repeat(noSource) {
            module("m${++i}", srcDir).addDependency(libraryA)
        }

        val libraryB = projectLibrary("libB", jarRoot, libSrcRoot)
        repeat(withSource) {
            module("m${++i}", srcDir).addDependency(libraryB)
        }

        checkFiles({ project.allKotlinFiles() }) {
            GotoCheck.checkGotoDirectives(GotoSymbolModel2(project, testRootDisposable), editor, nonProjectSymbols = true, checkNavigation = true)
        }
    }

    private fun module(name: String, srcDir: File) = createModuleFromTestData(srcDir.absolutePath, name, StdModuleTypes.JAVA, true)
}
