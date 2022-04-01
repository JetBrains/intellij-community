package model.testing

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.IntellijWsTest.IntellijWsTest



interface ParentSingleAbEntity : WorkspaceEntity {
    val child: @Child ChildSingleAbstractBaseEntity

    //region generated code
    //@formatter:off
    interface Builder: ParentSingleAbEntity, ObjBuilder<ParentSingleAbEntity> {
        override var child: ChildSingleAbstractBaseEntity
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ParentSingleAbEntity, Builder>(IntellijWsTest, 15) {
        val child: Field<ParentSingleAbEntity, ChildSingleAbstractBaseEntity> = Field(this, 0, "child", TRef("org.jetbrains.deft.IntellijWsTest", 16, child = true))
        val entitySource: Field<ParentSingleAbEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

@Abstract
interface ChildSingleAbstractBaseEntity : WorkspaceEntity {
    val commonData: String

    val parentEntity: ParentSingleAbEntity

    //region generated code
    //@formatter:off
    interface Builder: ChildSingleAbstractBaseEntity, ObjBuilder<ChildSingleAbstractBaseEntity> {
        override var commonData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentSingleAbEntity
    }
    
    companion object: ObjType<ChildSingleAbstractBaseEntity, Builder>(IntellijWsTest, 16) {
        val commonData: Field<ChildSingleAbstractBaseEntity, String> = Field(this, 0, "commonData", TString)
        val entitySource: Field<ChildSingleAbstractBaseEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<ChildSingleAbstractBaseEntity, ParentSingleAbEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWsTest", 15))
    }
    //@formatter:on
    //endregion
}

interface ChildSingleFirstEntity : ChildSingleAbstractBaseEntity {
    val firstData: String

    //region generated code
    //@formatter:off
    interface Builder: ChildSingleFirstEntity, ObjBuilder<ChildSingleFirstEntity> {
        override var commonData: String
        override var parentEntity: ParentSingleAbEntity
        override var firstData: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ChildSingleFirstEntity, Builder>(IntellijWsTest, 17, ChildSingleAbstractBaseEntity) {
        val firstData: Field<ChildSingleFirstEntity, String> = Field(this, 0, "firstData", TString)
        val entitySource: Field<ChildSingleFirstEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}

interface ChildSingleSecondEntity : ChildSingleAbstractBaseEntity {
    val secondData: String

    //region generated code
    //@formatter:off
    interface Builder: ChildSingleSecondEntity, ObjBuilder<ChildSingleSecondEntity> {
        override var commonData: String
        override var parentEntity: ParentSingleAbEntity
        override var secondData: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ChildSingleSecondEntity, Builder>(IntellijWsTest, 18, ChildSingleAbstractBaseEntity) {
        val secondData: Field<ChildSingleSecondEntity, String> = Field(this, 0, "secondData", TString)
        val entitySource: Field<ChildSingleSecondEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion
}
