package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service

@Remote("com.intellij.openapi.roots.ProjectFileIndex")
interface ProjectFileIndex {
  fun isExcluded(file: VirtualFile): Boolean

  fun isInProject(file: VirtualFile): Boolean
}

fun Driver.projectFileIndex(project: Project = singleProject()): ProjectFileIndex = service<ProjectFileIndex>(project)
