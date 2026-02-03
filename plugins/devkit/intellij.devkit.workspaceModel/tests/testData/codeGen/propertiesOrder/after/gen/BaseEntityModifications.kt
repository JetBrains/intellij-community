@file:JvmName("BaseEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract

@GeneratedCodeApiVersion(3)
interface BaseEntityBuilder<T: BaseEntity>: WorkspaceEntityBuilder<T>{
override var entitySource: EntitySource
var name: String
var moduleId: SimpleId
var aBaseEntityProperty: String
var dBaseEntityProperty: String
var bBaseEntityProperty: String
var sealedDataClassProperty: BaseDataClass
}

internal object BaseEntityType : EntityType<BaseEntity, BaseEntityBuilder<BaseEntity>>(){
override val entityClass: Class<BaseEntity> get() = BaseEntity::class.java
operator fun invoke(
name: String,
moduleId: SimpleId,
aBaseEntityProperty: String,
dBaseEntityProperty: String,
bBaseEntityProperty: String,
sealedDataClassProperty: BaseDataClass,
entitySource: EntitySource,
init: (BaseEntityBuilder<BaseEntity>.() -> Unit)? = null,
): BaseEntityBuilder<BaseEntity>{
val builder = builder()
builder.name = name
builder.moduleId = moduleId
builder.aBaseEntityProperty = aBaseEntityProperty
builder.dBaseEntityProperty = dBaseEntityProperty
builder.bBaseEntityProperty = bBaseEntityProperty
builder.sealedDataClassProperty = sealedDataClassProperty
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}
