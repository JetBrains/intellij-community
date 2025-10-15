package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableEntityWithChildren : ModifiableWorkspaceEntity<EntityWithChildren> {
  override var entitySource: EntitySource
  var name: String
  var propertyChild: ModifiableChildEntityType1?
  var typeChild: ModifiableChildEntityType2?
}

internal object EntityWithChildrenType : EntityType<EntityWithChildren, ModifiableEntityWithChildren>() {
  override val entityClass: Class<EntityWithChildren> get() = EntityWithChildren::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (ModifiableEntityWithChildren.() -> Unit)? = null,
  ): ModifiableEntityWithChildren {
    val builder = builder()
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEntityWithChildren(
  entity: EntityWithChildren,
  modification: ModifiableEntityWithChildren.() -> Unit,
): EntityWithChildren = modifyEntity(ModifiableEntityWithChildren::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithChildren")
fun EntityWithChildren(
  name: String,
  entitySource: EntitySource,
  init: (ModifiableEntityWithChildren.() -> Unit)? = null,
): ModifiableEntityWithChildren = EntityWithChildrenType(name, entitySource, init)
