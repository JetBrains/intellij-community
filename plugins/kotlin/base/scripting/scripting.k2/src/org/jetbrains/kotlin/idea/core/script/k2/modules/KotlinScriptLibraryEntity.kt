// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface KotlinScriptLibraryEntity : WorkspaceEntityWithSymbolicId {
    val classes: List<VirtualFileUrl>
    val sources: List<VirtualFileUrl>

    override val symbolicId: KotlinScriptLibraryEntityId
        get() = KotlinScriptLibraryEntityId(classes, sources)

    //region generated code
    @GeneratedCodeApiVersion(3)
    interface Builder : WorkspaceEntity.Builder<KotlinScriptLibraryEntity> {
        override var entitySource: EntitySource
        var classes: MutableList<VirtualFileUrl>
        var sources: MutableList<VirtualFileUrl>
    }

    companion object : EntityType<KotlinScriptLibraryEntity, Builder>() {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(
            classes: List<VirtualFileUrl>,
            sources: List<VirtualFileUrl>,
            entitySource: EntitySource,
            init: (Builder.() -> Unit)? = null,
        ): Builder {
            val builder = builder()
            builder.classes = classes.toMutableWorkspaceList()
            builder.sources = sources.toMutableWorkspaceList()
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
    //endregion
}

//region generated code
fun MutableEntityStorage.modifyKotlinScriptLibraryEntity(
    entity: KotlinScriptLibraryEntity,
    modification: KotlinScriptLibraryEntity.Builder.() -> Unit,
): KotlinScriptLibraryEntity {
    return modifyEntity(KotlinScriptLibraryEntity.Builder::class.java, entity, modification)
}
//endregion


data class KotlinScriptLibraryEntityId(val classes: List<VirtualFileUrl>, val sources: List<VirtualFileUrl> = emptyList()) :
    SymbolicEntityId<KotlinScriptLibraryEntity> {
    constructor(classUrl: VirtualFileUrl) : this(listOf(classUrl), emptyList())

    override val presentableName: @NlsSafe String
        get() = "classes=${classes.joinToString(prefix = "[", postfix = "]")}, sources=${sources.joinToString(prefix = "[", postfix = "]")}"
}