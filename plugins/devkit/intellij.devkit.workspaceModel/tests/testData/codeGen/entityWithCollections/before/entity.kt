package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspaceModel.storage.WorkspaceEntity

interface CollectionFieldEntity : WorkspaceEntity {
  val versions: Set<Int>
  val names: List<String>
}