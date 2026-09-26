@file:JvmName("CustomEntityModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.test.api.impl.CustomEntityImpl

@GeneratedCodeApiVersion(3)
interface CustomEntityBuilder: WorkspaceEntityBuilder<CustomEntity>{
override var entitySource: EntitySource
var name: String
var hasSuper: Boolean
var hasSuperSuper: Boolean
var url: VirtualFileUrl
}
internal object CustomEntityType : EntityType<CustomEntity, CustomEntityBuilder>(){
override val entityImplClass: Class<*> get() = CustomEntityImpl::class.java
override val entityImplBuilderClass: Class<*> get() = CustomEntityImpl.Builder::class.java
operator fun invoke(
name: String,
hasSuper: Boolean,
hasSuperSuper: Boolean,
url: VirtualFileUrl,
entitySource: EntitySource,
init: (CustomEntityBuilder.() -> Unit)? = null,
): CustomEntityBuilder{
val builder = builder()
builder.name = name
builder.hasSuper = hasSuper
builder.hasSuperSuper = hasSuperSuper
builder.url = url
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}
fun MutableEntityStorage.modifyCustomEntity(
entity: CustomEntity,
modification: CustomEntityBuilder.() -> Unit,
): CustomEntity = modifyEntity(CustomEntityBuilder::class.java, entity, modification)
@JvmOverloads
@JvmName("createCustomEntity")
fun CustomEntity(
name: String,
hasSuper: Boolean,
hasSuperSuper: Boolean,
url: VirtualFileUrl,
entitySource: EntitySource,
init: (CustomEntityBuilder.() -> Unit)? = null,
): CustomEntityBuilder = CustomEntityType(name, hasSuper, hasSuperSuper, url, entitySource, init)
