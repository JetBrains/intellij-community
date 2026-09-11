package com.intellij.driver.sdk.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project


fun Driver.getProjectLevelVcsManagerInstance(project: Project) = utility(ProjectLevelVcsManager::class).getInstance(project)

@Remote("com.intellij.openapi.vcs.ProjectLevelVcsManager", plugin = "com.intellij/intellij.platform.vcs")
interface ProjectLevelVcsManager {
  fun getInstance(project: Project): ProjectLevelVcsManager
  fun getDirectoryMappings(): List<VcsDirectoryMapping>
}

@Remote("com.intellij.openapi.vcs.VcsDirectoryMapping", plugin = "com.intellij/intellij.platform.vcs")
interface VcsDirectoryMapping {
  fun getDirectory(): String
}
