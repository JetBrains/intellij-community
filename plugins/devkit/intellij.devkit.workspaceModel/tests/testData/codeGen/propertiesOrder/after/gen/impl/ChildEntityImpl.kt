@file:OptIn(EntityStorageInstrumentationApi::class)
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.BaseDataClass
import com.intellij.workspaceModel.test.api.ChildEntity
import com.intellij.workspaceModel.test.api.ChildEntityBuilder
import com.intellij.workspaceModel.test.api.SimpleId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildEntityImpl(private val dataSource: ChildEntityData): ChildEntity, WorkspaceEntityBase(dataSource){

override val name: String
get(){
readField("name")
return dataSource.name
}
override val moduleId: SimpleId
get(){
readField("moduleId")
return dataSource.moduleId
}
override val aBaseEntityProperty: String
get(){
readField("aBaseEntityProperty")
return dataSource.aBaseEntityProperty
}
override val dBaseEntityProperty: String
get(){
readField("dBaseEntityProperty")
return dataSource.dBaseEntityProperty
}
override val bBaseEntityProperty: String
get(){
readField("bBaseEntityProperty")
return dataSource.bBaseEntityProperty
}
override val sealedDataClassProperty: BaseDataClass
get(){
readField("sealedDataClassProperty")
return dataSource.sealedDataClassProperty
}
override val cChildEntityProperty: String
get(){
readField("cChildEntityProperty")
return dataSource.cChildEntityProperty
}
override val entitySource: EntitySource
get(){
readField("entitySource")
return dataSource.entitySource
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
internal class Builder(result: ChildEntityData?): ModifiableWorkspaceEntityBase<ChildEntity, ChildEntityData>(result), ChildEntityBuilder{
internal constructor(): this(ChildEntityData())
override fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (!getEntityData().isNameInitialized()){
error("Field BaseEntity#name should be initialized")
}
if (!getEntityData().isModuleIdInitialized()){
error("Field BaseEntity#moduleId should be initialized")
}
if (!getEntityData().isABaseEntityPropertyInitialized()){
error("Field BaseEntity#aBaseEntityProperty should be initialized")
}
if (!getEntityData().isDBaseEntityPropertyInitialized()){
error("Field BaseEntity#dBaseEntityProperty should be initialized")
}
if (!getEntityData().isBBaseEntityPropertyInitialized()){
error("Field BaseEntity#bBaseEntityProperty should be initialized")
}
if (!getEntityData().isSealedDataClassPropertyInitialized()){
error("Field BaseEntity#sealedDataClassProperty should be initialized")
}
if (!getEntityData().isCChildEntityPropertyInitialized()){
error("Field ChildEntity#cChildEntityProperty should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return emptyList()
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as ChildEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.name != dataSource.name) this.name = dataSource.name
if (this.moduleId != dataSource.moduleId) this.moduleId = dataSource.moduleId
if (this.aBaseEntityProperty != dataSource.aBaseEntityProperty) this.aBaseEntityProperty = dataSource.aBaseEntityProperty
if (this.dBaseEntityProperty != dataSource.dBaseEntityProperty) this.dBaseEntityProperty = dataSource.dBaseEntityProperty
if (this.bBaseEntityProperty != dataSource.bBaseEntityProperty) this.bBaseEntityProperty = dataSource.bBaseEntityProperty
if (this.sealedDataClassProperty != dataSource.sealedDataClassProperty) this.sealedDataClassProperty = dataSource.sealedDataClassProperty
if (this.cChildEntityProperty != dataSource.cChildEntityProperty) this.cChildEntityProperty = dataSource.cChildEntityProperty
updateChildToParentReferences(parents)
}
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value){
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")
}
override var name: String
get() = getEntityData().name
set(value){
checkModificationAllowed()
getEntityData(true).name = value
changedProperty.add("name")
}
override var moduleId: SimpleId
get() = getEntityData().moduleId
set(value){
checkModificationAllowed()
getEntityData(true).moduleId = value
changedProperty.add("moduleId")
}
override var aBaseEntityProperty: String
get() = getEntityData().aBaseEntityProperty
set(value){
checkModificationAllowed()
getEntityData(true).aBaseEntityProperty = value
changedProperty.add("aBaseEntityProperty")
}
override var dBaseEntityProperty: String
get() = getEntityData().dBaseEntityProperty
set(value){
checkModificationAllowed()
getEntityData(true).dBaseEntityProperty = value
changedProperty.add("dBaseEntityProperty")
}
override var bBaseEntityProperty: String
get() = getEntityData().bBaseEntityProperty
set(value){
checkModificationAllowed()
getEntityData(true).bBaseEntityProperty = value
changedProperty.add("bBaseEntityProperty")
}
override var sealedDataClassProperty: BaseDataClass
get() = getEntityData().sealedDataClassProperty
set(value){
checkModificationAllowed()
getEntityData(true).sealedDataClassProperty = value
changedProperty.add("sealedDataClassProperty")
}
override var cChildEntityProperty: String
get() = getEntityData().cChildEntityProperty
set(value){
checkModificationAllowed()
getEntityData(true).cChildEntityProperty = value
changedProperty.add("cChildEntityProperty")
}
override fun getEntityClass(): Class<ChildEntity> = ChildEntity::class.java
}
}
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildEntityData : WorkspaceEntityData<ChildEntity>(), SoftLinkable{
lateinit var name: String
lateinit var moduleId: SimpleId
lateinit var aBaseEntityProperty: String
lateinit var dBaseEntityProperty: String
lateinit var bBaseEntityProperty: String
lateinit var sealedDataClassProperty: BaseDataClass
lateinit var cChildEntityProperty: String
internal fun isNameInitialized(): Boolean = ::name.isInitialized
internal fun isModuleIdInitialized(): Boolean = ::moduleId.isInitialized
internal fun isABaseEntityPropertyInitialized(): Boolean = ::aBaseEntityProperty.isInitialized
internal fun isDBaseEntityPropertyInitialized(): Boolean = ::dBaseEntityProperty.isInitialized
internal fun isBBaseEntityPropertyInitialized(): Boolean = ::bBaseEntityProperty.isInitialized
internal fun isSealedDataClassPropertyInitialized(): Boolean = ::sealedDataClassProperty.isInitialized
internal fun isCChildEntityPropertyInitialized(): Boolean = ::cChildEntityProperty.isInitialized
override fun getLinks(): Set<SymbolicEntityId<*>>{
val result = HashSet<SymbolicEntityId<*>>()
result.add(moduleId)
return result
}
override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>){
index.index(this, moduleId)
}
override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>){
val mutablePreviousSet = HashSet(prev)
val removedItem_moduleId = mutablePreviousSet.remove(moduleId)
if (!removedItem_moduleId){
index.index(this, moduleId)
}
for (removed in mutablePreviousSet){
index.remove(this, removed)
}
}
override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean{
var changed = false
val moduleId_data = if (moduleId == oldLink){
changed = true
newLink as SimpleId
}
else{
null
}
if (moduleId_data != null){
moduleId = moduleId_data
}
return changed
}
override fun newInstance(): ChildEntity = ChildEntityImpl(this)
override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChildEntity, *> = ChildEntityImpl.Builder(null)
override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.ChildEntity") as EntityMetadata
}
override fun getEntityInterface(): Class<out WorkspaceEntity>{
return ChildEntity::class.java
}
override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return ChildEntity(name, moduleId, aBaseEntityProperty, dBaseEntityProperty, bBaseEntityProperty, sealedDataClassProperty, cChildEntityProperty, entitySource)
}
override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
return res
}
override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ChildEntityData
if (this.entitySource != other.entitySource) return false
if (this.name != other.name) return false
if (this.moduleId != other.moduleId) return false
if (this.aBaseEntityProperty != other.aBaseEntityProperty) return false
if (this.dBaseEntityProperty != other.dBaseEntityProperty) return false
if (this.bBaseEntityProperty != other.bBaseEntityProperty) return false
if (this.sealedDataClassProperty != other.sealedDataClassProperty) return false
if (this.cChildEntityProperty != other.cChildEntityProperty) return false
return true
}
override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as ChildEntityData
if (this.name != other.name) return false
if (this.moduleId != other.moduleId) return false
if (this.aBaseEntityProperty != other.aBaseEntityProperty) return false
if (this.dBaseEntityProperty != other.dBaseEntityProperty) return false
if (this.bBaseEntityProperty != other.bBaseEntityProperty) return false
if (this.sealedDataClassProperty != other.sealedDataClassProperty) return false
if (this.cChildEntityProperty != other.cChildEntityProperty) return false
return true
}
override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + moduleId.hashCode()
result = 31 * result + aBaseEntityProperty.hashCode()
result = 31 * result + dBaseEntityProperty.hashCode()
result = 31 * result + bBaseEntityProperty.hashCode()
result = 31 * result + sealedDataClassProperty.hashCode()
result = 31 * result + cChildEntityProperty.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + name.hashCode()
result = 31 * result + moduleId.hashCode()
result = 31 * result + aBaseEntityProperty.hashCode()
result = 31 * result + dBaseEntityProperty.hashCode()
result = 31 * result + bBaseEntityProperty.hashCode()
result = 31 * result + sealedDataClassProperty.hashCode()
result = 31 * result + cChildEntityProperty.hashCode()
return result
}
}
