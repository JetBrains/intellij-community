package model.testing

import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.IntellijWsTest.IntellijWsTest



interface FirstEntityWithPId  : WorkspaceEntityWithPersistentId {
    val data: String
    override val persistentId: FirstPId
        get() {
            return FirstPId(data)
        }
    //region generated code
    //@formatter:off
    interface Builder: FirstEntityWithPId, ObjBuilder<FirstEntityWithPId> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<FirstEntityWithPId, Builder>(IntellijWsTest, 37) {
        val data: Field<FirstEntityWithPId, String> = Field(this, 0, "data", TString)
        val entitySource: Field<FirstEntityWithPId, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val persistentId: Field<FirstEntityWithPId, FirstPId> = Field(this, 0, "persistentId", TBlob("FirstPId"))
    }
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
    interface Builder: SecondEntityWithPId, ObjBuilder<SecondEntityWithPId> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<SecondEntityWithPId, Builder>(IntellijWsTest, 38) {
        val data: Field<SecondEntityWithPId, String> = Field(this, 0, "data", TString)
        val entitySource: Field<SecondEntityWithPId, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val persistentId: Field<SecondEntityWithPId, SecondPId> = Field(this, 0, "persistentId", TBlob("SecondPId"))
    }
    //@formatter:on
    //endregion

}

data class SecondPId(override val presentableName: String) : PersistentEntityId<SecondEntityWithPId>
data class TestPId(var presentableName: String, val aaa: Int?, var  angry:  Boolean)