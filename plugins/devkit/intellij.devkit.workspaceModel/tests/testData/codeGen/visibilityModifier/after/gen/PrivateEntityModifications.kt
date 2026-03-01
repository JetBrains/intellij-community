@file:JvmName("PrivateEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
private interface PrivateEntityBuilder: WorkspaceEntityBuilder<PrivateEntity>{
override var entitySource: EntitySource
var name: String
}

private object PrivateEntityType : EntityType<PrivateEntity, PrivateEntityBuilder>(){
override val entityClass: Class<PrivateEntity> get() = PrivateEntity::class.java
operator fun invoke(
name: String,
entitySource: EntitySource,
init: (PrivateEntityBuilder.() -> Unit)? = null,
): PrivateEntityBuilder{
val builder = builder()
builder.name = name
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

private fun MutableEntityStorage.modifyPrivateEntity(
entity: PrivateEntity,
modification: PrivateEntityBuilder.() -> Unit,
): PrivateEntity = modifyEntity(PrivateEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createPrivateEntity")
private fun PrivateEntity(
name: String,
entitySource: EntitySource,
init: (PrivateEntityBuilder.() -> Unit)? = null,
): PrivateEntityBuilder = PrivateEntityType(name, entitySource, init)
