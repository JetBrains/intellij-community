// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID

@GeneratedCodeApiVersion(3)
interface ModifiableSecondSampleEntity : ModifiableWorkspaceEntity<SecondSampleEntity> {
  override var entitySource: EntitySource
  var intProperty: Int
}

internal object SecondSampleEntityType : EntityType<SecondSampleEntity, ModifiableSecondSampleEntity>() {
  override val entityClass: Class<SecondSampleEntity> get() = SecondSampleEntity::class.java
  operator fun invoke(
    intProperty: Int,
    entitySource: EntitySource,
    init: (ModifiableSecondSampleEntity.() -> Unit)? = null,
  ): ModifiableSecondSampleEntity {
    val builder = builder()
    builder.intProperty = intProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySecondSampleEntity(
  entity: SecondSampleEntity,
  modification: ModifiableSecondSampleEntity.() -> Unit,
): SecondSampleEntity = modifyEntity(ModifiableSecondSampleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSecondSampleEntity")
fun SecondSampleEntity(
  intProperty: Int,
  entitySource: EntitySource,
  init: (ModifiableSecondSampleEntity.() -> Unit)? = null,
): ModifiableSecondSampleEntity = SecondSampleEntityType(intProperty, entitySource, init)
