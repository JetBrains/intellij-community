// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

/**
 * File Index Contributor of generated K/N forward declaration files.
 * They are not part of the project and require explicit indexing registration.
 *
 * @see [KotlinForwardDeclarationsFileGenerator], [KotlinForwardDeclarationsModelChangeService]
 */
class KotlinForwardDeclarationsWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<KotlinForwardDeclarationsWorkspaceEntity> {
    override val entityClass: Class<KotlinForwardDeclarationsWorkspaceEntity>
        get() = KotlinForwardDeclarationsWorkspaceEntity::class.java

    override fun registerFileSets(entity: KotlinForwardDeclarationsWorkspaceEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
        if (!Registry.`is`("kotlin.k2.kmp.enabled")) return

        entity.forwardDeclarationRoots.forEach { fwdDeclarationRootUrl ->
            registrar.registerFileSet(fwdDeclarationRootUrl, WorkspaceFileKind.EXTERNAL, entity, customData = null)
        }
    }
}
