package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.IntellijWsTestIj.IntellijWsTestIj
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*


@Abstract
interface BaseEntity : WorkspaceEntity {
  val parentEntity: CompositeBaseEntity?
  //region generated code
  //@formatter:off
  interface Builder: BaseEntity, ObjBuilder<BaseEntity> {
      override var parentEntity: CompositeBaseEntity?
      override var entitySource: EntitySource
  }
  
  companion object: ObjType<BaseEntity, Builder>(IntellijWsTestIj, 40) {
      val parentEntity: Field<BaseEntity, CompositeBaseEntity?> = Field(this, 0, "parentEntity", TOptional(TRef("org.jetbrains.deft.IntellijWsTestIj", 41)))
      val entitySource: Field<BaseEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
  }
  //@formatter:on
  //endregion

}

@Abstract
interface CompositeBaseEntity : BaseEntity {
  val children: List<@Child BaseEntity>
  //region generated code
  //@formatter:off
  interface Builder: CompositeBaseEntity, ObjBuilder<CompositeBaseEntity> {
      override var parentEntity: CompositeBaseEntity?
      override var children: List<BaseEntity>
      override var entitySource: EntitySource
  }
  
  companion object: ObjType<CompositeBaseEntity, Builder>(IntellijWsTestIj, 41, BaseEntity) {
      val children: Field<CompositeBaseEntity, List<BaseEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWsTestIj", 40, child = true)))
      val entitySource: Field<CompositeBaseEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
  }
  //@formatter:on
  //endregion

}

interface MiddleEntity : BaseEntity {
  val property: String
  //region generated code
  //@formatter:off
  interface Builder: MiddleEntity, ObjBuilder<MiddleEntity> {
      override var parentEntity: CompositeBaseEntity?
      override var property: String
      override var entitySource: EntitySource
  }
  
  companion object: ObjType<MiddleEntity, Builder>(IntellijWsTestIj, 42, BaseEntity) {
      val property: Field<MiddleEntity, String> = Field(this, 0, "property", TString)
      val entitySource: Field<MiddleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
  }
  //@formatter:on
  //endregion

}

fun WorkspaceEntityStorageBuilder.addMiddleEntity(property: String = "prop", source: EntitySource = MySource): MiddleEntity {
    val middleEntity = MiddleEntity {
        this.property = property
        this.entitySource = source
    }
    this.addEntity(middleEntity)
    return middleEntity
}

// ---------------------------

interface LeftEntity : CompositeBaseEntity {
    //region generated code
    //@formatter:off
    interface Builder: LeftEntity, ObjBuilder<LeftEntity> {
        override var parentEntity: CompositeBaseEntity?
        override var children: List<BaseEntity>
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<LeftEntity, Builder>(IntellijWsTestIj, 43, CompositeBaseEntity) {
    }
    //@formatter:on
    //endregion
}

fun WorkspaceEntityStorageBuilder.addLeftEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): LeftEntity {
    val leftEntity = LeftEntity {
        this.children = children.toList()
        this.entitySource = source
    }
    this.addEntity(leftEntity)
    return leftEntity
}

// ---------------------------

interface RightEntity : CompositeBaseEntity {
    //region generated code
    //@formatter:off
    interface Builder: RightEntity, ObjBuilder<RightEntity> {
        override var parentEntity: CompositeBaseEntity?
        override var children: List<BaseEntity>
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<RightEntity, Builder>(IntellijWsTestIj, 44, CompositeBaseEntity) {
    }
    //@formatter:on
    //endregion
}

fun WorkspaceEntityStorageBuilder.addRightEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): RightEntity {
    val rightEntity = RightEntity {
        this.children = children.toList()
        this.entitySource = source
    }
    this.addEntity(rightEntity)
    return rightEntity
}
