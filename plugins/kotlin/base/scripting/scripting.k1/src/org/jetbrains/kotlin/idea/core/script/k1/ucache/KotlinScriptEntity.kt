// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.K1Deprecation


// Use "Generate Workspace Model Implementation" action once interface is updated.
@K1Deprecation
interface KotlinScriptEntity : WorkspaceEntityWithSymbolicId {

    val path: String

    val dependencies: Set<KotlinScriptLibraryId>

    override val symbolicId: KotlinScriptId
        get() = KotlinScriptId(path)

}

@K1Deprecation
data class KotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : EntitySource

@K1Deprecation
data class KotlinScriptId(val path: String) : SymbolicEntityId<KotlinScriptEntity> {
    override val presentableName: String
        get() = path
}