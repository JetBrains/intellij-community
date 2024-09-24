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
        combinedScope: CombinedSourceAndClassRootsScope?,
    ) {
        val productionSourceFiles = getAllSourceFiles(includedTestModules, TestContentRootKind.PRODUCTION)
        val excludedProductionSourceFiles = getAllSourceFiles(excludedTestModules, TestContentRootKind.PRODUCTION)

        combinedProductionScope?.checkScopeContains(
            "combined production sources scope",
            productionSourceFiles,
            excludedProductionSourceFiles,
        )

        val testSourceFiles = getAllSourceFiles(includedTestModules, TestContentRootKind.TESTS)
        val excludedTestSourceFiles = getAllSourceFiles(excludedTestModules, TestContentRootKind.TESTS)

        combinedTestScope?.checkScopeContains(
            "combined test sources scope",
            testSourceFiles,
            excludedTestSourceFiles,
        )

        val libraryFiles = getAllLibraryClassFiles(includedTestLibraries)
        val excludedLibraryFiles = getAllLibraryClassFiles(excludedTestLibraries)

        combinedLibraryScope?.checkScopeContains(
            "combined library scope",
            libraryFiles,
            excludedLibraryFiles,
        )

        val allFiles = productionSourceFiles + testSourceFiles + libraryFiles
        val allExcludedFiles = excludedProductionSourceFiles + excludedTestSourceFiles + excludedLibraryFiles

        combinedScope?.checkScopeContains(
            "combined scope",
            allFiles,
            allExcludedFiles,
        )
    }

    private fun CombinedSourceAndClassRootsScope.checkScopeContains(
        scopeName: String,
        includedFiles: List<VirtualFile>,
        excludedFiles: List<VirtualFile>,
    ) {
        includedFiles.forEach { file ->
            assertTrue(
                "The $scopeName should contain the file `$file`.",
                contains(file),
            )
        }

        excludedFiles.forEach { file ->
            assertFalse(
                "The $scopeName should not contain the excluded file `$file`.",
                contains(file),
            )
        }
    }

    private fun getAllSourceFiles(testModules: List<TestProjectModule>, contentRootKind: TestContentRootKind): List<VirtualFile> =
        testModules
            .flatMap { it.contentRootVirtualFilesByKind(contentRootKind) }
            .flatMap { findAllChildren(it, KotlinFileType.INSTANCE) }

    private fun getAllLibraryClassFiles(testLibraries: List<TestProjectLibrary>): List<VirtualFile> =
        testLibraries
            .flatMap { it.toLibrary().classRoots }
            .flatMap { findAllChildren(it, JavaClassFileType.INSTANCE) }

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
