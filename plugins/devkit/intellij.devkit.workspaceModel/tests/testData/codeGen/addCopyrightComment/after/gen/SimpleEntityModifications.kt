@file:JvmName("SimpleEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface SimpleEntityBuilder : WorkspaceEntityBuilder<SimpleEntity> {
  override var entitySource: EntitySource
  var name: String
}

internal object SimpleEntityType : EntityType<SimpleEntity, SimpleEntityBuilder>() {
  override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (SimpleEntityBuilder.() -> Unit)? = null,
  ): SimpleEntityBuilder {
    val builder = builder()
    builder.name = name
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
  name: String,
  entitySource: EntitySource,
  init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder = SimpleEntityType(name, entitySource, init)
