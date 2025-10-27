@file:JvmName("SimpleEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.net.URL

@GeneratedCodeApiVersion(3)
interface SimpleEntityBuilder : WorkspaceEntityBuilder<SimpleEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
  var parent: EntityWithManyImportsBuilder
}

internal object SimpleEntityType : EntityType<SimpleEntity, SimpleEntityBuilder>() {
  override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (SimpleEntityBuilder.() -> Unit)? = null,
  ): SimpleEntityBuilder {
    val builder = builder()
    builder.url = url
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleEntity(
  entity: SimpleEntity,
  modification: SimpleEntityBuilder.() -> Unit,
): SimpleEntity = modifyEntity(SimpleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleEntity")
fun SimpleEntity(
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder = SimpleEntityType(url, entitySource, init)
