// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.K1Deprecation
import java.io.Serializable


@K1Deprecation
data class KotlinScriptLibraryRootTypeId(val name: String) : Serializable {
    companion object {
        val COMPILED = KotlinScriptLibraryRootTypeId("CLASSES")
        val SOURCES = KotlinScriptLibraryRootTypeId("SOURCES")
    }
}

@K1Deprecation
data class KotlinScriptLibraryRoot(val url: VirtualFileUrl, val type: KotlinScriptLibraryRootTypeId) : Serializable

@K1Deprecation
data class KotlinScriptLibraryId(val name: String) : SymbolicEntityId<KotlinScriptLibraryEntity> {
    override val presentableName: String
        get() = name
}

@K1Deprecation
data class KotlinScriptLibraryEntitySource(override val virtualFileUrl: VirtualFileUrl?) : EntitySource


@K1Deprecation
interface KotlinScriptLibraryEntity : WorkspaceEntityWithSymbolicId {

    val name: String

    val roots: List<KotlinScriptLibraryRoot>

    val indexSourceRoots: Boolean

    val usedInScripts: Set<KotlinScriptId>

    override val symbolicId: KotlinScriptLibraryId
        get() = KotlinScriptLibraryId(name)
}
