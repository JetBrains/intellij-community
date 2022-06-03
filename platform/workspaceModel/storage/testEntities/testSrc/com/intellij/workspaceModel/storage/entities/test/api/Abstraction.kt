package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage




@Abstract
interface BaseEntity : WorkspaceEntity {
  val parentEntity: CompositeBaseEntity?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder<T: BaseEntity>: BaseEntity, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
      override var parentEntity: CompositeBaseEntity?
      override var entitySource: EntitySource
  }
  
  companion object: Type<BaseEntity, Builder<BaseEntity>>() {
      operator fun invoke(entitySource: EntitySource, init: (Builder<BaseEntity>.() -> Unit)? = null): BaseEntity {
          val builder = builder()
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}

@Abstract
interface CompositeBaseEntity : BaseEntity {
  val children: List<@Child BaseEntity>


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder<T: CompositeBaseEntity>: CompositeBaseEntity, BaseEntity.Builder<T>, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
      override var parentEntity: CompositeBaseEntity?
      override var children: List<BaseEntity>
      override var entitySource: EntitySource
  }
  
  companion object: Type<CompositeBaseEntity, Builder<CompositeBaseEntity>>(BaseEntity) {
      operator fun invoke(entitySource: EntitySource, init: (Builder<CompositeBaseEntity>.() -> Unit)? = null): CompositeBaseEntity {
          val builder = builder()
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}

interface MiddleEntity : BaseEntity {
  val property: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: MiddleEntity, BaseEntity.Builder<MiddleEntity>, ModifiableWorkspaceEntity<MiddleEntity>, ObjBuilder<MiddleEntity> {
      override var parentEntity: CompositeBaseEntity?
      override var property: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<MiddleEntity, Builder>(BaseEntity) {
      operator fun invoke(property: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): MiddleEntity {
          val builder = builder()
          builder.property = property
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: MiddleEntity, modification: MiddleEntity.Builder.() -> Unit) = modifyEntity(MiddleEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addMiddleEntity(property: String = "prop", source: EntitySource = MySource): MiddleEntity {
  val middleEntity = MiddleEntity(property, source)
  this.addEntity(middleEntity)
  return middleEntity
}

// ---------------------------

interface LeftEntity : CompositeBaseEntity {


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(1)
    interface Builder: LeftEntity, CompositeBaseEntity.Builder<LeftEntity>, ModifiableWorkspaceEntity<LeftEntity>, ObjBuilder<LeftEntity> {
        override var parentEntity: CompositeBaseEntity?
        override var children: List<BaseEntity>
        override var entitySource: EntitySource
    }
    
    companion object: Type<LeftEntity, Builder>(CompositeBaseEntity) {
        operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): LeftEntity {
            val builder = builder()
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
    //@formatter:on
    //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: LeftEntity, modification: LeftEntity.Builder.() -> Unit) = modifyEntity(LeftEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addLeftEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): LeftEntity {
  val leftEntity = LeftEntity(source) {
    this.children = children.toList()
  }
  this.addEntity(leftEntity)
  return leftEntity
}

// ---------------------------

interface RightEntity : CompositeBaseEntity {


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(1)
    interface Builder: RightEntity, CompositeBaseEntity.Builder<RightEntity>, ModifiableWorkspaceEntity<RightEntity>, ObjBuilder<RightEntity> {
        override var parentEntity: CompositeBaseEntity?
        override var children: List<BaseEntity>
        override var entitySource: EntitySource
    }
    
    companion object: Type<RightEntity, Builder>(CompositeBaseEntity) {
        operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): RightEntity {
            val builder = builder()
            builder.entitySource = entitySource
            init?.invoke(builder)
            return builder
        }
    }
    //@formatter:on
    //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: RightEntity, modification: RightEntity.Builder.() -> Unit) = modifyEntity(RightEntity.Builder::class.java, entity, modification)
//endregion

fun MutableEntityStorage.addRightEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): RightEntity {
  val rightEntity = RightEntity(source) {
    this.children = children.toList()
  }
  this.addEntity(rightEntity)
  return rightEntity
}
