package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase

interface <warning descr="Absent entity implementation">EntityWithoutImplementation</warning> : WorkspaceEntity {
  val property: String
  val isValid: Boolean
}

interface <warning descr="Absent entity implementation">EntityWithFakeImplementation</warning> : WorkspaceEntity {
  val property: String
  val isValid: Boolean
}

internal class EntityWithFakeImplementationImpl() : EntityWithFakeImplementation {
  override val property: String = ""
  override val isValid: Boolean = false
}

interface EntityWithImplementation : WorkspaceEntity {
  val property: String
  val isValid: Boolean
}

internal class EntityWithImplementationImpl() : EntityWithImplementation, WorkspaceEntityBase() {
  override val property: String = ""
  override val isValid: Boolean = false
}