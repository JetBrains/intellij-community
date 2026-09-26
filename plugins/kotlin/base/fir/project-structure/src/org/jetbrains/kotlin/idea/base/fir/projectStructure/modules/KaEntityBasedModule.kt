// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules

import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaModuleBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

/**
 * An implementation of [KaModule] based on a [WorkspaceEntityWithSymbolicId].
 *
 * This does not represent a bidirectional mapping between [WorkspaceEntityWithSymbolicId] and [KaEntityBasedModule].
 * Some [WorkspaceEntityWithSymbolicId] instances are not represented by [KaEntityBasedModule] because they are not needed for Kotlin.
 * Additionally, certain [WorkspaceEntityWithSymbolicId] instances may produce multiple [KaEntityBasedModule] instances.
 * For example, [com.intellij.platform.workspace.jps.entities.ModuleEntity] may produce two
 * [org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source.KaSourceModuleBase] instances:
 * one for production and one for tests.
 *
 * Implementations typically store and operate based on [SymbolicEntityId].
 * However, they must not store [WorkspaceEntityWithSymbolicId] directly, as its lifetime is shorter than that of a [KaModule].
 * Instead, [WorkspaceEntityWithSymbolicId] can be retrieved using [entity].
 *
 * @see KaModule
 * @see SymbolicEntityId
 * @see com.intellij.platform.workspace.storage.WorkspaceEntity
 */
@ApiStatus.Internal
abstract class KaEntityBasedModule<E : WorkspaceEntityWithSymbolicId, EID : SymbolicEntityId<E>> : KaModuleBase() {
    abstract val entityId: EID

    protected val workspaceModel: WorkspaceModel get() = project.workspaceModel
    protected val currentSnapshot: ImmutableEntityStorage get() = workspaceModel.currentSnapshot

    open val entity: E
        get() = entityId.resolve(currentSnapshot)
            ?: couldNotResolveEntityError(this)

    /**
     * Should be directly overridden by the final inheritor.
     * In the implementation, comparing [SymbolicEntityId] is sufficient instead of retrieving a [WorkspaceEntityWithSymbolicId],
     * as they have a bidirectional mapping.
     */
    abstract override fun equals(other: Any?): Boolean

    /**
     * Should be directly overridden by the final inheritor.
     * In the implementation, using [SymbolicEntityId] is sufficient instead of retrieving a [WorkspaceEntityWithSymbolicId],
     * as they have a bidirectional mapping.
     */
    abstract override fun hashCode(): Int


    override fun toString(): String {
        return "${this::class.simpleName}($entityId), platform=$targetPlatform, moduleDescription=`$moduleDescription`"
    }
}