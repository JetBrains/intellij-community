package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface DefaultFieldEntity : WorkspaceEntity {
  val version: Int
  val data: TestData
  val anotherVersion: Int
    @Default get() = 0
  val description: String
    @Default get() = "Default description"

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : DefaultFieldEntity, WorkspaceEntity.Builder<DefaultFieldEntity> {
    override var entitySource: EntitySource
    override var version: Int
    override var data: TestData
    override var anotherVersion: Int
    override var description: String
  }

  companion object : EntityType<DefaultFieldEntity, Builder>() {
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