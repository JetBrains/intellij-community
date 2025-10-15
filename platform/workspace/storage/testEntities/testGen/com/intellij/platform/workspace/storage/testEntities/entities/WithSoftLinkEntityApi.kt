// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableWithSoftLinkEntity : ModifiableWorkspaceEntity<WithSoftLinkEntity> {
  override var entitySource: EntitySource
  var link: NameId
}

internal object WithSoftLinkEntityType : EntityType<WithSoftLinkEntity, ModifiableWithSoftLinkEntity>() {
  override val entityClass: Class<WithSoftLinkEntity> get() = WithSoftLinkEntity::class.java
  operator fun invoke(
    link: NameId,
    entitySource: EntitySource,
    init: (ModifiableWithSoftLinkEntity.() -> Unit)? = null,
  ): ModifiableWithSoftLinkEntity {
    val builder = builder()
    builder.link = link
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyWithSoftLinkEntity(
  entity: WithSoftLinkEntity,
  modification: ModifiableWithSoftLinkEntity.() -> Unit,
): WithSoftLinkEntity = modifyEntity(ModifiableWithSoftLinkEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createWithSoftLinkEntity")
fun WithSoftLinkEntity(
  link: NameId,
  entitySource: EntitySource,
  init: (ModifiableWithSoftLinkEntity.() -> Unit)? = null,
): ModifiableWithSoftLinkEntity = WithSoftLinkEntityType(link, entitySource, init)
