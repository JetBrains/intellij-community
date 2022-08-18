// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

internal class ModuleLibrariesSearchScope(module: Module) : GlobalSearchScope(module.project) {
    private val projectFileIndex = ProjectRootManager.getInstance(module.project).fileIndex
    private val moduleFileIndex = ModuleRootManager.getInstance(module).fileIndex

    override fun contains(file: VirtualFile): Boolean {
        // We want this scope to work only on .class files, not on source files
        if (!projectFileIndex.isInLibraryClasses(file)) return false

        val orderEntry = moduleFileIndex.getOrderEntryForFile(file)
        return orderEntry is JdkOrderEntry || orderEntry is LibraryOrderEntry
    }

    override fun compare(file1: VirtualFile, file2: VirtualFile): Int {
        return Comparing.compare(moduleFileIndex.getOrderEntryForFile(file2), moduleFileIndex.getOrderEntryForFile(file1))
    }

    override fun isSearchInModuleContent(aModule: Module): Boolean = false

    override fun isSearchInLibraries(): Boolean = true
}