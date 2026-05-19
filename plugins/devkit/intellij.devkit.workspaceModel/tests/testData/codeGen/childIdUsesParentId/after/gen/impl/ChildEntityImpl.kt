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
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.ChildEntity
import com.intellij.workspaceModel.test.api.ChildEntityBuilder
import com.intellij.workspaceModel.test.api.ChildId
import com.intellij.workspaceModel.test.api.ParentEntity
import com.intellij.workspaceModel.test.api.ParentEntityBuilder
import com.intellij.workspaceModel.test.api.ParentId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildEntityImpl(private val dataSource: ChildEntityData): ChildEntity, WorkspaceEntityBase(dataSource) {

private companion object {
internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID)

}
override val symbolicId: ChildId = ChildId(dataSource.name, dataSource.parentEntitySymbolicId_Synthetic)

override val name: String
get() {
readField("name")
return dataSource.name
}
override val parentEntity: ParentEntity
get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? ParentEntity ?: error("Parent parentEntity not found for ChildEntity")

override val entitySource: EntitySource
get() {
readField("entitySource")
return dataSource.entitySource
}

override fun connectionIdList(): List<ConnectionId> {
return connections
}


internal class Builder(result: ChildEntityData?): ModifiableWorkspaceEntityBase<ChildEntity, ChildEntityData>(result), ChildEntityBuilder {
internal constructor(): this(ChildEntityData())

override fun applyToBuilder(builder: MutableEntityStorage){
if (this.diff != null){
if (existsInBuilder(builder)){
this.diff = builder
return
}
else{
error("Entity ChildEntity is already created in a different builder")
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
error("Field ChildEntity#name should be initialized")
}
if (_diff != null){
if (_diff.instrumentation.getParentBuilder(PARENTENTITY_CONNECTION_ID, this) == null){
error("Field ChildEntity#parentEntity should be initialized")
}
}
else{
if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null){
error("Field ChildEntity#parentEntity should be initialized")
}
}
if (!getEntityData().isParentEntitySymbolicId_SyntheticInitialized()){
error("Field ChildEntity#parentEntity should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as ChildEntity
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
override var parentEntity: ParentEntityBuilder
get(){
val _diff = diff
return if (_diff != null) {
((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENTENTITY_CONNECTION_ID, this) as? ParentEntityBuilder) ?: (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? ParentEntityBuilder) ?: error("parentEntity is null for ChildEntity")
} else {
(this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? ParentEntityBuilder) ?: error("parentEntity is null for ChildEntity")
}
}
set(value){
checkModificationAllowed()
val _diff = diff
if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null){
if (value is ModifiableWorkspaceEntityBase<*, *>){
value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = this
}
// else you're attaching a new entity to an existing entity that is not modifiable
_diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
}
if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)){
_diff.instrumentation.addChild(PARENTENTITY_CONNECTION_ID, value, this)
}
else{
if (value is ModifiableWorkspaceEntityBase<*, *>){
value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = this
}
// else you're attaching a new entity to an existing entity that is not modifiable
this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
}
changedProperty.add("parentEntity")
getEntityData(true).parentEntitySymbolicId_Synthetic = ParentId(value.name)
changedProperty.add("parentEntitySymbolicId_Synthetic")
}

override fun getEntityClass(): Class<ChildEntity> = ChildEntity::class.java
}

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildEntityData : WorkspaceEntityData<ChildEntity>(), SoftLinkable{
lateinit var name: String

lateinit var parentEntitySymbolicId_Synthetic: ParentId

internal fun isNameInitialized(): Boolean = ::name.isInitialized

internal fun isParentEntitySymbolicId_SyntheticInitialized(): Boolean = ::parentEntitySymbolicId_Synthetic.isInitialized

override fun getLinks(): Set<SymbolicEntityId<*>>{
val result = HashSet<SymbolicEntityId<*>>()
result.add(parentEntitySymbolicId_Synthetic)
return result
}
override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>){
index.index(this, parentEntitySymbolicId_Synthetic)
}
override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>){
// TODO verify logic
val mutablePreviousSet = HashSet(prev)
val removedItem_parentEntitySymbolicId_Synthetic = mutablePreviousSet.remove(parentEntitySymbolicId_Synthetic)
if (!removedItem_parentEntitySymbolicId_Synthetic){
index.index(this, parentEntitySymbolicId_Synthetic)
}
for (removed in mutablePreviousSet){
index.remove(this, removed)
}
}
override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean{
var changed = false
val parentEntitySymbolicId_Synthetic_data = if (parentEntitySymbolicId_Synthetic == oldLink){
changed = true
newLink as ParentId
}
else{
null
}
if (parentEntitySymbolicId_Synthetic_data != null){
parentEntitySymbolicId_Synthetic = parentEntitySymbolicId_Synthetic_data
}
return changed
}
override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<ChildEntity>{
val modifiable = ChildEntityImpl.Builder(null)
modifiable.diff = diff
modifiable.id = createEntityId()
return modifiable
}

override fun createEntity(snapshot: EntityStorageInstrumentation): ChildEntity{
val entityId = createEntityId()
return snapshot.initializeEntity(entityId){
val entity = ChildEntityImpl(this)
entity.snapshot = snapshot
entity.id = entityId
entity
}
}

override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.ChildEntity") as EntityMetadata
}

override fun getEntityInterface(): Class<out WorkspaceEntity>{
return ChildEntity::class.java
}

override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return ChildEntity(name, entitySource){
parents.filterIsInstance<ParentEntityBuilder>().singleOrNull()?.let { this.parentEntity = it }
}
}

override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
res.add(ParentEntity::class.java)
return res
}

override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ChildEntityData
if (this.entitySource != other.entitySource) return false
if (this.name != other.name) return false
return true
}

override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ChildEntityData
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
