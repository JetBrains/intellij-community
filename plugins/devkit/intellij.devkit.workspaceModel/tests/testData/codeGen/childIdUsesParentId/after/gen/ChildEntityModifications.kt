@file:JvmName("ChildEntityModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.workspaceModel.test.api.impl.ChildEntityImpl

@GeneratedCodeApiVersion(3)
interface ChildEntityBuilder: WorkspaceEntityBuilder<ChildEntity>{
override var entitySource: EntitySource
var name: String
var parentEntity: ParentEntityBuilder
}

internal object ChildEntityType : EntityType<ChildEntity, ChildEntityBuilder>(){
override val entityClass: Class<ChildEntity> get() = ChildEntity::class.java
override val entityImplBuilderClass: Class<*> get() = ChildEntityImpl.Builder::class.java
operator fun invoke(
name: String,
entitySource: EntitySource,
init: (ChildEntityBuilder.() -> Unit)? = null,
): ChildEntityBuilder{
val builder = builder()
builder.name = name
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyChildEntity(
entity: ChildEntity,
modification: ChildEntityBuilder.() -> Unit,
): ChildEntity = modifyEntity(ChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntity")
fun ChildEntity(
name: String,
entitySource: EntitySource,
init: (ChildEntityBuilder.() -> Unit)? = null,
): ChildEntityBuilder = ChildEntityType(name, entitySource, init)
