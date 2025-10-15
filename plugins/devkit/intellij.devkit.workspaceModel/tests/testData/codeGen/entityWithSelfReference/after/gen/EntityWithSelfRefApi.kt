package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableEntityWithSelfRef : ModifiableWorkspaceEntity<EntityWithSelfRef> {
  override var entitySource: EntitySource
  var name: String
  var parentRef: ModifiableEntityWithSelfRef?
  var children: List<ModifiableEntityWithSelfRef>
}

internal object EntityWithSelfRefType : EntityType<EntityWithSelfRef, ModifiableEntityWithSelfRef>() {
  override val entityClass: Class<EntityWithSelfRef> get() = EntityWithSelfRef::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (ModifiableEntityWithSelfRef.() -> Unit)? = null,
  ): ModifiableEntityWithSelfRef {
    val builder = builder()
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEntityWithSelfRef(
  entity: EntityWithSelfRef,
  modification: ModifiableEntityWithSelfRef.() -> Unit,
): EntityWithSelfRef = modifyEntity(ModifiableEntityWithSelfRef::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithSelfRef")
fun EntityWithSelfRef(
  name: String,
  entitySource: EntitySource,
  init: (ModifiableEntityWithSelfRef.() -> Unit)? = null,
): ModifiableEntityWithSelfRef = EntityWithSelfRefType(name, entitySource, init)
