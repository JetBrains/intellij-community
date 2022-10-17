package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage




interface FirstEntityWithPId : WorkspaceEntityWithPersistentId {
  val data: String
  override val persistentId: FirstPId
    get() {
      return FirstPId(data)
    }

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : FirstEntityWithPId, WorkspaceEntity.Builder<FirstEntityWithPId>, ObjBuilder<FirstEntityWithPId> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : Type<FirstEntityWithPId, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): FirstEntityWithPId {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: FirstEntityWithPId, modification: FirstEntityWithPId.Builder.() -> Unit) = modifyEntity(
  FirstEntityWithPId.Builder::class.java, entity, modification)
//endregion

data class FirstPId(override val presentableName: String) : PersistentEntityId<FirstEntityWithPId>

interface SecondEntityWithPId : WorkspaceEntityWithPersistentId {
  val data: String
  override val persistentId: SecondPId
    get() = SecondPId(data)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SecondEntityWithPId, WorkspaceEntity.Builder<SecondEntityWithPId>, ObjBuilder<SecondEntityWithPId> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : Type<SecondEntityWithPId, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SecondEntityWithPId {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SecondEntityWithPId, modification: SecondEntityWithPId.Builder.() -> Unit) = modifyEntity(
  SecondEntityWithPId.Builder::class.java, entity, modification)
//endregion

data class SecondPId(override val presentableName: String) : PersistentEntityId<SecondEntityWithPId>
data class TestPId(var presentableName: String, val aaa: Int?, var angry: Boolean)