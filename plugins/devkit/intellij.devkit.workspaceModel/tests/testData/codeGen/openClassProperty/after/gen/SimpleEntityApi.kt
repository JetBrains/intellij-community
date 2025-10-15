package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableSimpleEntity : ModifiableWorkspaceEntity<SimpleEntity> {
  override var entitySource: EntitySource
  var info: String
  var descriptor: Descriptor
}

internal object SimpleEntityType : EntityType<SimpleEntity, ModifiableSimpleEntity>() {
  override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
  operator fun invoke(
    info: String,
    descriptor: Descriptor,
    entitySource: EntitySource,
    init: (ModifiableSimpleEntity.() -> Unit)? = null,
  ): ModifiableSimpleEntity {
    val builder = builder()
    builder.info = info
    builder.descriptor = descriptor
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleEntity(
  entity: SimpleEntity,
  modification: ModifiableSimpleEntity.() -> Unit,
): SimpleEntity = modifyEntity(ModifiableSimpleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleEntity")
fun SimpleEntity(
  info: String,
  descriptor: Descriptor,
  entitySource: EntitySource,
  init: (ModifiableSimpleEntity.() -> Unit)? = null,
): ModifiableSimpleEntity = SimpleEntityType(info, descriptor, entitySource, init)
