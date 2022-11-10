package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

interface DefaultFieldEntity : WorkspaceEntity {
  val version: Int
  val data: TestData
  val anotherVersion: Int
    @Default get() = 0
  val description: String
    @Default get() = "Default description"

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : DefaultFieldEntity, WorkspaceEntity.Builder<DefaultFieldEntity>, ObjBuilder<DefaultFieldEntity> {
    override var entitySource: EntitySource
    override var version: Int
    override var data: TestData
    override var anotherVersion: Int
    override var description: String
  }

  companion object : Type<DefaultFieldEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(version: Int, data: TestData, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): DefaultFieldEntity {
      val builder = builder()
      builder.version = version
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: DefaultFieldEntity, modification: DefaultFieldEntity.Builder.() -> Unit) = modifyEntity(
  DefaultFieldEntity.Builder::class.java, entity, modification)
//endregion

data class TestData(val name: String, val description: String)