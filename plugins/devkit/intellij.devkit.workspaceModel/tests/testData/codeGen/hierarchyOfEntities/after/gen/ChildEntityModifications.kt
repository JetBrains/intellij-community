@file:JvmName("ChildEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Open

@GeneratedCodeApiVersion(3)
interface ChildEntityBuilder: WorkspaceEntityBuilder<ChildEntity>, ParentEntityBuilder<ChildEntity>{
override var entitySource: EntitySource
override var data1: String
override var data2: String
var data3: String
}

internal object ChildEntityType : EntityType<ChildEntity, ChildEntityBuilder>(){
override val entityClass: Class<ChildEntity> get() = ChildEntity::class.java
operator fun invoke(
data1: String,
data2: String,
data3: String,
entitySource: EntitySource,
init: (ChildEntityBuilder.() -> Unit)? = null,
): ChildEntityBuilder{
val builder = builder()
builder.data1 = data1
builder.data2 = data2
builder.data3 = data3
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
data1: String,
data2: String,
data3: String,
entitySource: EntitySource,
init: (ChildEntityBuilder.() -> Unit)? = null,
): ChildEntityBuilder = ChildEntityType(data1, data2, data3, entitySource, init)
