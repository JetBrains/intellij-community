package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface MainEntity : WorkspaceEntity {
  val property: String
  val isValid: Boolean
  val secondaryEntities: Set<@Child SecondaryEntity>
}


interface SecondaryEntity : WorkspaceEntity {
  val name: String
  val version: Int

  val mainEntity: MainEntity
}

interface PrimaryEntity : WorkspaceEntity {
  val primaryName: String

  val mainEntity: MainEntity
}

val MainEntity.primaryEntities: Set<@Child PrimaryEntity>