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
import com.intellij.workspaceModel.test.api.SimpleEntity
import com.intellij.workspaceModel.test.api.SimpleEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityImpl(private val dataSource: SimpleEntityData): SimpleEntity, WorkspaceEntityBase(dataSource) {

private companion object {

private val connections = listOf<ConnectionId>()

}

override val version: Int
get() {
readField("version")
return dataSource.version
}
override val name: String
get() {
readField("name")
return dataSource.name
}
override val isSimple: Boolean
get() {
readField("isSimple")
return dataSource.isSimple
}
override val char: Char
get() {
readField("char")
return dataSource.char
}
override val long: Long
get() {
readField("long")
return dataSource.long
}
override val float: Float
get() {
readField("float")
return dataSource.float
}
override val double: Double
get() {
readField("double")
return dataSource.double
}
override val short: Short
get() {
readField("short")
return dataSource.short
}
override val byte: Byte
get() {
readField("byte")
return dataSource.byte
}

override val entitySource: EntitySource
get() {
readField("entitySource")
return dataSource.entitySource
}

override fun connectionIdList(): List<ConnectionId> {
return connections
}


internal class Builder(result: SimpleEntityData?): ModifiableWorkspaceEntityBase<SimpleEntity, SimpleEntityData>(result), SimpleEntityBuilder {
internal constructor(): this(SimpleEntityData())

override fun applyToBuilder(builder: MutableEntityStorage){
if (this.diff != null){
if (existsInBuilder(builder)){
this.diff = builder
return
}
else{
error("Entity SimpleEntity is already created in a different builder")
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
error("Field SimpleEntity#name should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as SimpleEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.version != dataSource.version) this.version = dataSource.version
if (this.name != dataSource.name) this.name = dataSource.name
if (this.isSimple != dataSource.isSimple) this.isSimple = dataSource.isSimple
if (this.char != dataSource.char) this.char = dataSource.char
if (this.long != dataSource.long) this.long = dataSource.long
if (this.float != dataSource.float) this.float = dataSource.float
if (this.double != dataSource.double) this.double = dataSource.double
if (this.short != dataSource.short) this.short = dataSource.short
if (this.byte != dataSource.byte) this.byte = dataSource.byte
updateChildToParentReferences(parents)
}

        
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value) {
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")

}
override var version: Int
get() = getEntityData().version
set(value) {
checkModificationAllowed()
getEntityData(true).version = value
changedProperty.add("version")
}
override var name: String
get() = getEntityData().name
set(value) {
checkModificationAllowed()
getEntityData(true).name = value
changedProperty.add("name")
}
override var isSimple: Boolean
get() = getEntityData().isSimple
set(value) {
checkModificationAllowed()
getEntityData(true).isSimple = value
changedProperty.add("isSimple")
}
override var char: Char
get() = getEntityData().char
set(value) {
checkModificationAllowed()
getEntityData(true).char = value
changedProperty.add("char")
}
override var long: Long
get() = getEntityData().long
set(value) {
checkModificationAllowed()
getEntityData(true).long = value
changedProperty.add("long")
}
override var float: Float
get() = getEntityData().float
set(value) {
checkModificationAllowed()
getEntityData(true).float = value
changedProperty.add("float")
}
override var double: Double
get() = getEntityData().double
set(value) {
checkModificationAllowed()
getEntityData(true).double = value
changedProperty.add("double")
}
override var short: Short
get() = getEntityData().short
set(value) {
checkModificationAllowed()
getEntityData(true).short = value
changedProperty.add("short")
}
override var byte: Byte
get() = getEntityData().byte
set(value) {
checkModificationAllowed()
getEntityData(true).byte = value
changedProperty.add("byte")
}

override fun getEntityClass(): Class<SimpleEntity> = SimpleEntity::class.java
}

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleEntityData : WorkspaceEntityData<SimpleEntity>(){
var version: Int = 0
lateinit var name: String
var isSimple: Boolean = false
var char: Char = 0.toChar()
var long: Long = 0
var float: Float = 0f
var double: Double = 0.0
var short: Short = 0
var byte: Byte = 0


internal fun isNameInitialized(): Boolean = ::name.isInitialized








override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<SimpleEntity>{
val modifiable = SimpleEntityImpl.Builder(null)
modifiable.diff = diff
modifiable.id = createEntityId()
return modifiable
}

@OptIn(EntityStorageInstrumentationApi::class)
override fun createEntity(snapshot: EntityStorageInstrumentation): SimpleEntity{
val entityId = createEntityId()
return snapshot.initializeEntity(entityId){
val entity = SimpleEntityImpl(this)
entity.snapshot = snapshot
entity.id = entityId
entity
}
}

override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.SimpleEntity") as EntityMetadata
}

override fun getEntityInterface(): Class<out WorkspaceEntity>{
return SimpleEntity::class.java
}

override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return SimpleEntity(version, name, isSimple, char, long, float, double, short, byte, entitySource)
}

override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}

override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityData
if (this.entitySource != other.entitySource) return false
if (this.version != other.version) return false
if (this.name != other.name) return false
if (this.isSimple != other.isSimple) return false
if (this.char != other.char) return false
if (this.long != other.long) return false
if (this.float != other.float) return false
if (this.double != other.double) return false
if (this.short != other.short) return false
if (this.byte != other.byte) return false
return true
}

override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as SimpleEntityData
if (this.version != other.version) return false
if (this.name != other.name) return false
if (this.isSimple != other.isSimple) return false
if (this.char != other.char) return false
if (this.long != other.long) return false
if (this.float != other.float) return false
if (this.double != other.double) return false
if (this.short != other.short) return false
if (this.byte != other.byte) return false
return true
}

override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + version.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + isSimple.hashCode()
result = 31 * result + char.hashCode()
result = 31 * result + long.hashCode()
result = 31 * result + float.hashCode()
result = 31 * result + double.hashCode()
result = 31 * result + short.hashCode()
result = 31 * result + byte.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + version.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + isSimple.hashCode()
result = 31 * result + char.hashCode()
result = 31 * result + long.hashCode()
result = 31 * result + float.hashCode()
result = 31 * result + double.hashCode()
result = 31 * result + short.hashCode()
result = 31 * result + byte.hashCode()
return result
}
}
