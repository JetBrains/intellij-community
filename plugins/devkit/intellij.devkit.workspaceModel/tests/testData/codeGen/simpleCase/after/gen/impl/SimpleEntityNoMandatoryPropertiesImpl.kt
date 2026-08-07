@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.SimpleEntityNoMandatoryProperties
import com.intellij.workspaceModel.test.api.SimpleEntityNoMandatoryPropertiesBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityNoMandatoryPropertiesImpl(private val dataSource: SimpleEntityNoMandatoryPropertiesData): SimpleEntityNoMandatoryProperties, WorkspaceEntityBase(dataSource){

override val optionalProperty: Int?
get(){
readField("optionalProperty")
return dataSource.optionalProperty
}
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
internal class Builder(result: SimpleEntityNoMandatoryPropertiesData?): ModifiableWorkspaceEntityBase<SimpleEntityNoMandatoryProperties, SimpleEntityNoMandatoryPropertiesData>(result), SimpleEntityNoMandatoryPropertiesBuilder{
internal constructor(): this(SimpleEntityNoMandatoryPropertiesData())
override fun applyToBuilder(builder: MutableEntityStorage){
if (this.diff != null){
if (existsInBuilder(builder)){
this.diff = builder
return
}
else{
error("Entity SimpleEntityNoMandatoryProperties is already created in a different builder")
}
}
this.diff = builder
addToBuilder()
this.id = getEntityData().createEntityId()
// After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
// Builder may switch to snapshot at any moment and lock entity data to modification
this.currentEntityData = null
// Process linked entities that are connected without a builder
processLinkedEntities(builder)
checkInitialization()
}
private fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as SimpleEntityNoMandatoryProperties
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.optionalProperty != dataSource?.optionalProperty) this.optionalProperty = dataSource.optionalProperty
updateChildToParentReferences(parents)
}
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value){
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")
}
override var optionalProperty: Int??
get() = getEntityData().optionalProperty
set(value){
checkModificationAllowed()
getEntityData(true).optionalProperty = value
changedProperty.add("optionalProperty")
}
override fun getEntityClass(): Class<SimpleEntityNoMandatoryProperties> = SimpleEntityNoMandatoryProperties::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityNoMandatoryPropertiesData : WorkspaceEntityData<SimpleEntityNoMandatoryProperties>(){
var optionalProperty: Int? = null
override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<SimpleEntityNoMandatoryProperties>{
val modifiable = SimpleEntityNoMandatoryPropertiesImpl.Builder(null)
modifiable.diff = diff
modifiable.id = createEntityId()
return modifiable
}
override fun createEntity(snapshot: EntityStorageInstrumentation): SimpleEntityNoMandatoryProperties{
val entityId = createEntityId()
return snapshot.initializeEntity(entityId){
val entity = SimpleEntityNoMandatoryPropertiesImpl(this)
entity.snapshot = snapshot
entity.id = entityId
entity
}
}
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.SimpleEntityNoMandatoryProperties") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return SimpleEntityNoMandatoryProperties::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return SimpleEntityNoMandatoryProperties(entitySource){
this.optionalProperty = this@SimpleEntityNoMandatoryPropertiesData.optionalProperty
}
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityNoMandatoryPropertiesData
if (this.entitySource != other.entitySource) return false
if (this.optionalProperty != other.optionalProperty) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityNoMandatoryPropertiesData
if (this.optionalProperty != other.optionalProperty) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + optionalProperty.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + optionalProperty.hashCode()
return result
}
}
