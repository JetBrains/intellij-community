//new comment
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SimpleEntity, ModifiableWorkspaceEntity<SimpleEntity> {
    //obsolete
  }

  companion object : Type<SimpleEntity, Builder>() {
    operator fun invoke(version: Int,
                        entitySource: EntitySource,
                        name: String,
                        isSimple: Boolean,
                        init: (Builder.() -> Unit)? = null): SimpleEntity {
      TODO("obsolete")
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.oldCode() = Unit
//endregion