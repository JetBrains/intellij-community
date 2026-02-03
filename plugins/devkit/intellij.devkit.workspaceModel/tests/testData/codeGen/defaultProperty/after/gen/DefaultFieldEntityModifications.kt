@file:JvmName("DefaultFieldEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Default

@GeneratedCodeApiVersion(3)
interface DefaultFieldEntityBuilder: WorkspaceEntityBuilder<DefaultFieldEntity>{
override var entitySource: EntitySource
var version: Int
var data: TestData
var anotherVersion: Int
var description: String
var defaultSet: MutableSet<String>
var defaultList: MutableList<String>
var defaultMap: Map<String, String>
}

internal object DefaultFieldEntityType : EntityType<DefaultFieldEntity, DefaultFieldEntityBuilder>(){
override val entityClass: Class<DefaultFieldEntity> get() = DefaultFieldEntity::class.java
operator fun invoke(
version: Int,
data: TestData,
entitySource: EntitySource,
init: (DefaultFieldEntityBuilder.() -> Unit)? = null,
): DefaultFieldEntityBuilder{
val builder = builder()
builder.version = version
builder.data = data
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyDefaultFieldEntity(
entity: DefaultFieldEntity,
modification: DefaultFieldEntityBuilder.() -> Unit,
): DefaultFieldEntity = modifyEntity(DefaultFieldEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createDefaultFieldEntity")
fun DefaultFieldEntity(
version: Int,
data: TestData,
entitySource: EntitySource,
init: (DefaultFieldEntityBuilder.() -> Unit)? = null,
): DefaultFieldEntityBuilder = DefaultFieldEntityType(version, data, entitySource, init)
