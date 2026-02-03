@file:JvmName("FinalFieldsEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface FinalFieldsEntityBuilder: WorkspaceEntityBuilder<FinalFieldsEntity>{
override var entitySource: EntitySource
var descriptor: AnotherDataClass
}

internal object FinalFieldsEntityType : EntityType<FinalFieldsEntity, FinalFieldsEntityBuilder>(){
override val entityClass: Class<FinalFieldsEntity> get() = FinalFieldsEntity::class.java
operator fun invoke(
descriptor: AnotherDataClass,
entitySource: EntitySource,
init: (FinalFieldsEntityBuilder.() -> Unit)? = null,
): FinalFieldsEntityBuilder{
val builder = builder()
builder.descriptor = descriptor
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyFinalFieldsEntity(
entity: FinalFieldsEntity,
modification: FinalFieldsEntityBuilder.() -> Unit,
): FinalFieldsEntity = modifyEntity(FinalFieldsEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createFinalFieldsEntity")
fun FinalFieldsEntity(
descriptor: AnotherDataClass,
entitySource: EntitySource,
init: (FinalFieldsEntityBuilder.() -> Unit)? = null,
): FinalFieldsEntityBuilder = FinalFieldsEntityType(descriptor, entitySource, init)
