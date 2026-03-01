@file:JvmName("UnknownPropertyTypeEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import java.util.Date

@GeneratedCodeApiVersion(3)
interface UnknownPropertyTypeEntityBuilder: WorkspaceEntityBuilder<UnknownPropertyTypeEntity>{
override var entitySource: EntitySource
var date: Date
}

internal object UnknownPropertyTypeEntityType : EntityType<UnknownPropertyTypeEntity, UnknownPropertyTypeEntityBuilder>(){
override val entityClass: Class<UnknownPropertyTypeEntity> get() = UnknownPropertyTypeEntity::class.java
operator fun invoke(
date: Date,
entitySource: EntitySource,
init: (UnknownPropertyTypeEntityBuilder.() -> Unit)? = null,
): UnknownPropertyTypeEntityBuilder{
val builder = builder()
builder.date = date
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyUnknownPropertyTypeEntity(
entity: UnknownPropertyTypeEntity,
modification: UnknownPropertyTypeEntityBuilder.() -> Unit,
): UnknownPropertyTypeEntity = modifyEntity(UnknownPropertyTypeEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createUnknownPropertyTypeEntity")
fun UnknownPropertyTypeEntity(
date: Date,
entitySource: EntitySource,
init: (UnknownPropertyTypeEntityBuilder.() -> Unit)? = null,
): UnknownPropertyTypeEntityBuilder = UnknownPropertyTypeEntityType(date, entitySource, init)
