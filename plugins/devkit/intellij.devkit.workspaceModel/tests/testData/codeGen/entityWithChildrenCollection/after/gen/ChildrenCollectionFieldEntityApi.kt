package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableChildrenCollectionFieldEntity : ModifiableWorkspaceEntity<ChildrenCollectionFieldEntity> {
  override var entitySource: EntitySource
  var name: String
  var childrenEntitiesCollection: List<ModifiableSimpleEntity>
}

internal object ChildrenCollectionFieldEntityType : EntityType<ChildrenCollectionFieldEntity, ModifiableChildrenCollectionFieldEntity>() {
  override val entityClass: Class<ChildrenCollectionFieldEntity> get() = ChildrenCollectionFieldEntity::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (ModifiableChildrenCollectionFieldEntity.() -> Unit)? = null,
  ): ModifiableChildrenCollectionFieldEntity {
    val builder = builder()
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildrenCollectionFieldEntity(
  entity: ChildrenCollectionFieldEntity,
  modification: ModifiableChildrenCollectionFieldEntity.() -> Unit,
): ChildrenCollectionFieldEntity = modifyEntity(ModifiableChildrenCollectionFieldEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildrenCollectionFieldEntity")
fun ChildrenCollectionFieldEntity(
  name: String,
  entitySource: EntitySource,
  init: (ModifiableChildrenCollectionFieldEntity.() -> Unit)? = null,
): ModifiableChildrenCollectionFieldEntity = ChildrenCollectionFieldEntityType(name, entitySource, init)
