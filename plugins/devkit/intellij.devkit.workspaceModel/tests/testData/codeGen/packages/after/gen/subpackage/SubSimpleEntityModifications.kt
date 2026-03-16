@file:JvmName("SubSimpleEntityModifications")

package com.intellij.workspaceModel.test.api.subpackage

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface SubSimpleEntityBuilder: WorkspaceEntityBuilder<SubSimpleEntity>{
override var entitySource: EntitySource
var version: Int
var name: String
var isSimple: Boolean
}

internal object SubSimpleEntityType : EntityType<SubSimpleEntity, SubSimpleEntityBuilder>(){
override val entityClass: Class<SubSimpleEntity> get() = SubSimpleEntity::class.java
operator fun invoke(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (SubSimpleEntityBuilder.() -> Unit)? = null,
): SubSimpleEntityBuilder{
val builder = builder()
builder.version = version
builder.name = name
builder.isSimple = isSimple
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifySubSimpleEntity(
entity: SubSimpleEntity,
modification: SubSimpleEntityBuilder.() -> Unit,
): SubSimpleEntity = modifyEntity(SubSimpleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSubSimpleEntity")
fun SubSimpleEntity(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (SubSimpleEntityBuilder.() -> Unit)? = null,
): SubSimpleEntityBuilder = SubSimpleEntityType(version, name, isSimple, entitySource, init)
