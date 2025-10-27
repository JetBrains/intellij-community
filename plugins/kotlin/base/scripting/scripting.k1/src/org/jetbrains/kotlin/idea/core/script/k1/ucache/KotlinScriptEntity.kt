// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


// Use "Generate Workspace Model Implementation" action once interface is updated.
interface KotlinScriptEntity : WorkspaceEntityWithSymbolicId {

    val path: String

    val dependencies: Set<KotlinScriptLibraryId>

    override val symbolicId: KotlinScriptId
        get() = KotlinScriptId(path)

}

data class KotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : EntitySource

data class KotlinScriptId(val path: String) : SymbolicEntityId<KotlinScriptEntity> {
    override val presentableName: String
        get() = path
}