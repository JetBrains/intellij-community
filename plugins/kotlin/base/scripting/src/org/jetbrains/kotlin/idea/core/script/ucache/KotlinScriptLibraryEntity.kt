// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.io.Serializable
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType


data class KotlinScriptLibraryRootTypeId(val name: String) : Serializable {
    companion object {
        val COMPILED = KotlinScriptLibraryRootTypeId("CLASSES")
        val SOURCES = KotlinScriptLibraryRootTypeId("SOURCES")
    }
}

data class KotlinScriptLibraryRoot(val url: VirtualFileUrl, val type: KotlinScriptLibraryRootTypeId) : Serializable

data class KotlinScriptLibraryId(val name: String) : SymbolicEntityId<KotlinScriptLibraryEntity> {
    override val presentableName: String
        get() = name
}

data class KotlinScriptLibraryEntitySource(override val virtualFileUrl: VirtualFileUrl?): EntitySource


interface KotlinScriptLibraryEntity : WorkspaceEntityWithSymbolicId {

    val name: String

    val roots: List<KotlinScriptLibraryRoot>

    val indexSourceRoots: Boolean

    val usedInScripts: Set<KotlinScriptId>

    override val symbolicId: KotlinScriptLibraryId
        get() = KotlinScriptLibraryId(name)

    //region generated code
    @GeneratedCodeApiVersion(2)
    interface Builder : KotlinScriptLibraryEntity, WorkspaceEntity.Builder<KotlinScriptLibraryEntity> {
        override var entitySource: EntitySource
        override var name: String
        override var roots: MutableList<KotlinScriptLibraryRoot>
        override var indexSourceRoots: Boolean
        override var usedInScripts: MutableSet<KotlinScriptId>
    }

    companion object : EntityType<KotlinScriptLibraryEntity, Builder>() {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(name: String,
                            roots: List<KotlinScriptLibraryRoot>,
                            indexSourceRoots: Boolean,
                            usedInScripts: Set<KotlinScriptId>,
                            entitySource: EntitySource,
                            init: (Builder.() -> Unit)? = null): KotlinScriptLibraryEntity {
            val builder = builder()
            builder.name = name
            builder.roots = roots.toMutableWorkspaceList()
            builder.indexSourceRoots = indexSourceRoots
            builder.usedInScripts = usedInScripts.toMutableWorkspaceSet()
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
    //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: KotlinScriptLibraryEntity,
                                      modification: KotlinScriptLibraryEntity.Builder.() -> Unit) = modifyEntity(
    KotlinScriptLibraryEntity.Builder::class.java, entity, modification)
//endregion
