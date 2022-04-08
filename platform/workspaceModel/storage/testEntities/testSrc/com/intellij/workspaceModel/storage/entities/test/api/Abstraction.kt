package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.TestEntities.TestEntities



@Abstract
interface BaseEntity : WorkspaceEntity {
  val parentEntity: CompositeBaseEntity?

  //region generated code
  //@formatter:off
  interface Builder: BaseEntity, ModifiableWorkspaceEntity<BaseEntity>, ObjBuilder<BaseEntity> {
      override var parentEntity: CompositeBaseEntity?
      override var entitySource: EntitySource
  }
  
  companion object: ObjType<BaseEntity, Builder>(TestEntities, 78)
  //@formatter:on
  //endregion

}

@Abstract
interface CompositeBaseEntity : BaseEntity {
  val children: List<@Child BaseEntity>



  //region generated code
  //@formatter:off
  interface Builder: CompositeBaseEntity, ModifiableWorkspaceEntity<CompositeBaseEntity>, ObjBuilder<CompositeBaseEntity> {
      override var parentEntity: CompositeBaseEntity?
      override var children: List<BaseEntity>
      override var entitySource: EntitySource
  }
  
  companion object: ObjType<CompositeBaseEntity, Builder>(TestEntities, 79, BaseEntity)
  //@formatter:on
  //endregion

}

interface MiddleEntity : BaseEntity {
  val property: String



  //region generated code
  //@formatter:off
  interface Builder: MiddleEntity, ModifiableWorkspaceEntity<MiddleEntity>, ObjBuilder<MiddleEntity> {
      override var parentEntity: CompositeBaseEntity?
      override var property: String
      override var entitySource: EntitySource
  }
  
  companion object: ObjType<MiddleEntity, Builder>(TestEntities, 80, BaseEntity)
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
    interface Builder: LeftEntity, ModifiableWorkspaceEntity<LeftEntity>, ObjBuilder<LeftEntity> {
        override var parentEntity: CompositeBaseEntity?
        override var children: List<BaseEntity>
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<LeftEntity, Builder>(TestEntities, 81, CompositeBaseEntity)
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
    interface Builder: RightEntity, ModifiableWorkspaceEntity<RightEntity>, ObjBuilder<RightEntity> {
        override var parentEntity: CompositeBaseEntity?
        override var children: List<BaseEntity>
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<RightEntity, Builder>(TestEntities, 82, CompositeBaseEntity)
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
