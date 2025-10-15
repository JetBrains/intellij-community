package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableSimpleEntity : ModifiableWorkspaceEntity<SimpleEntity> {
  override var entitySource: EntitySource
  var version: Int
  var name: String
}

internal object SimpleEntityType : EntityType<SimpleEntity, ModifiableSimpleEntity>() {
  override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
  operator fun invoke(
    version: Int,
    name: String,
    entitySource: EntitySource,
    init: (ModifiableSimpleEntity.() -> Unit)? = null,
  ): ModifiableSimpleEntity {
    val builder = builder()
    builder.version = version
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    version: Int,
    name: String,
    entitySource: EntitySource,
    init: (SimpleEntity.Builder.() -> Unit)? = null,
  ): SimpleEntity.Builder {
    val builder = builder() as SimpleEntity.Builder
    builder.version = version
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

@Parent
var ModifiableSimpleEntity.simpleParent: ModifiableSimpleParentByExtension
  by WorkspaceEntity.extensionBuilder(SimpleParentByExtension::class.java)

@JvmOverloads
@JvmName("createSimpleEntity")
fun SimpleEntity(
  version: Int,
  name: String,
  entitySource: EntitySource,
  init: (ModifiableSimpleEntity.() -> Unit)? = null,
): ModifiableSimpleEntity = SimpleEntityType(version, name, entitySource, init)
