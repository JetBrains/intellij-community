@file:JvmName("SimpleSymbolicIdEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

@GeneratedCodeApiVersion(3)
interface SimpleSymbolicIdEntityBuilder: WorkspaceEntityBuilder<SimpleSymbolicIdEntity>{
override var entitySource: EntitySource
var version: Int
var name: String
var related: SimpleId
var sealedClassWithLinks: SealedClassWithLinks
}

internal object SimpleSymbolicIdEntityType : EntityType<SimpleSymbolicIdEntity, SimpleSymbolicIdEntityBuilder>(){
override val entityClass: Class<SimpleSymbolicIdEntity> get() = SimpleSymbolicIdEntity::class.java
operator fun invoke(
version: Int,
name: String,
related: SimpleId,
sealedClassWithLinks: SealedClassWithLinks,
entitySource: EntitySource,
init: (SimpleSymbolicIdEntityBuilder.() -> Unit)? = null,
): SimpleSymbolicIdEntityBuilder{
val builder = builder()
builder.version = version
builder.name = name
builder.related = related
builder.sealedClassWithLinks = sealedClassWithLinks
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifySimpleSymbolicIdEntity(
entity: SimpleSymbolicIdEntity,
modification: SimpleSymbolicIdEntityBuilder.() -> Unit,
): SimpleSymbolicIdEntity = modifyEntity(SimpleSymbolicIdEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleSymbolicIdEntity")
fun SimpleSymbolicIdEntity(
version: Int,
name: String,
related: SimpleId,
sealedClassWithLinks: SealedClassWithLinks,
entitySource: EntitySource,
init: (SimpleSymbolicIdEntityBuilder.() -> Unit)? = null,
): SimpleSymbolicIdEntityBuilder = SimpleSymbolicIdEntityType(version, name, related, sealedClassWithLinks, entitySource, init)
