@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EqualsBy
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
import com.intellij.workspaceModel.test.api.EntityWithKeyField
import com.intellij.workspaceModel.test.api.EntityWithKeyFieldBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithKeyFieldImpl(private val dataSource: EntityWithKeyFieldData): EntityWithKeyField, WorkspaceEntityBase(dataSource) {

private companion object {

private val connections = listOf<ConnectionId>()

}

override val keyField: String
get() {
readField("keyField")
return dataSource.keyField
}
override val notKeyField: String
get() {
readField("notKeyField")
return dataSource.notKeyField
}

override val entitySource: EntitySource
get() {
readField("entitySource")
return dataSource.entitySource
}

override fun connectionIdList(): List<ConnectionId> {
return connections
}


internal class Builder(result: EntityWithKeyFieldData?): ModifiableWorkspaceEntityBase<EntityWithKeyField, EntityWithKeyFieldData>(result), EntityWithKeyFieldBuilder {
internal constructor(): this(EntityWithKeyFieldData())

override fun applyToBuilder(builder: MutableEntityStorage){
if (this.diff != null){
if (existsInBuilder(builder)){
this.diff = builder
return
}
else{
error("Entity EntityWithKeyField is already created in a different builder")
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
checkInitialization() // TODO uncomment and check failed tests
}

private fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isKeyFieldInitialized()){
error("Field EntityWithKeyField#keyField should be initialized")
}
if (!getEntityData().isNotKeyFieldInitialized()){
error("Field EntityWithKeyField#notKeyField should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as EntityWithKeyField
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.keyField != dataSource.keyField) this.keyField = dataSource.keyField
if (this.notKeyField != dataSource.notKeyField) this.notKeyField = dataSource.notKeyField
updateChildToParentReferences(parents)
}

        
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value) {
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")

}
override var keyField: String
get() = getEntityData().keyField
set(value) {
checkModificationAllowed()
getEntityData(true).keyField = value
changedProperty.add("keyField")
}
override var notKeyField: String
get() = getEntityData().notKeyField
set(value) {
checkModificationAllowed()
getEntityData(true).notKeyField = value
changedProperty.add("notKeyField")
}

override fun getEntityClass(): Class<EntityWithKeyField> = EntityWithKeyField::class.java
}

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithKeyFieldData : WorkspaceEntityData<EntityWithKeyField>(){
lateinit var keyField: String
lateinit var notKeyField: String

internal fun isKeyFieldInitialized(): Boolean = ::keyField.isInitialized
internal fun isNotKeyFieldInitialized(): Boolean = ::notKeyField.isInitialized

override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<EntityWithKeyField>{
val modifiable = EntityWithKeyFieldImpl.Builder(null)
modifiable.diff = diff
modifiable.id = createEntityId()
return modifiable
}

override fun createEntity(snapshot: EntityStorageInstrumentation): EntityWithKeyField{
val entityId = createEntityId()
return snapshot.initializeEntity(entityId){
val entity = EntityWithKeyFieldImpl(this)
entity.snapshot = snapshot
entity.id = entityId
entity
}
}

override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.EntityWithKeyField") as EntityMetadata
}

override fun getEntityInterface(): Class<out WorkspaceEntity>{
return EntityWithKeyField::class.java
}

override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return EntityWithKeyField(keyField, notKeyField, entitySource)
}

override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}

override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithKeyFieldData
if (this.entitySource != other.entitySource) return false
if (this.keyField != other.keyField) return false
if (this.notKeyField != other.notKeyField) return false
return true
}

override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithKeyFieldData
if (this.keyField != other.keyField) return false
if (this.notKeyField != other.notKeyField) return false
return true
}

override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + keyField.hashCode()
result = 31 * result + notKeyField.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + keyField.hashCode()
result = 31 * result + notKeyField.hashCode()
return result
}
override fun equalsByKey(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithKeyFieldData
if (this.keyField != other.keyField) return false
return true
}
override fun hashCodeByKey(): Int{
var result = javaClass.hashCode()
result = 31 * result + keyField.hashCode()
return result
}
}
