package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableChildEntityType2 : ModifiableWorkspaceEntity<ChildEntityType2> {
  override var entitySource: EntitySource
  var version: Int
  var parent: ModifiableEntityWithChildren
}

internal object ChildEntityType2Type : EntityType<ChildEntityType2, ModifiableChildEntityType2>() {
  override val entityClass: Class<ChildEntityType2> get() = ChildEntityType2::class.java
  operator fun invoke(
    version: Int,
    entitySource: EntitySource,
    init: (ModifiableChildEntityType2.() -> Unit)? = null,
  ): ModifiableChildEntityType2 {
    val builder = builder()
    builder.version = version
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildEntityType2(
  entity: ChildEntityType2,
  modification: ModifiableChildEntityType2.() -> Unit,
): ChildEntityType2 = modifyEntity(ModifiableChildEntityType2::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntityType2")
fun ChildEntityType2(
  version: Int,
  entitySource: EntitySource,
  init: (ModifiableChildEntityType2.() -> Unit)? = null,
): ModifiableChildEntityType2 = ChildEntityType2Type(version, entitySource, init)
