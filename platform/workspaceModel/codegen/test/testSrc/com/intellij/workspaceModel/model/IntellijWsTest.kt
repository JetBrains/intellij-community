package org.jetbrains.deft.IntellijWsTest

import com.intellij.workspace.model.testing.ChildEntity
import com.intellij.workspace.model.testing.ChildEntityImpl
import com.intellij.workspace.model.testing.ChildMultipleEntity
import com.intellij.workspace.model.testing.ChildMultipleEntityImpl
import com.intellij.workspace.model.testing.ChildSubEntity
import com.intellij.workspace.model.testing.ChildSubEntityImpl
import com.intellij.workspace.model.testing.ChildSubSubEntity
import com.intellij.workspace.model.testing.ChildSubSubEntityImpl
import com.intellij.workspace.model.testing.ParentEntity
import com.intellij.workspace.model.testing.ParentEntityImpl
import com.intellij.workspace.model.testing.ParentMultipleEntity
import com.intellij.workspace.model.testing.ParentMultipleEntityImpl
import com.intellij.workspace.model.testing.ParentSubEntity
import com.intellij.workspace.model.testing.ParentSubEntityImpl
import com.intellij.workspace.model.testing.SampleEntity
import com.intellij.workspace.model.testing.SampleEntityImpl
import com.intellij.workspace.model.testing.VFUEntity
import com.intellij.workspace.model.testing.VFUEntityImpl
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import model.testing.ChildAbstractBaseEntity
import model.testing.ChildFirstEntity
import model.testing.ChildFirstEntityImpl
import model.testing.ChildNullableEntity
import model.testing.ChildNullableEntityImpl
import model.testing.ChildSecondEntity
import model.testing.ChildSecondEntityImpl
import model.testing.ChildSingleAbstractBaseEntity
import model.testing.ChildSingleFirstEntity
import model.testing.ChildSingleFirstEntityImpl
import model.testing.ChildSingleSecondEntity
import model.testing.ChildSingleSecondEntityImpl
import model.testing.CompositeAbstractEntity
import model.testing.CompositeChildAbstractEntity
import model.testing.CompositeChildAbstractEntityImpl
import model.testing.EntityWithSoftLinks
import model.testing.EntityWithSoftLinksImpl
import model.testing.FirstEntityWithPId
import model.testing.FirstEntityWithPIdImpl
import model.testing.OneEntityWithPersistentId
import model.testing.OneEntityWithPersistentIdImpl
import model.testing.ParentAbEntity
import model.testing.ParentAbEntityImpl
import model.testing.ParentChainEntity
import model.testing.ParentChainEntityImpl
import model.testing.ParentNullableEntity
import model.testing.ParentNullableEntityImpl
import model.testing.ParentSingleAbEntity
import model.testing.ParentSingleAbEntityImpl
import model.testing.SecondEntityWithPId
import model.testing.SecondEntityWithPIdImpl
import model.testing.SimpleAbstractEntity
import model.testing.SimpleChildAbstractEntity
import model.testing.SimpleChildAbstractEntityImpl
import org.jetbrains.deft.impl.ObjModule

import org.jetbrains.deft.impl.* 
                        
object IntellijWsTest: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWsTest")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(40)
        add(ParentSubEntity)
        add(ChildSubEntity)
        add(ChildSubSubEntity)
        add(OneEntityWithPersistentId)
        add(EntityWithSoftLinks)
        add(SampleEntity)
        add(VFUEntity)
        add(ParentEntity)
        add(ChildEntity)
        add(ParentNullableEntity)
        add(ChildNullableEntity)
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
        add(ParentMultipleEntity)
        add(ChildMultipleEntity)
        add(ParentSingleAbEntity)
        add(ChildSingleAbstractBaseEntity)
        add(ChildSingleFirstEntity)
        add(ChildSingleSecondEntity)
        
        /*
        beginExtFieldsInit(0)
                
        */
    }
}



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentSubEntity, modification: ParentSubEntity.Builder.() -> Unit) = modifyEntity(ParentSubEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSubEntity, modification: ChildSubEntity.Builder.() -> Unit) = modifyEntity(ChildSubEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSubSubEntity, modification: ChildSubSubEntity.Builder.() -> Unit) = modifyEntity(ChildSubSubEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: OneEntityWithPersistentId, modification: OneEntityWithPersistentId.Builder.() -> Unit) = modifyEntity(OneEntityWithPersistentIdImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: EntityWithSoftLinks, modification: EntityWithSoftLinks.Builder.() -> Unit) = modifyEntity(EntityWithSoftLinksImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity, modification: SampleEntity.Builder.() -> Unit) = modifyEntity(SampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity, modification: VFUEntity.Builder.() -> Unit) = modifyEntity(VFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = modifyEntity(ParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildEntity, modification: ChildEntity.Builder.() -> Unit) = modifyEntity(ChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentNullableEntity, modification: ParentNullableEntity.Builder.() -> Unit) = modifyEntity(ParentNullableEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildNullableEntity, modification: ChildNullableEntity.Builder.() -> Unit) = modifyEntity(ChildNullableEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FirstEntityWithPId, modification: FirstEntityWithPId.Builder.() -> Unit) = modifyEntity(FirstEntityWithPIdImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondEntityWithPId, modification: SecondEntityWithPId.Builder.() -> Unit) = modifyEntity(SecondEntityWithPIdImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentChainEntity, modification: ParentChainEntity.Builder.() -> Unit) = modifyEntity(ParentChainEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: CompositeChildAbstractEntity, modification: CompositeChildAbstractEntity.Builder.() -> Unit) = modifyEntity(CompositeChildAbstractEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SimpleChildAbstractEntity, modification: SimpleChildAbstractEntity.Builder.() -> Unit) = modifyEntity(SimpleChildAbstractEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentAbEntity, modification: ParentAbEntity.Builder.() -> Unit) = modifyEntity(ParentAbEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildFirstEntity, modification: ChildFirstEntity.Builder.() -> Unit) = modifyEntity(ChildFirstEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSecondEntity, modification: ChildSecondEntity.Builder.() -> Unit) = modifyEntity(ChildSecondEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentMultipleEntity, modification: ParentMultipleEntity.Builder.() -> Unit) = modifyEntity(ParentMultipleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildMultipleEntity, modification: ChildMultipleEntity.Builder.() -> Unit) = modifyEntity(ChildMultipleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ParentSingleAbEntity, modification: ParentSingleAbEntity.Builder.() -> Unit) = modifyEntity(ParentSingleAbEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSingleFirstEntity, modification: ChildSingleFirstEntity.Builder.() -> Unit) = modifyEntity(ChildSingleFirstEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSingleSecondEntity, modification: ChildSingleSecondEntity.Builder.() -> Unit) = modifyEntity(ChildSingleSecondEntityImpl.Builder::class.java, entity, modification)
