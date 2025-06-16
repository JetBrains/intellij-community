// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.kmp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.kotlinForwardDeclarationsWorkspaceEntity


@ApiStatus.Internal
fun createForwardDeclarationScope(libraryId: LibraryId, project: Project): GlobalSearchScope? {
    val rootDirectories = getGeneratedForwardDeclarationRoots(libraryId, project)
    if (rootDirectories.isEmpty()) return null

    val files = rootDirectories.flatMap { directory ->
        directory.children.filter { it.fileType == KotlinFileType.INSTANCE }
    }

    return GlobalSearchScope.filesScope(project, files)
}

private fun getGeneratedForwardDeclarationRoots(libraryId: LibraryId, project: Project): List<VirtualFile> {
    val libraryEntity = WorkspaceModel.getInstance(project).currentSnapshot.resolve(libraryId) ?: return emptyList()
    val vFiles = getGeneratedFwdDeclarationRootsFromEntity(libraryEntity)
    return vFiles
}

private fun getGeneratedFwdDeclarationRootsFromEntity(libraryEntity: LibraryEntity): List<VirtualFile> {
    val forwardDeclarationLibraryWorkspaceEntity = libraryEntity.kotlinForwardDeclarationsWorkspaceEntity ?: return emptyList()

    return forwardDeclarationLibraryWorkspaceEntity
        .forwardDeclarationRoots
        .mapNotNull { it.virtualFile }
}