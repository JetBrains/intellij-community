@file:JvmName("EntityWithSelfRefModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface EntityWithSelfRefBuilder: WorkspaceEntityBuilder<EntityWithSelfRef>{
override var entitySource: EntitySource
var name: String
var parentRef: EntityWithSelfRefBuilder?
var children: List<EntityWithSelfRefBuilder>
}

internal object EntityWithSelfRefType : EntityType<EntityWithSelfRef, EntityWithSelfRefBuilder>(){
override val entityClass: Class<EntityWithSelfRef> get() = EntityWithSelfRef::class.java
operator fun invoke(
name: String,
entitySource: EntitySource,
init: (EntityWithSelfRefBuilder.() -> Unit)? = null,
): EntityWithSelfRefBuilder{
val builder = builder()
builder.name = name
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyEntityWithSelfRef(
entity: EntityWithSelfRef,
modification: EntityWithSelfRefBuilder.() -> Unit,
): EntityWithSelfRef = modifyEntity(EntityWithSelfRefBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithSelfRef")
fun EntityWithSelfRef(
name: String,
entitySource: EntitySource,
init: (EntityWithSelfRefBuilder.() -> Unit)? = null,
): EntityWithSelfRefBuilder = EntityWithSelfRefType(name, entitySource, init)
