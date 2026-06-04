@file:JvmName("CompatibilityEntityModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.workspaceModel.test.api.impl.CompatibilityEntityImpl

@GeneratedCodeApiVersion(3)
interface CompatibilityEntityBuilder: WorkspaceEntityBuilder<CompatibilityEntity>{
override var entitySource: EntitySource
var version: Int
var name: String
var isSimple: Boolean
}

internal object CompatibilityEntityType : EntityType<CompatibilityEntity, CompatibilityEntityBuilder>(){
override val entityClass: Class<CompatibilityEntity> get() = CompatibilityEntity::class.java
override val entityImplBuilderClass: Class<*> get() = CompatibilityEntityImpl.Builder::class.java
operator fun invoke(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (CompatibilityEntityBuilder.() -> Unit)? = null,
): CompatibilityEntityBuilder{
val builder = builder()
builder.version = version
builder.name = name
builder.isSimple = isSimple
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
@Deprecated(message = "Use new API instead")
fun compatibilityInvoke(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (CompatibilityEntity.Builder.() -> Unit)? = null,
): CompatibilityEntity.Builder{
val builder = builder() as CompatibilityEntity.Builder
builder.version = version
builder.name = name
builder.isSimple = isSimple
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyCompatibilityEntity(
entity: CompatibilityEntity,
modification: CompatibilityEntityBuilder.() -> Unit,
): CompatibilityEntity = modifyEntity(CompatibilityEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createCompatibilityEntity")
fun CompatibilityEntity(
version: Int,
name: String,
isSimple: Boolean,
entitySource: EntitySource,
init: (CompatibilityEntityBuilder.() -> Unit)? = null,
): CompatibilityEntityBuilder = CompatibilityEntityType(version, name, isSimple, entitySource, init)
