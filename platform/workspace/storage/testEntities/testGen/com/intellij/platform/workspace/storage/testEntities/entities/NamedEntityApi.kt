// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableNamedEntity : ModifiableWorkspaceEntity<NamedEntity> {
  override var entitySource: EntitySource
  var myName: String
  var additionalProperty: String?
  var children: List<ModifiableNamedChildEntity>
}

internal object NamedEntityType : EntityType<NamedEntity, ModifiableNamedEntity>() {
  override val entityClass: Class<NamedEntity> get() = NamedEntity::class.java
  operator fun invoke(
    myName: String,
    entitySource: EntitySource,
    init: (ModifiableNamedEntity.() -> Unit)? = null,
  ): ModifiableNamedEntity {
    val builder = builder()
    builder.myName = myName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNamedEntity(
  entity: NamedEntity,
  modification: ModifiableNamedEntity.() -> Unit,
): NamedEntity = modifyEntity(ModifiableNamedEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createNamedEntity")
fun NamedEntity(
  myName: String,
  entitySource: EntitySource,
  init: (ModifiableNamedEntity.() -> Unit)? = null,
): ModifiableNamedEntity = NamedEntityType(myName, entitySource, init)
