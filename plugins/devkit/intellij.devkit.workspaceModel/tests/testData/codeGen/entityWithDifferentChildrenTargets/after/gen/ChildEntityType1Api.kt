package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableChildEntityType1 : ModifiableWorkspaceEntity<ChildEntityType1> {
  override var entitySource: EntitySource
  var version: Int
  var parent: ModifiableEntityWithChildren
}

internal object ChildEntityType1Type : EntityType<ChildEntityType1, ModifiableChildEntityType1>() {
  override val entityClass: Class<ChildEntityType1> get() = ChildEntityType1::class.java
  operator fun invoke(
    version: Int,
    entitySource: EntitySource,
    init: (ModifiableChildEntityType1.() -> Unit)? = null,
  ): ModifiableChildEntityType1 {
    val builder = builder()
    builder.version = version
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildEntityType1(
  entity: ChildEntityType1,
  modification: ModifiableChildEntityType1.() -> Unit,
): ChildEntityType1 = modifyEntity(ModifiableChildEntityType1::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntityType1")
fun ChildEntityType1(
  version: Int,
  entitySource: EntitySource,
  init: (ModifiableChildEntityType1.() -> Unit)? = null,
): ModifiableChildEntityType1 = ChildEntityType1Type(version, entitySource, init)
