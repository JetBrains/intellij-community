package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import java.util.Date

@GeneratedCodeApiVersion(3)
interface ModifiableUnknownPropertyTypeEntity : ModifiableWorkspaceEntity<UnknownPropertyTypeEntity> {
  override var entitySource: EntitySource
  var date: Date
}

internal object UnknownPropertyTypeEntityType : EntityType<UnknownPropertyTypeEntity, ModifiableUnknownPropertyTypeEntity>() {
  override val entityClass: Class<UnknownPropertyTypeEntity> get() = UnknownPropertyTypeEntity::class.java
  operator fun invoke(
    date: Date,
    entitySource: EntitySource,
    init: (ModifiableUnknownPropertyTypeEntity.() -> Unit)? = null,
  ): ModifiableUnknownPropertyTypeEntity {
    val builder = builder()
    builder.date = date
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyUnknownPropertyTypeEntity(
  entity: UnknownPropertyTypeEntity,
  modification: ModifiableUnknownPropertyTypeEntity.() -> Unit,
): UnknownPropertyTypeEntity = modifyEntity(ModifiableUnknownPropertyTypeEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createUnknownPropertyTypeEntity")
fun UnknownPropertyTypeEntity(
  date: Date,
  entitySource: EntitySource,
  init: (ModifiableUnknownPropertyTypeEntity.() -> Unit)? = null,
): ModifiableUnknownPropertyTypeEntity = UnknownPropertyTypeEntityType(date, entitySource, init)
