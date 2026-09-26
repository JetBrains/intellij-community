package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.openapi.project.BaseProjectDirectories")
interface BaseProjectDirectories {
  fun getBaseDirectories(project: Project): List<VirtualFile>
}
