package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.WorkspaceEntity

@Abstract
interface AbstractEntity1 : WorkspaceEntity {
  val property: String
}


@Abstract
interface AbstractEntity2 : AbstractEntity1 {
  val property: String
}

@Abstract
interface AbstractEntity3 : AbstractEntity2 {
  val property: String
}

@Abstract
interface AnotherAbstractEntity : WorkspaceEntity {
  val anotherProperty: String
}


interface MultipleInheritanceEntity : AbstractEntity3, AnotherAbstractEntity {
  val name: String
  val version: Int
}
