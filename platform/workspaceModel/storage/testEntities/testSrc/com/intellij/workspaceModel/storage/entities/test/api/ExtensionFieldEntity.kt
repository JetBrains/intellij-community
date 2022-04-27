package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.referrersx




interface MainEntity : WorkspaceEntity {
  val x: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: MainEntity, ModifiableWorkspaceEntity<MainEntity>, ObjBuilder<MainEntity> {
      override var x: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<MainEntity, Builder>() {
      operator fun invoke(x: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): MainEntity {
          val builder = builder()
          builder.x = x
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: MainEntity, modification: MainEntity.Builder.() -> Unit) = modifyEntity(MainEntity.Builder::class.java, entity, modification)
var MainEntity.Builder.child: @Child AttachedEntity?
    get() {
        return referrersx(AttachedEntity::ref).singleOrNull()
    }
    set(value) {
        val diff = (this as MainEntityImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as AttachedEntityImpl.Builder).diff == null) {
                    value._ref = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(AttachedEntityImpl.REF_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("AttachedEntity", "ref", true, AttachedEntityImpl.REF_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as AttachedEntityImpl.Builder)._ref = this
            }
        }
    }

//endregion

interface AttachedEntity : WorkspaceEntity {
  val ref: MainEntity
  val data: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: AttachedEntity, ModifiableWorkspaceEntity<AttachedEntity>, ObjBuilder<AttachedEntity> {
      override var ref: MainEntity
      override var entitySource: EntitySource
      override var data: String
  }
  
  companion object: Type<AttachedEntity, Builder>() {
      operator fun invoke(entitySource: EntitySource, data: String, init: (Builder.() -> Unit)? = null): AttachedEntity {
          val builder = builder()
          builder.entitySource = entitySource
          builder.data = data
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: AttachedEntity, modification: AttachedEntity.Builder.() -> Unit) = modifyEntity(AttachedEntity.Builder::class.java, entity, modification)
//endregion

val MainEntity.child: @Child AttachedEntity?
  get() = referrersx(AttachedEntity::ref).singleOrNull()
