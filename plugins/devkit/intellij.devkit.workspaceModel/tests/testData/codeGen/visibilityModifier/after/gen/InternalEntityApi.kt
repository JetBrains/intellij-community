package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableInternalEntity : ModifiableWorkspaceEntity<InternalEntity> {
  override var entitySource: EntitySource
  var version: Int
  var name: String
  var isSimple: Boolean
}

internal object InternalEntityType : EntityType<InternalEntity, ModifiableInternalEntity>() {
  override val entityClass: Class<InternalEntity> get() = InternalEntity::class.java
  operator fun invoke(
    version: Int,
    name: String,
    isSimple: Boolean,
    entitySource: EntitySource,
    init: (ModifiableInternalEntity.() -> Unit)? = null,
  ): ModifiableInternalEntity {
    val builder = builder()
    builder.version = version
    builder.name = name
    builder.isSimple = isSimple
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyInternalEntity(
  entity: InternalEntity,
  modification: ModifiableInternalEntity.() -> Unit,
): InternalEntity = modifyEntity(ModifiableInternalEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createInternalEntity")
fun InternalEntity(
  version: Int,
  name: String,
  isSimple: Boolean,
  entitySource: EntitySource,
  init: (ModifiableInternalEntity.() -> Unit)? = null,
): ModifiableInternalEntity = InternalEntityType(version, name, isSimple, entitySource, init)
