package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service

@Remote("com.intellij.openapi.roots.ProjectRootManager")
interface ProjectRootManager {
  fun getContentRoots(): Array<VirtualFile>
}

fun Driver.findFile(project: Project, relativePath: String): VirtualFile? {
  return withReadAction {
    service<ProjectRootManager>(project).getContentRoots()
      .firstNotNullOfOrNull { it.findFileByRelativePath(relativePath) }
  }
}