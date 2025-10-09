package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default

interface DefaultFieldEntity : WorkspaceEntity {
  val version: Int
  val data: TestData
  val anotherVersion: Int
    @Default get() = 0
  val description: String
    @Default get() = "Default description"
  val defaultSet: Set<String>
    @Default get() = emptySet<String>()
  val defaultList: List<String>
    @Default get() = emptyList<String>()
  val defaultMap: Map<String, String>
    @Default get() = emptyMap<String, String>()

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<DefaultFieldEntity> {
    override var entitySource: EntitySource
    var version: Int
    var data: TestData
    var anotherVersion: Int
    var description: String
    var defaultSet: MutableSet<String>
    var defaultList: MutableList<String>
    var defaultMap: Map<String, String>
  }

  companion object : EntityType<DefaultFieldEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      data: TestData,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyDefaultFieldEntity(
  entity: DefaultFieldEntity,
  modification: DefaultFieldEntity.Builder.() -> Unit,
): DefaultFieldEntity = modifyEntity(DefaultFieldEntity.Builder::class.java, entity, modification)
//endregion

data class TestData(val name: String, val description: String)