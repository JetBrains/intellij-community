//some copyright comment
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspaceModel.storage.WorkspaceEntity

interface SimpleEntity : WorkspaceEntity {
  val name: String
}
