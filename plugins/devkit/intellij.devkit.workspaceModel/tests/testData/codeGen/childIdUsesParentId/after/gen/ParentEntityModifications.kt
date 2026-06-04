@file:JvmName("ParentEntityModifications")
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
import com.intellij.workspaceModel.test.api.impl.ParentEntityImpl

@GeneratedCodeApiVersion(3)
interface ParentEntityBuilder: WorkspaceEntityBuilder<ParentEntity>{
override var entitySource: EntitySource
var name: String
}

internal object ParentEntityType : EntityType<ParentEntity, ParentEntityBuilder>(){
override val entityClass: Class<ParentEntity> get() = ParentEntity::class.java
override val entityImplBuilderClass: Class<*> get() = ParentEntityImpl.Builder::class.java
operator fun invoke(
name: String,
entitySource: EntitySource,
init: (ParentEntityBuilder.() -> Unit)? = null,
): ParentEntityBuilder{
val builder = builder()
builder.name = name
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyParentEntity(
entity: ParentEntity,
modification: ParentEntityBuilder.() -> Unit,
): ParentEntity = modifyEntity(ParentEntityBuilder::class.java, entity, modification)
var ParentEntityBuilder.child: ChildEntityBuilder
by WorkspaceEntity.extensionBuilder(ChildEntity::class.java)


@JvmOverloads
@JvmName("createParentEntity")
fun ParentEntity(
name: String,
entitySource: EntitySource,
init: (ParentEntityBuilder.() -> Unit)? = null,
): ParentEntityBuilder = ParentEntityType(name, entitySource, init)
