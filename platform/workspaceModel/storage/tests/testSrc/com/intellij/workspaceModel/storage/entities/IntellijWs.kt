package org.jetbrains.deft.IntellijWs

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.api.ChildSampleEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSampleEntityImpl
import com.intellij.workspaceModel.storage.entities.api.ChildSourceEntity
import com.intellij.workspaceModel.storage.entities.api.ChildSourceEntityImpl
import com.intellij.workspaceModel.storage.entities.api.PersistentIdEntity
import com.intellij.workspaceModel.storage.entities.api.PersistentIdEntityImpl
import com.intellij.workspaceModel.storage.entities.api.SampleEntity
import com.intellij.workspaceModel.storage.entities.api.SampleEntityImpl
import com.intellij.workspaceModel.storage.entities.api.SecondSampleEntity
import com.intellij.workspaceModel.storage.entities.api.SecondSampleEntityImpl
import com.intellij.workspaceModel.storage.entities.api.SourceEntity
import com.intellij.workspaceModel.storage.entities.api.SourceEntityImpl
import com.intellij.workspaceModel.storage.entities.api.XChildChildEntity
import com.intellij.workspaceModel.storage.entities.api.XChildChildEntityImpl
import com.intellij.workspaceModel.storage.entities.api.XChildEntity
import com.intellij.workspaceModel.storage.entities.api.XChildEntityImpl
import com.intellij.workspaceModel.storage.entities.api.XChildWithOptionalParentEntity
import com.intellij.workspaceModel.storage.entities.api.XChildWithOptionalParentEntityImpl
import com.intellij.workspaceModel.storage.entities.api.XParentEntity
import com.intellij.workspaceModel.storage.entities.api.XParentEntityImpl
import org.jetbrains.deft.impl.ObjModule

import org.jetbrains.deft.impl.* 
                        
object IntellijWs: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWs")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(13)
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
