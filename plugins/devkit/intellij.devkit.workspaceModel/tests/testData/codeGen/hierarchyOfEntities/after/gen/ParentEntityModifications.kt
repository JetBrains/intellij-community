@file:JvmName("ParentEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Open

@GeneratedCodeApiVersion(3)
interface ParentEntityBuilder<T: ParentEntity>: WorkspaceEntityBuilder<T>, GrandParentEntityBuilder<T>{
override var entitySource: EntitySource
override var data1: String
var data2: String
}

internal object ParentEntityType : EntityType<ParentEntity, ParentEntityBuilder<ParentEntity>>(){
override val entityClass: Class<ParentEntity> get() = ParentEntity::class.java
operator fun invoke(
data1: String,
data2: String,
entitySource: EntitySource,
init: (ParentEntityBuilder<ParentEntity>.() -> Unit)? = null,
): ParentEntityBuilder<ParentEntity>{
val builder = builder()
builder.data1 = data1
builder.data2 = data2
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

@JvmOverloads
@JvmName("createParentEntity")
fun ParentEntity(
data1: String,
data2: String,
entitySource: EntitySource,
init: (ParentEntityBuilder<ParentEntity>.() -> Unit)? = null,
): ParentEntityBuilder = ParentEntityType(data1, data2, entitySource, init)
