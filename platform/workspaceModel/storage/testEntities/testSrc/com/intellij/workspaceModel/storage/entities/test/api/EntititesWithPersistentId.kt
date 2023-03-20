package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage




interface FirstEntityWithPId : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: FirstPId
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
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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

data class FirstPId(override val presentableName: String) : SymbolicEntityId<FirstEntityWithPId>

interface SecondEntityWithPId : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: SecondPId
    get() = SecondPId(data)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SecondEntityWithPId, WorkspaceEntity.Builder<SecondEntityWithPId>, ObjBuilder<SecondEntityWithPId> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : Type<SecondEntityWithPId, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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

data class SecondPId(override val presentableName: String) : SymbolicEntityId<SecondEntityWithPId>
data class TestPId(var presentableName: String, val aaa: Int?, var angry: Boolean)