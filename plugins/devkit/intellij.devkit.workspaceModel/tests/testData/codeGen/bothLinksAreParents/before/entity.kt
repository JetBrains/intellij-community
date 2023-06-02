package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspaceModel.storage.annotations.Child
import com.intellij.platform.workspaceModel.storage.WorkspaceEntity

interface MainEntity : WorkspaceEntity {
  val property: String
  val isValid: Boolean
  val secondaryEntities: List<SecondaryEntity>
}


interface SecondaryEntity : WorkspaceEntity {
  val name: String
  val version: Int

  val mainEntity: MainEntity
}
