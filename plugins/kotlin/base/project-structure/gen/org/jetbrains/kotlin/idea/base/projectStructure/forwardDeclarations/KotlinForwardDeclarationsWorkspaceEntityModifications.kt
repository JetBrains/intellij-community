// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinForwardDeclarationsWorkspaceEntityModifications")

package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.platform.workspace.jps.entities.LibraryEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface KotlinForwardDeclarationsWorkspaceEntityBuilder : WorkspaceEntityBuilder<KotlinForwardDeclarationsWorkspaceEntity> {
  override var entitySource: EntitySource
  var forwardDeclarationRoots: MutableSet<VirtualFileUrl>
  var library: LibraryEntityBuilder
}

internal object KotlinForwardDeclarationsWorkspaceEntityType :
  EntityType<KotlinForwardDeclarationsWorkspaceEntity, KotlinForwardDeclarationsWorkspaceEntityBuilder>() {
  override val entityClass: Class<KotlinForwardDeclarationsWorkspaceEntity> get() = KotlinForwardDeclarationsWorkspaceEntity::class.java
  operator fun invoke(
    forwardDeclarationRoots: Set<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (KotlinForwardDeclarationsWorkspaceEntityBuilder.() -> Unit)? = null,
  ): KotlinForwardDeclarationsWorkspaceEntityBuilder {
    val builder = builder()
    builder.forwardDeclarationRoots = forwardDeclarationRoots.toMutableWorkspaceSet()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyKotlinForwardDeclarationsWorkspaceEntity(
  entity: KotlinForwardDeclarationsWorkspaceEntity,
  modification: KotlinForwardDeclarationsWorkspaceEntityBuilder.() -> Unit,
): KotlinForwardDeclarationsWorkspaceEntity =
  modifyEntity(KotlinForwardDeclarationsWorkspaceEntityBuilder::class.java, entity, modification)

var LibraryEntityBuilder.kotlinForwardDeclarationsWorkspaceEntity: KotlinForwardDeclarationsWorkspaceEntityBuilder?
  by WorkspaceEntity.extensionBuilder(KotlinForwardDeclarationsWorkspaceEntity::class.java)


@JvmOverloads
@JvmName("createKotlinForwardDeclarationsWorkspaceEntity")
fun KotlinForwardDeclarationsWorkspaceEntity(
  forwardDeclarationRoots: Set<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (KotlinForwardDeclarationsWorkspaceEntityBuilder.() -> Unit)? = null,
): KotlinForwardDeclarationsWorkspaceEntityBuilder =
  KotlinForwardDeclarationsWorkspaceEntityType(forwardDeclarationRoots, entitySource, init)
