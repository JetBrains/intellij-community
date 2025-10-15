// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModifiableLibraryEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableKotlinForwardDeclarationsWorkspaceEntity : ModifiableWorkspaceEntity<KotlinForwardDeclarationsWorkspaceEntity> {
  override var entitySource: EntitySource
  var forwardDeclarationRoots: MutableSet<VirtualFileUrl>
  var library: ModifiableLibraryEntity
}

internal object KotlinForwardDeclarationsWorkspaceEntityType : EntityType<KotlinForwardDeclarationsWorkspaceEntity, ModifiableKotlinForwardDeclarationsWorkspaceEntity>() {
  override val entityClass: Class<KotlinForwardDeclarationsWorkspaceEntity> get() = KotlinForwardDeclarationsWorkspaceEntity::class.java
  operator fun invoke(
    forwardDeclarationRoots: Set<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ModifiableKotlinForwardDeclarationsWorkspaceEntity.() -> Unit)? = null,
  ): ModifiableKotlinForwardDeclarationsWorkspaceEntity {
    val builder = builder()
    builder.forwardDeclarationRoots = forwardDeclarationRoots.toMutableWorkspaceSet()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyKotlinForwardDeclarationsWorkspaceEntity(
  entity: KotlinForwardDeclarationsWorkspaceEntity,
  modification: ModifiableKotlinForwardDeclarationsWorkspaceEntity.() -> Unit,
): KotlinForwardDeclarationsWorkspaceEntity =
  modifyEntity(ModifiableKotlinForwardDeclarationsWorkspaceEntity::class.java, entity, modification)

var ModifiableLibraryEntity.kotlinForwardDeclarationsWorkspaceEntity: ModifiableKotlinForwardDeclarationsWorkspaceEntity?
  by WorkspaceEntity.extensionBuilder(KotlinForwardDeclarationsWorkspaceEntity::class.java)


@JvmOverloads
@JvmName("createKotlinForwardDeclarationsWorkspaceEntity")
fun KotlinForwardDeclarationsWorkspaceEntity(
  forwardDeclarationRoots: Set<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ModifiableKotlinForwardDeclarationsWorkspaceEntity.() -> Unit)? = null,
): ModifiableKotlinForwardDeclarationsWorkspaceEntity =
  KotlinForwardDeclarationsWorkspaceEntityType(forwardDeclarationRoots, entitySource, init)
