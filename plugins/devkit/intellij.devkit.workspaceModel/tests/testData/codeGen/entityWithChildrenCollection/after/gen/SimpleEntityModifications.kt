@file:JvmName("SimpleEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface SimpleEntityBuilder: WorkspaceEntityBuilder<SimpleEntity>{
override var entitySource: EntitySource
var version: Int
var name: String
var isSimple: Boolean
var parent: ChildrenCollectionFieldEntityBuilder
}

internal object SimpleEntityType : EntityType<SimpleEntity, SimpleEntityBuilder>(){
override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
operator fun invoke(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder{
val builder = builder()
builder.version = version
builder.name = name
builder.isSimple = isSimple
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
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder = SimpleEntityType(version, name, isSimple, entitySource, init)
