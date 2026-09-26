@file:JvmName("SimpleEntityModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.test.api.impl.SimpleEntityImpl

@GeneratedCodeApiVersion(3)
interface SimpleEntityBuilder: WorkspaceEntityBuilder<SimpleEntity>{
override var entitySource: EntitySource
var name: String
var relatedId: SimpleSymbolicId?
}
internal object SimpleEntityType : EntityType<SimpleEntity, SimpleEntityBuilder>(){
override val entityImplClass: Class<*> get() = SimpleEntityImpl::class.java
override val entityImplBuilderClass: Class<*> get() = SimpleEntityImpl.Builder::class.java
operator fun invoke(
name: String,
entitySource: EntitySource,
init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder{
val builder = builder()
builder.name = name
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}
fun MutableEntityStorage.modifySimpleEntity(
entity: SimpleEntity,
modification: SimpleEntityBuilder.() -> Unit,
): SimpleEntity = modifyEntity(SimpleEntityBuilder::class.java, entity, modification)
@JvmOverloads
@JvmName("createSimpleEntity")
fun SimpleEntity(
name: String,
entitySource: EntitySource,
init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder = SimpleEntityType(name, entitySource, init)
