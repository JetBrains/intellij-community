package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

@GeneratedCodeApiVersion(3)
interface ModifiableSimpleSymbolicIdEntity : ModifiableWorkspaceEntity<SimpleSymbolicIdEntity> {
  override var entitySource: EntitySource
  var version: Int
  var name: String
  var related: SimpleId
  var sealedClassWithLinks: SealedClassWithLinks
}

internal object SimpleSymbolicIdEntityType : EntityType<SimpleSymbolicIdEntity, ModifiableSimpleSymbolicIdEntity>() {
  override val entityClass: Class<SimpleSymbolicIdEntity> get() = SimpleSymbolicIdEntity::class.java
  operator fun invoke(
    version: Int,
    name: String,
    related: SimpleId,
    sealedClassWithLinks: SealedClassWithLinks,
    entitySource: EntitySource,
    init: (ModifiableSimpleSymbolicIdEntity.() -> Unit)? = null,
  ): ModifiableSimpleSymbolicIdEntity {
    val builder = builder()
    builder.version = version
    builder.name = name
    builder.related = related
    builder.sealedClassWithLinks = sealedClassWithLinks
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleSymbolicIdEntity(
  entity: SimpleSymbolicIdEntity,
  modification: ModifiableSimpleSymbolicIdEntity.() -> Unit,
): SimpleSymbolicIdEntity = modifyEntity(ModifiableSimpleSymbolicIdEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleSymbolicIdEntity")
fun SimpleSymbolicIdEntity(
  version: Int,
  name: String,
  related: SimpleId,
  sealedClassWithLinks: SealedClassWithLinks,
  entitySource: EntitySource,
  init: (ModifiableSimpleSymbolicIdEntity.() -> Unit)? = null,
): ModifiableSimpleSymbolicIdEntity = SimpleSymbolicIdEntityType(version, name, related, sealedClassWithLinks, entitySource, init)
