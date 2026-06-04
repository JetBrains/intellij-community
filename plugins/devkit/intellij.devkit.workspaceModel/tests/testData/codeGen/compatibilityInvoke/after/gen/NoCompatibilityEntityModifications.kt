@file:JvmName("NoCompatibilityEntityModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.workspaceModel.test.api.impl.NoCompatibilityEntityImpl

@GeneratedCodeApiVersion(3)
interface NoCompatibilityEntityBuilder: WorkspaceEntityBuilder<NoCompatibilityEntity>{
override var entitySource: EntitySource
var version: Int
var name: String
var isSimple: Boolean
}

internal object NoCompatibilityEntityType : EntityType<NoCompatibilityEntity, NoCompatibilityEntityBuilder>(){
override val entityClass: Class<NoCompatibilityEntity> get() = NoCompatibilityEntity::class.java
override val entityImplBuilderClass: Class<*> get() = NoCompatibilityEntityImpl.Builder::class.java
operator fun invoke(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (NoCompatibilityEntityBuilder.() -> Unit)? = null,
): NoCompatibilityEntityBuilder{
val builder = builder()
builder.version = version
builder.name = name
builder.isSimple = isSimple
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyNoCompatibilityEntity(
entity: NoCompatibilityEntity,
modification: NoCompatibilityEntityBuilder.() -> Unit,
): NoCompatibilityEntity = modifyEntity(NoCompatibilityEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createNoCompatibilityEntity")
fun NoCompatibilityEntity(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (NoCompatibilityEntityBuilder.() -> Unit)? = null,
): NoCompatibilityEntityBuilder = NoCompatibilityEntityType(version, name, isSimple, entitySource, init)
