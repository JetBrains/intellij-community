package com.intellij.driver.sdk

import com.intellij.driver.client.ProjectRef
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.openapi.project.Project")
interface Project : ProjectRef {
  fun isOpen(): Boolean
  fun isInitialized(): Boolean

  fun getBasePath(): String
  fun getName(): String
  fun getPresentableUrl(): String?
  fun getProjectFile(): VirtualFile?
}
