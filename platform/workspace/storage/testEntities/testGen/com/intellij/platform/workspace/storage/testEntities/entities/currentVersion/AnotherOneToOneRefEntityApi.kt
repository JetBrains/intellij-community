// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableAnotherOneToOneRefEntity : ModifiableWorkspaceEntity<AnotherOneToOneRefEntity> {
  override var entitySource: EntitySource
  var someString: String
  var boolean: Boolean
  var parentEntity: ModifiableOneToOneRefEntity
}

internal object AnotherOneToOneRefEntityType : EntityType<AnotherOneToOneRefEntity, ModifiableAnotherOneToOneRefEntity>() {
  override val entityClass: Class<AnotherOneToOneRefEntity> get() = AnotherOneToOneRefEntity::class.java
  operator fun invoke(
    someString: String,
    boolean: Boolean,
    entitySource: EntitySource,
    init: (ModifiableAnotherOneToOneRefEntity.() -> Unit)? = null,
  ): ModifiableAnotherOneToOneRefEntity {
    val builder = builder()
    builder.someString = someString
    builder.boolean = boolean
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAnotherOneToOneRefEntity(
  entity: AnotherOneToOneRefEntity,
  modification: ModifiableAnotherOneToOneRefEntity.() -> Unit,
): AnotherOneToOneRefEntity = modifyEntity(ModifiableAnotherOneToOneRefEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createAnotherOneToOneRefEntity")
fun AnotherOneToOneRefEntity(
  someString: String,
  boolean: Boolean,
  entitySource: EntitySource,
  init: (ModifiableAnotherOneToOneRefEntity.() -> Unit)? = null,
): ModifiableAnotherOneToOneRefEntity = AnotherOneToOneRefEntityType(someString, boolean, entitySource, init)
