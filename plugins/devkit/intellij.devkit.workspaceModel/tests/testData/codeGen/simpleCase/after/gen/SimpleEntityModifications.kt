@file:JvmName("SimpleEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface SimpleEntityBuilder: WorkspaceEntityBuilder<SimpleEntity>{
override var entitySource: EntitySource
var version: Int
var name: String
var isSimple: Boolean
var char: Char
var long: Long
var float: Float
var double: Double
var short: Short
var byte: Byte
}

internal object SimpleEntityType : EntityType<SimpleEntity, SimpleEntityBuilder>(){
override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
operator fun invoke(
version: Int,
name: String,
isSimple: Boolean,
char: Char,
long: Long,
float: Float,
double: Double,
short: Short,
byte: Byte,
entitySource: EntitySource,
init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder{
val builder = builder()
builder.version = version
builder.name = name
builder.isSimple = isSimple
builder.char = char
builder.long = long
builder.float = float
builder.double = double
builder.short = short
builder.byte = byte
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifySimpleEntity(
entity: SimpleEntity,
modification: SimpleEntityBuilder.() -> Unit,
): SimpleEntity = modifyEntity(SimpleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimpleEntity")
fun SimpleEntity(
version: Int,
name: String,
isSimple: Boolean,
char: Char,
long: Long,
float: Float,
double: Double,
short: Short,
byte: Byte,
entitySource: EntitySource,
init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder = SimpleEntityType(version, name, isSimple, char, long, float, double, short, byte, entitySource, init)
