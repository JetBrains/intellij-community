package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModifiableContentRootEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableReferredEntity : ModifiableWorkspaceEntity<ReferredEntity> {
  override var entitySource: EntitySource
  var version: Int
  var name: String
  var contentRoot: ModifiableContentRootEntity?
}

internal object ReferredEntityType : EntityType<ReferredEntity, ModifiableReferredEntity>() {
  override val entityClass: Class<ReferredEntity> get() = ReferredEntity::class.java
  operator fun invoke(
    version: Int,
    name: String,
    entitySource: EntitySource,
    init: (ModifiableReferredEntity.() -> Unit)? = null,
  ): ModifiableReferredEntity {
    val builder = builder()
    builder.version = version
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyReferredEntity(
  entity: ReferredEntity,
  modification: ModifiableReferredEntity.() -> Unit,
): ReferredEntity = modifyEntity(ModifiableReferredEntity::class.java, entity, modification)

@Parent
var ModifiableContentRootEntity.ref: ModifiableReferredEntity
  by WorkspaceEntity.extensionBuilder(ReferredEntity::class.java)


@JvmOverloads
@JvmName("createReferredEntity")
fun ReferredEntity(
  version: Int,
  name: String,
  entitySource: EntitySource,
  init: (ModifiableReferredEntity.() -> Unit)? = null,
): ModifiableReferredEntity = ReferredEntityType(version, name, entitySource, init)
