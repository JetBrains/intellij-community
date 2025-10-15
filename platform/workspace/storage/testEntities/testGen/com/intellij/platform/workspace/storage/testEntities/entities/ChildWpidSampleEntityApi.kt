// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableChildWpidSampleEntity : ModifiableWorkspaceEntity<ChildWpidSampleEntity> {
  override var entitySource: EntitySource
  var data: String
  var parentEntity: ModifiableSampleWithSymbolicIdEntity?
}

internal object ChildWpidSampleEntityType : EntityType<ChildWpidSampleEntity, ModifiableChildWpidSampleEntity>() {
  override val entityClass: Class<ChildWpidSampleEntity> get() = ChildWpidSampleEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableChildWpidSampleEntity.() -> Unit)? = null,
  ): ModifiableChildWpidSampleEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildWpidSampleEntity(
  entity: ChildWpidSampleEntity,
  modification: ModifiableChildWpidSampleEntity.() -> Unit,
): ChildWpidSampleEntity = modifyEntity(ModifiableChildWpidSampleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildWpidSampleEntity")
fun ChildWpidSampleEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableChildWpidSampleEntity.() -> Unit)? = null,
): ModifiableChildWpidSampleEntity = ChildWpidSampleEntityType(data, entitySource, init)
