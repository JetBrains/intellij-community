// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface KotlinScriptLibraryEntity : WorkspaceEntityWithSymbolicId {
    val classes: List<VirtualFileUrl>
    val usedInScripts: Set<VirtualFileUrl>

    @Suppress("RemoveExplicitTypeArguments")
    val sources: Set<VirtualFileUrl>
        @Default get() = setOf<VirtualFileUrl>()

    override val symbolicId: KotlinScriptLibraryEntityId
        get() = KotlinScriptLibraryEntityId(classes)
}

data class KotlinScriptLibraryEntityId(val classes: List<VirtualFileUrl>) :
    SymbolicEntityId<KotlinScriptLibraryEntity> {
    constructor(classUrl: VirtualFileUrl) : this(listOf(classUrl))

    override val presentableName: @NlsSafe String
        get() = "KotlinScriptLibraryEntityId(classes=$classes)"

    override fun toString(): String = presentableName
}