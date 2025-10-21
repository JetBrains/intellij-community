@file:JvmName("SimpleParentByExtensionModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface SimpleParentByExtensionBuilder : WorkspaceEntityBuilder<SimpleParentByExtension> {
  override var entitySource: EntitySource
  var simpleName: String
  var simpleChild: SimpleEntityBuilder?
}

internal object SimpleParentByExtensionType : EntityType<SimpleParentByExtension, SimpleParentByExtensionBuilder>() {
  override val entityClass: Class<SimpleParentByExtension> get() = SimpleParentByExtension::class.java
  operator fun invoke(
    simpleName: String,
    entitySource: EntitySource,
    init: (SimpleParentByExtensionBuilder.() -> Unit)? = null,
  ): SimpleParentByExtensionBuilder {
    val builder = builder()
    builder.simpleName = simpleName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    simpleName: String,
    entitySource: EntitySource,
    init: (SimpleParentByExtension.Builder.() -> Unit)? = null,
  ): SimpleParentByExtension.Builder {
    val builder = builder() as SimpleParentByExtension.Builder
    builder.simpleName = simpleName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimpleParentByExtension(
  entity: SimpleParentByExtension,
  modification: SimpleParentByExtensionBuilder.() -> Unit,
): SimpleParentByExtension = modifyEntity(SimpleParentByExtensionBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleParentByExtension")
fun SimpleParentByExtension(
  simpleName: String,
  entitySource: EntitySource,
  init: (SimpleParentByExtensionBuilder.() -> Unit)? = null,
): SimpleParentByExtensionBuilder = SimpleParentByExtensionType(simpleName, entitySource, init)
