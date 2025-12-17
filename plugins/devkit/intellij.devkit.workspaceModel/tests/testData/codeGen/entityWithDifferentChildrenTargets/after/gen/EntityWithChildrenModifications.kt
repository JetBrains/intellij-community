@file:JvmName("EntityWithChildrenModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface EntityWithChildrenBuilder: WorkspaceEntityBuilder<EntityWithChildren>{
override var entitySource: EntitySource
var name: String
var propertyChild: ChildEntityType1Builder?
var typeChild: ChildEntityType2Builder?
}

internal object EntityWithChildrenType : EntityType<EntityWithChildren, EntityWithChildrenBuilder>(){
override val entityClass: Class<EntityWithChildren> get() = EntityWithChildren::class.java
operator fun invoke(
name: String,
entitySource: EntitySource,
init: (EntityWithChildrenBuilder.() -> Unit)? = null,
): EntityWithChildrenBuilder{
val builder = builder()
builder.name = name
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyEntityWithChildren(
entity: EntityWithChildren,
modification: EntityWithChildrenBuilder.() -> Unit,
): EntityWithChildren = modifyEntity(EntityWithChildrenBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithChildren")
fun EntityWithChildren(
name: String,
entitySource: EntitySource,
init: (EntityWithChildrenBuilder.() -> Unit)? = null,
): EntityWithChildrenBuilder = EntityWithChildrenType(name, entitySource, init)
