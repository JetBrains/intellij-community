package org.jetbrains.deft.TestEntities

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.test.api.AssertConsistencyEntity
import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntity
import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntityImpl
import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntityList
import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntityListImpl
import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntityParentList
import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntityParentListImpl
import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntityToParent
import com.intellij.workspaceModel.storage.entities.test.api.AttachedEntityToParentImpl
import com.intellij.workspaceModel.storage.entities.test.api.ChildEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildFirstEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildMultipleEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildNullableEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildSampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildSecondEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildSingleFirstEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildSingleSecondEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildSourceEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildSubEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildSubSubEntity
import com.intellij.workspaceModel.storage.entities.test.api.ComposedIdSoftRefEntity
import com.intellij.workspaceModel.storage.entities.test.api.ComposedLinkEntity
import com.intellij.workspaceModel.storage.entities.test.api.CompositeChildAbstractEntity
import com.intellij.workspaceModel.storage.entities.test.api.EntityWithSoftLinks
import com.intellij.workspaceModel.storage.entities.test.api.FirstEntityWithPId
import com.intellij.workspaceModel.storage.entities.test.api.LeftEntity
import com.intellij.workspaceModel.storage.entities.test.api.LinkedListEntity
import com.intellij.workspaceModel.storage.entities.test.api.ListVFUEntity
import com.intellij.workspaceModel.storage.entities.test.api.MainEntity
import com.intellij.workspaceModel.storage.entities.test.api.MainEntityImpl
import com.intellij.workspaceModel.storage.entities.test.api.MainEntityList
import com.intellij.workspaceModel.storage.entities.test.api.MainEntityListImpl
import com.intellij.workspaceModel.storage.entities.test.api.MainEntityParentList
import com.intellij.workspaceModel.storage.entities.test.api.MainEntityParentListImpl
import com.intellij.workspaceModel.storage.entities.test.api.MainEntityToParent
import com.intellij.workspaceModel.storage.entities.test.api.MainEntityToParentImpl
import com.intellij.workspaceModel.storage.entities.test.api.MiddleEntity
import com.intellij.workspaceModel.storage.entities.test.api.NamedChildEntity
import com.intellij.workspaceModel.storage.entities.test.api.NamedEntity
import com.intellij.workspaceModel.storage.entities.test.api.NullableVFUEntity
import com.intellij.workspaceModel.storage.entities.test.api.OneEntityWithPersistentId
import com.intellij.workspaceModel.storage.entities.test.api.OoChildAlsoWithPidEntity
import com.intellij.workspaceModel.storage.entities.test.api.OoChildEntity
import com.intellij.workspaceModel.storage.entities.test.api.OoChildForParentWithPidEntity
import com.intellij.workspaceModel.storage.entities.test.api.OoChildWithNullableParentEntity
import com.intellij.workspaceModel.storage.entities.test.api.OoChildWithPidEntity
import com.intellij.workspaceModel.storage.entities.test.api.OoParentEntity
import com.intellij.workspaceModel.storage.entities.test.api.OoParentWithPidEntity
import com.intellij.workspaceModel.storage.entities.test.api.OoParentWithoutPidEntity
import com.intellij.workspaceModel.storage.entities.test.api.ParentAbEntity
import com.intellij.workspaceModel.storage.entities.test.api.ParentChainEntity
import com.intellij.workspaceModel.storage.entities.test.api.ParentEntity
import com.intellij.workspaceModel.storage.entities.test.api.ParentMultipleEntity
import com.intellij.workspaceModel.storage.entities.test.api.ParentNullableEntity
import com.intellij.workspaceModel.storage.entities.test.api.ParentSingleAbEntity
import com.intellij.workspaceModel.storage.entities.test.api.ParentSubEntity
import com.intellij.workspaceModel.storage.entities.test.api.PersistentIdEntity
import com.intellij.workspaceModel.storage.entities.test.api.RightEntity
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity2
import com.intellij.workspaceModel.storage.entities.test.api.SecondEntityWithPId
import com.intellij.workspaceModel.storage.entities.test.api.SecondSampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.SelfLinkedEntity
import com.intellij.workspaceModel.storage.entities.test.api.SelfLinkedEntityImpl
import com.intellij.workspaceModel.storage.entities.test.api.SimpleChildAbstractEntity
import com.intellij.workspaceModel.storage.entities.test.api.SoftLinkReferencedChild
import com.intellij.workspaceModel.storage.entities.test.api.SourceEntity
import com.intellij.workspaceModel.storage.entities.test.api.VFUEntity
import com.intellij.workspaceModel.storage.entities.test.api.VFUEntity2
import com.intellij.workspaceModel.storage.entities.test.api.VFUWithTwoPropertiesEntity
import com.intellij.workspaceModel.storage.entities.test.api.WithListSoftLinksEntity
import com.intellij.workspaceModel.storage.entities.test.api.WithSoftLinkEntity
import com.intellij.workspaceModel.storage.entities.test.api.XChildChildEntity
import com.intellij.workspaceModel.storage.entities.test.api.XChildEntity
import com.intellij.workspaceModel.storage.entities.test.api.XChildWithOptionalParentEntity
import com.intellij.workspaceModel.storage.entities.test.api.XParentEntity
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.referrersx
import com.intellij.workspaceModel.storage.referrersy
import org.jetbrains.deft.annotations.Child

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

