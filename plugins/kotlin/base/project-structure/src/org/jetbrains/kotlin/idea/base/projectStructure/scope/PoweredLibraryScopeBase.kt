// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.module.impl.scopes.LibraryScopeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import org.jetbrains.kotlin.resolve.jvm.TopPackageNamesProvider

internal open class PoweredLibraryScopeBase(project: Project, classes: Array<VirtualFile>, sources: Array<VirtualFile>) :
    LibraryScopeBase(project, classes, sources), TopPackageNamesProvider {

    private val entriesVirtualFileSystems: Set<NewVirtualFileSystem>? = run {
        val fileSystems = mutableSetOf<NewVirtualFileSystem>()
        for (file in classes + sources) {
            val newVirtualFile = file as? NewVirtualFile ?: return@run null
            fileSystems.add(newVirtualFile.fileSystem)
        }
        fileSystems
    }

    override val topPackageNames: Set<String> by lazy {
        (classes + sources)
            .flatMap { it.children.toList() }
            .filter(VirtualFile::isDirectory)
            .map(VirtualFile::getName)
            .toSet() + "" // empty package is always present
    }

    override fun contains(file: VirtualFile): Boolean {
        ((file as? NewVirtualFile)?.fileSystem)?.let {
            if (entriesVirtualFileSystems != null && !entriesVirtualFileSystems.contains(it)) {
                return false
            }
        }
        return super.contains(file)
    }
}