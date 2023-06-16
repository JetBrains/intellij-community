package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import java.util.Date

interface UnknownPropertyTypeEntity : WorkspaceEntity {
  val date: Date
}