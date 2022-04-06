package org.jetbrains.deft.IntellijWs.testEntities

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.api.*
import org.jetbrains.deft.impl.ObjModule
                        
object TestEntities: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWs.testEntities")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(39)
        add(LinkedListEntity)
        add(NamedEntity)
        add(NamedChildEntity)
        add(WithSoftLinkEntity)
        add(ComposedLinkEntity)
        add(WithListSoftLinksEntity)
        add(ComposedIdSoftRefEntity)
        add(AssertConsistencyEntity)
        add(BaseEntity)
        add(CompositeBaseEntity)
        add(MiddleEntity)
        add(LeftEntity)
        add(RightEntity)
        add(OoParentEntity)
        add(OoChildEntity)
        add(OoChildWithNullableParentEntity)
        add(OoParentWithPidEntity)
        add(OoChildForParentWithPidEntity)
        add(OoChildAlsoWithPidEntity)
        add(OoParentWithoutPidEntity)
        add(OoChildWithPidEntity)
    }
}



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LinkedListEntity, modification: LinkedListEntity.Builder.() -> Unit) = modifyEntity(LinkedListEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NamedEntity, modification: NamedEntity.Builder.() -> Unit) = modifyEntity(NamedEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NamedChildEntity, modification: NamedChildEntity.Builder.() -> Unit) = modifyEntity(NamedChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: WithSoftLinkEntity, modification: WithSoftLinkEntity.Builder.() -> Unit) = modifyEntity(WithSoftLinkEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ComposedLinkEntity, modification: ComposedLinkEntity.Builder.() -> Unit) = modifyEntity(ComposedLinkEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: WithListSoftLinksEntity, modification: WithListSoftLinksEntity.Builder.() -> Unit) = modifyEntity(WithListSoftLinksEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ComposedIdSoftRefEntity, modification: ComposedIdSoftRefEntity.Builder.() -> Unit) = modifyEntity(ComposedIdSoftRefEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AssertConsistencyEntity, modification: AssertConsistencyEntity.Builder.() -> Unit) = modifyEntity(AssertConsistencyEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MiddleEntity, modification: MiddleEntity.Builder.() -> Unit) = modifyEntity(MiddleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LeftEntity, modification: LeftEntity.Builder.() -> Unit) = modifyEntity(LeftEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: RightEntity, modification: RightEntity.Builder.() -> Unit) = modifyEntity(RightEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentEntity, modification: OoParentEntity.Builder.() -> Unit) = modifyEntity(OoParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildEntity, modification: OoChildEntity.Builder.() -> Unit) = modifyEntity(OoChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildWithNullableParentEntity, modification: OoChildWithNullableParentEntity.Builder.() -> Unit) = modifyEntity(OoChildWithNullableParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentWithPidEntity, modification: OoParentWithPidEntity.Builder.() -> Unit) = modifyEntity(OoParentWithPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildForParentWithPidEntity, modification: OoChildForParentWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildForParentWithPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildAlsoWithPidEntity, modification: OoChildAlsoWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildAlsoWithPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentWithoutPidEntity, modification: OoParentWithoutPidEntity.Builder.() -> Unit) = modifyEntity(OoParentWithoutPidEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildWithPidEntity, modification: OoChildWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildWithPidEntityImpl.Builder::class.java, entity, modification)
