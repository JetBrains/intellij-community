package org.jetbrains.deft.IntellijWs

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.api.*
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.referrersx
import com.intellij.workspaceModel.storage.referrersy
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.ObjModule

import org.jetbrains.deft.impl.* 
                        
object IntellijWs: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWs")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(65)
        add(ParentSubEntity)
        add(ChildSubEntity)
        add(ChildSubSubEntity)
        add(OneEntityWithPersistentId)
        add(EntityWithSoftLinks)
        add(MainEntityParentList)
        add(AttachedEntityParentList)
        add(MainEntityToParent)
        add(AttachedEntityToParent)
        add(MainEntityList)
        add(AttachedEntityList)
        add(SampleEntity)
        add(ChildSampleEntity)
        add(SecondSampleEntity)
        add(SourceEntity)
        add(ChildSourceEntity)
        add(PersistentIdEntity)
        add(ParentEntity)
        add(ChildEntity)
        add(ParentNullableEntity)
        add(ChildNullableEntity)
        add(MainEntity)
        add(AttachedEntity)
        add(FirstEntityWithPId)
        add(SecondEntityWithPId)
        add(ParentChainEntity)
        add(SimpleAbstractEntity)
        add(CompositeAbstractEntity)
        add(CompositeChildAbstractEntity)
        add(SimpleChildAbstractEntity)
        add(ParentAbEntity)
        add(ChildAbstractBaseEntity)
        add(ChildFirstEntity)
        add(ChildSecondEntity)
        add(XParentEntity)
        add(XChildEntity)
        add(XChildWithOptionalParentEntity)
        add(XChildChildEntity)
        add(SampleEntity2)
        add(VFUEntity2)
        add(ParentMultipleEntity)
        add(ChildMultipleEntity)
        add(VFUEntity)
        add(VFUWithTwoPropertiesEntity)
        add(NullableVFUEntity)
        add(ListVFUEntity)
        add(ParentSingleAbEntity)
        add(ChildSingleAbstractBaseEntity)
        add(ChildSingleFirstEntity)
        add(ChildSingleSecondEntity)
    }
}

var AttachedEntityParentList.Builder.ref: MainEntityParentList?
    get() {
        return referrersy(MainEntityParentList::children).singleOrNull()
    }
    set(value) {
        val diff = (this as AttachedEntityParentListImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as MainEntityParentListImpl.Builder).diff == null) {
                    value._children = (value._children ?: emptyList()) + this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneParentOfChild(MainEntityParentListImpl.CHILDREN_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("MainEntityParentList", "children", false, MainEntityParentListImpl.CHILDREN_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as MainEntityParentListImpl.Builder)._children = ((value as MainEntityParentListImpl.Builder)._children ?: emptyList()) + this
            }
        }
    }

