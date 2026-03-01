@file:JvmName("ReferredEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ReferredEntityBuilder: WorkspaceEntityBuilder<ReferredEntity>{
override var entitySource: EntitySource
var version: Int
var name: String
var contentRoot: ContentRootEntityBuilder?
}

internal object ReferredEntityType : EntityType<ReferredEntity, ReferredEntityBuilder>(){
override val entityClass: Class<ReferredEntity> get() = ReferredEntity::class.java
operator fun invoke(
version: Int,
name: String,
entitySource: EntitySource,
init: (ReferredEntityBuilder.() -> Unit)? = null,
): ReferredEntityBuilder{
val builder = builder()
builder.version = version
builder.name = name
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyReferredEntity(
entity: ReferredEntity,
modification: ReferredEntityBuilder.() -> Unit,
): ReferredEntity = modifyEntity(ReferredEntityBuilder::class.java, entity, modification)
@Parent
var ContentRootEntityBuilder.ref: ReferredEntityBuilder
by WorkspaceEntity.extensionBuilder(ReferredEntity::class.java)


@JvmOverloads
@JvmName("createReferredEntity")
fun ReferredEntity(
version: Int,
name: String,
entitySource: EntitySource,
init: (ReferredEntityBuilder.() -> Unit)? = null,
): ReferredEntityBuilder = ReferredEntityType(version, name, entitySource, init)
