package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean

  //region compatibility generated code
  interface Builder

  companion object : EntityType<SimpleEntity, Builder>() {
    fun foo() {
      // any code should be preserved in the original interface
      // we only delete code that is surronded by `region generated code` comments
    }
    @Deprecated(
      message = "This method is deprecated and will be removed in next major release",
      replaceWith = ReplaceWith("invoke(version, name, isSimple, entitySource, init)"),
    )
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    fun create(
      name: String,
      version: Int,
      isSimple: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = invoke(version, name, isSimple, entitySource, init)
  }
  //endregion compatibility generated code
}