// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableComposedLinkEntity : ModifiableWorkspaceEntity<ComposedLinkEntity> {
  override var entitySource: EntitySource
  var link: ComposedId
}

internal object ComposedLinkEntityType : EntityType<ComposedLinkEntity, ModifiableComposedLinkEntity>() {
  override val entityClass: Class<ComposedLinkEntity> get() = ComposedLinkEntity::class.java
  operator fun invoke(
    link: ComposedId,
    entitySource: EntitySource,
    init: (ModifiableComposedLinkEntity.() -> Unit)? = null,
  ): ModifiableComposedLinkEntity {
    val builder = builder()
    builder.link = link
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyComposedLinkEntity(
  entity: ComposedLinkEntity,
  modification: ModifiableComposedLinkEntity.() -> Unit,
): ComposedLinkEntity = modifyEntity(ModifiableComposedLinkEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createComposedLinkEntity")
fun ComposedLinkEntity(
  link: ComposedId,
  entitySource: EntitySource,
  init: (ModifiableComposedLinkEntity.() -> Unit)? = null,
): ModifiableComposedLinkEntity = ComposedLinkEntityType(link, entitySource, init)
