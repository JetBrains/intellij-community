package model.ext

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.IntellijWsTestIjExt.IntellijWsTestIjExt
import org.jetbrains.deft.Obj




interface MainEntityToParent : WorkspaceEntity {
    val child: @Child AttachedEntityToParent?
    val x: String
    //region generated code
    //@formatter:off
    interface Builder: MainEntityToParent, ObjBuilder<MainEntityToParent> {
        override var child: AttachedEntityToParent?
        override var entitySource: EntitySource
        override var x: String
    }
    
    companion object: ObjType<MainEntityToParent, Builder>(IntellijWsTestIjExt, 3) {
        val child: Field<MainEntityToParent, AttachedEntityToParent?> = Field(this, 0, "child", TOptional(TRef("org.jetbrains.deft.IntellijWsTestIjExt", 4, child = true)))
        val entitySource: Field<MainEntityToParent, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val x: Field<MainEntityToParent, String> = Field(this, 0, "x", TString)
    }
    //@formatter:on
    //endregion

}

interface AttachedEntityToParent : WorkspaceEntity {
    val data: String
    //region generated code
    //@formatter:off
    interface Builder: AttachedEntityToParent, ObjBuilder<AttachedEntityToParent> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<AttachedEntityToParent, Builder>(IntellijWsTestIjExt, 4) {
        val data: Field<AttachedEntityToParent, String> = Field(this, 0, "data", TString)
        val entitySource: Field<AttachedEntityToParent, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

val AttachedEntityToParent.ref: MainEntityToParent
    get() = referrersx(MainEntityToParent::child).single()
