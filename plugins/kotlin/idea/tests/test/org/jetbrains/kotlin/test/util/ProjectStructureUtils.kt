// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.test.util

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IndexingTestUtil
import java.io.File

fun HeavyPlatformTestCase.projectLibrary(
    libraryName: String = "TestLibrary",
    classesRoot: VirtualFile? = null,
    sourcesRoot: VirtualFile? = null,
    kind: PersistentLibraryKind<*>? = null
): LibraryEx = multiRootProjectLibrary(
    libraryName = libraryName,
    classRoots = classesRoot?.let { listOf(it) } ?: emptyList(),
    sourceRoots = sourcesRoot?.let { listOf(it) } ?: emptyList(),
    kind = kind
)

fun HeavyPlatformTestCase.multiRootProjectLibrary(
    libraryName: String = "TestLibrary",
    classRoots: List<VirtualFile> = emptyList(),
    sourceRoots: List<VirtualFile> = emptyList(),
    kind: PersistentLibraryKind<*>? = null
): LibraryEx {
    return runWriteAction {
        val modifiableModel = ProjectLibraryTable.getInstance(project).modifiableModel
        val library = try {
            modifiableModel.createLibrary(libraryName, kind) as LibraryEx
        } finally {
            modifiableModel.commit()
        }
        with(library.modifiableModel) {
            classRoots.forEach { addRoot(it, OrderRootType.CLASSES) }
            sourceRoots.forEach { addRoot(it, OrderRootType.SOURCES) }
            commit()
        }
        library
    }.also {
        IndexingTestUtil.waitUntilIndexesAreReady(module.project)
    }
}

fun moduleLibrary(
    module: Module,
    libraryName: String? = "TestLibrary",
    classesRoot: VirtualFile? = null,
    sourcesRoot: VirtualFile? = null,
): LibraryEx {
    return runWriteAction {
        val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
        val moduleLibraryTable = modifiableModel.moduleLibraryTable
        val library = try {
            moduleLibraryTable.createLibrary(libraryName) as LibraryEx
        } finally {
            modifiableModel.commit()
        }

        with(library.modifiableModel) {
            classesRoot?.let { addRoot(it, OrderRootType.CLASSES) }
            sourcesRoot?.let { addRoot(it, OrderRootType.SOURCES) }
            commit()
        }

        library
    }.also {
        IndexingTestUtil.waitUntilIndexesAreReady(module.project)
    }
}

val File.jarRoot: VirtualFile
    get() {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this) ?: error("Cannot find file $this")
        return JarFileSystem.getInstance().getRootByLocal(virtualFile) ?: error("Can't find root by file $virtualFile")
    }
