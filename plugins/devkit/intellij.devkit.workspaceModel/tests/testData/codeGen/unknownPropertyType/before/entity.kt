package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspaceModel.storage.WorkspaceEntity
import java.util.Date

interface UnknownPropertyTypeEntity : WorkspaceEntity {
  val date: Date
}