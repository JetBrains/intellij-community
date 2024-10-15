// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.module.impl.scopes.LibraryScopeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import org.jetbrains.kotlin.resolve.jvm.TopPackageNamesProvider

internal open class PoweredLibraryScopeBase(
    project: Project,
    classes: Array<VirtualFile>,
    sources: Array<VirtualFile>,
    override val topPackageNames: Set<String>?,
    private val entriesVirtualFileSystems: Set<NewVirtualFileSystem>?
) : LibraryScopeBase(project, classes, sources), TopPackageNamesProvider {

    @Deprecated("Use the primary constructor", level = DeprecationLevel.HIDDEN)
    constructor(project: Project, classes: Array<VirtualFile>, sources: Array<VirtualFile>) : this(
        project,
        classes,
        sources,
        (classes + sources).calculateTopPackageNames(),
        (classes + sources).calculateEntriesVirtualFileSystems()
    )

    override fun contains(file: VirtualFile): Boolean {
        ((file as? NewVirtualFile)?.fileSystem)?.let {
            if (entriesVirtualFileSystems != null && !entriesVirtualFileSystems.contains(it)) {
                return false
            }
        }
        return super.contains(file)
    }
}

internal fun Array<VirtualFile>.calculateTopPackageNames(): Set<String>? {
    if (isEmpty()) return null
    val topPackageNames = this.flatMap { it.children.toList() }
        .filter(VirtualFile::isDirectory)
        .map(VirtualFile::getName)
        .toSet()
    return topPackageNames + "" // empty package is always present
}

internal fun Array<VirtualFile>.calculateEntriesVirtualFileSystems(): Set<NewVirtualFileSystem>? {
    val fileSystems = hashSetOf<NewVirtualFileSystem>()
    // optimization to use NewVirtualFileSystem is applicable iff all the library files are archives
    for (file in this) {
        val newVirtualFile = file as? NewVirtualFile ?: return null
        fileSystems.add(newVirtualFile.fileSystem)
    }
    return fileSystems
}
