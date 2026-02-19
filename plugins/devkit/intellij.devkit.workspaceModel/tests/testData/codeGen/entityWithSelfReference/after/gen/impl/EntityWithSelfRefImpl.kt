package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.EntityWithSelfRef
import com.intellij.workspaceModel.test.api.EntityWithSelfRefBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithSelfRefImpl(private val dataSource: EntityWithSelfRefData): EntityWithSelfRef, WorkspaceEntityBase(dataSource) {

private companion object {
internal val PARENTREF_CONNECTION_ID: ConnectionId = ConnectionId.create(EntityWithSelfRef::class.java, EntityWithSelfRef::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(EntityWithSelfRef::class.java, EntityWithSelfRef::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
private val connections = listOf<ConnectionId>(PARENTREF_CONNECTION_ID,CHILDREN_CONNECTION_ID)

}

override val name: String
get() {
readField("name")
return dataSource.name
}
override val parentRef: EntityWithSelfRef?
get() = snapshot.extractOneToManyParent(PARENTREF_CONNECTION_ID, this)           
override val children: List<EntityWithSelfRef>
get() = snapshot.extractOneToManyChildren<EntityWithSelfRef>(CHILDREN_CONNECTION_ID, this)!!.toList()

override val entitySource: EntitySource
get() {
readField("entitySource")
return dataSource.entitySource
}

override fun connectionIdList(): List<ConnectionId> {
return connections
}


internal class Builder(result: EntityWithSelfRefData?): ModifiableWorkspaceEntityBase<EntityWithSelfRef, EntityWithSelfRefData>(result), EntityWithSelfRefBuilder {
internal constructor(): this(EntityWithSelfRefData())

override fun applyToBuilder(builder: MutableEntityStorage){
if (this.diff != null){
if (existsInBuilder(builder)){
this.diff = builder
return
}
else{
error("Entity EntityWithSelfRef is already created in a different builder")
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
error("Field EntityWithSelfRef#name should be initialized")
}
// Check initialization for list with ref type
if (_diff != null){
if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null){
error("Field EntityWithSelfRef#children should be initialized")
}
}
else{
if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null){
error("Field EntityWithSelfRef#children should be initialized")
}
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as EntityWithSelfRef
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
override var parentRef: EntityWithSelfRefBuilder?
get(){
val _diff = diff
return if (_diff != null) {
@OptIn(EntityStorageInstrumentationApi::class)
((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENTREF_CONNECTION_ID, this) as? EntityWithSelfRefBuilder) ?: (this.entityLinks[EntityLink(false, PARENTREF_CONNECTION_ID)] as? EntityWithSelfRefBuilder)
} else {
this.entityLinks[EntityLink(false, PARENTREF_CONNECTION_ID)] as? EntityWithSelfRefBuilder
}
}
set(value){
checkModificationAllowed()
val _diff = diff
if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null){
// Setting backref of the list
if (value is ModifiableWorkspaceEntityBase<*, *>){
val data = (value.entityLinks[EntityLink(true, PARENTREF_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
value.entityLinks[EntityLink(true, PARENTREF_CONNECTION_ID)] = data
}
// else you're attaching a new entity to an existing entity that is not modifiable
_diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
}
if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)){
_diff.updateOneToManyParentOfChild(PARENTREF_CONNECTION_ID, this, value)
}
else{
// Setting backref of the list
if (value is ModifiableWorkspaceEntityBase<*, *>){
val data = (value.entityLinks[EntityLink(true, PARENTREF_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
value.entityLinks[EntityLink(true, PARENTREF_CONNECTION_ID)] = data
}
// else you're attaching a new entity to an existing entity that is not modifiable
this.entityLinks[EntityLink(false, PARENTREF_CONNECTION_ID)] = value
}
changedProperty.add("parentRef")
}

// List of non-abstract referenced types
var _children: List<EntityWithSelfRef>? = emptyList()
override var children: List<EntityWithSelfRefBuilder>
get(){
// Getter of the list of non-abstract referenced types
val _diff = diff
return if (_diff != null) {
@OptIn(EntityStorageInstrumentationApi::class)
((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)!!.toList() as List<EntityWithSelfRefBuilder>) + (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<EntityWithSelfRefBuilder> ?: emptyList())
} else {
this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<EntityWithSelfRefBuilder> ?: emptyList()
}
}
set(value){
// Setter of the list of non-abstract referenced types
checkModificationAllowed()
val _diff = diff
if (_diff != null){
for (item_value in value){
if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null){
// Backref setup before adding to store
if (item_value is ModifiableWorkspaceEntityBase<*, *>){
item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
}
// else you're attaching a new entity to an existing entity that is not modifiable
_diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
}
}
_diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
}
else{
for (item_value in value){
if (item_value is ModifiableWorkspaceEntityBase<*, *>){
item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
}
// else you're attaching a new entity to an existing entity that is not modifiable
}
this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
}
changedProperty.add("children")
}

override fun getEntityClass(): Class<EntityWithSelfRef> = EntityWithSelfRef::class.java
}

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithSelfRefData : WorkspaceEntityData<EntityWithSelfRef>(){
lateinit var name: String

internal fun isNameInitialized(): Boolean = ::name.isInitialized

override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<EntityWithSelfRef>{
val modifiable = EntityWithSelfRefImpl.Builder(null)
modifiable.diff = diff
modifiable.id = createEntityId()
return modifiable
}

@OptIn(EntityStorageInstrumentationApi::class)
override fun createEntity(snapshot: EntityStorageInstrumentation): EntityWithSelfRef{
val entityId = createEntityId()
return snapshot.initializeEntity(entityId){
val entity = EntityWithSelfRefImpl(this)
entity.snapshot = snapshot
entity.id = entityId
entity
}
}

override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.EntityWithSelfRef") as EntityMetadata
}

override fun getEntityInterface(): Class<out WorkspaceEntity>{
return EntityWithSelfRef::class.java
}

override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return EntityWithSelfRef(name, entitySource){
this.parentRef = parents.filterIsInstance<EntityWithSelfRefBuilder>().singleOrNull()
}
}

override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}

override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithSelfRefData
if (this.entitySource != other.entitySource) return false
if (this.name != other.name) return false
return true
}

override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as EntityWithSelfRefData
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
