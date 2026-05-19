@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.ParentEntity
import com.intellij.workspaceModel.test.api.ParentEntityBuilder
import com.intellij.workspaceModel.test.api.ParentId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentEntityImpl(private val dataSource: ParentEntityData): ParentEntity, WorkspaceEntityBase(dataSource) {

private companion object {

private val connections = listOf<ConnectionId>()

}
override val symbolicId: ParentId = super.symbolicId

override val name: String
get() {
readField("name")
return dataSource.name
}

override val entitySource: EntitySource
get() {
readField("entitySource")
return dataSource.entitySource
}

override fun connectionIdList(): List<ConnectionId> {
return connections
}


internal class Builder(result: ParentEntityData?): ModifiableWorkspaceEntityBase<ParentEntity, ParentEntityData>(result), ParentEntityBuilder {
internal constructor(): this(ParentEntityData())

override fun applyToBuilder(builder: MutableEntityStorage){
if (this.diff != null){
if (existsInBuilder(builder)){
this.diff = builder
return
}
else{
error("Entity ParentEntity is already created in a different builder")
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
if (!getEntityData().isNameInitialized()){
error("Field ParentEntity#name should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as ParentEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.name != dataSource.name) this.name = dataSource.name
updateChildToParentReferences(parents)
}

        
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value) {
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")

}
override var name: String
get() = getEntityData().name
set(value) {
checkModificationAllowed()
getEntityData(true).name = value
changedProperty.add("name")
}

override fun getEntityClass(): Class<ParentEntity> = ParentEntity::class.java
}

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentEntityData : WorkspaceEntityData<ParentEntity>(){
lateinit var name: String

internal fun isNameInitialized(): Boolean = ::name.isInitialized

override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<ParentEntity>{
val modifiable = ParentEntityImpl.Builder(null)
modifiable.diff = diff
modifiable.id = createEntityId()
return modifiable
}

override fun createEntity(snapshot: EntityStorageInstrumentation): ParentEntity{
val entityId = createEntityId()
return snapshot.initializeEntity(entityId){
val entity = ParentEntityImpl(this)
entity.snapshot = snapshot
entity.id = entityId
entity
}
}

override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.ParentEntity") as EntityMetadata
}

override fun getEntityInterface(): Class<out WorkspaceEntity>{
return ParentEntity::class.java
}

override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return ParentEntity(name, entitySource)
}

override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}

override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ParentEntityData
if (this.entitySource != other.entitySource) return false
if (this.name != other.name) return false
return true
}

override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ParentEntityData
if (this.name != other.name) return false
return true
}

override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + name.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + name.hashCode()
return result
}
}
