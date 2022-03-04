package model.ext

import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.referrersy
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.IntellijWsTestIjExt.IntellijWsTestIjExt
import org.jetbrains.deft.Obj




interface MainEntityParentList : WorkspaceEntity {
    val x: String
    val children: List<@Child AttachedEntityParentList>
    //region generated code
    //@formatter:off
    interface Builder: MainEntityParentList, ObjBuilder<MainEntityParentList> {
        override var x: String
        override var entitySource: EntitySource
        override var children: List<AttachedEntityParentList>
    }
    
    companion object: ObjType<MainEntityParentList, Builder>(IntellijWsTestIjExt, 1) {
        val x: Field<MainEntityParentList, String> = Field(this, 0, "x", TString)
        val entitySource: Field<MainEntityParentList, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val children: Field<MainEntityParentList, List<AttachedEntityParentList>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWsTestIjExt", 2, child = true)))
    }
    //@formatter:on
    //endregion

}

interface AttachedEntityParentList : WorkspaceEntity {
    val data: String
    //region generated code
    //@formatter:off
    interface Builder: AttachedEntityParentList, ObjBuilder<AttachedEntityParentList> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<AttachedEntityParentList, Builder>(IntellijWsTestIjExt, 2) {
        val data: Field<AttachedEntityParentList, String> = Field(this, 0, "data", TString)
        val entitySource: Field<AttachedEntityParentList, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

val AttachedEntityParentList.ref: MainEntityParentList?
    get() =  referrersy(MainEntityParentList::children).singleOrNull()
