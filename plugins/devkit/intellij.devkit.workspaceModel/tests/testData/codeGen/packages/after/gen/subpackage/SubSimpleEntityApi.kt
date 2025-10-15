package com.intellij.workspaceModel.test.api.subpackage

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableSubSimpleEntity : ModifiableWorkspaceEntity<SubSimpleEntity> {
  override var entitySource: EntitySource
  var version: Int
  var name: String
  var isSimple: Boolean
}

internal object SubSimpleEntityType : EntityType<SubSimpleEntity, ModifiableSubSimpleEntity>() {
  override val entityClass: Class<SubSimpleEntity> get() = SubSimpleEntity::class.java
  operator fun invoke(
    version: Int,
    name: String,
    isSimple: Boolean,
    entitySource: EntitySource,
    init: (ModifiableSubSimpleEntity.() -> Unit)? = null,
  ): ModifiableSubSimpleEntity {
    val builder = builder()
    builder.version = version
    builder.name = name
    builder.isSimple = isSimple
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySubSimpleEntity(
  entity: SubSimpleEntity,
  modification: ModifiableSubSimpleEntity.() -> Unit,
): SubSimpleEntity = modifyEntity(ModifiableSubSimpleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSubSimpleEntity")
fun SubSimpleEntity(
  version: Int,
  name: String,
  isSimple: Boolean,
  entitySource: EntitySource,
  init: (ModifiableSubSimpleEntity.() -> Unit)? = null,
): ModifiableSubSimpleEntity = SubSimpleEntityType(version, name, isSimple, entitySource, init)
