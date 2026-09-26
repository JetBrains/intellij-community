@file:JvmName("EmptyCustomEntityModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.test.api.impl.EmptyCustomEntityImpl

@GeneratedCodeApiVersion(3)
interface EmptyCustomEntityBuilder: WorkspaceEntityBuilder<EmptyCustomEntity>{
override var entitySource: EntitySource
var url: VirtualFileUrl
var hasSuper: Boolean
}
internal object EmptyCustomEntityType : EntityType<EmptyCustomEntity, EmptyCustomEntityBuilder>(){
override val entityImplClass: Class<*> get() = EmptyCustomEntityImpl::class.java
override val entityImplBuilderClass: Class<*> get() = EmptyCustomEntityImpl.Builder::class.java
operator fun invoke(
url: VirtualFileUrl,
hasSuper: Boolean,
entitySource: EntitySource,
init: (EmptyCustomEntityBuilder.() -> Unit)? = null,
): EmptyCustomEntityBuilder{
val builder = builder()
builder.url = url
builder.hasSuper = hasSuper
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}
fun MutableEntityStorage.modifyEmptyCustomEntity(
entity: EmptyCustomEntity,
modification: EmptyCustomEntityBuilder.() -> Unit,
): EmptyCustomEntity = modifyEntity(EmptyCustomEntityBuilder::class.java, entity, modification)
@JvmOverloads
@JvmName("createEmptyCustomEntity")
fun EmptyCustomEntity(
url: VirtualFileUrl,
hasSuper: Boolean,
entitySource: EntitySource,
init: (EmptyCustomEntityBuilder.() -> Unit)? = null,
): EmptyCustomEntityBuilder = EmptyCustomEntityType(url, hasSuper, entitySource, init)
