@file:JvmName("ChildEntityType1Modifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ChildEntityType1Builder: WorkspaceEntityBuilder<ChildEntityType1>{
override var entitySource: EntitySource
var version: Int
var parent: EntityWithChildrenBuilder
}

internal object ChildEntityType1Type : EntityType<ChildEntityType1, ChildEntityType1Builder>(){
override val entityClass: Class<ChildEntityType1> get() = ChildEntityType1::class.java
operator fun invoke(
version: Int,
entitySource: EntitySource,
init: (ChildEntityType1Builder.() -> Unit)? = null,
): ChildEntityType1Builder{
val builder = builder()
builder.version = version
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyChildEntityType1(
entity: ChildEntityType1,
modification: ChildEntityType1Builder.() -> Unit,
): ChildEntityType1 = modifyEntity(ChildEntityType1Builder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntityType1")
fun ChildEntityType1(
version: Int,
entitySource: EntitySource,
init: (ChildEntityType1Builder.() -> Unit)? = null,
): ChildEntityType1Builder = ChildEntityType1Type(version, entitySource, init)
