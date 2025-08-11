// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface KotlinScriptEntity : WorkspaceEntity {
    val virtualFileUrl: VirtualFileUrl
    val dependencies: List<KotlinScriptLibraryEntityId>
    val sdk: ModuleDependencyItem

    //region generated code
    @GeneratedCodeApiVersion(3)
    interface Builder : WorkspaceEntity.Builder<KotlinScriptEntity> {
        override var entitySource: EntitySource
        var virtualFileUrl: VirtualFileUrl
        var dependencies: MutableList<KotlinScriptLibraryEntityId>
        var sdk: ModuleDependencyItem
    }

    companion object : EntityType<KotlinScriptEntity, Builder>() {
        @JvmOverloads
        @JvmStatic
        @JvmName("create")
        operator fun invoke(
            virtualFileUrl: VirtualFileUrl,
            dependencies: List<KotlinScriptLibraryEntityId>,
            sdk: ModuleDependencyItem,
            entitySource: EntitySource,
            init: (Builder.() -> Unit)? = null,
        ): Builder {
            val builder = builder()
            builder.virtualFileUrl = virtualFileUrl
            builder.dependencies = dependencies.toMutableWorkspaceList()
            builder.sdk = sdk
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
    //endregion
}

//region generated code
fun MutableEntityStorage.modifyKotlinScriptEntity(
    entity: KotlinScriptEntity,
    modification: KotlinScriptEntity.Builder.() -> Unit,
): KotlinScriptEntity {
    return modifyEntity(KotlinScriptEntity.Builder::class.java, entity, modification)
}
//endregion
