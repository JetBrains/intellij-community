// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinScriptLibraryEntityModifications")

package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface KotlinScriptLibraryEntityBuilder : WorkspaceEntityBuilder<KotlinScriptLibraryEntity> {
    override var entitySource: EntitySource
    var classes: MutableList<VirtualFileUrl>
    var usedInScripts: MutableSet<VirtualFileUrl>
    var sources: MutableSet<VirtualFileUrl>
}

internal object KotlinScriptLibraryEntityType : EntityType<KotlinScriptLibraryEntity, KotlinScriptLibraryEntityBuilder>() {
    override val entityClass: Class<KotlinScriptLibraryEntity> get() = KotlinScriptLibraryEntity::class.java
    operator fun invoke(
        classes: List<VirtualFileUrl>,
        usedInScripts: Set<VirtualFileUrl>,
        entitySource: EntitySource,
        init: (KotlinScriptLibraryEntityBuilder.() -> Unit)? = null,
    ): KotlinScriptLibraryEntityBuilder {
        val builder = builder()
        builder.classes = classes.toMutableWorkspaceList()
        builder.usedInScripts = usedInScripts.toMutableWorkspaceSet()
        builder.entitySource = entitySource
        init?.invoke(builder)
        return builder
    }
}

fun MutableEntityStorage.modifyKotlinScriptLibraryEntity(
    entity: KotlinScriptLibraryEntity,
    modification: KotlinScriptLibraryEntityBuilder.() -> Unit,
): KotlinScriptLibraryEntity = modifyEntity(KotlinScriptLibraryEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createKotlinScriptLibraryEntity")
fun KotlinScriptLibraryEntity(
    classes: List<VirtualFileUrl>,
    usedInScripts: Set<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (KotlinScriptLibraryEntityBuilder.() -> Unit)? = null,
): KotlinScriptLibraryEntityBuilder = KotlinScriptLibraryEntityType(classes, usedInScripts, entitySource, init)
