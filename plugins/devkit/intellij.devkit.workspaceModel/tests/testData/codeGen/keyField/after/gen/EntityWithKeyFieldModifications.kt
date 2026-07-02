@file:JvmName("EntityWithKeyFieldModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.EqualsBy
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.workspaceModel.test.api.impl.EntityWithKeyFieldImpl

@GeneratedCodeApiVersion(3)
interface EntityWithKeyFieldBuilder: WorkspaceEntityBuilder<EntityWithKeyField>{
override var entitySource: EntitySource
var keyField: String
var notKeyField: String
}

internal object EntityWithKeyFieldType : EntityType<EntityWithKeyField, EntityWithKeyFieldBuilder>(){
override val entityClass: Class<EntityWithKeyField> get() = EntityWithKeyField::class.java
override val entityImplBuilderClass: Class<*> get() = EntityWithKeyFieldImpl.Builder::class.java
operator fun invoke(
keyField: String,
notKeyField: String,
entitySource: EntitySource,
init: (EntityWithKeyFieldBuilder.() -> Unit)? = null,
): EntityWithKeyFieldBuilder{
val builder = builder()
builder.keyField = keyField
builder.notKeyField = notKeyField
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyEntityWithKeyField(
entity: EntityWithKeyField,
modification: EntityWithKeyFieldBuilder.() -> Unit,
): EntityWithKeyField = modifyEntity(EntityWithKeyFieldBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithKeyField")
fun EntityWithKeyField(
keyField: String,
notKeyField: String,
entitySource: EntitySource,
init: (EntityWithKeyFieldBuilder.() -> Unit)? = null,
): EntityWithKeyFieldBuilder = EntityWithKeyFieldType(keyField, notKeyField, entitySource, init)
