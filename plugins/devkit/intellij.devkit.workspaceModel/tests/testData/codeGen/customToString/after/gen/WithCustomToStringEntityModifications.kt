@file:JvmName("WithCustomToStringEntityModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.ToString
import com.intellij.workspaceModel.test.api.impl.WithCustomToStringEntityImpl

@GeneratedCodeApiVersion(3)
interface WithCustomToStringEntityBuilder: WorkspaceEntityBuilder<WithCustomToStringEntity>{
override var entitySource: EntitySource
var myName: String
var version: Int
}
internal object WithCustomToStringEntityType : EntityType<WithCustomToStringEntity, WithCustomToStringEntityBuilder>(){
override val entityImplClass: Class<*> get() = WithCustomToStringEntityImpl::class.java
override val entityImplBuilderClass: Class<*> get() = WithCustomToStringEntityImpl.Builder::class.java
operator fun invoke(
myName: String,
version: Int,
entitySource: EntitySource,
init: (WithCustomToStringEntityBuilder.() -> Unit)? = null,
): WithCustomToStringEntityBuilder{
val builder = builder()
builder.myName = myName
builder.version = version
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}
fun MutableEntityStorage.modifyWithCustomToStringEntity(
entity: WithCustomToStringEntity,
modification: WithCustomToStringEntityBuilder.() -> Unit,
): WithCustomToStringEntity = modifyEntity(WithCustomToStringEntityBuilder::class.java, entity, modification)
@JvmOverloads
@JvmName("createWithCustomToStringEntity")
fun WithCustomToStringEntity(
myName: String,
version: Int,
entitySource: EntitySource,
init: (WithCustomToStringEntityBuilder.() -> Unit)? = null,
): WithCustomToStringEntityBuilder = WithCustomToStringEntityType(myName, version, entitySource, init)
