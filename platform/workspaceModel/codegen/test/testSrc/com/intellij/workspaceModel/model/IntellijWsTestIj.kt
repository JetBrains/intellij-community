package org.jetbrains.deft.IntellijWsTestIj

import com.intellij.workspaceModel.storage.AssertConsistencyEntity
import com.intellij.workspaceModel.storage.AssertConsistencyEntityImpl
import com.intellij.workspaceModel.storage.ChildChildEntity
import com.intellij.workspaceModel.storage.ChildChildEntityImpl
import com.intellij.workspaceModel.storage.ChildEntity
import com.intellij.workspaceModel.storage.ChildEntityImpl
import com.intellij.workspaceModel.storage.ChildWithOptionalParentEntity
import com.intellij.workspaceModel.storage.ChildWithOptionalParentEntityImpl
import com.intellij.workspaceModel.storage.ComposedIdSoftRefEntity
import com.intellij.workspaceModel.storage.ComposedIdSoftRefEntityImpl
import com.intellij.workspaceModel.storage.ComposedLinkEntity
import com.intellij.workspaceModel.storage.ComposedLinkEntityImpl
import com.intellij.workspaceModel.storage.LinkedListEntity
import com.intellij.workspaceModel.storage.LinkedListEntityImpl
import com.intellij.workspaceModel.storage.NamedChildEntity
import com.intellij.workspaceModel.storage.NamedChildEntityImpl
import com.intellij.workspaceModel.storage.NamedEntity
import com.intellij.workspaceModel.storage.NamedEntityImpl
import com.intellij.workspaceModel.storage.OoChildAlsoWithPidEntity
import com.intellij.workspaceModel.storage.OoChildAlsoWithPidEntityImpl
import com.intellij.workspaceModel.storage.OoChildEntity
import com.intellij.workspaceModel.storage.OoChildEntityImpl
import com.intellij.workspaceModel.storage.OoChildForParentWithPidEntity
import com.intellij.workspaceModel.storage.OoChildForParentWithPidEntityImpl
import com.intellij.workspaceModel.storage.OoChildWithNullableParentEntity
import com.intellij.workspaceModel.storage.OoChildWithNullableParentEntityImpl
import com.intellij.workspaceModel.storage.OoChildWithPidEntity
import com.intellij.workspaceModel.storage.OoChildWithPidEntityImpl
import com.intellij.workspaceModel.storage.OoParentEntity
import com.intellij.workspaceModel.storage.OoParentEntityImpl
import com.intellij.workspaceModel.storage.OoParentWithPidEntity
import com.intellij.workspaceModel.storage.OoParentWithPidEntityImpl
import com.intellij.workspaceModel.storage.OoParentWithoutPidEntity
import com.intellij.workspaceModel.storage.OoParentWithoutPidEntityImpl
import com.intellij.workspaceModel.storage.ParentEntity
import com.intellij.workspaceModel.storage.ParentEntityImpl
import com.intellij.workspaceModel.storage.WithListSoftLinksEntity
import com.intellij.workspaceModel.storage.WithListSoftLinksEntityImpl
import com.intellij.workspaceModel.storage.WithSoftLinkEntity
import com.intellij.workspaceModel.storage.WithSoftLinkEntityImpl
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.BaseEntity
import com.intellij.workspaceModel.storage.entities.ChildSampleEntity
import com.intellij.workspaceModel.storage.entities.ChildSampleEntityImpl
import com.intellij.workspaceModel.storage.entities.ChildSourceEntity
import com.intellij.workspaceModel.storage.entities.ChildSourceEntityImpl
import com.intellij.workspaceModel.storage.entities.CompositeBaseEntity
import com.intellij.workspaceModel.storage.entities.LeftEntity
import com.intellij.workspaceModel.storage.entities.LeftEntityImpl
import com.intellij.workspaceModel.storage.entities.ListVFUEntity
import com.intellij.workspaceModel.storage.entities.ListVFUEntityImpl
import com.intellij.workspaceModel.storage.entities.MiddleEntity
import com.intellij.workspaceModel.storage.entities.MiddleEntityImpl
import com.intellij.workspaceModel.storage.entities.NullableVFUEntity
import com.intellij.workspaceModel.storage.entities.NullableVFUEntityImpl
import com.intellij.workspaceModel.storage.entities.PersistentIdEntity
import com.intellij.workspaceModel.storage.entities.PersistentIdEntityImpl
import com.intellij.workspaceModel.storage.entities.RightEntity
import com.intellij.workspaceModel.storage.entities.RightEntityImpl
import com.intellij.workspaceModel.storage.entities.SampleEntity
import com.intellij.workspaceModel.storage.entities.SampleEntityImpl
import com.intellij.workspaceModel.storage.entities.SecondSampleEntity
import com.intellij.workspaceModel.storage.entities.SecondSampleEntityImpl
import com.intellij.workspaceModel.storage.entities.SourceEntity
import com.intellij.workspaceModel.storage.entities.SourceEntityImpl
import com.intellij.workspaceModel.storage.entities.VFUEntity
import com.intellij.workspaceModel.storage.entities.VFUEntityImpl
import com.intellij.workspaceModel.storage.entities.VFUWithTwoPropertiesEntity
import com.intellij.workspaceModel.storage.entities.VFUWithTwoPropertiesEntityImpl
import org.jetbrains.deft.impl.ObjModule

