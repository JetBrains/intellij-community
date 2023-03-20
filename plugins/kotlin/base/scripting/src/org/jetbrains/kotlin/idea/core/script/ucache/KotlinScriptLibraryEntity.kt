// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.SymbolicEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithSymbolicId
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.io.Serializable
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


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
    @GeneratedCodeApiVersion(1)
    interface Builder : KotlinScriptLibraryEntity, WorkspaceEntity.Builder<KotlinScriptLibraryEntity>, ObjBuilder<KotlinScriptLibraryEntity> {
        override var entitySource: EntitySource
        override var name: String
        override var roots: MutableList<KotlinScriptLibraryRoot>
        override var indexSourceRoots: Boolean
        override var usedInScripts: MutableSet<KotlinScriptId>
    }

    companion object : Type<KotlinScriptLibraryEntity, Builder>() {
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
