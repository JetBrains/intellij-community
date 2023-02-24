package com.intellij.workspaceModel.test.api

import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.WorkspaceEntity

interface MainEntity : WorkspaceEntity {
  val property: String
  var isValid: Boolean
}