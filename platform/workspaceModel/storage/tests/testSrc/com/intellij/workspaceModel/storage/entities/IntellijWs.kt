package org.jetbrains.deft.IntellijWs

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.api.AttachedEntity
import com.intellij.workspaceModel.storage.entities.api.AttachedEntityImpl
import com.intellij.workspaceModel.storage.entities.api.AttachedEntityList
import com.intellij.workspaceModel.storage.entities.api.AttachedEntityListImpl
import com.intellij.workspaceModel.storage.entities.api.AttachedEntityParentList
import com.intellij.workspaceModel.storage.entities.api.AttachedEntityParentListImpl
import com.intellij.workspaceModel.storage.entities.api.AttachedEntityToParent
import com.intellij.workspaceModel.storage.entities.api.AttachedEntityToParentImpl
import com.intellij.workspaceModel.storage.entities.api.ChildAbstractBaseEntity
import com.intellij.workspaceModel.storage.entities.api.ChildEntity
import com.intellij.workspaceModel.storage.entities.api.ChildFirstEntity
import com.intellij.workspaceModel.storage.entities.api.ChildMultipleEntity
import com.intellij.workspaceModel.storage.entities.api.ChildNullableEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSampleEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSecondEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSingleAbstractBaseEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSingleFirstEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSingleSecondEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSourceEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSubEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSubSubEntity
import com.intellij.workspaceModel.storage.entities.api.CompositeAbstractEntity
import com.intellij.workspaceModel.storage.entities.api.CompositeChildAbstractEntity
import com.intellij.workspaceModel.storage.entities.api.EntityWithSoftLinks
import com.intellij.workspaceModel.storage.entities.api.FirstEntityWithPId
import com.intellij.workspaceModel.storage.entities.api.ListVFUEntity
import com.intellij.workspaceModel.storage.entities.api.MainEntity
import com.intellij.workspaceModel.storage.entities.api.MainEntityImpl
import com.intellij.workspaceModel.storage.entities.api.MainEntityList
import com.intellij.workspaceModel.storage.entities.api.MainEntityListImpl
import com.intellij.workspaceModel.storage.entities.api.MainEntityParentList
import com.intellij.workspaceModel.storage.entities.api.MainEntityParentListImpl
import com.intellij.workspaceModel.storage.entities.api.MainEntityToParent
import com.intellij.workspaceModel.storage.entities.api.MainEntityToParentImpl
import com.intellij.workspaceModel.storage.entities.api.NullableVFUEntity
import com.intellij.workspaceModel.storage.entities.api.OneEntityWithPersistentId
import com.intellij.workspaceModel.storage.entities.api.ParentAbEntity
import com.intellij.workspaceModel.storage.entities.api.ParentChainEntity
import com.intellij.workspaceModel.storage.entities.api.ParentEntity
import com.intellij.workspaceModel.storage.entities.api.ParentMultipleEntity
import com.intellij.workspaceModel.storage.entities.api.ParentNullableEntity
import com.intellij.workspaceModel.storage.entities.api.ParentSingleAbEntity
import com.intellij.workspaceModel.storage.entities.api.ParentSubEntity
import com.intellij.workspaceModel.storage.entities.api.PersistentIdEntity
import com.intellij.workspaceModel.storage.entities.api.SampleEntity
import com.intellij.workspaceModel.storage.entities.api.SampleEntity2
import com.intellij.workspaceModel.storage.entities.api.SecondEntityWithPId
import com.intellij.workspaceModel.storage.entities.api.SecondSampleEntity
import com.intellij.workspaceModel.storage.entities.api.SimpleAbstractEntity
import com.intellij.workspaceModel.storage.entities.api.SimpleChildAbstractEntity
import com.intellij.workspaceModel.storage.entities.api.SourceEntity
import com.intellij.workspaceModel.storage.entities.api.VFUEntity
import com.intellij.workspaceModel.storage.entities.api.VFUEntity2
import com.intellij.workspaceModel.storage.entities.api.VFUWithTwoPropertiesEntity
import com.intellij.workspaceModel.storage.entities.api.XChildChildEntity
import com.intellij.workspaceModel.storage.entities.api.XChildEntity
import com.intellij.workspaceModel.storage.entities.api.XChildWithOptionalParentEntity
import com.intellij.workspaceModel.storage.entities.api.XParentEntity
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


fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentSubEntity, modification: ParentSubEntity.Builder.() -> Unit) = modifyEntity(ParentSubEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSubEntity, modification: ChildSubEntity.Builder.() -> Unit) = modifyEntity(ChildSubEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSubSubEntity, modification: ChildSubSubEntity.Builder.() -> Unit) = modifyEntity(ChildSubSubEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OneEntityWithPersistentId, modification: OneEntityWithPersistentId.Builder.() -> Unit) = modifyEntity(OneEntityWithPersistentId.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: EntityWithSoftLinks, modification: EntityWithSoftLinks.Builder.() -> Unit) = modifyEntity(EntityWithSoftLinks.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityParentList, modification: MainEntityParentList.Builder.() -> Unit) = modifyEntity(MainEntityParentList.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityParentList, modification: AttachedEntityParentList.Builder.() -> Unit) = modifyEntity(AttachedEntityParentList.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityToParent, modification: MainEntityToParent.Builder.() -> Unit) = modifyEntity(MainEntityToParent.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityToParent, modification: AttachedEntityToParent.Builder.() -> Unit) = modifyEntity(AttachedEntityToParent.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityList, modification: MainEntityList.Builder.() -> Unit) = modifyEntity(MainEntityList.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityList, modification: AttachedEntityList.Builder.() -> Unit) = modifyEntity(AttachedEntityList.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity, modification: SampleEntity.Builder.() -> Unit) = modifyEntity(SampleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSampleEntity, modification: ChildSampleEntity.Builder.() -> Unit) = modifyEntity(ChildSampleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondSampleEntity, modification: SecondSampleEntity.Builder.() -> Unit) = modifyEntity(SecondSampleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceEntity, modification: SourceEntity.Builder.() -> Unit) = modifyEntity(SourceEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSourceEntity, modification: ChildSourceEntity.Builder.() -> Unit) = modifyEntity(ChildSourceEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: PersistentIdEntity, modification: PersistentIdEntity.Builder.() -> Unit) = modifyEntity(PersistentIdEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = modifyEntity(ParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildEntity, modification: ChildEntity.Builder.() -> Unit) = modifyEntity(ChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentNullableEntity, modification: ParentNullableEntity.Builder.() -> Unit) = modifyEntity(ParentNullableEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildNullableEntity, modification: ChildNullableEntity.Builder.() -> Unit) = modifyEntity(ChildNullableEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntity, modification: MainEntity.Builder.() -> Unit) = modifyEntity(MainEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntity, modification: AttachedEntity.Builder.() -> Unit) = modifyEntity(AttachedEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FirstEntityWithPId, modification: FirstEntityWithPId.Builder.() -> Unit) = modifyEntity(FirstEntityWithPId.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondEntityWithPId, modification: SecondEntityWithPId.Builder.() -> Unit) = modifyEntity(SecondEntityWithPId.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentChainEntity, modification: ParentChainEntity.Builder.() -> Unit) = modifyEntity(ParentChainEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: CompositeChildAbstractEntity, modification: CompositeChildAbstractEntity.Builder.() -> Unit) = modifyEntity(CompositeChildAbstractEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SimpleChildAbstractEntity, modification: SimpleChildAbstractEntity.Builder.() -> Unit) = modifyEntity(SimpleChildAbstractEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentAbEntity, modification: ParentAbEntity.Builder.() -> Unit) = modifyEntity(ParentAbEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildFirstEntity, modification: ChildFirstEntity.Builder.() -> Unit) = modifyEntity(ChildFirstEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSecondEntity, modification: ChildSecondEntity.Builder.() -> Unit) = modifyEntity(ChildSecondEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XParentEntity, modification: XParentEntity.Builder.() -> Unit) = modifyEntity(XParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildEntity, modification: XChildEntity.Builder.() -> Unit) = modifyEntity(XChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildWithOptionalParentEntity, modification: XChildWithOptionalParentEntity.Builder.() -> Unit) = modifyEntity(XChildWithOptionalParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildChildEntity, modification: XChildChildEntity.Builder.() -> Unit) = modifyEntity(XChildChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity2, modification: SampleEntity2.Builder.() -> Unit) = modifyEntity(SampleEntity2.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity2, modification: VFUEntity2.Builder.() -> Unit) = modifyEntity(VFUEntity2.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentMultipleEntity, modification: ParentMultipleEntity.Builder.() -> Unit) = modifyEntity(ParentMultipleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildMultipleEntity, modification: ChildMultipleEntity.Builder.() -> Unit) = modifyEntity(ChildMultipleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity, modification: VFUEntity.Builder.() -> Unit) = modifyEntity(VFUEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUWithTwoPropertiesEntity, modification: VFUWithTwoPropertiesEntity.Builder.() -> Unit) = modifyEntity(VFUWithTwoPropertiesEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NullableVFUEntity, modification: NullableVFUEntity.Builder.() -> Unit) = modifyEntity(NullableVFUEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ListVFUEntity, modification: ListVFUEntity.Builder.() -> Unit) = modifyEntity(ListVFUEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentSingleAbEntity, modification: ParentSingleAbEntity.Builder.() -> Unit) = modifyEntity(ParentSingleAbEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSingleFirstEntity, modification: ChildSingleFirstEntity.Builder.() -> Unit) = modifyEntity(ChildSingleFirstEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSingleSecondEntity, modification: ChildSingleSecondEntity.Builder.() -> Unit) = modifyEntity(ChildSingleSecondEntity.Builder::class.java, entity, modification)
