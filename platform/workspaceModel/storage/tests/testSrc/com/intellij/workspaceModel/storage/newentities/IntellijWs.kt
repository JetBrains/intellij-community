package org.jetbrains.deft.IntellijWs

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.newentities.api.*
import org.jetbrains.deft.impl.ObjModule
                        
object IntellijWs: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWs")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(5)
        add(XParentEntity)
        add(XChildEntity)
        add(XChildWithOptionalParentEntity)
        add(XChildChildEntity)
    }
}



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XParentEntity, modification: XParentEntity.Builder.() -> Unit) = modifyEntity(XParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildEntity, modification: XChildEntity.Builder.() -> Unit) = modifyEntity(XChildEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildWithOptionalParentEntity, modification: XChildWithOptionalParentEntity.Builder.() -> Unit) = modifyEntity(XChildWithOptionalParentEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: XChildChildEntity, modification: XChildChildEntity.Builder.() -> Unit) = modifyEntity(XChildChildEntityImpl.Builder::class.java, entity, modification)
