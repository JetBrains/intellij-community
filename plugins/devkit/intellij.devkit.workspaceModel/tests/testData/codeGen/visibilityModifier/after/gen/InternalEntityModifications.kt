@file:JvmName("InternalEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
internal interface InternalEntityBuilder: WorkspaceEntityBuilder<InternalEntity>{
override var entitySource: EntitySource
var version: Int
var name: String
var isSimple: Boolean
}

internal object InternalEntityType : EntityType<InternalEntity, InternalEntityBuilder>(){
override val entityClass: Class<InternalEntity> get() = InternalEntity::class.java
operator fun invoke(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (InternalEntityBuilder.() -> Unit)? = null,
): InternalEntityBuilder{
val builder = builder()
builder.version = version
builder.name = name
builder.isSimple = isSimple
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

internal fun MutableEntityStorage.modifyInternalEntity(
entity: InternalEntity,
modification: InternalEntityBuilder.() -> Unit,
): InternalEntity = modifyEntity(InternalEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createInternalEntity")
internal fun InternalEntity(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (InternalEntityBuilder.() -> Unit)? = null,
): InternalEntityBuilder = InternalEntityType(version, name, isSimple, entitySource, init)
