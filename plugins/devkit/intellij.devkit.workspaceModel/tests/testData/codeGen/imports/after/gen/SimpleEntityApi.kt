package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.net.URL

@GeneratedCodeApiVersion(3)
interface ModifiableSimpleEntity : ModifiableWorkspaceEntity<SimpleEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
  var parent: ModifiableEntityWithManyImports
}

internal object SimpleEntityType : EntityType<SimpleEntity, ModifiableSimpleEntity>() {
  override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableSimpleEntity.() -> Unit)? = null,
  ): ModifiableSimpleEntity {
    val builder = builder()
    builder.url = url
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
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableSimpleEntity.() -> Unit)? = null,
): ModifiableSimpleEntity = SimpleEntityType(url, entitySource, init)
