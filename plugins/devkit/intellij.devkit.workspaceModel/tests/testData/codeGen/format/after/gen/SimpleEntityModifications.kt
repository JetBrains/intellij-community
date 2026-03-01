// 2700-2200 BCE fake copyright for test
// another line of fake copyright
@file:JvmName("SimpleEntityModifications")

package com.intellij.workspaceModel.test.api

import com.intellij.another.module.ClassToImport
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface SimpleEntityBuilder : WorkspaceEntityBuilder<SimpleEntity> {
    override var entitySource: EntitySource
    var version: Int
    var name: String
    var isSimple: Boolean
    var imported: ClassToImport
}

internal object SimpleEntityType : EntityType<SimpleEntity, SimpleEntityBuilder>() {
    override val entityClass: Class<SimpleEntity> get() = SimpleEntity::class.java
    operator fun invoke(
        version: Int,
        name: String,
        isSimple: Boolean,
        imported: ClassToImport,
        entitySource: EntitySource,
        init: (SimpleEntityBuilder.() -> Unit)? = null,
    ): SimpleEntityBuilder {
        val builder = builder()
        builder.version = version
        builder.name = name
        builder.isSimple = isSimple
        builder.imported = imported
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
    imported: ClassToImport,
    entitySource: EntitySource,
    init: (SimpleEntityBuilder.() -> Unit)? = null,
): SimpleEntityBuilder = SimpleEntityType(version, name, isSimple, imported, entitySource, init)
