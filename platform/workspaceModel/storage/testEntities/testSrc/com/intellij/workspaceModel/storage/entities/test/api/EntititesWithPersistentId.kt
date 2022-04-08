package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.TBlob
import org.jetbrains.deft.impl.TString
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.TestEntities.TestEntities
import org.jetbrains.deft.Type







interface FirstEntityWithPId  : WorkspaceEntityWithPersistentId {
    val data: String
    override val persistentId: FirstPId
        get() {
            return FirstPId(data)
        }


    //region generated code
    //@formatter:off
    interface Builder: FirstEntityWithPId, ModifiableWorkspaceEntity<FirstEntityWithPId>, ObjBuilder<FirstEntityWithPId> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<FirstEntityWithPId, Builder>(89)
    //@formatter:on
    //endregion

}

data class FirstPId(override val presentableName: String) : PersistentEntityId<FirstEntityWithPId>

interface SecondEntityWithPId  : WorkspaceEntityWithPersistentId {
    val data: String
    override val persistentId: SecondPId
        get() = SecondPId(data)


    //region generated code
    //@formatter:off
    interface Builder: SecondEntityWithPId, ModifiableWorkspaceEntity<SecondEntityWithPId>, ObjBuilder<SecondEntityWithPId> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<SecondEntityWithPId, Builder>(90)
    //@formatter:on
    //endregion

}

data class SecondPId(override val presentableName: String) : PersistentEntityId<SecondEntityWithPId>
data class TestPId(var presentableName: String, val aaa: Int?, var  angry:  Boolean)