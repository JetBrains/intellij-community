package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Open

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