// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableComposedIdSoftRefEntity : ModifiableWorkspaceEntity<ComposedIdSoftRefEntity> {
  override var entitySource: EntitySource
  var myName: String
  var link: NameId
}

internal object ComposedIdSoftRefEntityType : EntityType<ComposedIdSoftRefEntity, ModifiableComposedIdSoftRefEntity>() {
  override val entityClass: Class<ComposedIdSoftRefEntity> get() = ComposedIdSoftRefEntity::class.java
  operator fun invoke(
    myName: String,
    link: NameId,
    entitySource: EntitySource,
    init: (ModifiableComposedIdSoftRefEntity.() -> Unit)? = null,
  ): ModifiableComposedIdSoftRefEntity {
    val builder = builder()
    builder.myName = myName
    builder.link = link
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyComposedIdSoftRefEntity(
  entity: ComposedIdSoftRefEntity,
  modification: ModifiableComposedIdSoftRefEntity.() -> Unit,
): ComposedIdSoftRefEntity = modifyEntity(ModifiableComposedIdSoftRefEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createComposedIdSoftRefEntity")
fun ComposedIdSoftRefEntity(
  myName: String,
  link: NameId,
  entitySource: EntitySource,
  init: (ModifiableComposedIdSoftRefEntity.() -> Unit)? = null,
): ModifiableComposedIdSoftRefEntity = ComposedIdSoftRefEntityType(myName, link, entitySource, init)
