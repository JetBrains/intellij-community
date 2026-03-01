@file:JvmName("ChildrenCollectionFieldEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ChildrenCollectionFieldEntityBuilder: WorkspaceEntityBuilder<ChildrenCollectionFieldEntity>{
override var entitySource: EntitySource
var name: String
var childrenEntitiesCollection: List<SimpleEntityBuilder>
}

internal object ChildrenCollectionFieldEntityType : EntityType<ChildrenCollectionFieldEntity, ChildrenCollectionFieldEntityBuilder>(){
override val entityClass: Class<ChildrenCollectionFieldEntity> get() = ChildrenCollectionFieldEntity::class.java
operator fun invoke(
name: String,
entitySource: EntitySource,
init: (ChildrenCollectionFieldEntityBuilder.() -> Unit)? = null,
): ChildrenCollectionFieldEntityBuilder{
val builder = builder()
builder.name = name
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyChildrenCollectionFieldEntity(
entity: ChildrenCollectionFieldEntity,
modification: ChildrenCollectionFieldEntityBuilder.() -> Unit,
): ChildrenCollectionFieldEntity = modifyEntity(ChildrenCollectionFieldEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildrenCollectionFieldEntity")
fun ChildrenCollectionFieldEntity(
name: String,
entitySource: EntitySource,
init: (ChildrenCollectionFieldEntityBuilder.() -> Unit)? = null,
): ChildrenCollectionFieldEntityBuilder = ChildrenCollectionFieldEntityType(name, entitySource, init)
