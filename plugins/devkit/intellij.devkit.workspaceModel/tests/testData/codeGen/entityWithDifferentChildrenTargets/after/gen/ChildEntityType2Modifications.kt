@file:JvmName("ChildEntityType2Modifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ChildEntityType2Builder: WorkspaceEntityBuilder<ChildEntityType2>{
override var entitySource: EntitySource
var version: Int
var parent: EntityWithChildrenBuilder
}

internal object ChildEntityType2Type : EntityType<ChildEntityType2, ChildEntityType2Builder>(){
override val entityClass: Class<ChildEntityType2> get() = ChildEntityType2::class.java
operator fun invoke(
version: Int,
entitySource: EntitySource,
init: (ChildEntityType2Builder.() -> Unit)? = null,
): ChildEntityType2Builder{
val builder = builder()
builder.version = version
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyChildEntityType2(
entity: ChildEntityType2,
modification: ChildEntityType2Builder.() -> Unit,
): ChildEntityType2 = modifyEntity(ChildEntityType2Builder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntityType2")
fun ChildEntityType2(
version: Int,
entitySource: EntitySource,
init: (ChildEntityType2Builder.() -> Unit)? = null,
): ChildEntityType2Builder = ChildEntityType2Type(version, entitySource, init)
