@file:JvmName("EntityWithManyImportsModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.net.URL

@GeneratedCodeApiVersion(3)
interface EntityWithManyImportsBuilder : WorkspaceEntityBuilder<EntityWithManyImports> {
  override var entitySource: EntitySource
  var version: Int
  var name: String
  var files: List<SimpleEntityBuilder>
  var pointer: EntityPointer
}

internal object EntityWithManyImportsType : EntityType<EntityWithManyImports, EntityWithManyImportsBuilder>() {
  override val entityClass: Class<EntityWithManyImports> get() = EntityWithManyImports::class.java
  operator fun invoke(
    version: Int,
    name: String,
    pointer: EntityPointer,
    entitySource: EntitySource,
    init: (EntityWithManyImportsBuilder.() -> Unit)? = null,
  ): EntityWithManyImportsBuilder {
    val builder = builder()
    builder.version = version
    builder.name = name
    builder.pointer = pointer
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEntityWithManyImports(
  entity: EntityWithManyImports,
  modification: EntityWithManyImportsBuilder.() -> Unit,
): EntityWithManyImports = modifyEntity(EntityWithManyImportsBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithManyImports")
fun EntityWithManyImports(
  version: Int,
  name: String,
  pointer: EntityPointer,
  entitySource: EntitySource,
  init: (EntityWithManyImportsBuilder.() -> Unit)? = null,
): EntityWithManyImportsBuilder = EntityWithManyImportsType(version, name, pointer, entitySource, init)
