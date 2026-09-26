// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.scope

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinedSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestContentRootKind
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectLibrary
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectModule

abstract class AbstractCombinedSourceAndClassRootsScopeContainsTest : AbstractCombinedSourceAndClassRootsScopeTest() {
    override fun doTestWithScopes(
        combinedProductionScope: CombinedSourceAndClassRootsScope?,
        combinedTestScope: CombinedSourceAndClassRootsScope?,
        combinedLibraryScope: CombinedSourceAndClassRootsScope?,
        combinedLibrarySourcesScope: CombinedSourceAndClassRootsScope?,
        combinedScope: CombinedSourceAndClassRootsScope?,
    ) {
        val productionSourceFiles = getAllSourceFiles(includedTestModules, TestContentRootKind.PRODUCTION)
        val testSourceFiles = getAllSourceFiles(includedTestModules, TestContentRootKind.TESTS)
        val libraryFiles = getAllLibraryClassFiles(includedTestLibraries)
        val librarySourceFiles = getAllLibrarySourceFiles(includedTestLibraries)

        val allResourceFiles = getAllSourceFiles(testModules, TestContentRootKind.RESOURCES, TestContentRootKind.TEST_RESOURCES)
        val excludedProductionSourceFiles = getAllSourceFiles(excludedTestModules, TestContentRootKind.PRODUCTION)
        val excludedTestSourceFiles = getAllSourceFiles(excludedTestModules, TestContentRootKind.TESTS)
        val excludedLibraryFiles = getAllLibraryClassFiles(excludedTestLibraries)
        val excludedLibrarySourceFiles = getAllLibraryClassFiles(excludedTestLibraries)

        val allIncludedFiles = productionSourceFiles + testSourceFiles + libraryFiles + librarySourceFiles
        val allExcludedFiles = excludedProductionSourceFiles + excludedTestSourceFiles + excludedLibraryFiles + excludedLibrarySourceFiles + allResourceFiles

        val allFiles = allIncludedFiles + allExcludedFiles

        combinedProductionScope?.checkScopeContains(
            "combined production sources scope",
            containedFiles = productionSourceFiles,
            nonContainedFiles = allFiles - productionSourceFiles,
        )

        combinedTestScope?.checkScopeContains(
            "combined test sources scope",
            containedFiles = testSourceFiles,
            nonContainedFiles = allFiles - testSourceFiles,
        )

        combinedLibraryScope?.checkScopeContains(
            "combined library scope",
            containedFiles = libraryFiles,
            nonContainedFiles = allFiles - libraryFiles,
        )

        combinedLibrarySourcesScope?.checkScopeContains(
            "combined library sources scope",
            containedFiles = librarySourceFiles,
            nonContainedFiles = allFiles - librarySourceFiles,
        )

        combinedScope?.checkScopeContains(
            "combined scope",
            containedFiles = allIncludedFiles,
            nonContainedFiles = allExcludedFiles,
        )
    }

    private fun CombinedSourceAndClassRootsScope.checkScopeContains(
        scopeName: String,
        containedFiles: List<VirtualFile>,
        nonContainedFiles: List<VirtualFile>,
    ) {
        containedFiles.forEach { file ->
            assertTrue(
                "The $scopeName should contain the file `$file`.",
                contains(file),
            )
        }

        nonContainedFiles.forEach { file ->
            assertFalse(
                "The $scopeName should not contain the file `$file`.",
                contains(file),
            )
        }
    }

    private fun getAllSourceFiles(testModules: List<TestProjectModule>, vararg contentRootKinds: TestContentRootKind): List<VirtualFile> =
        testModules
            .flatMap { it.contentRootVirtualFilesByKind(*contentRootKinds) }
            .flatMap { findAllChildren(it, KotlinFileType.INSTANCE) }

    private fun getAllLibraryClassFiles(testLibraries: List<TestProjectLibrary>): List<VirtualFile> =
        testLibraries
            .flatMap { it.toLibrary().classRoots }
            .flatMap { findAllChildren(it, JavaClassFileType.INSTANCE) }

    private fun getAllLibrarySourceFiles(testLibraries: List<TestProjectLibrary>): List<VirtualFile> =
        testLibraries
            .flatMap { it.toLibrary().sourceRoots }
            .flatMap { findAllChildren(it, KotlinFileType.INSTANCE) }

    private fun findAllChildren(root: VirtualFile, fileType: FileType): List<VirtualFile> {
        return buildList {
            VfsUtilCore.visitChildrenRecursively(
                root,
                object : VirtualFileVisitor<Unit>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (file.extension == fileType.defaultExtension) {
                            add(file)
                        }
                        return true
                    }
                }
            )
        }
    }
}