var SelfLinkedEntity.Builder.children: @Child List<SelfLinkedEntity>
    get() {
        return referrersx(SelfLinkedEntity::parentEntity)
    }
    set(value) {
        val diff = (this as SelfLinkedEntityImpl.Builder).diff
        if (diff != null) {
            for (item in value) {
                if ((item as SelfLinkedEntityImpl.Builder).diff == null) {
                    item._parentEntity = this
                    diff.addEntity(item)
                }
            }
            diff.updateOneToManyChildrenOfParent(SelfLinkedEntityImpl.PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("SelfLinkedEntity", "parentEntity", true, SelfLinkedEntityImpl.PARENTENTITY_CONNECTION_ID)
            this.extReferences[key] = value
            
            for (item in value) {
                (item as SelfLinkedEntityImpl.Builder)._parentEntity = this
            }
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


fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AssertConsistencyEntity, modification: AssertConsistencyEntity.Builder.() -> Unit) = modifyEntity(AssertConsistencyEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntity, modification: AttachedEntity.Builder.() -> Unit) = modifyEntity(AttachedEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityList, modification: AttachedEntityList.Builder.() -> Unit) = modifyEntity(AttachedEntityList.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityParentList, modification: AttachedEntityParentList.Builder.() -> Unit) = modifyEntity(AttachedEntityParentList.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityToParent, modification: AttachedEntityToParent.Builder.() -> Unit) = modifyEntity(AttachedEntityToParent.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildEntity, modification: ChildEntity.Builder.() -> Unit) = modifyEntity(ChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildFirstEntity, modification: ChildFirstEntity.Builder.() -> Unit) = modifyEntity(ChildFirstEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildMultipleEntity, modification: ChildMultipleEntity.Builder.() -> Unit) = modifyEntity(ChildMultipleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildNullableEntity, modification: ChildNullableEntity.Builder.() -> Unit) = modifyEntity(ChildNullableEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSampleEntity, modification: ChildSampleEntity.Builder.() -> Unit) = modifyEntity(ChildSampleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSecondEntity, modification: ChildSecondEntity.Builder.() -> Unit) = modifyEntity(ChildSecondEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSingleFirstEntity, modification: ChildSingleFirstEntity.Builder.() -> Unit) = modifyEntity(ChildSingleFirstEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSingleSecondEntity, modification: ChildSingleSecondEntity.Builder.() -> Unit) = modifyEntity(ChildSingleSecondEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSourceEntity, modification: ChildSourceEntity.Builder.() -> Unit) = modifyEntity(ChildSourceEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSubEntity, modification: ChildSubEntity.Builder.() -> Unit) = modifyEntity(ChildSubEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSubSubEntity, modification: ChildSubSubEntity.Builder.() -> Unit) = modifyEntity(ChildSubSubEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ComposedIdSoftRefEntity, modification: ComposedIdSoftRefEntity.Builder.() -> Unit) = modifyEntity(ComposedIdSoftRefEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ComposedLinkEntity, modification: ComposedLinkEntity.Builder.() -> Unit) = modifyEntity(ComposedLinkEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: CompositeChildAbstractEntity, modification: CompositeChildAbstractEntity.Builder.() -> Unit) = modifyEntity(CompositeChildAbstractEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: EntityWithSoftLinks, modification: EntityWithSoftLinks.Builder.() -> Unit) = modifyEntity(EntityWithSoftLinks.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FirstEntityWithPId, modification: FirstEntityWithPId.Builder.() -> Unit) = modifyEntity(FirstEntityWithPId.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LeftEntity, modification: LeftEntity.Builder.() -> Unit) = modifyEntity(LeftEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LinkedListEntity, modification: LinkedListEntity.Builder.() -> Unit) = modifyEntity(LinkedListEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ListVFUEntity, modification: ListVFUEntity.Builder.() -> Unit) = modifyEntity(ListVFUEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntity, modification: MainEntity.Builder.() -> Unit) = modifyEntity(MainEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityList, modification: MainEntityList.Builder.() -> Unit) = modifyEntity(MainEntityList.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityParentList, modification: MainEntityParentList.Builder.() -> Unit) = modifyEntity(MainEntityParentList.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityToParent, modification: MainEntityToParent.Builder.() -> Unit) = modifyEntity(MainEntityToParent.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MiddleEntity, modification: MiddleEntity.Builder.() -> Unit) = modifyEntity(MiddleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NamedChildEntity, modification: NamedChildEntity.Builder.() -> Unit) = modifyEntity(NamedChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NamedEntity, modification: NamedEntity.Builder.() -> Unit) = modifyEntity(NamedEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NullableVFUEntity, modification: NullableVFUEntity.Builder.() -> Unit) = modifyEntity(NullableVFUEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OneEntityWithPersistentId, modification: OneEntityWithPersistentId.Builder.() -> Unit) = modifyEntity(OneEntityWithPersistentId.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildAlsoWithPidEntity, modification: OoChildAlsoWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildAlsoWithPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildEntity, modification: OoChildEntity.Builder.() -> Unit) = modifyEntity(OoChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildForParentWithPidEntity, modification: OoChildForParentWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildForParentWithPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildWithNullableParentEntity, modification: OoChildWithNullableParentEntity.Builder.() -> Unit) = modifyEntity(OoChildWithNullableParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildWithPidEntity, modification: OoChildWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildWithPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentEntity, modification: OoParentEntity.Builder.() -> Unit) = modifyEntity(OoParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentWithPidEntity, modification: OoParentWithPidEntity.Builder.() -> Unit) = modifyEntity(OoParentWithPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentWithoutPidEntity, modification: OoParentWithoutPidEntity.Builder.() -> Unit) = modifyEntity(OoParentWithoutPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentAbEntity, modification: ParentAbEntity.Builder.() -> Unit) = modifyEntity(ParentAbEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentChainEntity, modification: ParentChainEntity.Builder.() -> Unit) = modifyEntity(ParentChainEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = modifyEntity(ParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentMultipleEntity, modification: ParentMultipleEntity.Builder.() -> Unit) = modifyEntity(ParentMultipleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentNullableEntity, modification: ParentNullableEntity.Builder.() -> Unit) = modifyEntity(ParentNullableEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentSingleAbEntity, modification: ParentSingleAbEntity.Builder.() -> Unit) = modifyEntity(ParentSingleAbEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentSubEntity, modification: ParentSubEntity.Builder.() -> Unit) = modifyEntity(ParentSubEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: PersistentIdEntity, modification: PersistentIdEntity.Builder.() -> Unit) = modifyEntity(PersistentIdEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: RightEntity, modification: RightEntity.Builder.() -> Unit) = modifyEntity(RightEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity, modification: SampleEntity.Builder.() -> Unit) = modifyEntity(SampleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity2, modification: SampleEntity2.Builder.() -> Unit) = modifyEntity(SampleEntity2.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondEntityWithPId, modification: SecondEntityWithPId.Builder.() -> Unit) = modifyEntity(SecondEntityWithPId.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondSampleEntity, modification: SecondSampleEntity.Builder.() -> Unit) = modifyEntity(SecondSampleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SelfLinkedEntity, modification: SelfLinkedEntity.Builder.() -> Unit) = modifyEntity(SelfLinkedEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SimpleChildAbstractEntity, modification: SimpleChildAbstractEntity.Builder.() -> Unit) = modifyEntity(SimpleChildAbstractEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SoftLinkReferencedChild, modification: SoftLinkReferencedChild.Builder.() -> Unit) = modifyEntity(SoftLinkReferencedChild.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceEntity, modification: SourceEntity.Builder.() -> Unit) = modifyEntity(SourceEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity, modification: VFUEntity.Builder.() -> Unit) = modifyEntity(VFUEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity2, modification: VFUEntity2.Builder.() -> Unit) = modifyEntity(VFUEntity2.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUWithTwoPropertiesEntity, modification: VFUWithTwoPropertiesEntity.Builder.() -> Unit) = modifyEntity(VFUWithTwoPropertiesEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: WithListSoftLinksEntity, modification: WithListSoftLinksEntity.Builder.() -> Unit) = modifyEntity(WithListSoftLinksEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: WithSoftLinkEntity, modification: WithSoftLinkEntity.Builder.() -> Unit) = modifyEntity(WithSoftLinkEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildChildEntity, modification: XChildChildEntity.Builder.() -> Unit) = modifyEntity(XChildChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildEntity, modification: XChildEntity.Builder.() -> Unit) = modifyEntity(XChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildWithOptionalParentEntity, modification: XChildWithOptionalParentEntity.Builder.() -> Unit) = modifyEntity(XChildWithOptionalParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XParentEntity, modification: XParentEntity.Builder.() -> Unit) = modifyEntity(XParentEntity.Builder::class.java, entity, modification)
