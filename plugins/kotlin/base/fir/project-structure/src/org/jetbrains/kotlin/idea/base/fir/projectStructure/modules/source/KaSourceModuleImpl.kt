// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaEntityBasedModuleCreationData
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaModuleWithDebugData
import org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.InternalKaModuleConstructor
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind

internal class KaSourceModuleImpl @InternalKaModuleConstructor constructor(
    override val entityId: ModuleId,
    override val kind: KaSourceModuleKind,
    override val project: Project,
    override val creationData: KaEntityBasedModuleCreationData,
) : KaSourceModuleBase(), KaModuleWithDebugData {
    override val entityInterface: Class<out WorkspaceEntity> get() = ModuleEntity::class.java

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaSourceModuleImpl
                && entityId == other.entityId
                && kind == other.kind
    }

    override fun hashCode(): Int {
        return 31 * entityId.hashCode() + kind.hashCode()
    }
}