package org.jetbrains.deft.IntellijWs.testEntities

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.api.AssertConsistencyEntity
import com.intellij.workspaceModel.storage.entities.api.BaseEntity
import com.intellij.workspaceModel.storage.entities.api.ComposedIdSoftRefEntity
import com.intellij.workspaceModel.storage.entities.api.ComposedLinkEntity
import com.intellij.workspaceModel.storage.entities.api.CompositeBaseEntity
import com.intellij.workspaceModel.storage.entities.api.LeftEntity
import com.intellij.workspaceModel.storage.entities.api.LinkedListEntity
import com.intellij.workspaceModel.storage.entities.api.MiddleEntity
import com.intellij.workspaceModel.storage.entities.api.NamedChildEntity
import com.intellij.workspaceModel.storage.entities.api.NamedEntity
import com.intellij.workspaceModel.storage.entities.api.OoChildAlsoWithPidEntity
import com.intellij.workspaceModel.storage.entities.api.OoChildEntity
import com.intellij.workspaceModel.storage.entities.api.OoChildForParentWithPidEntity
import com.intellij.workspaceModel.storage.entities.api.OoChildWithNullableParentEntity
import com.intellij.workspaceModel.storage.entities.api.OoChildWithPidEntity
import com.intellij.workspaceModel.storage.entities.api.OoParentEntity
import com.intellij.workspaceModel.storage.entities.api.OoParentWithPidEntity
import com.intellij.workspaceModel.storage.entities.api.OoParentWithoutPidEntity
import com.intellij.workspaceModel.storage.entities.api.RightEntity
import com.intellij.workspaceModel.storage.entities.api.WithListSoftLinksEntity
import com.intellij.workspaceModel.storage.entities.api.WithSoftLinkEntity
import org.jetbrains.deft.impl.ObjModule

import org.jetbrains.deft.impl.* 
                        
object TestEntities: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWs.testEntities")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(28)
        add(LinkedListEntity)
        add(NamedEntity)
        add(NamedChildEntity)
        add(WithSoftLinkEntity)
        add(ComposedLinkEntity)
        add(WithListSoftLinksEntity)
        add(ComposedIdSoftRefEntity)
        add(OoParentEntity)
        add(OoChildEntity)
        add(OoChildWithNullableParentEntity)
        add(OoParentWithPidEntity)
        add(OoChildForParentWithPidEntity)
        add(OoChildAlsoWithPidEntity)
        add(OoParentWithoutPidEntity)
        add(OoChildWithPidEntity)
        add(AssertConsistencyEntity)
        add(BaseEntity)
        add(CompositeBaseEntity)
        add(MiddleEntity)
        add(LeftEntity)
        add(RightEntity)
    }
}



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LinkedListEntity, modification: LinkedListEntity.Builder.() -> Unit) = modifyEntity(LinkedListEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NamedEntity, modification: NamedEntity.Builder.() -> Unit) = modifyEntity(NamedEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NamedChildEntity, modification: NamedChildEntity.Builder.() -> Unit) = modifyEntity(NamedChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: WithSoftLinkEntity, modification: WithSoftLinkEntity.Builder.() -> Unit) = modifyEntity(WithSoftLinkEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ComposedLinkEntity, modification: ComposedLinkEntity.Builder.() -> Unit) = modifyEntity(ComposedLinkEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: WithListSoftLinksEntity, modification: WithListSoftLinksEntity.Builder.() -> Unit) = modifyEntity(WithListSoftLinksEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ComposedIdSoftRefEntity, modification: ComposedIdSoftRefEntity.Builder.() -> Unit) = modifyEntity(ComposedIdSoftRefEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentEntity, modification: OoParentEntity.Builder.() -> Unit) = modifyEntity(OoParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildEntity, modification: OoChildEntity.Builder.() -> Unit) = modifyEntity(OoChildEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildWithNullableParentEntity, modification: OoChildWithNullableParentEntity.Builder.() -> Unit) = modifyEntity(OoChildWithNullableParentEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentWithPidEntity, modification: OoParentWithPidEntity.Builder.() -> Unit) = modifyEntity(OoParentWithPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildForParentWithPidEntity, modification: OoChildForParentWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildForParentWithPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildAlsoWithPidEntity, modification: OoChildAlsoWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildAlsoWithPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoParentWithoutPidEntity, modification: OoParentWithoutPidEntity.Builder.() -> Unit) = modifyEntity(OoParentWithoutPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OoChildWithPidEntity, modification: OoChildWithPidEntity.Builder.() -> Unit) = modifyEntity(OoChildWithPidEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AssertConsistencyEntity, modification: AssertConsistencyEntity.Builder.() -> Unit) = modifyEntity(AssertConsistencyEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MiddleEntity, modification: MiddleEntity.Builder.() -> Unit) = modifyEntity(MiddleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LeftEntity, modification: LeftEntity.Builder.() -> Unit) = modifyEntity(LeftEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: RightEntity, modification: RightEntity.Builder.() -> Unit) = modifyEntity(RightEntity.Builder::class.java, entity, modification)
