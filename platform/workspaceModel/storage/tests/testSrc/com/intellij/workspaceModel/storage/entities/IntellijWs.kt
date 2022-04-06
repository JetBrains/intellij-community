package org.jetbrains.deft.IntellijWs

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.api.*
import org.jetbrains.deft.impl.ObjModule

object IntellijWs: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWs")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(17)
        add(SampleEntity)
        add(ChildSampleEntity)
        add(SecondSampleEntity)
        add(SourceEntity)
        add(ChildSourceEntity)
        add(PersistentIdEntity)
        add(XParentEntity)
        add(XChildEntity)
        add(XChildWithOptionalParentEntity)
        add(XChildChildEntity)
        add(VFUEntity)
        add(VFUWithTwoPropertiesEntity)
        add(NullableVFUEntity)
        add(ListVFUEntity)
    }
}



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SampleEntity, modification: SampleEntity.Builder.() -> Unit) = modifyEntity(SampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSampleEntity, modification: ChildSampleEntity.Builder.() -> Unit) = modifyEntity(ChildSampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SecondSampleEntity, modification: SecondSampleEntity.Builder.() -> Unit) = modifyEntity(SecondSampleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceEntity, modification: SourceEntity.Builder.() -> Unit) = modifyEntity(SourceEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ChildSourceEntity, modification: ChildSourceEntity.Builder.() -> Unit) = modifyEntity(ChildSourceEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: PersistentIdEntity, modification: PersistentIdEntity.Builder.() -> Unit) = modifyEntity(PersistentIdEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XParentEntity, modification: XParentEntity.Builder.() -> Unit) = modifyEntity(XParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildEntity, modification: XChildEntity.Builder.() -> Unit) = modifyEntity(XChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildWithOptionalParentEntity, modification: XChildWithOptionalParentEntity.Builder.() -> Unit) = modifyEntity(XChildWithOptionalParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildChildEntity, modification: XChildChildEntity.Builder.() -> Unit) = modifyEntity(XChildChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUEntity, modification: VFUEntity.Builder.() -> Unit) = modifyEntity(VFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: VFUWithTwoPropertiesEntity, modification: VFUWithTwoPropertiesEntity.Builder.() -> Unit) = modifyEntity(VFUWithTwoPropertiesEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: NullableVFUEntity, modification: NullableVFUEntity.Builder.() -> Unit) = modifyEntity(NullableVFUEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ListVFUEntity, modification: ListVFUEntity.Builder.() -> Unit) = modifyEntity(ListVFUEntityImpl.Builder::class.java, entity, modification)
