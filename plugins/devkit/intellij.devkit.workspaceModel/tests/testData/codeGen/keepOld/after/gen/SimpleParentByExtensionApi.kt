package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableSimpleParentByExtension : ModifiableWorkspaceEntity<SimpleParentByExtension> {
  override var entitySource: EntitySource
  var simpleName: String
  var simpleChild: ModifiableSimpleEntity?
}

internal object SimpleParentByExtensionType : EntityType<SimpleParentByExtension, ModifiableSimpleParentByExtension>() {
  override val entityClass: Class<SimpleParentByExtension> get() = SimpleParentByExtension::class.java
  operator fun invoke(
    simpleName: String,
    entitySource: EntitySource,
    init: (ModifiableSimpleParentByExtension.() -> Unit)? = null,
  ): ModifiableSimpleParentByExtension {
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
  modification: ModifiableSimpleParentByExtension.() -> Unit,
): SimpleParentByExtension = modifyEntity(ModifiableSimpleParentByExtension::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleParentByExtension")
fun SimpleParentByExtension(
  simpleName: String,
  entitySource: EntitySource,
  init: (ModifiableSimpleParentByExtension.() -> Unit)? = null,
): ModifiableSimpleParentByExtension = SimpleParentByExtensionType(simpleName, entitySource, init)
