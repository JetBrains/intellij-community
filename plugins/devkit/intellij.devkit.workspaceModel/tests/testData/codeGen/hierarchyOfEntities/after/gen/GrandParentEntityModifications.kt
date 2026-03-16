@file:JvmName("GrandParentEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Open

@GeneratedCodeApiVersion(3)
interface GrandParentEntityBuilder<T: GrandParentEntity>: WorkspaceEntityBuilder<T>{
override var entitySource: EntitySource
var data1: String
}

internal object GrandParentEntityType : EntityType<GrandParentEntity, GrandParentEntityBuilder<GrandParentEntity>>(){
override val entityClass: Class<GrandParentEntity> get() = GrandParentEntity::class.java
operator fun invoke(
data1: String,
entitySource: EntitySource,
init: (GrandParentEntityBuilder<GrandParentEntity>.() -> Unit)? = null,
): GrandParentEntityBuilder<GrandParentEntity>{
val builder = builder()
builder.data1 = data1
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}
