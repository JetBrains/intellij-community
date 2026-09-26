@file:JvmName("SimpleEntityNoMandatoryPropertiesModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.workspaceModel.test.api.impl.SimpleEntityNoMandatoryPropertiesImpl

@GeneratedCodeApiVersion(3)
interface SimpleEntityNoMandatoryPropertiesBuilder: WorkspaceEntityBuilder<SimpleEntityNoMandatoryProperties>{
override var entitySource: EntitySource
var optionalProperty: Int?
}
internal object SimpleEntityNoMandatoryPropertiesType : EntityType<SimpleEntityNoMandatoryProperties, SimpleEntityNoMandatoryPropertiesBuilder>(){
override val entityImplClass: Class<*> get() = SimpleEntityNoMandatoryPropertiesImpl::class.java
override val entityImplBuilderClass: Class<*> get() = SimpleEntityNoMandatoryPropertiesImpl.Builder::class.java
operator fun invoke(
entitySource: EntitySource,
init: (SimpleEntityNoMandatoryPropertiesBuilder.() -> Unit)? = null,
): SimpleEntityNoMandatoryPropertiesBuilder{
val builder = builder()
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}
fun MutableEntityStorage.modifySimpleEntityNoMandatoryProperties(
entity: SimpleEntityNoMandatoryProperties,
modification: SimpleEntityNoMandatoryPropertiesBuilder.() -> Unit,
): SimpleEntityNoMandatoryProperties = modifyEntity(SimpleEntityNoMandatoryPropertiesBuilder::class.java, entity, modification)
@JvmOverloads
@JvmName("createSimpleEntityNoMandatoryProperties")
fun SimpleEntityNoMandatoryProperties(
entitySource: EntitySource,
init: (SimpleEntityNoMandatoryPropertiesBuilder.() -> Unit)? = null,
): SimpleEntityNoMandatoryPropertiesBuilder = SimpleEntityNoMandatoryPropertiesType(entitySource, init)
