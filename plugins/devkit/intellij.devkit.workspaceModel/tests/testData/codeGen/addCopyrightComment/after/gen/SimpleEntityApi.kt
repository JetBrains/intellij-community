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
  var name: String
}

internal object SimpleEntityType : EntityType<SimpleEntity, ModifiableSimpleEntity>() {
  override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (ModifiableSimpleEntity.() -> Unit)? = null,
  ): ModifiableSimpleEntity {
    val builder = builder()
    builder.name = name
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
  name: String,
  entitySource: EntitySource,
  init: (ModifiableSimpleEntity.() -> Unit)? = null,
): ModifiableSimpleEntity = SimpleEntityType(name, entitySource, init)