import org.jetbrains.deft.impl.* 
                        
object IntellijWsTestIj: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWsTestIj")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(44)
        add(LinkedListEntity)
        add(NamedEntity)
        add(NamedChildEntity)
        add(WithSoftLinkEntity)
        add(ComposedLinkEntity)
        add(WithListSoftLinksEntity)
        add(ComposedIdSoftRefEntity)
        add(SampleEntity)
        add(SecondSampleEntity)
        add(SourceEntity)
        add(ChildSourceEntity)
        add(ChildSampleEntity)
        add(PersistentIdEntity)
        add(VFUEntity)
        add(VFUWithTwoPropertiesEntity)
        add(NullableVFUEntity)
        add(ListVFUEntity)
        add(OoParentEntity)
        add(OoChildEntity)
        add(OoChildWithNullableParentEntity)
        add(OoParentWithPidEntity)
        add(OoChildForParentWithPidEntity)
        add(OoChildAlsoWithPidEntity)
        add(OoParentWithoutPidEntity)
        add(OoChildWithPidEntity)
        add(ParentEntity)
        add(ChildEntity)
        add(ChildWithOptionalParentEntity)
        add(ChildChildEntity)
        add(AssertConsistencyEntity)
        add(BaseEntity)
        add(CompositeBaseEntity)
        add(MiddleEntity)
        add(LeftEntity)
        add(RightEntity)
    }
}



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LinkedListEntity, modification: LinkedListEntity.Builder.() -> Unit) = modifyEntity(LinkedListEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NamedEntity, modification: NamedEntity.Builder.() -> Unit) = modifyEntity(NamedEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NamedChildEntity, modification: NamedChildEntity.Builder.() -> Unit) = modifyEntity(NamedChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: WithSoftLinkEntity, modification: WithSoftLinkEntity.Builder.() -> Unit) = modifyEntity(WithSoftLinkEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ComposedLinkEntity, modification: ComposedLinkEntity.Builder.() -> Unit) = modifyEntity(ComposedLinkEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: WithListSoftLinksEntity, modification: WithListSoftLinksEntity.Builder.() -> Unit) = modifyEntity(WithListSoftLinksEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ComposedIdSoftRefEntity, modification: ComposedIdSoftRefEntity.Builder.() -> Unit) = modifyEntity(ComposedIdSoftRefEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity, modification: SampleEntity.Builder.() -> Unit) = modifyEntity(SampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondSampleEntity, modification: SecondSampleEntity.Builder.() -> Unit) = modifyEntity(SecondSampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceEntity, modification: SourceEntity.Builder.() -> Unit) = modifyEntity(SourceEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSourceEntity, modification: ChildSourceEntity.Builder.() -> Unit) = modifyEntity(ChildSourceEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSampleEntity, modification: ChildSampleEntity.Builder.() -> Unit) = modifyEntity(ChildSampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: PersistentIdEntity, modification: PersistentIdEntity.Builder.() -> Unit) = modifyEntity(PersistentIdEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity, modification: VFUEntity.Builder.() -> Unit) = modifyEntity(VFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUWithTwoPropertiesEntity, modification: VFUWithTwoPropertiesEntity.Builder.() -> Unit) = modifyEntity(VFUWithTwoPropertiesEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NullableVFUEntity, modification: NullableVFUEntity.Builder.() -> Unit) = modifyEntity(NullableVFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ListVFUEntity, modification: ListVFUEntity.Builder.() -> Unit) = modifyEntity(ListVFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentEntity, modification: OoParentEntity.Builder.() -> Unit) = modifyEntity(OoParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildEntity, modification: OoChildEntity.Builder.() -> Unit) = modifyEntity(OoChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildWithNullableParentEntity, modification: OoChildWithNullableParentEntity.Builder.() -> Unit) = modifyEntity(OoChildWithNullableParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentWithPidEntity, modification: OoParentWithPidEntity.Builder.() -> Unit) = modifyEntity(OoParentWithPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildForParentWithPidEntity, modification: OoChildForParentWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildForParentWithPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildAlsoWithPidEntity, modification: OoChildAlsoWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildAlsoWithPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentWithoutPidEntity, modification: OoParentWithoutPidEntity.Builder.() -> Unit) = modifyEntity(OoParentWithoutPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildWithPidEntity, modification: OoChildWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildWithPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = modifyEntity(ParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildEntity, modification: ChildEntity.Builder.() -> Unit) = modifyEntity(ChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildWithOptionalParentEntity, modification: ChildWithOptionalParentEntity.Builder.() -> Unit) = modifyEntity(ChildWithOptionalParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildChildEntity, modification: ChildChildEntity.Builder.() -> Unit) = modifyEntity(ChildChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AssertConsistencyEntity, modification: AssertConsistencyEntity.Builder.() -> Unit) = modifyEntity(AssertConsistencyEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MiddleEntity, modification: MiddleEntity.Builder.() -> Unit) = modifyEntity(MiddleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LeftEntity, modification: LeftEntity.Builder.() -> Unit) = modifyEntity(LeftEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: RightEntity, modification: RightEntity.Builder.() -> Unit) = modifyEntity(RightEntityImpl.Builder::class.java, entity, modification)
