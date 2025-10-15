package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiablePrivateEntity : ModifiableWorkspaceEntity<PrivateEntity> {
  override var entitySource: EntitySource
  var name: String
}

internal object PrivateEntityType : EntityType<PrivateEntity, ModifiablePrivateEntity>() {
  override val entityClass: Class<PrivateEntity> get() = PrivateEntity::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (ModifiablePrivateEntity.() -> Unit)? = null,
  ): ModifiablePrivateEntity {
    val builder = builder()
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyPrivateEntity(
  entity: PrivateEntity,
  modification: ModifiablePrivateEntity.() -> Unit,
): PrivateEntity = modifyEntity(ModifiablePrivateEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createPrivateEntity")
fun PrivateEntity(
  name: String,
  entitySource: EntitySource,
  init: (ModifiablePrivateEntity.() -> Unit)? = null,
): ModifiablePrivateEntity = PrivateEntityType(name, entitySource, init)
