package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface CollectionFieldEntity : WorkspaceEntity {
  val versions: Set<Int>
  val names: List<String>
  val manifestAttributes: Map<String, String>
}
