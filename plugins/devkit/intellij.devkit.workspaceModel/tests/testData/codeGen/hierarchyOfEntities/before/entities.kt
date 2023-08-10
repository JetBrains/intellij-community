package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Open

@Abstract
interface GrandParentEntity : WorkspaceEntity {
  val data1: String
}

@Open
interface ParentEntity : GrandParentEntity {
  val data2: String
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = oldCode
//endregion

interface ChildEntity: ParentEntity {
  val data3: String
}