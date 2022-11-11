package com.intellij.workspaceModel.test.api

import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.WorkspaceEntity

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