var AttachedEntityToParent.Builder.ref: MainEntityToParent
    get() {
        return referrersx(MainEntityToParent::child).single()
    }
    set(value) {
        val diff = (this as AttachedEntityToParentImpl.Builder).diff
        if (diff != null) {
            if ((value as MainEntityToParentImpl.Builder).diff == null) {
                value._child = this
                diff.addEntity(value)
            }
            diff.updateOneToOneParentOfChild(MainEntityToParentImpl.CHILD_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("MainEntityToParent", "child", false, MainEntityToParentImpl.CHILD_CONNECTION_ID)
            this.extReferences[key] = value
            
            (value as MainEntityToParentImpl.Builder)._child = this
        }
    }

var MainEntityList.Builder.child: @Child List<AttachedEntityList>
    get() {
        return referrersx(AttachedEntityList::ref)
    }
    set(value) {
        val diff = (this as MainEntityListImpl.Builder).diff
        if (diff != null) {
            for (item in value) {
                if ((item as AttachedEntityListImpl.Builder).diff == null) {
                    item._ref = this
                    diff.addEntity(item)
                }
            }
            diff.updateOneToManyChildrenOfParent(AttachedEntityListImpl.REF_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("AttachedEntityList", "ref", true, AttachedEntityListImpl.REF_CONNECTION_ID)
            this.extReferences[key] = value
            
            for (item in value) {
                (item as AttachedEntityListImpl.Builder)._ref = this
            }
        }
    }

var MainEntity.Builder.child: @Child AttachedEntity?
    get() {
        return referrersx(AttachedEntity::ref).singleOrNull()
    }
    set(value) {
        val diff = (this as MainEntityImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as AttachedEntityImpl.Builder).diff == null) {
                    value._ref = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(AttachedEntityImpl.REF_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("AttachedEntity", "ref", true, AttachedEntityImpl.REF_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as AttachedEntityImpl.Builder)._ref = this
            }
        }
    }


fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentSubEntity, modification: ParentSubEntity.Builder.() -> Unit) = modifyEntity(ParentSubEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSubEntity, modification: ChildSubEntity.Builder.() -> Unit) = modifyEntity(ChildSubEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSubSubEntity, modification: ChildSubSubEntity.Builder.() -> Unit) = modifyEntity(ChildSubSubEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OneEntityWithPersistentId, modification: OneEntityWithPersistentId.Builder.() -> Unit) = modifyEntity(OneEntityWithPersistentIdImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: EntityWithSoftLinks, modification: EntityWithSoftLinks.Builder.() -> Unit) = modifyEntity(EntityWithSoftLinksImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityParentList, modification: MainEntityParentList.Builder.() -> Unit) = modifyEntity(MainEntityParentListImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityParentList, modification: AttachedEntityParentList.Builder.() -> Unit) = modifyEntity(AttachedEntityParentListImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityToParent, modification: MainEntityToParent.Builder.() -> Unit) = modifyEntity(MainEntityToParentImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityToParent, modification: AttachedEntityToParent.Builder.() -> Unit) = modifyEntity(AttachedEntityToParentImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityList, modification: MainEntityList.Builder.() -> Unit) = modifyEntity(MainEntityListImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityList, modification: AttachedEntityList.Builder.() -> Unit) = modifyEntity(AttachedEntityListImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity, modification: SampleEntity.Builder.() -> Unit) = modifyEntity(SampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSampleEntity, modification: ChildSampleEntity.Builder.() -> Unit) = modifyEntity(ChildSampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondSampleEntity, modification: SecondSampleEntity.Builder.() -> Unit) = modifyEntity(SecondSampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceEntity, modification: SourceEntity.Builder.() -> Unit) = modifyEntity(SourceEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSourceEntity, modification: ChildSourceEntity.Builder.() -> Unit) = modifyEntity(ChildSourceEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: PersistentIdEntity, modification: PersistentIdEntity.Builder.() -> Unit) = modifyEntity(PersistentIdEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = modifyEntity(ParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildEntity, modification: ChildEntity.Builder.() -> Unit) = modifyEntity(ChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentNullableEntity, modification: ParentNullableEntity.Builder.() -> Unit) = modifyEntity(ParentNullableEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildNullableEntity, modification: ChildNullableEntity.Builder.() -> Unit) = modifyEntity(ChildNullableEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntity, modification: MainEntity.Builder.() -> Unit) = modifyEntity(MainEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntity, modification: AttachedEntity.Builder.() -> Unit) = modifyEntity(AttachedEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FirstEntityWithPId, modification: FirstEntityWithPId.Builder.() -> Unit) = modifyEntity(FirstEntityWithPIdImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondEntityWithPId, modification: SecondEntityWithPId.Builder.() -> Unit) = modifyEntity(SecondEntityWithPIdImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentChainEntity, modification: ParentChainEntity.Builder.() -> Unit) = modifyEntity(ParentChainEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: CompositeChildAbstractEntity, modification: CompositeChildAbstractEntity.Builder.() -> Unit) = modifyEntity(CompositeChildAbstractEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SimpleChildAbstractEntity, modification: SimpleChildAbstractEntity.Builder.() -> Unit) = modifyEntity(SimpleChildAbstractEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentAbEntity, modification: ParentAbEntity.Builder.() -> Unit) = modifyEntity(ParentAbEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildFirstEntity, modification: ChildFirstEntity.Builder.() -> Unit) = modifyEntity(ChildFirstEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSecondEntity, modification: ChildSecondEntity.Builder.() -> Unit) = modifyEntity(ChildSecondEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XParentEntity, modification: XParentEntity.Builder.() -> Unit) = modifyEntity(XParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildEntity, modification: XChildEntity.Builder.() -> Unit) = modifyEntity(XChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildWithOptionalParentEntity, modification: XChildWithOptionalParentEntity.Builder.() -> Unit) = modifyEntity(XChildWithOptionalParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildChildEntity, modification: XChildChildEntity.Builder.() -> Unit) = modifyEntity(XChildChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity2, modification: SampleEntity2.Builder.() -> Unit) = modifyEntity(SampleEntity2Impl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity2, modification: VFUEntity2.Builder.() -> Unit) = modifyEntity(VFUEntity2Impl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentMultipleEntity, modification: ParentMultipleEntity.Builder.() -> Unit) = modifyEntity(ParentMultipleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildMultipleEntity, modification: ChildMultipleEntity.Builder.() -> Unit) = modifyEntity(ChildMultipleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity, modification: VFUEntity.Builder.() -> Unit) = modifyEntity(VFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUWithTwoPropertiesEntity, modification: VFUWithTwoPropertiesEntity.Builder.() -> Unit) = modifyEntity(VFUWithTwoPropertiesEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NullableVFUEntity, modification: NullableVFUEntity.Builder.() -> Unit) = modifyEntity(NullableVFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ListVFUEntity, modification: ListVFUEntity.Builder.() -> Unit) = modifyEntity(ListVFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentSingleAbEntity, modification: ParentSingleAbEntity.Builder.() -> Unit) = modifyEntity(ParentSingleAbEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSingleFirstEntity, modification: ChildSingleFirstEntity.Builder.() -> Unit) = modifyEntity(ChildSingleFirstEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSingleSecondEntity, modification: ChildSingleSecondEntity.Builder.() -> Unit) = modifyEntity(ChildSingleSecondEntityImpl.Builder::class.java, entity, modification)
