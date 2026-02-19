// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

/**
 * Base contract for providing and maintaining [KotlinScriptEntity] instances in the Workspace Model.
 *
 * The provider works against the project's [WorkspaceModel]. Read operations use the current immutable
 * snapshot, while write operations must be performed via [WorkspaceModel.update] calls. Implementations of
 * [updateWorkspaceModel] should confine all Workspace Model mutations to such update blocks and avoid holding
 * onto entities or snapshots across suspending points.
 *
 * Threading/lifetime notes:
 * - [updateWorkspaceModel] is `suspend` to allow implementors to perform I/O (e.g., configuration resolution) before
 *   applying the result to the Workspace Model in a single transaction.
 * - [removeKotlinScriptEntity] is idempotent — it silently returns if no entity is found for the file.
 *
 * See also:
 * - [DefaultKotlinScriptEntityProvider] — a default implementation used for standard .kts files.
 * - [KotlinScriptEntity] and [KotlinScriptLibraryEntity] — entities created/managed by providers.
 * - workspace helpers in this package (e.g., [workspaceModelHelpers.kt]).
 */
abstract class KotlinScriptEntityProvider(
    open val project: Project
) {
    protected val currentSnapshot: ImmutableEntityStorage
        get() = project.workspaceModel.currentSnapshot

    protected val VirtualFile.virtualFileUrl: VirtualFileUrl
        get() = toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())

    /**
     * Finds a [KotlinScriptEntity] associated with the given [virtualFile] in the current snapshot.
     *
     * @return the single script entity for the file, or `null` if no such entity exists.
     */
    open fun getKotlinScriptEntity(virtualFile: VirtualFile): KotlinScriptEntity? {
        return currentSnapshot.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFile.virtualFileUrl)
            .filterIsInstance<KotlinScriptEntity>().singleOrNull()
    }

    /**
     * Create or update Workspace Model entities that represent the given Kotlin script file according to [definition].
     *
     * Implementations should resolve configuration (dependencies, JVM targets, imports, etc.) first, and then
     * wrap all Workspace Model mutations into a single [WorkspaceModel.update] transaction to minimize churn.
     *
     */
    abstract suspend fun updateWorkspaceModel(virtualFile: VirtualFile, definition: ScriptDefinition)

    /**
     * Removes the [KotlinScriptEntity] corresponding to [virtualFile], if present.
     *
     * The operation is safe to call repeatedly; when no entity exists, it returns without making changes.
     */
    open suspend fun removeKotlinScriptEntity(virtualFile: VirtualFile) {
        val entity = currentSnapshot.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFile.virtualFileUrl)
            .filterIsInstance<KotlinScriptEntity>().singleOrNull() ?: return

        project.workspaceModel.update("removing .kts modules") {
            it.removeEntity(entity)
        }
    }

    /**
     * A helper to update Kotlin script–related entities for the given [entitySource] within a single Workspace Model
     * transaction. The [updater] receives a [MutableEntityStorage] where it can add, modify, or remove entities.
     */
    protected suspend fun Project.updateKotlinScriptEntities(entitySource: EntitySource, updater: (MutableEntityStorage) -> Unit) {
        workspaceModel.update("updating kotlin script entities [$entitySource]") {
            updater(it)
        }
    }
}
