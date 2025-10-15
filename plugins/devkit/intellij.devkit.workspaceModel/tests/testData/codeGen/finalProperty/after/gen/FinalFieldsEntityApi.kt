package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableFinalFieldsEntity : ModifiableWorkspaceEntity<FinalFieldsEntity> {
  override var entitySource: EntitySource
  var descriptor: AnotherDataClass
}

internal object FinalFieldsEntityType : EntityType<FinalFieldsEntity, ModifiableFinalFieldsEntity>() {
  override val entityClass: Class<FinalFieldsEntity> get() = FinalFieldsEntity::class.java
  operator fun invoke(
    descriptor: AnotherDataClass,
    entitySource: EntitySource,
    init: (ModifiableFinalFieldsEntity.() -> Unit)? = null,
  ): ModifiableFinalFieldsEntity {
    val builder = builder()
    builder.descriptor = descriptor
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyFinalFieldsEntity(
  entity: FinalFieldsEntity,
  modification: ModifiableFinalFieldsEntity.() -> Unit,
): FinalFieldsEntity = modifyEntity(ModifiableFinalFieldsEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createFinalFieldsEntity")
fun FinalFieldsEntity(
  descriptor: AnotherDataClass,
  entitySource: EntitySource,
  init: (ModifiableFinalFieldsEntity.() -> Unit)? = null,
): ModifiableFinalFieldsEntity = FinalFieldsEntityType(descriptor, entitySource, init)
